package io.github.carvalhoxx.gecko.domain.usecase

import io.github.carvalhoxx.gecko.domain.model.BacklogPolicy
import io.github.carvalhoxx.gecko.domain.model.GameItem
import io.github.carvalhoxx.gecko.domain.model.GameStatus
import io.github.carvalhoxx.gecko.domain.model.LicenseType
import io.github.carvalhoxx.gecko.domain.model.Playthrough
import io.github.carvalhoxx.gecko.domain.repository.GameRepository
import io.github.carvalhoxx.gecko.domain.repository.PlaythroughRepository

sealed interface TransitionResult {
    data class Success(val updatedGame: GameItem, val affectedPlaythrough: Playthrough? = null) : TransitionResult

    data class GameNotFound(val gameId: String) : TransitionResult

    data class InvalidTransition(val from: GameStatus, val to: GameStatus, val reason: String) : TransitionResult

    data class LicenseRequired(val message: String) : TransitionResult
}

/**
 * Máquina de estados baseada em eventos do ciclo de vida do jogo (RF10, RF20, RF23, RN-BKL-01).
 */
class TransitionGameStatusUseCase(
    private val gameRepository: GameRepository,
    private val playthroughRepository: PlaythroughRepository,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {

    fun isTransitionAllowed(from: GameStatus, to: GameStatus): Boolean = to in ALLOWED_TRANSITIONS[from].orEmpty()

    suspend operator fun invoke(
        gameId: String,
        targetStatus: GameStatus,
        acquiredLicenseType: LicenseType? = null,
        completionRating: Float? = null
    ): TransitionResult {
        val currentGame = gameRepository.getGameById(gameId)
            ?: return TransitionResult.GameNotFound(gameId)

        if (!isTransitionAllowed(currentGame.status, targetStatus)) {
            return TransitionResult.InvalidTransition(
                from = currentGame.status,
                to = targetStatus,
                reason = "Transition from ${currentGame.status} to $targetStatus is not permitted."
            )
        }

        val nowMs = clockMs()

        return when {
            currentGame.status == GameStatus.WISHLIST && targetStatus == GameStatus.BACKLOG ->
                handleWishlistToBacklog(currentGame, acquiredLicenseType, nowMs)

            targetStatus == GameStatus.PLAYING ->
                handleTransitionToPlaying(currentGame, nowMs)

            currentGame.status == GameStatus.PLAYING && targetStatus == GameStatus.COMPLETED ->
                handlePlayingToCompleted(currentGame, completionRating, nowMs)

            currentGame.status == GameStatus.PLAYING &&
                (targetStatus == GameStatus.PAUSED || targetStatus == GameStatus.ABANDONED) ->
                handlePlayingToSuspended(currentGame, targetStatus, nowMs)

            (currentGame.status == GameStatus.PAUSED || currentGame.status == GameStatus.ABANDONED) &&
                targetStatus == GameStatus.BACKLOG ->
                handleReenterBacklog(currentGame, nowMs)

            else -> TransitionResult.InvalidTransition(
                from = currentGame.status,
                to = targetStatus,
                reason = "Unhandled state transition."
            )
        }
    }

    private suspend fun handleWishlistToBacklog(
        game: GameItem,
        acquiredLicenseType: LicenseType?,
        nowMs: Long
    ): TransitionResult {
        if (acquiredLicenseType == null || acquiredLicenseType !in BacklogPolicy.ELIGIBLE_LICENSES) {
            return TransitionResult.LicenseRequired(
                "Transitioning from WISHLIST to BACKLOG requires OWNED or SUBSCRIPTION license."
            )
        }
        val updatedGame = game.copy(
            status = GameStatus.BACKLOG,
            licenseType = acquiredLicenseType,
            statusUpdatedAtMs = nowMs
        )
        gameRepository.upsertGame(updatedGame)
        return TransitionResult.Success(updatedGame = updatedGame)
    }

    private suspend fun handleTransitionToPlaying(game: GameItem, nowMs: Long): TransitionResult {
        if (game.licenseType !in BacklogPolicy.ELIGIBLE_LICENSES) {
            return TransitionResult.LicenseRequired(
                "Cannot start playing a game without OWNED or SUBSCRIPTION license."
            )
        }

        val playthrough = if (game.status == GameStatus.COMPLETED) {
            // Replay / New Game+ (RF23): cria nova instância 1:N sem sobrescrever jornadas anteriores
            createPlaythrough(game.id, nowMs)
        } else {
            playthroughRepository.getActivePlaythroughForGame(game.id)
                ?: createPlaythrough(game.id, nowMs)
        }

        val updatedGame = game.copy(
            status = GameStatus.PLAYING,
            statusUpdatedAtMs = nowMs
        )
        gameRepository.upsertGame(updatedGame)
        return TransitionResult.Success(updatedGame = updatedGame, affectedPlaythrough = playthrough)
    }

    private suspend fun handlePlayingToCompleted(
        game: GameItem,
        completionRating: Float?,
        nowMs: Long
    ): TransitionResult {
        val activePlaythrough = playthroughRepository.getActivePlaythroughForGame(game.id)
        var addedSessionMs = 0L

        val closedPlaythrough = if (activePlaythrough != null) {
            addedSessionMs = activePlaythrough.currentSessionStartMs
                ?.let { sessionStart -> (nowMs - sessionStart).coerceAtLeast(0L) }
                ?: 0L
            val completed = activePlaythrough.copy(
                endTimestampMs = nowMs,
                currentSessionStartMs = null,
                rating = completionRating ?: activePlaythrough.rating,
                notesArchived = true
            )
            playthroughRepository.updatePlaythrough(completed)
            completed
        } else {
            val newCompleted = Playthrough(
                gameId = game.id,
                startTimestampMs = nowMs,
                endTimestampMs = nowMs,
                currentSessionStartMs = null,
                rating = completionRating,
                notesArchived = true
            )
            val generatedId = playthroughRepository.insertPlaythrough(newCompleted)
            newCompleted.copy(id = generatedId)
        }

        val updatedGame = game.copy(
            status = GameStatus.COMPLETED,
            localLoggedTimeMs = game.localLoggedTimeMs + addedSessionMs,
            userRating = completionRating ?: game.userRating,
            statusUpdatedAtMs = nowMs
        )
        gameRepository.upsertGame(updatedGame)
        return TransitionResult.Success(updatedGame = updatedGame, affectedPlaythrough = closedPlaythrough)
    }

    private suspend fun handlePlayingToSuspended(
        game: GameItem,
        targetStatus: GameStatus,
        nowMs: Long
    ): TransitionResult {
        val activePlaythrough = playthroughRepository.getActivePlaythroughForGame(game.id)
        var addedSessionMs = 0L

        val updatedPlaythrough = if (activePlaythrough != null && activePlaythrough.currentSessionStartMs != null) {
            addedSessionMs = (nowMs - activePlaythrough.currentSessionStartMs).coerceAtLeast(0L)
            val pausedPlaythrough = activePlaythrough.copy(currentSessionStartMs = null)
            playthroughRepository.updatePlaythrough(pausedPlaythrough)
            pausedPlaythrough
        } else {
            activePlaythrough
        }

        val updatedGame = game.copy(
            status = targetStatus,
            localLoggedTimeMs = game.localLoggedTimeMs + addedSessionMs,
            statusUpdatedAtMs = nowMs
        )
        gameRepository.upsertGame(updatedGame)
        return TransitionResult.Success(updatedGame = updatedGame, affectedPlaythrough = updatedPlaythrough)
    }

    private suspend fun handleReenterBacklog(game: GameItem, nowMs: Long): TransitionResult {
        val updatedGame = game.copy(
            status = GameStatus.BACKLOG,
            statusUpdatedAtMs = nowMs
        )
        gameRepository.upsertGame(updatedGame)
        return TransitionResult.Success(updatedGame = updatedGame)
    }

    private suspend fun createPlaythrough(gameId: String, nowMs: Long): Playthrough {
        val newPlaythrough = Playthrough(
            gameId = gameId,
            startTimestampMs = nowMs
        )
        val id = playthroughRepository.insertPlaythrough(newPlaythrough)
        return newPlaythrough.copy(id = id)
    }

    companion object {
        private val ALLOWED_TRANSITIONS: Map<GameStatus, Set<GameStatus>> = mapOf(
            GameStatus.WISHLIST to setOf(GameStatus.BACKLOG),
            GameStatus.BACKLOG to setOf(GameStatus.PLAYING),
            GameStatus.PLAYING to setOf(GameStatus.COMPLETED, GameStatus.PAUSED, GameStatus.ABANDONED),
            GameStatus.PAUSED to setOf(GameStatus.PLAYING, GameStatus.BACKLOG),
            GameStatus.ABANDONED to setOf(GameStatus.PLAYING, GameStatus.BACKLOG),
            GameStatus.COMPLETED to setOf(GameStatus.PLAYING)
        )
    }
}
