package io.github.carvalhoxx.gecko.data.remote.igdb

import io.github.carvalhoxx.gecko.core.ByokCredentialValidator
import io.github.carvalhoxx.gecko.core.CredentialValidationResult
import io.github.carvalhoxx.gecko.data.local.IgdbCredentials
import io.github.carvalhoxx.gecko.domain.model.ByokConnectionState
import io.github.carvalhoxx.gecko.domain.model.ByokProvider
import io.github.carvalhoxx.gecko.domain.model.ByokProviderStatus
import io.github.carvalhoxx.gecko.domain.model.ReleasePrecision
import io.github.carvalhoxx.gecko.domain.model.RemoteCatalogGame
import java.io.IOException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class IgdbHttpException(val statusCode: Int, message: String = "IGDB HTTP error $statusCode") : IOException(message)

private data class CachedIgdbToken(val clientId: String, val accessToken: String, val expiresAtMs: Long) {
    override fun toString(): String = "CachedIgdbToken(clientId=***, accessToken=***, expiresAtMs=$expiresAtMs)"

    fun isValid(nowMs: Long, requestedClientId: String): Boolean =
        clientId == requestedClientId && nowMs < expiresAtMs - EXPIRY_BUFFER_MS

    companion object {
        private const val EXPIRY_BUFFER_MS = 60_000L
    }
}

/**
 * Cliente HTTP BYOK para IGDB/Twitch (US1.1, RF01, RNF03, RNF05):
 * - Troca Client ID + Client Secret por App Access Token via POST OAuth2 (segredo apenas no corpo).
 * - Busca jogos com sintaxe APICalypse respeitando teto base de concorrência (4 requisições simultâneas).
 * - Nenhum segredo é incluído em logs, URLs ou mensagens de exceção.
 */
