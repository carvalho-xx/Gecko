package io.github.carvalhoxx.gecko.domain.usecase

import io.github.carvalhoxx.gecko.domain.fake.FakeGameRepository
import io.github.carvalhoxx.gecko.domain.model.GameItem
import io.github.carvalhoxx.gecko.domain.model.GameStatus
import io.github.carvalhoxx.gecko.domain.model.LicenseType
import io.github.carvalhoxx.gecko.domain.model.PlayStyle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeDeltaBaselineAndPlayStyleUseCaseTest {

    @Test
    fun `MergeDeltaBaselineUseCase sums platform, local, and active session above 24h in memory`() {
        val useCase = MergeDeltaBaselineUseCase()
        val over24HoursMs = 95L * 3_600_000L + 30L * 60_000L // 343_800_000L ms
        val localMs = 7_200_000L // 2h
        val activeSessionMs = 1_800_000L // 30m

        val game = GameItem(
            id = "g1",
            title = "Elden Ring",
            status = GameStatus.PLAYING,
            licenseType = LicenseType.OWNED,
            platformReportedTimeMs = over24HoursMs,
            localLoggedTimeMs = localMs
        )

        val consolidated = useCase(game, activeSessionElapsedMs = activeSessionMs)
        assertEquals(over24HoursMs, consolidated.platformReportedTimeMs)
        assertEquals(localMs, consolidated.localLoggedTimeMs)
        assertEquals(activeSessionMs, consolidated.activeSessionElapsedMs)
        assertEquals(over24HoursMs + localMs + activeSessionMs, consolidated.totalTimeMs)
    }

    @Test
    fun `applyRemoteSync never regresses baseline and never overwrites localLoggedTimeMs`() {
        val useCase = MergeDeltaBaselineUseCase()
        val game = GameItem(
            id = "g1",
            title = "Hollow Knight",
            status = GameStatus.PLAYING,
            licenseType = LicenseType.OWNED,
            platformReportedTimeMs = 54_000_000L, // 15h
            localLoggedTimeMs = 7_200_000L // 2h
        )

        // Sync remoto menor que baseline existente não reduz nem zera o tempo
        val afterSmallerRemote = useCase.applyRemoteSync(game, incomingRemoteTimeMs = 36_000_000L)
        assertEquals(54_000_000L, afterSmallerRemote.platformReportedTimeMs)
        assertEquals(7_200_000L, afterSmallerRemote.localLoggedTimeMs)

        // Sync remoto maior atualiza apenas platformReportedTimeMs
        val afterLargerRemote = useCase.applyRemoteSync(game, incomingRemoteTimeMs = 72_000_000L)
        assertEquals(72_000_000L, afterLargerRemote.platformReportedTimeMs)
        assertEquals(7_200_000L, afterLargerRemote.localLoggedTimeMs)
    }

    @Test
    fun `UpdatePlayStyleUseCase updates play style and fails for missing game`() = runTest {
        val repo = FakeGameRepository(
            listOf(
                GameItem(
                    id = "g1",
                    title = "Hades",
                    status = GameStatus.PLAYING,
                    licenseType = LicenseType.OWNED,
                    selectedPlayStyle = PlayStyle.NORMAL
                )
            )
        )
        val useCase = UpdatePlayStyleUseCase(repo)

        val updated = useCase("g1", PlayStyle.RUSH)
        assertTrue(updated.isSuccess)
        assertEquals(PlayStyle.RUSH, updated.getOrThrow().selectedPlayStyle)
        assertEquals(PlayStyle.RUSH, repo.getGameById("g1")?.selectedPlayStyle)

        val missing = useCase("missing", PlayStyle.PLATINUM)
        assertTrue(missing.isFailure)
    }
}
