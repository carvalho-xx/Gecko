package io.github.carvalhoxx.gecko.domain.model

data class DayOneAnchor(
    val game: GameItem,
    val releaseDateMs: Long,
    val daysUntilRelease: Int,
    val isUnreleasedWithoutHltb: Boolean
)

data class GameScheduleItem(
    val game: GameItem,
    val effectiveDailyQuotaMs: Long,
    val targetHltbTimeMs: Long,
    val totalConsolidatedTimeMs: Long,
    val remainingTimeMs: Long,
    val projectedDaysToFinish: Int,
    val projectedCompletionDateMs: Long,
    val isOvertime: Boolean,
    val isHltbUnavailable: Boolean,
    val progressFraction: Float
)

sealed interface ConflictResolutionAction {
    data class SwitchToRush(val gameId: String, val savedDays: Int) : ConflictResolutionAction

    data class PauseConcurrentGame(val candidateGameId: String, val candidateTitle: String) : ConflictResolutionAction

    data class IncreaseDailyAvailability(val recommendedDailyAvailabilityMs: Long) : ConflictResolutionAction
}

data class DayOneConflict(
    val activeGame: GameScheduleItem,
    val dayOneAnchor: DayOneAnchor,
    val overflowDays: Int,
    val suggestedActions: List<ConflictResolutionAction>
)

data class FreeWindowRecommendation(
    val candidateGame: GameItem,
    val estimatedDurationMs: Long,
    val estimatedDaysAtFullQuota: Int,
    val fitsBeforeDayOne: Boolean
)

data class ScheduleProjection(
    val calculatedAtMs: Long,
    val dailyAvailabilityMs: Long,
    val activeGamesCount: Int,
    val effectiveDailyQuotaPerGameMs: Long,
    val calibrationFactor: Float,
    val activeGameSchedules: List<GameScheduleItem>,
    val nextDayOneAnchor: DayOneAnchor?,
    val conflicts: List<DayOneConflict>,
    val freeHoursBeforeNextDayOneMs: Long,
    val freeWindowRecommendations: List<FreeWindowRecommendation>
)
