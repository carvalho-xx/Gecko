package io.github.carvalhoxx.gecko.data.sync

import android.app.Notification
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.carvalhoxx.gecko.data.local.GeckoDatabase
import io.github.carvalhoxx.gecko.data.repository.GameRepositoryImpl
import io.github.carvalhoxx.gecko.data.repository.PlaythroughRepositoryImpl
import io.github.carvalhoxx.gecko.domain.model.GameItem
import io.github.carvalhoxx.gecko.domain.model.GameStatus
import io.github.carvalhoxx.gecko.domain.model.LicenseType
import io.github.carvalhoxx.gecko.domain.usecase.ActiveSessionSnapshot
import io.github.carvalhoxx.gecko.domain.usecase.ActiveTimerCoordinator
import io.github.carvalhoxx.gecko.domain.usecase.ForegroundServiceBridge
import io.github.carvalhoxx.gecko.domain.usecase.StartSessionResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TI-02: Ciclo iniciar -> pausar -> retomar -> concluir sessão com simulação de sync remoto
 * simultâneo contra Room/SQLite real, comprovando isolamento do Delta Baseline.
 */
@RunWith(AndroidJUnit4::class)
class ActiveTimerDeltaBaselineInstrumentedTest {

    private lateinit var context: Context
    private lateinit var database: GeckoDatabase
    private lateinit var gameRepository: GameRepositoryImpl
    private lateinit var playthroughRepository: PlaythroughRepositoryImpl

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, GeckoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        gameRepository = GameRepositoryImpl(database.gameDao())
        playthroughRepository = PlaythroughRepositoryImpl(
            database.playthroughDao(),
            database.journalEntryDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun ti02_timerCycleWithSimultaneousRemoteSyncPreservesDeltaBaselineInRealSqlite() = runBlocking {
        val minuteMs = 60_000L
        val hourMs = 3_600_000L
        var nowMs = 1_767_225_600_000L

        gameRepository.upsertGame(
            GameItem(
                id = "igdb-1020",
                title = "Elden Ring",
                status = GameStatus.PLAYING,
                licenseType = LicenseType.OWNED,
                platformReportedTimeMs = 10L * hourMs,
                localLoggedTimeMs = 1L * hourMs
            )
        )

        val coordinator = ActiveTimerCoordinator(
            gameRepository = gameRepository,
            playthroughRepository = playthroughRepository,
            foregroundServiceBridge = ForegroundServiceBridge.NO_OP,
            clockMs = { nowMs }
        )

        // 1. Iniciar -> grava current_session_start_ms no SQLite imediatamente
        val startResult = coordinator.startSession("igdb-1020")
        assertTrue(startResult is StartSessionResult.Started)
        assertEquals(nowMs, playthroughRepository.getActiveSessionPlaythrough()?.currentSessionStartMs)

        // 2. Rodar 10m e pausar -> soma +10m em local_logged_time_ms e limpa current_session_start_ms
        nowMs += 10L * minuteMs
        coordinator.pauseSession()
        assertNull(playthroughRepository.getActiveSessionPlaythrough())
        assertEquals(1L * hourMs + 10L * minuteMs, gameRepository.getGameById("igdb-1020")?.localLoggedTimeMs)

        // 3. Aguardar 5m pausado (não contabiliza) e retomar
        nowMs += 5L * minuteMs
        coordinator.resumeSession()
        assertEquals(nowMs, playthroughRepository.getActiveSessionPlaythrough()?.currentSessionStartMs)

        // 4. Rodar +20m + simular sync remoto simultâneo (Steam/PSN reporta 25h)
        nowMs += 20L * minuteMs
        gameRepository.updatePlatformReportedTime("igdb-1020", 25L * hourMs)

        // 5. Concluir sessão -> local_logged_time_ms recebe +20m (total 1h30m) sem perder sync remoto (25h)
        val summary = coordinator.finishSession()
        assertNotNull(summary)
        assertEquals(30L * minuteMs, summary!!.addedLocalTimeMs)

        val finalGame = gameRepository.getGameById("igdb-1020")!!
        assertEquals(25L * hourMs, finalGame.platformReportedTimeMs)
        assertEquals(1L * hourMs + 30L * minuteMs, finalGame.localLoggedTimeMs)
        assertNull(playthroughRepository.getActiveSessionPlaythrough())
    }

    @Test
    fun notificationBuilderBuildsOngoingNotificationWithTwoServiceActionsAndContentIntent() {
        val nowMs = 1_767_225_600_000L
        val runningSession = ActiveSessionSnapshot(
            gameId = "igdb-1020",
            gameTitle = "Elden Ring",
            playthroughId = 1L,
            startedAtMs = nowMs - 3_723_000L, // 01:02:03
            isPaused = false
        )

        val runningNotification = SessionNotificationBuilder.buildNotification(context, runningSession, nowMs)
        assertNotNull(runningNotification.contentIntent)
        assertTrue((runningNotification.flags and Notification.FLAG_ONGOING_EVENT) != 0)
        assertEquals(2, runningNotification.actions?.size)
        assertEquals("Pausar", runningNotification.actions[0].title.toString())
        assertEquals("Concluir Sessão", runningNotification.actions[1].title.toString())

        val pausedNotification = SessionNotificationBuilder.buildNotification(
            context,
            runningSession.copy(isPaused = true),
            nowMs
        )
        assertEquals("Retomar", pausedNotification.actions[0].title.toString())
    }
}
