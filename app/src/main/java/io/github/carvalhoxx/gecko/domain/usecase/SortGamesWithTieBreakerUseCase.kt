package io.github.carvalhoxx.gecko.domain.usecase

import io.github.carvalhoxx.gecko.domain.model.GameItem
import io.github.carvalhoxx.gecko.domain.model.GameStatus

/**
 * RF22: Ordenação determinística de ranking em 3 níveis:
 * 1º maior nota -> 2º maior tempo total consolidado (Delta Baseline) -> 3º conclusão mais recente,
 * com desempate final estável por título e id.
 */
class SortGamesWithTieBreakerUseCase(
    private val mergeDeltaBaselineUseCase: MergeDeltaBaselineUseCase = MergeDeltaBaselineUseCase()
) {

    operator fun invoke(
        games: List<GameItem>,
        completionTimestampsByGameId: Map<String, Long> = emptyMap()
    ): List<GameItem> = games.sortedWith(
        compareByDescending<GameItem> { it.userRating != null }
            .thenByDescending { it.userRating ?: Float.NEGATIVE_INFINITY }
            .thenByDescending { mergeDeltaBaselineUseCase(it).totalTimeMs }
            .thenByDescending { resolveCompletionTimestampMs(it, completionTimestampsByGameId) }
            .thenBy { it.title.lowercase() }
            .thenBy { it.id }
    )

    private fun resolveCompletionTimestampMs(game: GameItem, completionTimestampsByGameId: Map<String, Long>): Long {
        val explicit = completionTimestampsByGameId[game.id]
        if (explicit != null) return explicit
        return if (game.status == GameStatus.COMPLETED) {
            game.statusUpdatedAtMs ?: Long.MIN_VALUE
        } else {
            Long.MIN_VALUE
        }
    }
}
