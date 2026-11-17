package io.github.carvalhoxx.gecko.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class GameDaoTest {

    private lateinit var gameDao: InMemoryGameDao

    @Before
    fun setUp() {
        gameDao = InMemoryGameDao()
    }

    @Test
    fun `insertGame and getGameById should persist and read Game with Long durations in memory`() = runTest {
        val over24HoursMs = 95L * 3_600_000L + 30L * 60_000L // 95h 30m = 343_800_000 ms
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
            platform_reported_time_ms = over24HoursMs,
            local_logged_time_ms = 7_200_000L,
            user_rating = 11.0f,
            cost_paid = 249.90f
        )

        gameDao.insertGame(game)

        val retrieved = gameDao.getGameById("igdb-1020")
        assertNotNull(retrieved)
        assertEquals("Elden Ring", retrieved?.title)
        assertEquals(over24HoursMs, retrieved?.platform_reported_time_ms)
        assertEquals(7_200_000L, retrieved?.local_logged_time_ms)

        val observedList = gameDao.observeAllGames().first()
        assertEquals(1, observedList.size)
        assertEquals(game, observedList.first())
    }

    @Test
    fun `delta baseline updates should isolate platform_reported_time_ms and local_logged_time_ms`() = runTest {
        val initialGame = Game(
            id = "igdb-2048",
            title = "Hollow Knight",
            status = "Jogando Atualmente",
            license_type = "Possuo",
            platform_reported_time_ms = 36_000_000L, // 10h
            local_logged_time_ms = 5_400_000L // 1h 30m
        )

        gameDao.insertGame(initialGame)

        // Atualiza tempo da plataforma com valor superior; não deve sobrescrever local_logged_time_ms
        gameDao.updatePlatformReportedTime(gameId = "igdb-2048", reportedTimeMs = 54_000_000L) // 15h
        val afterPlatformSync = gameDao.getGameById("igdb-2048")
        assertNotNull(afterPlatformSync)
        assertEquals(54_000_000L, afterPlatformSync?.platform_reported_time_ms)
        assertEquals(5_400_000L, afterPlatformSync?.local_logged_time_ms)

        // Incrementa sessão local; não deve alterar platform_reported_time_ms
        gameDao.addLocalLoggedTime(gameId = "igdb-2048", sessionDurationMs = 1_800_000L) // +30m
        val afterLocalSession = gameDao.getGameById("igdb-2048")
        assertNotNull(afterLocalSession)
        assertEquals(54_000_000L, afterLocalSession?.platform_reported_time_ms)
        assertEquals(7_200_000L, afterLocalSession?.local_logged_time_ms)
    }

    @Test
    fun `deleteGameById should remove entity from memory`() = runTest {
        val game = Game(
            id = "igdb-999",
            title = "Celeste",
            status = "Zerado",
            license_type = "Possuo"
        )
        gameDao.insertGame(game)
        assertNotNull(gameDao.getGameById("igdb-999"))

        gameDao.deleteGameById("igdb-999")
        assertNull(gameDao.getGameById("igdb-999"))
    }

    private class InMemoryGameDao : GameDao {
        private val storage = MutableStateFlow<Map<String, Game>>(emptyMap())

        override suspend fun insertGame(game: Game) {
            storage.update { current -> current + (game.id to game) }
        }

        override suspend fun insertGames(games: List<Game>) {
            storage.update { current ->
                current + games.associateBy { it.id }
            }
        }

        override suspend fun getGameById(gameId: String): Game? {
            return storage.value[gameId]
        }

        override fun observeGameById(gameId: String): Flow<Game?> {
            return storage.map { it[gameId] }
        }

        override fun observeAllGames(): Flow<List<Game>> {
            return storage.map { it.values.toList() }
        }

        override fun observeGamesByStatus(status: String): Flow<List<Game>> {
            return storage.map { map -> map.values.filter { it.status == status } }
        }

        override suspend fun updatePlatformReportedTime(gameId: String, reportedTimeMs: Long) {
            storage.update { current ->
                val existing = current[gameId] ?: return@update current
                if (reportedTimeMs > existing.platform_reported_time_ms) {
                    current + (gameId to existing.copy(platform_reported_time_ms = reportedTimeMs))
                } else {
                    current
                }
            }
        }

        override suspend fun addLocalLoggedTime(gameId: String, sessionDurationMs: Long) {
            storage.update { current ->
                val existing = current[gameId] ?: return@update current
                current + (gameId to existing.copy(
                    local_logged_time_ms = existing.local_logged_time_ms + sessionDurationMs
                ))
            }
        }

        override suspend fun deleteGame(game: Game) {
            deleteGameById(game.id)
        }

        override suspend fun deleteGameById(gameId: String) {
            storage.update { current -> current - gameId }
        }
    }
}
