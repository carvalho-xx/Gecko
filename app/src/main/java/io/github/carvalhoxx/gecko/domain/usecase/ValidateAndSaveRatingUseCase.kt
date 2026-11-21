package io.github.carvalhoxx.gecko.domain.usecase

import io.github.carvalhoxx.gecko.domain.model.GameItem
import io.github.carvalhoxx.gecko.domain.repository.GameRepository
import kotlin.math.abs
import kotlin.math.roundToInt

enum class RatingValidationError {
    NOT_FINITE,
    OUT_OF_RANGE,
    TOO_MANY_DECIMALS
}

sealed interface RatingValidationResult {
    data class Valid(val normalizedRating: Float) : RatingValidationResult

    data class Invalid(val error: RatingValidationError) : RatingValidationResult
}

sealed interface SaveRatingResult {
    data class Success(val updatedGame: GameItem) : SaveRatingResult

    data class InvalidRating(val error: RatingValidationError) : SaveRatingResult

    data class GameNotFound(val gameId: String) : SaveRatingResult
}

/**
 * RF22: Valida e persiste notas na escala [0.00..10.00] (até 2 casas decimais)
 * ou o valor honorário exato 11.00 ("Jogo da Vida").
 */
class ValidateAndSaveRatingUseCase(private val gameRepository: GameRepository) {

    fun validate(rating: Float): RatingValidationResult {
        if (!rating.isFinite()) {
            return RatingValidationResult.Invalid(RatingValidationError.NOT_FINITE)
        }
        if (rating < MIN_RATING) {
            return RatingValidationResult.Invalid(RatingValidationError.OUT_OF_RANGE)
        }
        if (abs(rating - HONORARY_RATING) <= EPSILON) {
            return RatingValidationResult.Valid(HONORARY_RATING)
        }
        if (rating > MAX_REGULAR_RATING + EPSILON) {
            return RatingValidationResult.Invalid(RatingValidationError.OUT_OF_RANGE)
        }

        val scaledDouble = rating.toDouble() * 100.0
        val roundedScaled = scaledDouble.roundToInt()
        if (abs(scaledDouble - roundedScaled) > DECIMAL_EPSILON) {
            return RatingValidationResult.Invalid(RatingValidationError.TOO_MANY_DECIMALS)
        }

        val normalized = (roundedScaled / 100.0).toFloat()
        if (normalized > MAX_REGULAR_RATING) {
            return RatingValidationResult.Invalid(RatingValidationError.OUT_OF_RANGE)
        }
        return RatingValidationResult.Valid(normalized)
    }

    suspend operator fun invoke(gameId: String, rating: Float): SaveRatingResult {
        val validation = validate(rating)
        if (validation is RatingValidationResult.Invalid) {
            return SaveRatingResult.InvalidRating(validation.error)
        }

        val existing = gameRepository.getGameById(gameId)
            ?: return SaveRatingResult.GameNotFound(gameId)

        val normalized = (validation as RatingValidationResult.Valid).normalizedRating
        val updated = existing.copy(userRating = normalized)
        gameRepository.upsertGame(updated)
        return SaveRatingResult.Success(updated)
    }

    suspend fun clearRating(gameId: String): SaveRatingResult {
        val existing = gameRepository.getGameById(gameId)
            ?: return SaveRatingResult.GameNotFound(gameId)

        val updated = existing.copy(userRating = null)
        gameRepository.upsertGame(updated)
        return SaveRatingResult.Success(updated)
    }

    companion object {
        const val MIN_RATING: Float = 0.00f
        const val MAX_REGULAR_RATING: Float = 10.00f
        const val HONORARY_RATING: Float = 11.00f
        private const val EPSILON: Float = 1e-4f
        private const val DECIMAL_EPSILON: Double = 1e-2
    }
}
