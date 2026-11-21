package io.github.carvalhoxx.gecko.data.remote.psn

import io.github.carvalhoxx.gecko.core.ByokCredentialValidator
import io.github.carvalhoxx.gecko.core.CredentialValidationResult
import io.github.carvalhoxx.gecko.data.local.SecureCredentialsStorage
import io.github.carvalhoxx.gecko.domain.model.ByokConnectionState
import io.github.carvalhoxx.gecko.domain.model.ByokProvider
import io.github.carvalhoxx.gecko.domain.model.ByokProviderStatus
import java.io.IOException
import kotlin.math.roundToLong
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

data class PsnAuthTokens(val accessToken: String, val refreshToken: String?, val expiresAtMs: Long) {
    override fun toString(): String {
        val maskedRefresh = if (refreshToken != null) "***" else "null"
        return "PsnAuthTokens(accessToken=***, refreshToken=$maskedRefresh, expiresAtMs=$expiresAtMs)"
    }
}

data class PsnPlayedTitle(val titleId: String, val title: String, val playtimeMs: Long, val coverUrl: String?)

sealed interface PsnTitlesFetchResult {
    data class Success(val titles: List<PsnPlayedTitle>) : PsnTitlesFetchResult

    data class PrivacyRestricted(val errorCode: Int? = null, val message: String) : PsnTitlesFetchResult
}

class PsnHttpException(
    val statusCode: Int,
    val psnErrorCode: Int? = null,
    message: String = "PSN HTTP error $statusCode"
) : IOException(message)

/**
 * Parser puro (JVM) para durações ISO-8601 retornadas pela telemetria PSN (ex.: PT42H15M30S, P1DT3H).
 * Converte diretamente para milissegundos Long (invariante ANIR sem kotlin.time.Duration).
 */
object PsnDurationParser {
    private val isoDurationRegex = Regex(
        pattern = "^P(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+(?:\\.\\d+)?)S)?)?$",
        option = RegexOption.IGNORE_CASE
    )

    private const val MS_PER_SECOND = 1_000L
    private const val MS_PER_MINUTE = 60_000L
    private const val MS_PER_HOUR = 3_600_000L
    private const val MS_PER_DAY = 86_400_000L

    fun parseIso8601ToMillis(raw: String?): Long {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed.equals("P", ignoreCase = true) || trimmed.equals("PT", ignoreCase = true)) {
            return 0L
        }
        val match = isoDurationRegex.matchEntire(trimmed) ?: return 0L
        val days = match.groupValues[1].toLongOrNull() ?: 0L
        val hours = match.groupValues[2].toLongOrNull() ?: 0L
        val minutes = match.groupValues[3].toLongOrNull() ?: 0L
        val secondsMs = match.groupValues[4].toDoubleOrNull()?.let { (it * MS_PER_SECOND).roundToLong() } ?: 0L
        return days * MS_PER_DAY + hours * MS_PER_HOUR + minutes * MS_PER_MINUTE + secondsMs
    }
}

/**
 * Cliente HTTP BYOK MVP para PlayStation Network (US1.1, RF01, RF07, RNF03):
 * - Valida cookie npsso (64 chars), troca por Access/Refresh Token OAuth2 e persiste no EncryptedSharedPreferences.
 * - Lê tempos jogados via /api/gamelist/v2/users/me/titles tratando respostas vazias e erro 2281077 graciosamente.
 * - Automação periódica de 60 dias e Token Bucket (300/15min) ficam para a Etapa 4.1 (v1.1).
 */
