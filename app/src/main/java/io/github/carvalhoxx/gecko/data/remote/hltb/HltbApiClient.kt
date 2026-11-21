package io.github.carvalhoxx.gecko.data.remote.hltb

import java.io.IOException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class HltbRemoteResult(val title: String, val mainStoryMs: Long, val mainSidesMs: Long, val completionistMs: Long)

class HltbHttpException(val statusCode: Int, message: String = "HLTB HTTP error $statusCode") : IOException(message)

/**
 * Cliente HTTP em 2 etapas para HowLongToBeat:
 * 1) GET /api/find/init com Desktop User-Agent consistente + Referer.
 * 2) POST /api/find injetando hpKey/hpVal simultaneamente nos headers HTTP e na raiz do JSON.
 *
 * Em HTTP 403/404 ou falha de rede, lança exceção imediatamente para o Circuit Breaker degradar
 * graciosamente ao cache local Room (sem contornar desafios anti-bot/Turnstile).
 */
class HltbApiClient(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val userAgent: String = DEFAULT_DESKTOP_USER_AGENT,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {

    private val handshakeMutex = Mutex()
    private var cachedHandshake: HltbSessionHandshake? = null

    suspend fun searchGame(title: String): HltbRemoteResult? {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty()) return null

        val handshake = getOrFetchHandshake()
        val payload = buildFindPayload(title = trimmedTitle, handshake = handshake)

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/find")
            .header("User-Agent", userAgent)
            .header("Referer", HLTB_REFERER)
            .header("Origin", HLTB_ORIGIN)
            .header("Accept", "application/json")
            .header("x-auth-token", handshake.sessionToken)
            .header(handshake.hpKey, handshake.hpVal)
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 401 || response.code == 403 || response.code == 404) {
                    invalidateHandshake()
                }
                throw HltbHttpException(response.code)
            }
            val body = response.body?.string().orEmpty()
            return parseBestMatch(body)
        }
    }

    private suspend fun getOrFetchHandshake(): HltbSessionHandshake = handshakeMutex.withLock {
        val nowMs = clockMs()
        val existing = cachedHandshake
        if (existing != null && !existing.isExpired(nowMs)) {
            return@withLock existing
        }

        val initUrl = "${baseUrl.trimEnd('/')}/api/find/init?t=$nowMs"
        val request = Request.Builder()
            .url(initUrl)
            .header("User-Agent", userAgent)
            .header("Referer", HLTB_REFERER)
            .header("Origin", HLTB_ORIGIN)
            .header("Accept", "application/json")
            .get()
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw HltbHttpException(response.code)
            }
            val rawBody = response.body?.string().orEmpty()
            val extracted = HltbHoneypotExtractor.extract(rawBody, nowMs, json)
                ?: throw IOException("HLTB /api/find/init response missing handshake fields.")
            cachedHandshake = extracted
            extracted
        }
    }

    private suspend fun invalidateHandshake() {
        handshakeMutex.withLock {
            cachedHandshake = null
        }
    }

    private fun buildFindPayload(title: String, handshake: HltbSessionHandshake): String {
        val terms = title.split(Regex("\\s+")).filter { it.isNotBlank() }
        val root = buildJsonObject {
            put("searchType", "games")
            put("searchTerms", buildJsonArray { terms.forEach { add(JsonPrimitive(it)) } })
            put("searchPage", 1)
            put("size", 5)
            put(
                "searchOptions",
                buildJsonObject {
                    put(
                        "games",
                        buildJsonObject {
                            put("userId", 0)
                            put("platform", "")
                            put("sortCategory", "popular")
                            put("rangeCategory", "main")
                            put(
                                "rangeTime",
                                buildJsonObject {
                                    put("min", 0)
                                    put("max", 0)
                                }
                            )
                            put(
                                "gameplay",
                                buildJsonObject {
                                    put("perspective", "")
                                    put("flow", "")
                                    put("genre", "")
                                }
                            )
                            put("modifier", "")
                        }
                    )
                    put("users", buildJsonObject { put("sortCategory", "postcount") })
                    put("filter", "")
                    put("sort", 0)
                    put("randomizer", 0)
                }
            )
            put(handshake.hpKey, handshake.hpVal)
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    private fun parseBestMatch(rawBody: String): HltbRemoteResult? {
        if (rawBody.isBlank()) return null
        val root = json.parseToJsonElement(rawBody) as? JsonObject ?: return null
        val data = root["data"] as? JsonArray ?: return null
        val firstItem = data.firstOrNull() as? JsonObject ?: return null

        val gameName = (firstItem["game_name"] as? JsonPrimitive)?.contentOrNull ?: return null
        val mainStoryMs = extractDurationMs(firstItem, secondsKey = "comp_main", hoursKey = "comp_main_hours")
        val mainSidesMs = extractDurationMs(firstItem, secondsKey = "comp_plus", hoursKey = "comp_plus_hours")
        val completionistMs = extractDurationMs(firstItem, secondsKey = "comp_100", hoursKey = "comp_100_hours")

        return HltbRemoteResult(
            title = gameName,
            mainStoryMs = mainStoryMs,
            mainSidesMs = mainSidesMs.coerceAtLeast(mainStoryMs),
            completionistMs = completionistMs.coerceAtLeast(mainSidesMs.coerceAtLeast(mainStoryMs))
        )
    }

    private fun extractDurationMs(item: JsonObject, secondsKey: String, hoursKey: String): Long {
        val seconds = (item[secondsKey] as? JsonPrimitive)?.longOrNull
        if (seconds != null && seconds > 0L) {
            return HltbHoneypotExtractor.secondsToMillis(seconds)
        }
        val decimalHours = (item[hoursKey] as? JsonPrimitive)?.doubleOrNull
        if (decimalHours != null && decimalHours > 0.0) {
            return HltbHoneypotExtractor.decimalHoursToMillis(decimalHours)
        }
        return 0L
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://howlongtobeat.com"
        const val HLTB_REFERER = "https://howlongtobeat.com/"
        const val HLTB_ORIGIN = "https://howlongtobeat.com"
        const val DEFAULT_DESKTOP_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
