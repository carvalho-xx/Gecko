package io.github.carvalhoxx.gecko.domain.usecase

import io.github.carvalhoxx.gecko.domain.model.BacklogPolicy
import io.github.carvalhoxx.gecko.domain.model.GameItem
import io.github.carvalhoxx.gecko.domain.repository.GameRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * RN-BKL-01: Fonte exclusiva de jogos do Backlog Elegível para sugestões e planejamento
 * (RF15, RF16, SmartScheduler e widgets).
 *
 * Consome estritamente a query explícita status = 'BACKLOG' com licença ativa (OWNED/SUBSCRIPTION)
 * e aplica verificação defensiva de domínio via [BacklogPolicy.isEligibleForSuggestions].
 */
class GetEligibleBacklogGamesUseCase(private val gameRepository: GameRepository) {

    operator fun invoke(): Flow<List<GameItem>> = gameRepository.observeEligibleBacklogGames().map { games ->
        games.filter { game ->
            BacklogPolicy.isEligibleForSuggestions(game.status, game.licenseType)
        }
    }

    suspend fun getSnapshot(): List<GameItem> = gameRepository.getEligibleBacklogGames().filter { game ->
        BacklogPolicy.isEligibleForSuggestions(game.status, game.licenseType)
    }
}
