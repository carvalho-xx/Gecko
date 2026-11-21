package io.github.carvalhoxx.gecko.data.remote.steam

import io.github.carvalhoxx.gecko.core.ByokCredentialValidator
import io.github.carvalhoxx.gecko.core.CredentialValidationResult
import io.github.carvalhoxx.gecko.data.local.SteamCredentials
import io.github.carvalhoxx.gecko.domain.model.ByokConnectionState
import io.github.carvalhoxx.gecko.domain.model.ByokProvider
import io.github.carvalhoxx.gecko.domain.model.ByokProviderStatus
import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

data class SteamOwnedGame(val appId: Long, val title: String, val playtimeMs: Long, val coverUrl: String?)

data class SteamOwnedGamesResponse(val games: List<SteamOwnedGame>, val isProfileLikelyPrivate: Boolean)

class SteamHttpException(val statusCode: Int, message: String = "Steam HTTP error $statusCode") : IOException(message)

/**
 * Cliente HTTP BYOK para Steam Web API (IPlayerService/GetOwnedGames):
 * - Converte minutos (playtime_forever) para milissegundos Long destinados exclusivamente a platform_reported_time_ms.
 * - Trata perfis privados (objeto response vazio) sem exceções.
 * - Jamais expõe Steam API Key ou SteamID em mensagens de erro.
 */
class SteamApiClient(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val baseUrl: String = DEFAULT_BASE_URL
) {

    fun fetchOwnedGames(credentials: SteamCredentials): SteamOwnedGamesResponse {
        val url = "${baseUrl.trimEnd('/')}/IPlayerService/GetOwnedGames/v1/"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("key", credentials.apiKey)
            .addQueryParameter("steamid", credentials.steamId64)
            .addQueryParameter("include_appinfo", "1")
            .addQueryParameter("include_played_free_games", "1")
            .addQueryParameter("format", "json")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .get()
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw SteamHttpException(response.code)
            }
            val rawBody = response.body?.string().orEmpty()
            return parseOwnedGamesResponse(rawBody)
        }
    }

    fun testConnection(credentials: SteamCredentials): ByokProviderStatus {
        val keyValidation = ByokCredentialValidator.validateSteamApiKey(credentials.apiKey)
        if (keyValidation is CredentialValidationResult.Invalid) {
            return ByokProviderStatus(
                provider = ByokProvider.STEAM,
                state = ByokConnectionState.ERROR,
                message = "Steam Web API Key inválida (${keyValidation.reason.name})."
            )
        }
        val idValidation = ByokCredentialValidator.validateSteamId64(credentials.steamId64)
        if (idValidation is CredentialValidationResult.Invalid) {
            return ByokProviderStatus(
                provider = ByokProvider.STEAM,
                state = ByokConnectionState.ERROR,
                message = "SteamID64 inválido (${idValidation.reason.name})."
            )
        }

        return try {
            val result = fetchOwnedGames(credentials)
            val message = if (result.isProfileLikelyPrivate) {
                "Steam conectada, mas detalhes de jogos estão privados. Libere 'Detalhes de jogos' no perfil."
            } else {
                "Steam conectada (${result.games.size} jogos detectados)."
            }
            ByokProviderStatus(
                provider = ByokProvider.STEAM,
                state = ByokConnectionState.CONNECTED,
                message = message
            )
        } catch (ex: SteamHttpException) {
            val state = if (ex.statusCode == 401 || ex.statusCode == 403) {
                ByokConnectionState.EXPIRED
            } else {
                ByokConnectionState.ERROR
            }
            ByokProviderStatus(
                provider = ByokProvider.STEAM,
                state = state,
                message = "Chave Steam rejeitada ou sem permissão (HTTP ${ex.statusCode})."
            )
        } catch (_: IOException) {
            ByokProviderStatus(
                provider = ByokProvider.STEAM,
                state = ByokConnectionState.ERROR,
                message = "Falha de rede ao conectar à Steam."
            )
        }
    }

    private fun parseOwnedGamesResponse(rawBody: String): SteamOwnedGamesResponse {
        if (rawBody.isBlank()) {
            return SteamOwnedGamesResponse(games = emptyList(), isProfileLikelyPrivate = true)
        }
        val root = runCatching { json.parseToJsonElement(rawBody) }.getOrNull() as? JsonObject
            ?: return SteamOwnedGamesResponse(games = emptyList(), isProfileLikelyPrivate = true)
        val responseObj = root["response"] as? JsonObject
            ?: return SteamOwnedGamesResponse(games = emptyList(), isProfileLikelyPrivate = true)

        val gamesArray = responseObj["games"] as? JsonArray
        val gameCount = (responseObj["game_count"] as? JsonPrimitive)?.longOrNull
        if (gamesArray == null) {
            return SteamOwnedGamesResponse(
                games = emptyList(),
                isProfileLikelyPrivate = gameCount == null
            )
        }

        val parsed = gamesArray.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val appId = (obj["appid"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null
            val title = (obj["name"] as? JsonPrimitive)?.contentOrNull?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: "Steam App $appId"
            val minutes = (obj["playtime_forever"] as? JsonPrimitive)?.longOrNull ?: 0L
            SteamOwnedGame(
                appId = appId,
                title = title,
                playtimeMs = minutesToMillis(minutes),
                coverUrl = buildSteamCoverUrl(appId)
            )
        }
        return SteamOwnedGamesResponse(games = parsed, isProfileLikelyPrivate = false)
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.steampowered.com"
        private const val MILLIS_PER_MINUTE = 60_000L

        fun minutesToMillis(minutes: Long): Long = minutes.coerceAtLeast(0L) * MILLIS_PER_MINUTE

        fun buildSteamCoverUrl(appId: Long): String =
            "https://cdn.cloudflare.steamstatic.com/steam/apps/$appId/library_600x900.jpg"
    }
}
