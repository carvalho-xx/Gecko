package io.github.carvalhoxx.gecko.data.remote.rawg

import io.github.carvalhoxx.gecko.core.ByokCredentialValidator
import io.github.carvalhoxx.gecko.core.CredentialValidationResult
import io.github.carvalhoxx.gecko.data.local.RawgCredentials
import io.github.carvalhoxx.gecko.domain.model.ByokConnectionState
import io.github.carvalhoxx.gecko.domain.model.ByokProvider
import io.github.carvalhoxx.gecko.domain.model.ByokProviderStatus
import io.github.carvalhoxx.gecko.domain.model.ReleasePrecision
import io.github.carvalhoxx.gecko.domain.model.RemoteCatalogGame
import java.io.IOException
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class RawgHttpException(val statusCode: Int, message: String = "RAWG HTTP error $statusCode") : IOException(message)

/**
 * Cliente HTTP BYOK opcional para RAWG (fallback de capas/metadados):
 * - Respeita guarda local de cota mensal de 20.000 requisições/mês.
 * - Jamais expõe a chave RAWG em logs ou mensagens de erro.
 */
class RawgApiClient(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val monthlyQuotaLimit: Int = DEFAULT_MONTHLY_QUOTA_LIMIT,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {

    private val currentMonthKey = AtomicReference("")
    private val monthlyRequestCount = AtomicInteger(0)

    fun searchGames(credentials: RawgCredentials, query: String, pageSize: Int = 10): List<RemoteCatalogGame> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (!tryConsumeMonthlyQuota()) return emptyList()

        val url = "${baseUrl.trimEnd('/')}/api/games"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("key", credentials.apiKey)
            .addQueryParameter("search", trimmed)
            .addQueryParameter("page_size", pageSize.coerceIn(1, MAX_PAGE_SIZE).toString())
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .get()
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RawgHttpException(response.code)
            }
            val rawBody = response.body?.string().orEmpty()
            return parseResults(rawBody)
        }
    }

    fun testConnection(credentials: RawgCredentials): ByokProviderStatus {
        val validation = ByokCredentialValidator.validateRawgApiKey(credentials.apiKey)
        if (validation is CredentialValidationResult.Invalid) {
            return ByokProviderStatus(
                provider = ByokProvider.RAWG,
                state = ByokConnectionState.ERROR,
                message = "Chave RAWG com formato inválido (${validation.reason.name})."
            )
        }
        if (!tryConsumeMonthlyQuota()) {
            return ByokProviderStatus(
                provider = ByokProvider.RAWG,
                state = ByokConnectionState.ERROR,
                message = "Limite mensal local da cota RAWG atingido ($monthlyQuotaLimit req/mês)."
            )
        }

        val url = "${baseUrl.trimEnd('/')}/api/games"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("key", credentials.apiKey)
            .addQueryParameter("page_size", "1")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .get()
            .build()

        return try {
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    ByokProviderStatus(
                        provider = ByokProvider.RAWG,
                        state = ByokConnectionState.CONNECTED,
                        message = "RAWG conectado com sucesso."
                    )
                } else {
                    val state = if (response.code == 401 || response.code == 403) {
                        ByokConnectionState.EXPIRED
                    } else {
                        ByokConnectionState.ERROR
                    }
                    ByokProviderStatus(
                        provider = ByokProvider.RAWG,
                        state = state,
                        message = "Chave RAWG rejeitada ou indisponível (HTTP ${response.code})."
                    )
                }
            }
        } catch (_: IOException) {
            ByokProviderStatus(
                provider = ByokProvider.RAWG,
                state = ByokConnectionState.ERROR,
                message = "Falha de rede ao conectar à RAWG."
            )
        }
    }

    private fun tryConsumeMonthlyQuota(): Boolean {
        val monthKey = computeMonthKey(clockMs())
        val previous = currentMonthKey.get()
        if (previous != monthKey && currentMonthKey.compareAndSet(previous, monthKey)) {
            monthlyRequestCount.set(0)
        }
        val count = monthlyRequestCount.incrementAndGet()
        if (count > monthlyQuotaLimit) {
            monthlyRequestCount.decrementAndGet()
            return false
        }
        return true
    }

    private fun computeMonthKey(timestampMs: Long): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = timestampMs
        }
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        return "$year-$month"
    }

    private fun parseResults(rawBody: String): List<RemoteCatalogGame> {
        if (rawBody.isBlank()) return emptyList()
        val root = runCatching { json.parseToJsonElement(rawBody) }.getOrNull() as? JsonObject ?: return emptyList()
        val results = root["results"] as? JsonArray ?: return emptyList()

        return results.mapNotNull { entry ->
            val obj = entry as? JsonObject ?: return@mapNotNull null
            val id = (obj["id"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null
            val title = (obj["name"] as? JsonPrimitive)?.contentOrNull?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            val tba = (obj["tba"] as? JsonPrimitive)?.booleanOrNull == true
            val releasedRaw = (obj["released"] as? JsonPrimitive)?.contentOrNull
            val releaseDateMs = parseIsoDateToUtcMillis(releasedRaw)
            val precision = when {
                tba -> ReleasePrecision.TBA
                releaseDateMs != null -> ReleasePrecision.EXACT
                else -> null
            }
            val backgroundImage = (obj["background_image"] as? JsonPrimitive)?.contentOrNull
                ?.takeIf { it.isNotBlank() }

            val genres = (obj["genres"] as? JsonArray)
                ?.mapNotNull { ((it as? JsonObject)?.get("name") as? JsonPrimitive)?.contentOrNull?.trim() }
                ?.filter { it.isNotEmpty() }
                ?.distinct()
                ?: emptyList()

            val platforms = (obj["platforms"] as? JsonArray)
                ?.mapNotNull {
                    val platformObj = (it as? JsonObject)?.get("platform") as? JsonObject
                    (platformObj?.get("name") as? JsonPrimitive)?.contentOrNull?.trim()
                }
                ?.filter { it.isNotEmpty() }
                ?.distinct()
                ?: emptyList()

            RemoteCatalogGame(
                externalId = "rawg:$id",
                title = title,
                releaseDateMs = releaseDateMs,
                releasePrecision = precision,
                coverUrl = backgroundImage,
                backdropUrl = backgroundImage,
                genres = genres,
                platforms = platforms
            )
        }
    }

    private fun parseIsoDateToUtcMillis(raw: String?): Long? {
        val parts = raw?.trim()?.split('-') ?: return null
        if (parts.size != 3) return null
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val day = parts[2].toIntOrNull() ?: return null
        if (month !in 1..12 || day !in 1..31) return null

        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month - 1, day, 0, 0, 0)
        }.timeInMillis
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.rawg.io"
        const val DEFAULT_MONTHLY_QUOTA_LIMIT = 20_000
        private const val MAX_PAGE_SIZE = 20
    }
}
