package io.github.carvalhoxx.gecko.domain.usecase

import io.github.carvalhoxx.gecko.domain.fake.FakeGameRepository
import io.github.carvalhoxx.gecko.domain.model.ConflictResolutionAction
import io.github.carvalhoxx.gecko.domain.model.GameItem
import io.github.carvalhoxx.gecko.domain.model.GameStatus
import io.github.carvalhoxx.gecko.domain.model.LicenseType
import io.github.carvalhoxx.gecko.domain.model.PlayStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculateSmartScheduleUseCaseTest {

    private val fixedNowMs = 1_767_225_600_000L // 2026-01-01T00:00:00Z
    private val hourMs = 3_600_000L
    private val dayMs = CalculateSmartScheduleUseCase.DAY_MS

    @Test
    fun `TU-01 2h per day with 2 active games and 10h remaining yields 1h per game and exact 10 days`() = runTest {
        val gameA = GameItem(
            id = "g1",
            title = "Game A",
            status = GameStatus.PLAYING,
            licenseType = LicenseType.OWNED,
            hltbMainSidesMs = 20L * hourMs,
            selectedPlayStyle = PlayStyle.NORMAL,
            platformReportedTimeMs = 8L * hourMs,
            localLoggedTimeMs = 2L * hourMs // Total = 10h -> 10h restantes
        )
        val gameB = GameItem(
            id = "g2",
            title = "Game B",
            status = GameStatus.PLAYING,
            licenseType = LicenseType.OWNED,
            hltbMainSidesMs = 15L * hourMs,
            selectedPlayStyle = PlayStyle.NORMAL,
            platformReportedTimeMs = 5L * hourMs // 10h restantes
        )
        val repo = FakeGameRepository(listOf(gameA, gameB))
        val useCase = CalculateSmartScheduleUseCase(
            gameRepository = repo,
            getEligibleBacklogGamesUseCase = GetEligibleBacklogGamesUseCase(repo),
            clockMs = { fixedNowMs }
        )

        val projection = useCase(dailyAvailabilityMs = 2L * hourMs)

        assertEquals(2, projection.activeGamesCount)
        assertEquals(1L * hourMs, projection.effectiveDailyQuotaPerGameMs)
        assertEquals(2, projection.activeGameSchedules.size)

        projection.activeGameSchedules.forEach { schedule ->
            assertEquals(1L * hourMs, schedule.effectiveDailyQuotaMs)
            assertEquals(10L * hourMs, schedule.remainingTimeMs)
            assertEquals(10, schedule.projectedDaysToFinish)
            assertEquals(fixedNowMs + 10L * dayMs, schedule.projectedCompletionDateMs)
            assertFalse(schedule.isOvertime)
        }
    }

    @Test
    fun `TU-02 total playtime exceeding HLTB activates overtime mode without negative days`() = runTest {
        val overtimeGame = GameItem(
            id = "elden",
            title = "Elden Ring",
            status = GameStatus.PLAYING,
            licenseType = LicenseType.OWNED,
            hltbMainSidesMs = 100L * hourMs,
            selectedPlayStyle = PlayStyle.NORMAL,
            platformReportedTimeMs = 110L * hourMs,
            localLoggedTimeMs = 5L * hourMs // 115h > 100h HLTB
        )
        val repo = FakeGameRepository(listOf(overtimeGame))
        val useCase = CalculateSmartScheduleUseCase(
            gameRepository = repo,
            getEligibleBacklogGamesUseCase = GetEligibleBacklogGamesUseCase(repo),
            clockMs = { fixedNowMs }
        )

        val projection = useCase(dailyAvailabilityMs = 2L * hourMs)
        val item = projection.activeGameSchedules.single()

        assertTrue(item.isOvertime)
        assertEquals(0L, item.remainingTimeMs)
        assertTrue("Overtime must project positive incremental days", item.projectedDaysToFinish >= 1)
        assertTrue(item.projectedCompletionDateMs > fixedNowMs)
        assertEquals(1f, item.progressFraction, 0.001f)
    }

    @Test
    fun `Scenario A Day One conflict triggers overflow days and suggested mitigation actions`() = runTest {
        val activeSlowGame = GameItem(
            id = "rpg-1",
            title = "Persona 5 Royal",
            status = GameStatus.PLAYING,
            licenseType = LicenseType.OWNED,
            hltbMainStoryMs = 10L * hourMs,
            hltbMainSidesMs = 20L * hourMs,
            selectedPlayStyle = PlayStyle.NORMAL
        )
        val concurrentGame = GameItem(
            id = "action-2",
            title = "God of War",
            status = GameStatus.PLAYING,
            licenseType = LicenseType.OWNED,
            hltbMainSidesMs = 10L * hourMs,
            selectedPlayStyle = PlayStyle.NORMAL
        )
        val dayOneUnreleased = GameItem(
            id = "dayone-1",
            title = "GTA VI",
            status = GameStatus.WISHLIST,
            licenseType = LicenseType.WISHLIST,
            isDayOne = true,
            releaseDateMs = fixedNowMs + 10L * dayMs, // Lançamento em 10 dias
            hltbMainStoryMs = 0L,
            hltbMainSidesMs = 0L,
            hltbCompletionistMs = 0L // Trava Unreleased (RF15.1)
        )

        val repo = FakeGameRepository(listOf(activeSlowGame, concurrentGame, dayOneUnreleased))
        val useCase = CalculateSmartScheduleUseCase(
            gameRepository = repo,
            getEligibleBacklogGamesUseCase = GetEligibleBacklogGamesUseCase(repo),
            clockMs = { fixedNowMs }
        )

        // Com 2h/dia e N=2 -> 1h/dia/jogo -> rpg-1 precisa de 20 dias > 10 dias até Day One
        val projection = useCase(dailyAvailabilityMs = 2L * hourMs)

        val anchor = projection.nextDayOneAnchor
        assertNotNull(anchor)
        assertEquals("dayone-1", anchor?.game?.id)
        assertEquals(10, anchor?.daysUntilRelease)
        assertTrue("Unreleased Day One without HLTB must trigger Trava Unreleased", anchor!!.isUnreleasedWithoutHltb)

        assertEquals(1, projection.conflicts.size)
        val conflict = projection.conflicts.single()
        assertEquals("rpg-1", conflict.activeGame.game.id)
        assertEquals(10, conflict.overflowDays)
        assertTrue(conflict.suggestedActions.any { it is ConflictResolutionAction.SwitchToRush })
        assertTrue(conflict.suggestedActions.any { it is ConflictResolutionAction.PauseConcurrentGame })
        assertTrue(conflict.suggestedActions.any { it is ConflictResolutionAction.IncreaseDailyAvailability })
        assertEquals(0L, projection.freeHoursBeforeNextDayOneMs)
        assertTrue(projection.freeWindowRecommendations.isEmpty())
    }

    @Test
    fun `Scenario B free window recommends strictly BACKLOG eligible games and excludes PAUSED, ABANDONED, WISHLIST`() =
        runTest {
            val activeGame = GameItem(
                id = "active",
                title = "Portal 2",
                status = GameStatus.PLAYING,
                licenseType = LicenseType.OWNED,
                hltbMainSidesMs = 6L * hourMs,
                selectedPlayStyle = PlayStyle.NORMAL
            )
            val dayOneAnchor = GameItem(
                id = "dayone",
                title = "New Release",
                status = GameStatus.WISHLIST,
                licenseType = LicenseType.WISHLIST,
                isDayOne = true,
                releaseDateMs = fixedNowMs + 10L * dayMs // 10 dias * 2h/dia = 20h -> 14h livres
            )
            val eligibleFits = GameItem(
                id = "backlog-fits",
                title = "Celeste",
                status = GameStatus.BACKLOG,
                licenseType = LicenseType.OWNED,
                hltbMainSidesMs = 12L * hourMs,
                selectedPlayStyle = PlayStyle.NORMAL
            )
            val eligibleSubFits = GameItem(
                id = "backlog-sub-fits",
                title = "Inside",
                status = GameStatus.BACKLOG,
                licenseType = LicenseType.SUBSCRIPTION,
                hltbMainSidesMs = 4L * hourMs,
                selectedPlayStyle = PlayStyle.NORMAL
            )
            val eligibleTooLong = GameItem(
                id = "backlog-too-long",
                title = "Baldur's Gate 3",
                status = GameStatus.BACKLOG,
                licenseType = LicenseType.OWNED,
                hltbMainSidesMs = 80L * hourMs,
                selectedPlayStyle = PlayStyle.NORMAL
            )
            val pausedFits = GameItem(
                id = "paused-must-exclude",
                title = "Sekiro",
                status = GameStatus.PAUSED,
                licenseType = LicenseType.OWNED,
                hltbMainSidesMs = 8L * hourMs,
                selectedPlayStyle = PlayStyle.NORMAL
            )
            val abandonedFits = GameItem(
                id = "abandoned-must-exclude",
                title = "Returnal",
                status = GameStatus.ABANDONED,
                licenseType = LicenseType.OWNED,
                hltbMainSidesMs = 8L * hourMs,
                selectedPlayStyle = PlayStyle.NORMAL
            )
            val wishlistFits = GameItem(
                id = "wishlist-must-exclude",
                title = "Stray",
                status = GameStatus.WISHLIST,
                licenseType = LicenseType.WISHLIST,
                hltbMainSidesMs = 5L * hourMs,
                selectedPlayStyle = PlayStyle.NORMAL
            )

            val repo = FakeGameRepository(
                listOf(
                    activeGame,
                    dayOneAnchor,
                    eligibleFits,
                    eligibleSubFits,
                    eligibleTooLong,
                    pausedFits,
                    abandonedFits,
                    wishlistFits
                )
            )
            val useCase = CalculateSmartScheduleUseCase(
                gameRepository = repo,
                getEligibleBacklogGamesUseCase = GetEligibleBacklogGamesUseCase(repo),
                clockMs = { fixedNowMs }
            )

            val projection = useCase.observe(dailyAvailabilityMs = 2L * hourMs).first()

            assertTrue(projection.conflicts.isEmpty())
            assertEquals(14L * hourMs, projection.freeHoursBeforeNextDayOneMs)
            assertEquals(
                listOf("backlog-fits", "backlog-sub-fits"),
                projection.freeWindowRecommendations.map { it.candidateGame.id }
            )
            assertTrue(projection.freeWindowRecommendations.all { it.fitsBeforeDayOne })
        }

    @Test
    fun `switching PlayStyle and applying calibrationFactor recalculate projected days accurately`() = runTest {
        val baseGame = GameItem(
            id = "g1",
            title = "Hollow Knight",
            status = GameStatus.PLAYING,
            licenseType = LicenseType.OWNED,
            hltbMainStoryMs = 20L * hourMs,
            hltbMainSidesMs = 40L * hourMs,
            hltbCompletionistMs = 60L * hourMs,
            selectedPlayStyle = PlayStyle.RUSH
        )
        val repo = FakeGameRepository(listOf(baseGame))
        val useCase = CalculateSmartScheduleUseCase(
            gameRepository = repo,
            getEligibleBacklogGamesUseCase = GetEligibleBacklogGamesUseCase(repo),
            clockMs = { fixedNowMs }
        )

        val rushProjection = useCase(dailyAvailabilityMs = 2L * hourMs).activeGameSchedules.single()
        assertEquals(10, rushProjection.projectedDaysToFinish)

        repo.upsertGame(baseGame.copy(selectedPlayStyle = PlayStyle.NORMAL))
        val normalProjection = useCase(dailyAvailabilityMs = 2L * hourMs).activeGameSchedules.single()
        assertEquals(20, normalProjection.projectedDaysToFinish)

        repo.upsertGame(baseGame.copy(selectedPlayStyle = PlayStyle.PLATINUM))
        val platinumProjection = useCase(dailyAvailabilityMs = 2L * hourMs).activeGameSchedules.single()
        assertEquals(30, platinumProjection.projectedDaysToFinish)

        // Calibração 0.80x (20% mais rápido): 60h * 0.80 = 48h -> 24 dias
        val calibratedFaster = useCase(
            dailyAvailabilityMs = 2L * hourMs,
            calibrationFactor = 0.80f
        ).activeGameSchedules.single()
        assertEquals(48L * hourMs, calibratedFaster.targetHltbTimeMs)
        assertEquals(24, calibratedFaster.projectedDaysToFinish)
    }

    @Test
    fun `zero dailyAvailabilityMs is safely clamped without division by zero`() = runTest {
        val game = GameItem(
            id = "g1",
            title = "Celeste",
            status = GameStatus.PLAYING,
            licenseType = LicenseType.OWNED,
            hltbMainSidesMs = 10L * hourMs
        )
        val repo = FakeGameRepository(listOf(game))
        val useCase = CalculateSmartScheduleUseCase(
            gameRepository = repo,
            getEligibleBacklogGamesUseCase = GetEligibleBacklogGamesUseCase(repo),
            clockMs = { fixedNowMs }
        )

        val projection = useCase(dailyAvailabilityMs = 0L)
        assertEquals(1L, projection.dailyAvailabilityMs)
        assertEquals(1L, projection.effectiveDailyQuotaPerGameMs)
        assertTrue(projection.activeGameSchedules.single().projectedDaysToFinish > 0)
    }
}
