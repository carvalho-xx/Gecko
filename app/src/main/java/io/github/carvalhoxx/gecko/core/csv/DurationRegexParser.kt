package io.github.carvalhoxx.gecko.core.csv

import kotlin.math.roundToLong

/**
 * RNF07 / TU-03: Conversor Regex de durações arbitrárias (incluindo > 24h) diretamente para Long ms,
 * sem truncamento de relógio de 24 horas e sem uso de kotlin.time.Duration.
 */
object DurationRegexParser {

    private const val HOUR_MS = 3_600_000L
    private const val MINUTE_MS = 60_000L
    private const val SECOND_MS = 1_000L

    private val hmsRegex = Regex("^\\s*(\\d{1,5}):([0-5]?\\d):([0-5]?\\d)(?:\\.(\\d{1,3}))?\\s*$")
    private val hmRegex = Regex("^\\s*(\\d{1,5}):([0-5]?\\d)\\s*$")
    private val decimalHoursRegex = Regex("^\\s*(\\d{1,5})(?:[.,](\\d{1,2}))?\\s*h\\s*$", RegexOption.IGNORE_CASE)

    fun parseToMillisOrNull(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null

        hmsRegex.matchEntire(raw)?.let { match ->
            val hours = match.groupValues[1].toLongOrNull() ?: return null
            val minutes = match.groupValues[2].toLongOrNull() ?: return null
            val seconds = match.groupValues[3].toLongOrNull() ?: return null
            val millisStr = match.groupValues[4]
            val millis = if (millisStr.isEmpty()) {
                0L
            } else {
                millisStr.padEnd(3, '0').toLongOrNull() ?: return null
            }
            return hours * HOUR_MS + minutes * MINUTE_MS + seconds * SECOND_MS + millis
        }

        hmRegex.matchEntire(raw)?.let { match ->
            val hours = match.groupValues[1].toLongOrNull() ?: return null
            val minutes = match.groupValues[2].toLongOrNull() ?: return null
            return hours * HOUR_MS + minutes * MINUTE_MS
        }

        decimalHoursRegex.matchEntire(raw)?.let { match ->
            val wholeHours = match.groupValues[1].toLongOrNull() ?: return null
            val fractionStr = match.groupValues[2]
            val fractionHours = if (fractionStr.isEmpty()) {
                0.0
            } else {
                "0.$fractionStr".toDoubleOrNull() ?: return null
            }
            return wholeHours * HOUR_MS + (fractionHours * HOUR_MS.toDouble()).roundToLong()
        }

        return null
    }
}
