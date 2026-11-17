package io.github.carvalhoxx.gecko.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
            status = "Jogando Atualmente",
            license_type = "Possuo",
            is_day_one = true,
            hltb_main_story_ms = 216_000_000L,
            hltb_main_sides_ms = 360_000_000L,
            hltb_completionist_ms = 486_000_000L,
            selected_play_style = "Normal",
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
}
