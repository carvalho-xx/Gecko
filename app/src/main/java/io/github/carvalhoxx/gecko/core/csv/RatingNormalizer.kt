package io.github.carvalhoxx.gecko.core.csv

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * TU-04 / RF22: Normaliza notas em formatos mistos ("9,25", "9.25", "10", "11")
 * para Float na escala válida do Gecko ([0.00..10.00] ou 11.00 exato).
 */
object RatingNormalizer {

    private const val MIN_RATING = 0.00f
    private const val MAX_REGULAR_RATING = 10.00f
    private const val HONORARY_RATING = 11.00f
    private const val EPSILON = 1e-4f

    private val ratingRegex = Regex("^\\s*(\\d{1,2}(?:[.,]\\d{1,2})?)\\s*$")

    fun normalizeOrNull(raw: String?): Float? {
        if (raw.isNullOrBlank()) return null
        val match = ratingRegex.matchEntire(raw) ?: return null
        val normalizedText = match.groupValues[1].replace(',', '.')
        val parsed = normalizedText.toDoubleOrNull() ?: return null

        val rounded = (parsed * 100.0).roundToInt() / 100.0
        val asFloat = rounded.toFloat()

        return when {
            asFloat < MIN_RATING -> null
            abs(asFloat - HONORARY_RATING) <= EPSILON -> HONORARY_RATING
            asFloat <= MAX_REGULAR_RATING + EPSILON -> asFloat
            else -> null
        }
    }
}
