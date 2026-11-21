package io.github.carvalhoxx.gecko.data.remote.hltb

import kotlin.math.roundToLong
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class HltbSessionHandshake(val sessionToken: String, val hpKey: String, val hpVal: String, val expiresAtMs: Long) {
    fun isExpired(nowMs: Long): Boolean = nowMs >= expiresAtMs
}

/**
 * Extrator adaptativo (AST JSON + fallback Regex) para a resposta de /api/find/init do HLTB
 * e conversores puros de durações HLTB para Long ms (RNF07).
 */
object HltbHoneypotExtractor {

    const val DEFAULT_SESSION_TTL_MS: Long = 15L * 60_000L // 15 min
    private const val HOUR_MS: Long = 3_600_000L
    private const val SECOND_MS: Long = 1_000L

    private val tokenRegex = Regex(""""token"\s*:\s*"([^"]+)"""")
    private val hpKeyRegex = Regex(""""hpKey"\s*:\s*"([^"]+)"""")
    private val hpValRegex = Regex(""""hpVal"\s*:\s*"([^"]+)"""")

    fun extract(
        rawJson: String,
        nowMs: Long,
        json: Json = Json { ignoreUnknownKeys = true },
        ttlMs: Long = DEFAULT_SESSION_TTL_MS
    ): HltbSessionHandshake? {
        val fromAst = extractFromJsonAst(rawJson, json)
        val token = fromAst?.first ?: tokenRegex.find(rawJson)?.groupValues?.getOrNull(1)
        val hpKey = fromAst?.second ?: hpKeyRegex.find(rawJson)?.groupValues?.getOrNull(1)
        val hpVal = fromAst?.third ?: hpValRegex.find(rawJson)?.groupValues?.getOrNull(1)

        if (token.isNullOrBlank() || hpKey.isNullOrBlank() || hpVal.isNullOrBlank()) {
            return null
        }

        return HltbSessionHandshake(
            sessionToken = token,
            hpKey = hpKey,
            hpVal = hpVal,
            expiresAtMs = nowMs + ttlMs
        )
    }

    fun secondsToMillis(seconds: Long): Long = seconds.coerceAtLeast(0L) * SECOND_MS

    fun decimalHoursToMillis(hours: Double): Long {
        if (!hours.isFinite() || hours <= 0.0) return 0L
        return (hours * HOUR_MS.toDouble()).roundToLong().coerceAtLeast(0L)
    }

    private fun extractFromJsonAst(rawJson: String, json: Json): Triple<String?, String?, String?>? = try {
        val root = json.parseToJsonElement(rawJson)
        val token = findStringRecursively(root, "token")
        val hpKey = findStringRecursively(root, "hpKey")
        val hpVal = findStringRecursively(root, "hpVal")
        Triple(token, hpKey, hpVal)
    } catch (_: Exception) {
        null
    }

    private fun findStringRecursively(element: JsonElement, key: String): String? {
        if (element !is JsonObject) return null
        val direct = (element[key] as? JsonPrimitive)?.contentOrNull
        if (!direct.isNullOrBlank()) return direct
        element.values.forEach { child ->
            val nested = findStringRecursively(child, key)
            if (!nested.isNullOrBlank()) return nested
        }
        return null
    }
}
