package io.github.carvalhoxx.gecko.domain.usecase

import io.github.carvalhoxx.gecko.domain.model.GameItem
import io.github.carvalhoxx.gecko.domain.model.GameStatus
import io.github.carvalhoxx.gecko.domain.repository.GameRepository
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * RF17: KPIs essenciais do Dashboard (Total Zerados, Horas Totais Acumuladas e Média Geral),
 * omitindo cartões punitivos de jogo mais longo/rápido.
 */
data class DashboardKpis(
    val totalGamesCount: Int,
    val completedGamesCount: Int,
    val playingGamesCount: Int,
    val totalAccumulatedTimeMs: Long,
    val averageRating: Float?,
    val ratedGamesCount: Int
)

class GetDashboardKpisUseCase(
    private val gameRepository: GameRepository,
    private val mergeDeltaBaselineUseCase: MergeDeltaBaselineUseCase = MergeDeltaBaselineUseCase()
) {

    operator fun invoke(): Flow<DashboardKpis> = gameRepository.observeAllGames().map(::calculate)

    fun calculate(games: List<GameItem>): DashboardKpis {
        if (games.isEmpty()) {
            return DashboardKpis(
                totalGamesCount = 0,
                completedGamesCount = 0,
                playingGamesCount = 0,
                totalAccumulatedTimeMs = 0L,
                averageRating = null,
                ratedGamesCount = 0
            )
        }

        val completedCount = games.count { it.status == GameStatus.COMPLETED }
        val playingCount = games.count { it.status == GameStatus.PLAYING }
        val totalTimeMs = games.sumOf { mergeDeltaBaselineUseCase(it).totalTimeMs }

        val ratings = games.mapNotNull { it.userRating }
        val averageRating = if (ratings.isEmpty()) {
            null
        } else {
            val rawAvg = ratings.sumOf { it.toDouble() } / ratings.size.toDouble()
            ((rawAvg * 100.0).roundToInt() / 100.0).toFloat()
        }

        return DashboardKpis(
            totalGamesCount = games.size,
            completedGamesCount = completedCount,
            playingGamesCount = playingCount,
            totalAccumulatedTimeMs = totalTimeMs,
            averageRating = averageRating,
            ratedGamesCount = ratings.size
        )
    }
}
