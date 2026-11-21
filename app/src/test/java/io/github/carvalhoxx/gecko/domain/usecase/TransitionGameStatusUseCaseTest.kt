package io.github.carvalhoxx.gecko.domain.usecase

import io.github.carvalhoxx.gecko.domain.fake.FakeGameRepository
import io.github.carvalhoxx.gecko.domain.fake.FakePlaythroughRepository
import io.github.carvalhoxx.gecko.domain.model.GameItem
import io.github.carvalhoxx.gecko.domain.model.GameStatus
import io.github.carvalhoxx.gecko.domain.model.LicenseType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitionGameStatusUseCaseTest {

    @Test
    fun `WISHLIST to BACKLOG requires OWNED or SUBSCRIPTION license and updates timestamp`() = runTest {
        var nowMs = 1_700_000_000_000L
        val gameRepo = FakeGameRepository(
            listOf(
                GameItem(
                    id = "g1",
                    title = "Silksong",
                    status = GameStatus.WISHLIST,
                    licenseType = LicenseType.WISHLIST
                )
            )
        )
        val useCase = TransitionGameStatusUseCase(gameRepo, FakePlaythroughRepository()) { nowMs }

        val missingLicense = useCase("g1", GameStatus.BACKLOG, acquiredLicenseType = null)
        assertTrue(missingLicense is TransitionResult.LicenseRequired)

        val invalidLicense = useCase("g1", GameStatus.BACKLOG, acquiredLicenseType = LicenseType.WISHLIST)
        assertTrue(invalidLicense is TransitionResult.LicenseRequired)

        val acquired = useCase("g1", GameStatus.BACKLOG, acquiredLicenseType = LicenseType.SUBSCRIPTION)
        assertTrue(acquired is TransitionResult.Success)
        val updated = (acquired as TransitionResult.Success).updatedGame
        assertEquals(GameStatus.BACKLOG, updated.status)
        assertEquals(LicenseType.SUBSCRIPTION, updated.licenseType)
        assertEquals(nowMs, updated.statusUpdatedAtMs)
    }

    @Test
    fun `BACKLOG to PLAYING to COMPLETED opens playthrough, consolidates active session into local time, and closes`() =
        runTest {
            var nowMs = 1_700_000_000_000L
            val gameRepo = FakeGameRepository(
                listOf(
                    GameItem(
                        id = "g1",
                        title = "Elden Ring",
                        status = GameStatus.BACKLOG,
                        licenseType = LicenseType.OWNED,
                        platformReportedTimeMs = 36_000_000L, // 10h
                        localLoggedTimeMs = 3_600_000L // 1h
                    )
                )
            )
            val playthroughRepo = FakePlaythroughRepository()
            val useCase = TransitionGameStatusUseCase(gameRepo, playthroughRepo) { nowMs }

            val startResult = useCase("g1", GameStatus.PLAYING) as TransitionResult.Success
            assertEquals(GameStatus.PLAYING, startResult.updatedGame.status)
            assertEquals(nowMs, startResult.updatedGame.statusUpdatedAtMs)
            val startedPlaythrough = startResult.affectedPlaythrough!!
            assertEquals(nowMs, startedPlaythrough.startTimestampMs)
            assertNull(startedPlaythrough.endTimestampMs)

            // Simula sessão ativa no Foreground Service iniciada 30 min antes da conclusão
            playthroughRepo.updateCurrentSessionStart(startedPlaythrough.id, nowMs)
            nowMs += 1_800_000L // +30m

            val finishResult = useCase("g1", GameStatus.COMPLETED, completionRating = 11.0f) as TransitionResult.Success
            assertEquals(GameStatus.COMPLETED, finishResult.updatedGame.status)
            assertEquals(36_000_000L, finishResult.updatedGame.platformReportedTimeMs)
            assertEquals(5_400_000L, finishResult.updatedGame.localLoggedTimeMs) // 1h + 30m
            assertEquals(11.0f, finishResult.updatedGame.userRating)
            assertEquals(nowMs, finishResult.updatedGame.statusUpdatedAtMs)

            val closedPlaythrough = finishResult.affectedPlaythrough!!
            assertEquals(nowMs, closedPlaythrough.endTimestampMs)
            assertNull(closedPlaythrough.currentSessionStartMs)
            assertTrue(closedPlaythrough.notesArchived)
            assertEquals(11.0f, closedPlaythrough.rating)
        }

    @Test
    fun `PLAYING to PAUSED or ABANDONED removes game from eligible backlog until explicit re-entry`() = runTest {
        var nowMs = 1_700_000_000_000L
        val gameRepo = FakeGameRepository(
            listOf(
                GameItem(id = "g1", title = "Sekiro", status = GameStatus.BACKLOG, licenseType = LicenseType.OWNED)
            )
        )
        val playthroughRepo = FakePlaythroughRepository()
        val transition = TransitionGameStatusUseCase(gameRepo, playthroughRepo) { nowMs }
        val eligibleUseCase = GetEligibleBacklogGamesUseCase(gameRepo)

        assertEquals(listOf("g1"), eligibleUseCase.getSnapshot().map { it.id })

        transition("g1", GameStatus.PLAYING)
        nowMs += 60_000L
        val paused = transition("g1", GameStatus.PAUSED)
        assertTrue(paused is TransitionResult.Success)
        assertTrue(eligibleUseCase.getSnapshot().isEmpty())

        // Reentrada manual explícita para BACKLOG devolve elegibilidade (RN-BKL-01)
        nowMs += 60_000L
        val backToBacklog = transition("g1", GameStatus.BACKLOG)
        assertTrue(backToBacklog is TransitionResult.Success)
        assertEquals(listOf("g1"), eligibleUseCase.getSnapshot().map { it.id })

        // PLAYING -> ABANDONED -> PLAYING (Tenta Novamente)
        nowMs += 60_000L
        transition("g1", GameStatus.PLAYING)
        nowMs += 60_000L
        val abandoned = transition("g1", GameStatus.ABANDONED)
        assertTrue(abandoned is TransitionResult.Success)
        assertTrue(eligibleUseCase.getSnapshot().isEmpty())

        nowMs += 60_000L
        val retryPlaying = transition("g1", GameStatus.PLAYING)
        assertTrue(retryPlaying is TransitionResult.Success)
        assertEquals(GameStatus.PLAYING, (retryPlaying as TransitionResult.Success).updatedGame.status)
    }

    @Test
    fun `COMPLETED to PLAYING creates a second playthrough without overwriting the first`() = runTest {
        var nowMs = 1_700_000_000_000L
        val gameRepo = FakeGameRepository(
            listOf(
                GameItem(id = "g1", title = "Bloodborne", status = GameStatus.BACKLOG, licenseType = LicenseType.OWNED)
            )
        )
        val playthroughRepo = FakePlaythroughRepository()
        val transition = TransitionGameStatusUseCase(gameRepo, playthroughRepo) { nowMs }

        transition("g1", GameStatus.PLAYING)
        nowMs += 3_600_000L
        transition("g1", GameStatus.COMPLETED, completionRating = 10.0f)

        nowMs += 86_400_000L
        val replay = transition("g1", GameStatus.PLAYING) as TransitionResult.Success
        assertEquals(GameStatus.PLAYING, replay.updatedGame.status)
        assertNotNull(replay.affectedPlaythrough)

        val history = playthroughRepo.getPlaythroughsForGame("g1")
        assertEquals(2, history.size)
        assertEquals(1, history.count { it.isCompleted })
        assertEquals(1, history.count { !it.isCompleted })
    }

    @Test
    fun `invalid transitions and unknown game ids are rejected without mutating state`() = runTest {
        val gameRepo = FakeGameRepository(
            listOf(
                GameItem(
                    id = "w1",
                    title = "Wishlist",
                    status = GameStatus.WISHLIST,
                    licenseType = LicenseType.WISHLIST
                ),
                GameItem(id = "b1", title = "Backlog", status = GameStatus.BACKLOG, licenseType = LicenseType.OWNED),
                GameItem(id = "c1", title = "Done", status = GameStatus.COMPLETED, licenseType = LicenseType.OWNED)
            )
        )
        val transition = TransitionGameStatusUseCase(gameRepo, FakePlaythroughRepository()) { 1_700_000_000_000L }

        assertTrue(transition("missing", GameStatus.PLAYING) is TransitionResult.GameNotFound)
        assertTrue(transition("w1", GameStatus.PLAYING) is TransitionResult.InvalidTransition)
        assertTrue(transition("b1", GameStatus.COMPLETED) is TransitionResult.InvalidTransition)
        assertTrue(transition("c1", GameStatus.BACKLOG) is TransitionResult.InvalidTransition)
        assertTrue(transition("b1", GameStatus.BACKLOG) is TransitionResult.InvalidTransition)

        assertFalse(transition.isTransitionAllowed(GameStatus.PAUSED, GameStatus.COMPLETED))
        assertFalse(transition.isTransitionAllowed(GameStatus.ABANDONED, GameStatus.COMPLETED))
        assertEquals(GameStatus.WISHLIST, gameRepo.getGameById("w1")?.status)
    }
}
