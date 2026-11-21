package io.github.carvalhoxx.gecko.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameDaoInstrumentedTest {

    private lateinit var database: GeckoDatabase
    private lateinit var gameDao: GameDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, GeckoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        gameDao = database.gameDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndReadGameInMemory() = runBlocking {
        val game = Game(
            id = "igdb-1020",
            steam_app_id = 1245620L,
            psn_title_id = "PPSA04610_00",
            title = "Elden Ring",
            release_date_ms = 1645747200000L,
            status = "PLAYING",
            license_type = "OWNED",
            is_day_one = true,
            hltb_main_story_ms = 216_000_000L,
            hltb_main_sides_ms = 360_000_000L,
            hltb_completionist_ms = 486_000_000L,
            selected_play_style = "NORMAL",
            platform_reported_time_ms = 343_800_000L,
            local_logged_time_ms = 7_200_000L,
            user_rating = 11.0f,
            cost_paid = 249.90f
        )

        gameDao.insertGame(game)

        val retrieved = gameDao.getGameById("igdb-1020")
        assertNotNull(retrieved)
        assertEquals(game, retrieved)

        val games = gameDao.observeAllGames().first()
        assertEquals(1, games.size)
        assertEquals(343_800_000L, games.first().platform_reported_time_ms)
        assertEquals(7_200_000L, games.first().local_logged_time_ms)
    }

    @Test
    fun deltaBaselineSqlQueriesIsolateColumnsAndPreventRegression() = runBlocking {
        val game = Game(
            id = "igdb-2048",
            title = "Hollow Knight",
            status = "PLAYING",
            license_type = "OWNED",
            platform_reported_time_ms = 54_000_000L, // 15h
            local_logged_time_ms = 3_600_000L // 1h
        )
        gameDao.insertGame(game)

        // Sync remoto menor não pode regredir o baseline nem alterar local_logged_time_ms
        gameDao.updatePlatformReportedTime(gameId = "igdb-2048", reportedTimeMs = 36_000_000L)
        val afterSmallerRemote = gameDao.getGameById("igdb-2048")!!
        assertEquals(54_000_000L, afterSmallerRemote.platform_reported_time_ms)
        assertEquals(3_600_000L, afterSmallerRemote.local_logged_time_ms)

        // Sync remoto maior atualiza apenas platform_reported_time_ms
        gameDao.updatePlatformReportedTime(gameId = "igdb-2048", reportedTimeMs = 72_000_000L)
        val afterLargerRemote = gameDao.getGameById("igdb-2048")!!
        assertEquals(72_000_000L, afterLargerRemote.platform_reported_time_ms)
        assertEquals(3_600_000L, afterLargerRemote.local_logged_time_ms)

        // Sessão local incrementa apenas local_logged_time_ms
        gameDao.addLocalLoggedTime(gameId = "igdb-2048", sessionDurationMs = 1_800_000L)
        val afterLocalSession = gameDao.getGameById("igdb-2048")!!
        assertEquals(72_000_000L, afterLocalSession.platform_reported_time_ms)
        assertEquals(5_400_000L, afterLocalSession.local_logged_time_ms)
    }

    @Test
    fun upsertGamePreservesChildPlaythroughAndCrossRefsWithoutCascadeDelete() = runBlocking {
        val game = Game(id = "igdb-500", title = "Bloodborne", status = "PLAYING", license_type = "OWNED")
        gameDao.insertGame(game)

        val playthroughId = database.playthroughDao().insertPlaythrough(
            PlaythroughEntity(game_id = game.id, start_timestamp_ms = 1_700_000_000_000L)
        )
        database.journalEntryDao().insertEntry(
            JournalEntryEntity(
                playthrough_id = playthroughId,
                timestamp_ms = 1_700_005_000_000L,
                text_content = "Father Gascoigne derrotado.",
                accumulated_playtime_ms = 7_200_000L
            )
        )
        database.platformDao().insertPlatform(PlatformEntity(id = "ps4", name = "PlayStation 4"))
        database.platformDao().insertGamePlatformRef(GamePlatformCrossRef(game_id = game.id, platform_id = "ps4"))

        // Atualiza o jogo existente via @Upsert — filhos devem permanecer intactos
        gameDao.insertGame(game.copy(selected_play_style = "PLATINUM", user_rating = 10.0f))

        assertEquals(1, database.playthroughDao().getPlaythroughsForGame(game.id).size)
        assertEquals(1, database.journalEntryDao().getEntryCountForPlaythrough(playthroughId))
        assertEquals(listOf("ps4"), database.platformDao().getPlatformIdsForGame(game.id))
        assertEquals("PLATINUM", gameDao.getGameById(game.id)?.selected_play_style)
    }

    @Test
    fun uniqueIndicesAllowMultipleNullsAndRejectDuplicateSteamAppId() = runBlocking {
        gameDao.insertGame(Game(id = "m1", title = "Manual 1", status = "BACKLOG", license_type = "OWNED"))
        gameDao.insertGame(Game(id = "m2", title = "Manual 2", status = "BACKLOG", license_type = "OWNED"))
        assertEquals(2, gameDao.observeTotalGamesCount().first())

        gameDao.insertGame(
            Game(
                id = "s1",
                steam_app_id = 570L,
                title = "Game A",
                status = "BACKLOG",
                license_type = "OWNED"
            )
        )

        // @Upsert rejeita silenciosamente novo id com steam_app_id duplicado sem corromper s1
        gameDao.insertGame(
            Game(
                id = "s2",
                steam_app_id = 570L,
                title = "Game B Duplicate SteamId",
                status = "BACKLOG",
                license_type = "OWNED"
            )
        )
        assertNull(gameDao.getGameById("s2"))
        assertEquals("Game A", gameDao.getGameById("s1")?.title)

        // Índice único no SQLite rejeita INSERT direto com steam_app_id duplicado
        var constraintThrown = false
        try {
            database.openHelper.writableDatabase.execSQL(
                """
                INSERT INTO GAME (
                    id, steam_app_id, title, status, license_type, is_day_one,
                    hltb_main_story_ms, hltb_main_sides_ms, hltb_completionist_ms,
                    selected_play_style, platform_reported_time_ms, local_logged_time_ms
                ) VALUES ('s3', 570, 'Duplicate', 'BACKLOG', 'OWNED', 0, 0, 0, 0, 'NORMAL', 0, 0)
                """.trimIndent()
            )
        } catch (_: SQLiteConstraintException) {
            constraintThrown = true
        }
        assertTrue("Duplicate steam_app_id must trigger SQLiteConstraintException", constraintThrown)
    }
}
