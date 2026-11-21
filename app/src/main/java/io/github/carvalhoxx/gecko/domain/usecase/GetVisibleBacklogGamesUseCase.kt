package io.github.carvalhoxx.gecko.domain.usecase

import io.github.carvalhoxx.gecko.domain.model.BacklogPolicy
import io.github.carvalhoxx.gecko.domain.model.GameItem
import io.github.carvalhoxx.gecko.domain.model.GameStatus
import io.github.carvalhoxx.gecko.domain.repository.GameRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * RN-BKL-01: Backlog Visível (BACKLOG + PAUSED + ABANDONED) para exibição exclusiva na LibraryScreen.
 * Proibido usar este Use Case em qualquer lógica de sugestão ou planejamento preditivo.
 */
class GetVisibleBacklogGamesUseCase(private val gameRepository: GameRepository) {

    operator fun invoke(subStatusFilter: GameStatus? = null): Flow<List<GameItem>> {
        if (subStatusFilter != null) {
            require(subStatusFilter in BacklogPolicy.VISIBLE) {
                "subStatusFilter must belong to BacklogPolicy.VISIBLE, got $subStatusFilter."
            }
        }
        return gameRepository.observeAllGames().map { games ->
            games.filter { game ->
                val inVisibleBacklog = BacklogPolicy.isVisibleInBacklog(game.status)
                inVisibleBacklog && (subStatusFilter == null || game.status == subStatusFilter)
            }
        }
    }
}
