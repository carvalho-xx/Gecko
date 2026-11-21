package io.github.carvalhoxx.gecko.domain.usecase

import io.github.carvalhoxx.gecko.data.sync.SessionNotificationBuilder
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

class ActiveTimerCoordinatorTest {

    private val minuteMs = 60_000L

    @Test
    fun `start, pause, resume, and finish cycle accumulates only active intervals and notifies FGS bridge`() = runTest {
        var nowMs = 1_767_225_600_000L
        val gameRepo = FakeGameRepository(
            listOf(
                GameItem(
                    id = "elden",
                    title = "Elden Ring",
                    status = GameStatus.PLAYING,
                    licenseType = LicenseType.OWNED,
                    platformReportedTimeMs = 36_000_000L,
                    localLoggedTimeMs = 3_600_000L
                )
            )
        )
        val playthroughRepo = FakePlaythroughRepository()
        val bridge = RecordingBridge()
        val coordinator = ActiveTimerCoordinator(gameRepo, playthroughRepo, bridge) { nowMs }

        val started = coordinator.startSession("elden") as StartSessionResult.Started
        assertEquals(nowMs, playthroughRepo.getActiveSessionPlaythrough()?.currentSessionStartMs)
        assertEquals(1, bridge.startedSessions.size)

        // 10 minutos ativos -> pausa
        nowMs += 10L * minuteMs
        val paused = coordinator.pauseSession()
        assertNotNull(paused)
        assertTrue(paused!!.isPaused)
        assertEquals(10L * minuteMs, paused.accumulatedBeforeResumeMs)
        assertNull(playthroughRepo.getActiveSessionPlaythrough())
        assertEquals(3_600_000L + 10L * minuteMs, gameRepo.getGameById("elden")?.localLoggedTimeMs)

        // 5 minutos pausados (não devem contar) -> retoma
        nowMs += 5L * minuteMs
        val resumed = coordinator.resumeSession()
        assertNotNull(resumed)
        assertFalse(resumed!!.isPaused)
        assertEquals(nowMs, playthroughRepo.getActiveSessionPlaythrough()?.currentSessionStartMs)

        // +20 minutos ativos -> conclui sessão
        nowMs += 20L * minuteMs
        val summary = coordinator.finishSession()
        assertNotNull(summary)
        assertEquals("elden", summary!!.gameId)
        assertEquals(started.session.playthroughId, summary.playthroughId)
        assertEquals(30L * minuteMs, summary.addedLocalTimeMs)
        assertEquals(SessionTimerState.Idle, coordinator.timerState.value)
        assertEquals(36_000_000L, gameRepo.getGameById("elden")?.platformReportedTimeMs)
        assertEquals(3_600_000L + 30L * minuteMs, gameRepo.getGameById("elden")?.localLoggedTimeMs)
        assertEquals(1, bridge.stopCount)
    }