class PsnApiClient(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val authBaseUrl: String = DEFAULT_AUTH_BASE_URL,
    private val apiBaseUrl: String = DEFAULT_API_BASE_URL,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {

    private val noRedirectClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    fun exchangeNpssoForTokens(npsso: String): PsnAuthTokens {
        val validation = ByokCredentialValidator.validatePsnNpsso(npsso)
        if (validation is CredentialValidationResult.Invalid) {
            throw IllegalArgumentException("Invalid PSN npsso format (${validation.reason.name}).")
        }

        val authCode = requestAuthorizationCode(npsso)
        return exchangeAuthCodeForTokens(authCode)
    }

    fun getOrRefreshAccessToken(storage: SecureCredentialsStorage): String? {
        val credentials = storage.getPsnCredentials() ?: return null
        val nowMs = clockMs()
        val existingAccess = credentials.accessToken
        val expiresAt = credentials.expiresAtMs
        if (!existingAccess.isNullOrBlank() && expiresAt != null && nowMs < expiresAt - TOKEN_EXPIRY_BUFFER_MS) {
            return existingAccess
        }

        val tokens = exchangeNpssoForTokens(credentials.npsso)
        storage.updatePsnTokens(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            expiresAtMs = tokens.expiresAtMs
        )
        return tokens.accessToken
    }

    fun fetchPlayedTitles(accessToken: String, limit: Int = 50, offset: Int = 0): PsnTitlesFetchResult {
        val url = "${apiBaseUrl.trimEnd('/')}/api/gamelist/v2/users/me/titles"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("limit", limit.coerceIn(1, 200).toString())
            .addQueryParameter("offset", offset.coerceAtLeast(0).toString())
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/json")
            .header("Accept-Language", "en-US")
            .get()
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val rawBody = response.body?.string().orEmpty()
            val errorCode = extractPsnErrorCode(rawBody)

            if (errorCode == PSN_PRIVACY_ERROR_CODE) {
                return PsnTitlesFetchResult.PrivacyRestricted(
                    errorCode = errorCode,
                    message = PRIVACY_GUIDANCE_MESSAGE
                )
            }

            if (!response.isSuccessful) {
                throw PsnHttpException(statusCode = response.code, psnErrorCode = errorCode)
            }

            return parsePlayedTitles(rawBody)
        }
    }

    fun testConnection(storage: SecureCredentialsStorage): ByokProviderStatus {
        val credentials = storage.getPsnCredentials()
            ?: return ByokProviderStatus(
                provider = ByokProvider.PSN,
                state = ByokConnectionState.NOT_CONFIGURED,
                message = "Cookie npsso não configurado."
            )

        val validation = ByokCredentialValidator.validatePsnNpsso(credentials.npsso)
        if (validation is CredentialValidationResult.Invalid) {
            return ByokProviderStatus(
                provider = ByokProvider.PSN,
                state = ByokConnectionState.ERROR,
                message = "Formato de cookie npsso inválido (esperado: 64 caracteres alfanuméricos)."
            )
        }

        return try {
            val token = getOrRefreshAccessToken(storage)
                ?: return ByokProviderStatus(
                    provider = ByokProvider.PSN,
                    state = ByokConnectionState.NOT_CONFIGURED
                )
            when (val result = fetchPlayedTitles(accessToken = token, limit = 1)) {
                is PsnTitlesFetchResult.Success -> ByokProviderStatus(
                    provider = ByokProvider.PSN,
                    state = ByokConnectionState.CONNECTED,
                    message = "PSN conectada com sucesso."
                )

                is PsnTitlesFetchResult.PrivacyRestricted -> ByokProviderStatus(
                    provider = ByokProvider.PSN,
                    state = ByokConnectionState.CONNECTED,
                    message = result.message
                )
            }
        } catch (ex: PsnHttpException) {
            val state = if (ex.statusCode == 401 || ex.statusCode == 403) {
                ByokConnectionState.EXPIRED
            } else {
                ByokConnectionState.ERROR
            }
            ByokProviderStatus(
                provider = ByokProvider.PSN,
                state = state,
                message = if (state == ByokConnectionState.EXPIRED) {
                    "Sessão PSN expirada ou cookie npsso inválido (HTTP ${ex.statusCode})."
                } else {
                    "Erro ao comunicar com a PSN (HTTP ${ex.statusCode})."
                }
            )
        } catch (_: IOException) {
            ByokProviderStatus(
                provider = ByokProvider.PSN,
                state = ByokConnectionState.ERROR,
                message = "Falha de rede ao conectar à PSN."
            )
        }
    }

    private fun requestAuthorizationCode(npsso: String): String {
        val authorizeUrl = "${authBaseUrl.trimEnd('/')}/api/authz/v3/oauth/authorize"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("access_type", "offline")
            .addQueryParameter("client_id", PSN_OAUTH_CLIENT_ID)
            .addQueryParameter("redirect_uri", PSN_REDIRECT_URI)
            .addQueryParameter("response_type", "code")
            .addQueryParameter("scope", PSN_OAUTH_SCOPE)
            .build()

        val request = Request.Builder()
            .url(authorizeUrl)
            .header("Cookie", "npsso=$npsso")
            .header("Accept", "application/json")
            .get()
            .build()

        noRedirectClient.newCall(request).execute().use { response ->
            val location = response.header("Location")
            if (location != null) {
                val code = extractQueryParam(location, "code")
                if (!code.isNullOrBlank()) {
                    return code
                }
                throw PsnHttpException(statusCode = 401, message = "PSN authorize redirect missing code.")
            }
            val statusCode = if (response.code in 200..299) 401 else response.code
            throw PsnHttpException(statusCode = statusCode, message = "PSN authorize failed with HTTP $statusCode.")
        }
    }

    private fun exchangeAuthCodeForTokens(authCode: String): PsnAuthTokens {
        val formBody = FormBody.Builder()
            .add("code", authCode)
            .add("redirect_uri", PSN_REDIRECT_URI)
            .add("grant_type", "authorization_code")
            .add("token_format", "jwt")
            .build()

        val request = Request.Builder()
            .url("${authBaseUrl.trimEnd('/')}/api/authz/v3/oauth/token")
            .header("Authorization", PSN_BASIC_AUTH_HEADER)
            .header("Accept", "application/json")
            .post(formBody)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw PsnHttpException(statusCode = response.code, message = "PSN token HTTP error ${response.code}")
            }
            val raw = response.body?.string().orEmpty()
            val root = runCatching { json.parseToJsonElement(raw) }.getOrNull() as? JsonObject
                ?: throw IOException("Invalid PSN OAuth token response.")
            val accessToken = (root["access_token"] as? JsonPrimitive)?.contentOrNull
                ?.takeIf { it.isNotBlank() }
                ?: throw IOException("PSN OAuth token response missing access_token.")
            val refreshToken = (root["refresh_token"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            val expiresInSeconds = (root["expires_in"] as? JsonPrimitive)?.longOrNull ?: DEFAULT_ACCESS_TTL_SECONDS
            return PsnAuthTokens(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAtMs = clockMs() + expiresInSeconds.coerceAtLeast(60L) * 1_000L
            )
        }
    }

    private fun parsePlayedTitles(rawBody: String): PsnTitlesFetchResult {
        if (rawBody.isBlank()) {
            return PsnTitlesFetchResult.PrivacyRestricted(message = PRIVACY_GUIDANCE_MESSAGE)
        }
        val root = runCatching { json.parseToJsonElement(rawBody) }.getOrNull() as? JsonObject
            ?: return PsnTitlesFetchResult.PrivacyRestricted(message = PRIVACY_GUIDANCE_MESSAGE)

        val errorCode = extractPsnErrorCode(root)
        if (errorCode == PSN_PRIVACY_ERROR_CODE) {
            return PsnTitlesFetchResult.PrivacyRestricted(errorCode = errorCode, message = PRIVACY_GUIDANCE_MESSAGE)
        }

        val titlesElement = root["titles"]
        if (titlesElement !is JsonArray) {
            return PsnTitlesFetchResult.PrivacyRestricted(message = PRIVACY_GUIDANCE_MESSAGE)
        }

        val titles = titlesElement.mapNotNull { entry ->
            val obj = entry as? JsonObject ?: return@mapNotNull null
            val titleId = (obj["titleId"] as? JsonPrimitive)?.contentOrNull?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            val name = (obj["name"] as? JsonPrimitive)?.contentOrNull?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: "PSN Title $titleId"
            val durationRaw = (obj["playDuration"] as? JsonPrimitive)?.contentOrNull
            val coverUrl = (obj["imageUrl"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            PsnPlayedTitle(
                titleId = titleId,
                title = name,
                playtimeMs = PsnDurationParser.parseIso8601ToMillis(durationRaw),
                coverUrl = coverUrl
            )
        }
        return PsnTitlesFetchResult.Success(titles)
    }

    private fun extractPsnErrorCode(rawBody: String): Int? {
        if (rawBody.isBlank()) return null
        val root = runCatching { json.parseToJsonElement(rawBody) }.getOrNull() as? JsonObject ?: return null
        return extractPsnErrorCode(root)
    }

    private fun extractPsnErrorCode(root: JsonObject): Int? {
        val errorObj = root["error"] as? JsonObject ?: return null
        return (errorObj["code"] as? JsonPrimitive)?.intOrNull
    }

    private fun extractQueryParam(location: String, paramName: String): String? {
        val directUrl = location.toHttpUrlOrNull()
        if (directUrl != null) {
            return directUrl.queryParameter(paramName)
        }
        val queryStart = location.indexOf('?')
        if (queryStart == -1 || queryStart == location.lastIndex) return null
        val queryPart = location.substring(queryStart + 1).substringBefore('#')
        return queryPart.split('&')
            .map { it.split('=', limit = 2) }
            .firstOrNull { it.size == 2 && it[0] == paramName }
            ?.get(1)
    }

    companion object {
        const val DEFAULT_AUTH_BASE_URL = "https://ca.account.sony.com"
        const val DEFAULT_API_BASE_URL = "https://m.np.playstation.com"
        const val PSN_PRIVACY_ERROR_CODE = 2281077

        private const val PSN_OAUTH_CLIENT_ID = "09515159-7237-4370-9b40-3806e67c0891"
        private const val PSN_REDIRECT_URI = "com.scee.psxandroid.scecompcall://redirect"
        private const val PSN_OAUTH_SCOPE = "psn:mobile.v2.core psn:clientapp"
        private const val PSN_BASIC_AUTH_HEADER =
            "Basic MDk1MTUxNTktNzIzNy00MzcwLTliNDAtMzgwNmU2N2MwODkxOnVjUGprYTV0bnRCMktxc1A="
        private const val DEFAULT_ACCESS_TTL_SECONDS = 3_600L
        private const val TOKEN_EXPIRY_BUFFER_MS = 60_000L

        const val PRIVACY_GUIDANCE_MESSAGE =
            "A privacidade da PSN ocultou o histórico de jogos. Desbloqueie 'Atividade de Jogo' no console."
    }
}
