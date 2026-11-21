package io.github.carvalhoxx.gecko.domain.usecase

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
import io.github.carvalhoxx.gecko.domain.model.PlayStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Teste instrumentado end-to-end da Etapa 1.2 contra SQLite/Room real:
 * valida TransitionGameStatusUseCase, GetEligibleBacklogGamesUseCase (RN-BKL-01),
 * GetVisibleBacklogGamesUseCase, Delta Baseline e Playthrough 1:N.
 */
@RunWith(AndroidJUnit4::class)
class StateMachineAndBacklogInstrumentedTest {

    private lateinit var database: GeckoDatabase
    private lateinit var gameRepository: GameRepositoryImpl
    private lateinit var playthroughRepository: PlaythroughRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
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
    fun fullLifecycleAndRnBkl01WorkEndToEndOnRealRoomSqlite() = runBlocking {
        var nowMs = 1_700_000_000_000L
        val transition = TransitionGameStatusUseCase(gameRepository, playthroughRepository) { nowMs }
        val eligibleUseCase = GetEligibleBacklogGamesUseCase(gameRepository)
        val visibleUseCase = GetVisibleBacklogGamesUseCase(gameRepository)
        val updatePlayStyle = UpdatePlayStyleUseCase(gameRepository)
        val mergeDelta = MergeDeltaBaselineUseCase()

        gameRepository.upsertGame(
            GameItem(
                id = "igdb-1020",
                title = "Elden Ring",
                status = GameStatus.WISHLIST,
                licenseType = LicenseType.WISHLIST,
                platformReportedTimeMs = 343_800_000L, // 95h 30m
                localLoggedTimeMs = 3_600_000L // 1h
            )
        )

        // WISHLIST não aparece no Backlog Elegível nem no Visível
        assertTrue(eligibleUseCase.getSnapshot().isEmpty())
        assertTrue(visibleUseCase().first().isEmpty())

        // WISHLIST -> BACKLOG com licença OWNED entra no Backlog Elegível
        val acquired = transition("igdb-1020", GameStatus.BACKLOG, acquiredLicenseType = LicenseType.OWNED)
        assertTrue(acquired is TransitionResult.Success)
        assertEquals(listOf("igdb-1020"), eligibleUseCase.getSnapshot().map { it.id })

        // BACKLOG -> PLAYING abre Playthrough e sai do Backlog
        nowMs += 60_000L
        val started = transition("igdb-1020", GameStatus.PLAYING) as TransitionResult.Success
        val playthrough1 = started.affectedPlaythrough!!
        assertTrue(eligibleUseCase.getSnapshot().isEmpty())
        assertEquals(1, playthroughRepository.getPlaythroughsForGame("igdb-1020").size)

        // Atualiza estilo para PLATINUM
        val styled = updatePlayStyle("igdb-1020", PlayStyle.PLATINUM)
        assertTrue(styled.isSuccess)
        assertEquals(PlayStyle.PLATINUM, gameRepository.getGameById("igdb-1020")?.selectedPlayStyle)

        // PLAYING -> PAUSED sai do Backlog Elegível, mas permanece no Backlog Visível (RN-BKL-01)
        playthroughRepository.updateCurrentSessionStart(playthrough1.id, nowMs)
        nowMs += 1_800_000L // +30m
        val paused = transition("igdb-1020", GameStatus.PAUSED) as TransitionResult.Success
        assertEquals(5_400_000L, paused.updatedGame.localLoggedTimeMs) // 1h + 30m
        assertEquals(343_800_000L, paused.updatedGame.platformReportedTimeMs)
        assertTrue(eligibleUseCase.getSnapshot().isEmpty())
        assertEquals(listOf("igdb-1020"), visibleUseCase().first().map { it.id })
        assertEquals(listOf("igdb-1020"), visibleUseCase(GameStatus.PAUSED).first().map { it.id })

        // PAUSED -> PLAYING -> COMPLETED encerra Playthrough 1 e preserva Delta Baseline
        nowMs += 60_000L
        transition("igdb-1020", GameStatus.PLAYING)
        nowMs += 60_000L
        val completed = transition("igdb-1020", GameStatus.COMPLETED, completionRating = 11.0f)
        assertTrue(completed is TransitionResult.Success)
        val doneGame = gameRepository.getGameById("igdb-1020")!!
        assertEquals(11.0f, doneGame.userRating)
        assertEquals(343_800_000L + 5_400_000L, mergeDelta(doneGame).totalTimeMs)

        // COMPLETED -> PLAYING cria Playthrough 2 (1:N) sem sobrescrever o 1º
        nowMs += 86_400_000L
        val replay = transition("igdb-1020", GameStatus.PLAYING) as TransitionResult.Success
        assertNull(replay.affectedPlaythrough?.endTimestampMs)
        val allPlaythroughs = playthroughRepository.getPlaythroughsForGame("igdb-1020")
        assertEquals(2, allPlaythroughs.size)
        assertEquals(1, allPlaythroughs.count { it.isCompleted })
    }
}