    @Test
    fun `starting Game B while Game A is active or paused detects conflict and atomic switch preserves Game A time`() =
        runTest {
            var nowMs = 1_767_225_600_000L
            val gameRepo = FakeGameRepository(
                listOf(
                    GameItem(
                        id = "game-a",
                        title = "Elden Ring",
                        status = GameStatus.PLAYING,
                        licenseType = LicenseType.OWNED
                    ),
                    GameItem(
                        id = "game-b",
                        title = "Hades",
                        status = GameStatus.PLAYING,
                        licenseType = LicenseType.OWNED
                    )
                )
            )
            val playthroughRepo = FakePlaythroughRepository()
            val bridge = RecordingBridge()
            val coordinator = ActiveTimerCoordinator(gameRepo, playthroughRepo, bridge) { nowMs }

            coordinator.startSession("game-a")
            nowMs += 15L * minuteMs

            // Colisão enquanto Game A está rodando
            val conflictResult = coordinator.startSession("game-b")
            assertTrue(conflictResult is StartSessionResult.ConflictDetected)
            val conflict = (conflictResult as StartSessionResult.ConflictDetected).conflict
            assertEquals("game-a", conflict.activeSession.gameId)
            assertEquals("game-b", conflict.requestedGameId)

            val activeState = coordinator.timerState.value as SessionTimerState.Active
            assertNotNull(activeState.pendingConflict)

            // Cancelar conflito limpa pendingConflict sem alterar Game A
            coordinator.dismissConflict()
            assertNull((coordinator.timerState.value as SessionTimerState.Active).pendingConflict)

            // Colisão também dispara quando Game A está pausado
            coordinator.pauseSession()
            val pausedConflict = coordinator.startSession("game-b")
            assertTrue(pausedConflict is StartSessionResult.ConflictDetected)

            // Troca atômica consolida Game A e transfere FGS para Game B sem parar o serviço
            nowMs += 5L * minuteMs
            val switched = coordinator.confirmSwitchSession("game-b")
            assertTrue(switched is StartSessionResult.Started)
            assertEquals("game-b", (switched as StartSessionResult.Started).session.gameId)
            assertEquals(15L * minuteMs, gameRepo.getGameById("game-a")?.localLoggedTimeMs)
            assertEquals(0L, gameRepo.getGameById("game-b")?.localLoggedTimeMs)
            assertEquals(0, bridge.stopCount)
            assertEquals("game-b", bridge.startedSessions.last().gameId)
        }

    @Test
    fun `recoverInterruptedSession restores exact running session from Room current_session_start_ms after LMK kill`() =
        runTest {
            var nowMs = 1_767_225_600_000L
            val gameRepo = FakeGameRepository(
                listOf(
                    GameItem(
                        id = "sekiro",
                        title = "Sekiro",
                        status = GameStatus.PLAYING,
                        licenseType = LicenseType.OWNED
                    )
                )
            )
            val playthroughRepo = FakePlaythroughRepository()
            val firstInstance = ActiveTimerCoordinator(gameRepo, playthroughRepo) { nowMs }
            firstInstance.startSession("sekiro")

            // Simula morte do processo pelo LMK 25 minutos depois -> nova instância limpa do coordinator
            nowMs += 25L * minuteMs
            val recoveredBridge = RecordingBridge()
            val secondInstance = ActiveTimerCoordinator(gameRepo, playthroughRepo, recoveredBridge) { nowMs }

            val recovered = secondInstance.recoverInterruptedSession()
            assertNotNull(recovered)
            assertEquals("sekiro", recovered!!.gameId)
            assertEquals(25L * minuteMs, recovered.elapsedMs(nowMs))
            assertEquals(1, recoveredBridge.startedSessions.size)

            // Ao concluir após recuperação, os 25 minutos são gravados intactos em localLoggedTimeMs
            val finished = secondInstance.finishSession()
            assertEquals(25L * minuteMs, finished?.addedLocalTimeMs)
            assertEquals(25L * minuteMs, gameRepo.getGameById("sekiro")?.localLoggedTimeMs)
        }

    @Test
    fun `SessionNotificationBuilder formatElapsedDuration supports durations above 24h without wrapping`() {
        assertEquals("00:00:00", SessionNotificationBuilder.formatElapsedDuration(0L))
        assertEquals("00:35:09", SessionNotificationBuilder.formatElapsedDuration(35L * 60_000L + 9_000L))
        assertEquals("24:00:00", SessionNotificationBuilder.formatElapsedDuration(24L * 3_600_000L))
        assertEquals(
            "95:30:05",
            SessionNotificationBuilder.formatElapsedDuration(95L * 3_600_000L + 30L * 60_000L + 5_000L)
        )
    }

    private class RecordingBridge : ForegroundServiceBridge {
        val startedSessions = mutableListOf<ActiveSessionSnapshot>()
        val updatedSessions = mutableListOf<ActiveSessionSnapshot>()
        var stopCount = 0

        override fun onSessionStarted(session: ActiveSessionSnapshot) {
            startedSessions += session
        }

        override fun onSessionUpdated(session: ActiveSessionSnapshot) {
            updatedSessions += session
        }

        override fun onSessionStopped() {
            stopCount++
        }
    }
}