class IgdbApiClient(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val authBaseUrl: String = DEFAULT_AUTH_BASE_URL,
    private val apiBaseUrl: String = DEFAULT_API_BASE_URL,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {

    private val tokenMutex = Mutex()
    private val concurrencyLimiter = Semaphore(MAX_CONCURRENT_REQUESTS)
    private var cachedToken: CachedIgdbToken? = null

    suspend fun searchGames(credentials: IgdbCredentials, query: String, limit: Int = 10): List<RemoteCatalogGame> {
        val sanitized = sanitizeApicalypseQuery(query)
        if (sanitized.isEmpty()) return emptyList()

        val safeLimit = limit.coerceIn(1, MAX_SEARCH_LIMIT)
        val body = buildString {
            append("search \"$sanitized\"; ")
            append(GAME_FIELDS_CLAUSE)
            append(" limit $safeLimit;")
        }

        val rawResponse = executeApicalypsePost(credentials = credentials, endpoint = "/v4/games", queryBody = body)
        return parseGamesArray(rawResponse)
    }

    suspend fun testConnection(credentials: IgdbCredentials): ByokProviderStatus {
        val validation = ByokCredentialValidator.validateIgdbCredentials(
            clientId = credentials.clientId,
            clientSecret = credentials.clientSecret
        )
        if (validation is CredentialValidationResult.Invalid) {
            return ByokProviderStatus(
                provider = ByokProvider.IGDB,
                state = ByokConnectionState.ERROR,
                message = "Formato de credenciais IGDB inválido (${validation.reason.name})."
            )
        }

        return try {
            executeApicalypsePost(
                credentials = credentials,
                endpoint = "/v4/games",
                queryBody = "fields id; limit 1;"
            )
            ByokProviderStatus(
                provider = ByokProvider.IGDB,
                state = ByokConnectionState.CONNECTED,
                message = "IGDB/Twitch conectado com sucesso."
            )
        } catch (ex: IgdbHttpException) {
            val state = if (ex.statusCode == 401 || ex.statusCode == 403) {
                ByokConnectionState.EXPIRED
            } else {
                ByokConnectionState.ERROR
            }
            ByokProviderStatus(
                provider = ByokProvider.IGDB,
                state = state,
                message = "Falha de autenticação ou acesso no IGDB (HTTP ${ex.statusCode})."
            )
        } catch (_: IOException) {
            ByokProviderStatus(
                provider = ByokProvider.IGDB,
                state = ByokConnectionState.ERROR,
                message = "Falha de rede ao conectar ao IGDB."
            )
        }
    }

    private suspend fun executeApicalypsePost(
        credentials: IgdbCredentials,
        endpoint: String,
        queryBody: String
    ): String = concurrencyLimiter.withPermit {
        val accessToken = getOrRefreshAppToken(credentials)
        val request = Request.Builder()
            .url("${apiBaseUrl.trimEnd('/')}$endpoint")
            .header("Client-ID", credentials.clientId)
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/json")
            .post(queryBody.toRequestBody(PLAIN_TEXT_MEDIA_TYPE))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 401 || response.code == 403) {
                    invalidateToken()
                }
                throw IgdbHttpException(response.code)
            }
            response.body?.string().orEmpty()
        }
    }

    private suspend fun getOrRefreshAppToken(credentials: IgdbCredentials): String = tokenMutex.withLock {
        val nowMs = clockMs()
        val current = cachedToken
        if (current != null && current.isValid(nowMs = nowMs, requestedClientId = credentials.clientId)) {
            return@withLock current.accessToken
        }

        val formBody = FormBody.Builder()
            .add("client_id", credentials.clientId)
            .add("client_secret", credentials.clientSecret)
            .add("grant_type", "client_credentials")
            .build()

        val request = Request.Builder()
            .url("${authBaseUrl.trimEnd('/')}/oauth2/token")
            .header("Accept", "application/json")
            .post(formBody)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IgdbHttpException(response.code, "Twitch OAuth2 HTTP error ${response.code}")
            }
            val raw = response.body?.string().orEmpty()
            val root = json.parseToJsonElement(raw) as? JsonObject
                ?: throw IOException("Invalid Twitch OAuth2 JSON response.")
            val accessToken = (root["access_token"] as? JsonPrimitive)?.contentOrNull
                ?.takeIf { it.isNotBlank() }
                ?: throw IOException("Twitch OAuth2 response missing access_token.")
            val expiresInSeconds = (root["expires_in"] as? JsonPrimitive)?.longOrNull ?: DEFAULT_TOKEN_TTL_SECONDS
            val token = CachedIgdbToken(
                clientId = credentials.clientId,
                accessToken = accessToken,
                expiresAtMs = nowMs + expiresInSeconds.coerceAtLeast(60L) * 1_000L
            )
            cachedToken = token
            token.accessToken
        }
    }

    private suspend fun invalidateToken() {
        tokenMutex.withLock {
            cachedToken = null
        }
    }

    private fun parseGamesArray(rawBody: String): List<RemoteCatalogGame> {
        if (rawBody.isBlank()) return emptyList()
        val element = runCatching { json.parseToJsonElement(rawBody) }.getOrNull() as? JsonArray ?: return emptyList()
        return element.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            parseGameObject(obj)
        }
    }

    private fun parseGameObject(obj: JsonObject): RemoteCatalogGame? {
        val id = (obj["id"] as? JsonPrimitive)?.longOrNull ?: return null
        val name = (obj["name"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        val firstReleaseSeconds = (obj["first_release_date"] as? JsonPrimitive)?.longOrNull
        val releaseDates = obj["release_dates"] as? JsonArray
        val firstReleaseInfo = releaseDates?.firstOrNull() as? JsonObject
        val releaseDateMs = firstReleaseSeconds?.times(1_000L)
            ?: (firstReleaseInfo?.get("date") as? JsonPrimitive)?.longOrNull?.times(1_000L)
        val releaseCategory = (firstReleaseInfo?.get("category") as? JsonPrimitive)?.intOrNull
        val releasePrecision = mapReleasePrecision(category = releaseCategory, hasReleaseDate = releaseDateMs != null)

        val coverImageId = ((obj["cover"] as? JsonObject)?.get("image_id") as? JsonPrimitive)?.contentOrNull
        val coverUrl = coverImageId?.takeIf { it.isNotBlank() }?.let { buildIgdbImageUrl("t_cover_big", it) }

        val backdropImageId = extractFirstImageId(obj["artworks"] as? JsonArray)
            ?: extractFirstImageId(obj["screenshots"] as? JsonArray)
        val backdropUrl = backdropImageId?.takeIf { it.isNotBlank() }?.let { buildIgdbImageUrl("t_1080p", it) }

        val companies = obj["involved_companies"] as? JsonArray
        val developer = extractCompanyName(companies = companies, roleKey = "developer")
        val publisher = extractCompanyName(companies = companies, roleKey = "publisher")

        val genres = extractNamedList(obj["genres"] as? JsonArray)
        val platforms = extractNamedList(obj["platforms"] as? JsonArray)
        val synopsis = (obj["summary"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

        return RemoteCatalogGame(
            externalId = "igdb:$id",
            title = name,
            releaseDateMs = releaseDateMs,
            releasePrecision = releasePrecision,
            coverUrl = coverUrl,
            backdropUrl = backdropUrl,
            synopsis = synopsis,
            developer = developer,
            publisher = publisher,
            genres = genres,
            platforms = platforms
        )
    }

    private fun extractFirstImageId(array: JsonArray?): String? = array
        ?.asSequence()
        ?.mapNotNull { (it as? JsonObject)?.get("image_id") as? JsonPrimitive }
        ?.mapNotNull { it.contentOrNull }
        ?.firstOrNull { it.isNotBlank() }

    private fun extractCompanyName(companies: JsonArray?, roleKey: String): String? = companies
        ?.asSequence()
        ?.mapNotNull { it as? JsonObject }
        ?.firstOrNull { (it[roleKey] as? JsonPrimitive)?.booleanOrNull == true }
        ?.let { ((it["company"] as? JsonObject)?.get("name") as? JsonPrimitive)?.contentOrNull }
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    private fun extractNamedList(array: JsonArray?): List<String> = array
        ?.mapNotNull { ((it as? JsonObject)?.get("name") as? JsonPrimitive)?.contentOrNull?.trim() }
        ?.filter { it.isNotEmpty() }
        ?.distinct()
        ?: emptyList()

    private fun mapReleasePrecision(category: Int?, hasReleaseDate: Boolean): ReleasePrecision? = when (category) {
        0 -> ReleasePrecision.EXACT
        1 -> ReleasePrecision.MONTH
        2 -> ReleasePrecision.YEAR
        3, 4, 5, 6 -> ReleasePrecision.QUARTER
        7 -> ReleasePrecision.TBA
        else -> if (hasReleaseDate) ReleasePrecision.EXACT else null
    }

    private fun buildIgdbImageUrl(size: String, imageId: String): String =
        "https://images.igdb.com/igdb/image/upload/$size/$imageId.jpg"

    private fun sanitizeApicalypseQuery(raw: String): String = raw
        .trim()
        .replace("\\", "")
        .replace("\"", "")
        .replace(";", "")
        .replace(Regex("\\s+"), " ")

    companion object {
        const val DEFAULT_AUTH_BASE_URL = "https://id.twitch.tv"
        const val DEFAULT_API_BASE_URL = "https://api.igdb.com"
        const val MAX_CONCURRENT_REQUESTS = 4
        private const val MAX_SEARCH_LIMIT = 20
        private const val DEFAULT_TOKEN_TTL_SECONDS = 3_600L

        private val PLAIN_TEXT_MEDIA_TYPE = "text/plain; charset=utf-8".toMediaType()

        private const val GAME_FIELDS_CLAUSE =
            "fields id,name,summary,first_release_date,cover.image_id,artworks.image_id," +
                "screenshots.image_id,genres.name,platforms.name," +
                "involved_companies.developer,involved_companies.publisher,involved_companies.company.name," +
                "release_dates.date,release_dates.category;"
    }
}
