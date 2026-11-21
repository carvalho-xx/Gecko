package io.github.carvalhoxx.gecko.domain.usecase

import io.github.carvalhoxx.gecko.domain.model.ConflictResolutionAction
import io.github.carvalhoxx.gecko.domain.model.DayOneAnchor
import io.github.carvalhoxx.gecko.domain.model.DayOneConflict
import io.github.carvalhoxx.gecko.domain.model.FreeWindowRecommendation
import io.github.carvalhoxx.gecko.domain.model.GameItem
import io.github.carvalhoxx.gecko.domain.model.GameScheduleItem
import io.github.carvalhoxx.gecko.domain.model.GameStatus
import io.github.carvalhoxx.gecko.domain.model.PlayStyle
import io.github.carvalhoxx.gecko.domain.model.ScheduleProjection
import io.github.carvalhoxx.gecko.domain.repository.GameRepository
import kotlin.math.roundToLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/**
 * Motor Preditivo Smart Scheduler (RF02, RF06, RF13, RF15, RF15.1, RN-BKL-01).
 *
 * Opera estritamente em milissegundos (Long) com aritmética segura (zero divisão por zero,
 * zero dias negativos), suportando Peso de Atenção (Disponibilidade / N), Modo Hora Extra (RF15.1),
 * Trava Unreleased (RF15.1) e sugestões restritas ao Backlog Elegível via [GetEligibleBacklogGamesUseCase].
 */
