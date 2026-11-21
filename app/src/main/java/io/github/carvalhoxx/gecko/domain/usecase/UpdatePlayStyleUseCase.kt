package io.github.carvalhoxx.gecko.domain.usecase

import io.github.carvalhoxx.gecko.domain.model.GameItem
import io.github.carvalhoxx.gecko.domain.model.PlayStyle
import io.github.carvalhoxx.gecko.domain.repository.GameRepository

/**
 * RF06: Atualiza o estilo de jogo (Rushar, Normal ou Platina) para recalcular o alvo HLTB.
 */
class UpdatePlayStyleUseCase(private val gameRepository: GameRepository) {

    suspend operator fun invoke(gameId: String, playStyle: PlayStyle): Result<GameItem> {
        val existing = gameRepository.getGameById(gameId)
            ?: return Result.failure(NoSuchElementException("Game not found: $gameId"))

        val updated = existing.copy(selectedPlayStyle = playStyle)
        gameRepository.upsertGame(updated)
        return Result.success(updated)
    }
}