class CalculateSmartScheduleUseCase(
    private val gameRepository: GameRepository,
    private val getEligibleBacklogGamesUseCase: GetEligibleBacklogGamesUseCase,
    private val mergeDeltaBaselineUseCase: MergeDeltaBaselineUseCase = MergeDeltaBaselineUseCase(),
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {

    fun observe(dailyAvailabilityMs: Long, calibrationFactor: Float = 1.0f): Flow<ScheduleProjection> = combine(
        gameRepository.observeAllGames(),
        getEligibleBacklogGamesUseCase()
    ) { allGames, eligibleBacklog ->
        calculate(
            allGames = allGames,
            eligibleBacklogGames = eligibleBacklog,
            dailyAvailabilityMs = dailyAvailabilityMs,
            calibrationFactor = calibrationFactor
        )
    }

    suspend operator fun invoke(dailyAvailabilityMs: Long, calibrationFactor: Float = 1.0f): ScheduleProjection {
        val allGames = gameRepository.observeAllGames().first()
        val eligibleBacklog = getEligibleBacklogGamesUseCase.getSnapshot()
        return calculate(
            allGames = allGames,
            eligibleBacklogGames = eligibleBacklog,
            dailyAvailabilityMs = dailyAvailabilityMs,
            calibrationFactor = calibrationFactor
        )
    }

    internal fun calculate(
        allGames: List<GameItem>,
        eligibleBacklogGames: List<GameItem>,
        dailyAvailabilityMs: Long,
        calibrationFactor: Float = 1.0f
    ): ScheduleProjection {
        val nowMs = clockMs()
        val safeDailyAvailabilityMs = dailyAvailabilityMs.coerceAtLeast(1L)
        val safeCalibration = if (calibrationFactor.isFinite() && calibrationFactor > 0f) calibrationFactor else 1.0f

        val activeGames = allGames
            .filter { it.status == GameStatus.PLAYING }
            .sortedBy { it.title }
        val activeCount = activeGames.size
        val effectiveQuotaPerGameMs = if (activeCount > 0) {
            (safeDailyAvailabilityMs / activeCount.toLong()).coerceAtLeast(1L)
        } else {
            safeDailyAvailabilityMs
        }

        val activeSchedules = activeGames.map { game ->
            buildGameScheduleItem(
                game = game,
                effectiveQuotaMs = effectiveQuotaPerGameMs,
                calibrationFactor = safeCalibration,
                nowMs = nowMs
            )
        }

        val nextDayOneAnchor = findNextDayOneAnchor(allGames = allGames, nowMs = nowMs)

        val conflicts = if (nextDayOneAnchor != null) {
            buildDayOneConflicts(
                activeSchedules = activeSchedules,
                anchor = nextDayOneAnchor,
                activeGamesCount = activeCount,
                calibrationFactor = safeCalibration
            )
        } else {
            emptyList()
        }

        val freeHoursBeforeDayOneMs = if (nextDayOneAnchor != null && conflicts.isEmpty()) {
            val totalCapacityMs = nextDayOneAnchor.daysUntilRelease.toLong() * safeDailyAvailabilityMs
            val totalRemainingWorkMs = activeSchedules.sumOf { it.remainingTimeMs }
            (totalCapacityMs - totalRemainingWorkMs).coerceAtLeast(0L)
        } else {
            0L
        }

        val recommendations = if (freeHoursBeforeDayOneMs > 0L) {
            buildFreeWindowRecommendations(
                eligibleBacklogGames = eligibleBacklogGames,
                freeWindowMs = freeHoursBeforeDayOneMs,
                dailyAvailabilityMs = safeDailyAvailabilityMs,
                calibrationFactor = safeCalibration
            )
        } else {
            emptyList()
        }

        return ScheduleProjection(
            calculatedAtMs = nowMs,
            dailyAvailabilityMs = safeDailyAvailabilityMs,
            activeGamesCount = activeCount,
            effectiveDailyQuotaPerGameMs = effectiveQuotaPerGameMs,
            calibrationFactor = safeCalibration,
            activeGameSchedules = activeSchedules,
            nextDayOneAnchor = nextDayOneAnchor,
            conflicts = conflicts,
            freeHoursBeforeNextDayOneMs = freeHoursBeforeDayOneMs,
            freeWindowRecommendations = recommendations
        )
    }

    private fun buildGameScheduleItem(
        game: GameItem,
        effectiveQuotaMs: Long,
        calibrationFactor: Float,
        nowMs: Long
    ): GameScheduleItem {
        val rawHltbMs = game.targetHltbTimeMs().coerceAtLeast(0L)
        val calibratedHltbMs = applyCalibration(rawHltbMs, calibrationFactor)
        val totalConsolidatedMs = mergeDeltaBaselineUseCase(game).totalTimeMs

        if (calibratedHltbMs == 0L) {
            return GameScheduleItem(
                game = game,
                effectiveDailyQuotaMs = effectiveQuotaMs,
                targetHltbTimeMs = 0L,
                totalConsolidatedTimeMs = totalConsolidatedMs,
                remainingTimeMs = 0L,
                projectedDaysToFinish = 0,
                projectedCompletionDateMs = nowMs,
                isOvertime = false,
                isHltbUnavailable = true,
                progressFraction = 0f
            )
        }

        val isOvertime = totalConsolidatedMs >= calibratedHltbMs
        val remainingMs = (calibratedHltbMs - totalConsolidatedMs).coerceAtLeast(0L)

        // RF15.1 (Modo Hora Extra): nunca retornar dias negativos; projeta janela incremental positiva
        val projectedDays = if (isOvertime) {
            ceilDivToInt(OVERTIME_INCREMENT_MS, effectiveQuotaMs).coerceAtLeast(1)
        } else {
            ceilDivToInt(remainingMs, effectiveQuotaMs)
        }

        val progress = if (isOvertime) {
            1f
        } else {
            (totalConsolidatedMs.toFloat() / calibratedHltbMs.toFloat()).coerceIn(0f, 1f)
        }

        return GameScheduleItem(
            game = game,
            effectiveDailyQuotaMs = effectiveQuotaMs,
            targetHltbTimeMs = calibratedHltbMs,
            totalConsolidatedTimeMs = totalConsolidatedMs,
            remainingTimeMs = remainingMs,
            projectedDaysToFinish = projectedDays,
            projectedCompletionDateMs = nowMs + projectedDays.toLong() * DAY_MS,
            isOvertime = isOvertime,
            isHltbUnavailable = false,
            progressFraction = progress
        )
    }

    private fun findNextDayOneAnchor(allGames: List<GameItem>, nowMs: Long): DayOneAnchor? {
        val candidate = allGames
            .filter { game ->
                game.isDayOne &&
                    game.releaseDateMs != null &&
                    game.releaseDateMs > nowMs &&
                    game.status != GameStatus.COMPLETED &&
                    game.status != GameStatus.ABANDONED
            }
            .minByOrNull { it.releaseDateMs!! }
            ?: return null

        val releaseDateMs = candidate.releaseDateMs!!
        val daysUntilRelease = ((releaseDateMs - nowMs) / DAY_MS).toInt().coerceAtLeast(0)
        val hasAnyHltb = candidate.hltbMainStoryMs > 0L ||
            candidate.hltbMainSidesMs > 0L ||
            candidate.hltbCompletionistMs > 0L

        return DayOneAnchor(
            game = candidate,
            releaseDateMs = releaseDateMs,
            daysUntilRelease = daysUntilRelease,
            isUnreleasedWithoutHltb = !hasAnyHltb
        )
    }

    private fun buildDayOneConflicts(
        activeSchedules: List<GameScheduleItem>,
        anchor: DayOneAnchor,
        activeGamesCount: Int,
        calibrationFactor: Float
    ): List<DayOneConflict> = activeSchedules.mapNotNull { schedule ->
        if (schedule.isHltbUnavailable || schedule.projectedCompletionDateMs <= anchor.releaseDateMs) {
            return@mapNotNull null
        }

        val overflowMs = schedule.projectedCompletionDateMs - anchor.releaseDateMs
        val overflowDays = ceilDivToInt(overflowMs, DAY_MS).coerceAtLeast(1)
        val actions = mutableListOf<ConflictResolutionAction>()

        if (schedule.game.selectedPlayStyle != PlayStyle.RUSH && schedule.game.hltbMainStoryMs > 0L) {
            val rushTargetMs = applyCalibration(schedule.game.hltbMainStoryMs, calibrationFactor)
            val rushRemainingMs = (rushTargetMs - schedule.totalConsolidatedTimeMs).coerceAtLeast(0L)
            val rushDays = if (rushRemainingMs == 0L) {
                0
            } else {
                ceilDivToInt(rushRemainingMs, schedule.effectiveDailyQuotaMs)
            }
            val savedDays = (schedule.projectedDaysToFinish - rushDays).coerceAtLeast(0)
            if (savedDays > 0) {
                actions += ConflictResolutionAction.SwitchToRush(
                    gameId = schedule.game.id,
                    savedDays = savedDays
                )
            }
        }

        if (activeGamesCount >= 2) {
            val concurrent = activeSchedules.firstOrNull { it.game.id != schedule.game.id }?.game
            if (concurrent != null) {
                actions += ConflictResolutionAction.PauseConcurrentGame(
                    candidateGameId = concurrent.id,
                    candidateTitle = concurrent.title
                )
            }
        }

        if (anchor.daysUntilRelease > 0 && schedule.remainingTimeMs > 0L) {
            val requiredPerGameDailyMs = ceilDivLong(schedule.remainingTimeMs, anchor.daysUntilRelease.toLong())
            val recommendedTotalDailyMs = requiredPerGameDailyMs * activeGamesCount.toLong()
            if (recommendedTotalDailyMs in 1L..DAY_MS) {
                actions += ConflictResolutionAction.IncreaseDailyAvailability(
                    recommendedDailyAvailabilityMs = recommendedTotalDailyMs
                )
            }
        }

        DayOneConflict(
            activeGame = schedule,
            dayOneAnchor = anchor,
            overflowDays = overflowDays,
            suggestedActions = actions
        )
    }

    private fun buildFreeWindowRecommendations(
        eligibleBacklogGames: List<GameItem>,
        freeWindowMs: Long,
        dailyAvailabilityMs: Long,
        calibrationFactor: Float
    ): List<FreeWindowRecommendation> = eligibleBacklogGames
        .mapNotNull { candidate ->
            val rawDurationMs = candidate.targetHltbTimeMs().coerceAtLeast(0L)
            if (rawDurationMs == 0L) return@mapNotNull null

            val calibratedMs = applyCalibration(rawDurationMs, calibrationFactor).coerceAtLeast(1L)
            if (calibratedMs > freeWindowMs) return@mapNotNull null

            FreeWindowRecommendation(
                candidateGame = candidate,
                estimatedDurationMs = calibratedMs,
                estimatedDaysAtFullQuota = ceilDivToInt(calibratedMs, dailyAvailabilityMs),
                fitsBeforeDayOne = true
            )
        }
        .sortedWith(
            compareByDescending<FreeWindowRecommendation> { it.estimatedDurationMs }
                .thenBy { it.candidateGame.title }
        )

    private fun applyCalibration(rawDurationMs: Long, calibrationFactor: Float): Long {
        if (rawDurationMs <= 0L) return 0L
        val basisPoints = (calibrationFactor * 10_000.0).roundToLong().coerceAtLeast(1L)
        return ((rawDurationMs * basisPoints) + 5_000L) / 10_000L
    }

    private fun ceilDivLong(numerator: Long, denominator: Long): Long {
        if (numerator <= 0L) return 0L
        return (numerator + denominator - 1L) / denominator
    }

    private fun ceilDivToInt(numerator: Long, denominator: Long): Int =
        ceilDivLong(numerator, denominator).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    companion object {
        const val DAY_MS: Long = 86_400_000L
        const val OVERTIME_INCREMENT_MS: Long = 7_200_000L // 2h incrementais no Modo Hora Extra (RF15.1)
    }
}
