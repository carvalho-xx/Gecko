package io.github.carvalhoxx.gecko.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.carvalhoxx.gecko.data.repository.GameRepositoryImpl
import io.github.carvalhoxx.gecko.domain.model.GameItem
import io.github.carvalhoxx.gecko.domain.model.GameStatus
import io.github.carvalhoxx.gecko.domain.model.LicenseType
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

/**
 * Cobertura SQLite real dos DAOs da Etapa 0.3 (AnnualGoal, Platform, CustomList,
 * Playthrough, HltbFallback) e do GameRepositoryImpl da Etapa 1.1.
 */
@RunWith(AndroidJUnit4::class)
class DerDaosAndRepositoryInstrumentedTest {

    private lateinit var database: GeckoDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, GeckoDatabase::class.java)
            .addCallback(HltbFallbackCallback(context))
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun annualGoalDaoUpsertObserveAndDeleteInSqlite() = runBlocking {
        val dao = database.annualGoalDao()
        assertNull(dao.getGoalForYear(2026))

        dao.upsertGoal(AnnualGoalEntity(year = 2026, target_games = 24))
        assertEquals(24, dao.observeGoalForYear(2026).first()?.target_games)

        dao.upsertGoal(AnnualGoalEntity(year = 2026, target_games = 36))
        assertEquals(36, dao.getGoalForYear(2026)?.target_games)

        dao.deleteGoalForYear(2026)
        assertNull(dao.getGoalForYear(2026))
    }

    @Test
    fun platformAndCustomListJoinsAndUpdatesPreserveCrossRefsInSqlite() = runBlocking {
        val game = Game(id = "g1", title = "Resident Evil 4", status = "BACKLOG", license_type = "OWNED")
        database.gameDao().insertGame(game)

        val platformDao = database.platformDao()
        platformDao.insertPlatforms(
            listOf(
                PlatformEntity(id = "ps5", name = "PlayStation 5"),
                PlatformEntity(id = "pc", name = "PC")
            )
        )
        platformDao.insertGamePlatformRefs(
            listOf(
                GamePlatformCrossRef(game_id = "g1", platform_id = "ps5"),
                GamePlatformCrossRef(game_id = "g1", platform_id = "pc")
            )
        )

        // Atualizar plataforma via @Upsert não pode disparar CASCADE em GAME_PLATFORM_REF
        platformDao.insertPlatform(PlatformEntity(id = "ps5", name = "PS5"))
        val platforms = platformDao.observePlatformsForGame("g1").first()
        assertEquals(listOf("PC", "PS5"), platforms.map { it.name })

        val customListDao = database.customListDao()
        val franchiseId = customListDao.insertCustomList(
            CustomListEntity(name = "Resident Evil", is_franchise = true, external_franchise_id = 77L)
        )
        val regularListId = customListDao.insertCustomList(
            CustomListEntity(name = "Favoritos de Terror", is_franchise = false)
        )
        customListDao.insertGameCustomListRef(GameCustomListCrossRef(game_id = "g1", custom_list_id = franchiseId))
        customListDao.insertGameCustomListRef(GameCustomListCrossRef(game_id = "g1", custom_list_id = regularListId))

        // Atualizar lista via @Update não pode disparar CASCADE em GAME_CUSTOM_LIST_REF
        customListDao.updateCustomList(
            CustomListEntity(
                id = franchiseId,
                name = "Resident Evil Saga",
                is_franchise = true,
                external_franchise_id = 77L
            )
        )
        assertEquals(listOf("Resident Evil Saga"), customListDao.observeFranchiseLists().first().map { it.name })
        assertEquals(listOf("g1"), customListDao.observeGamesInCustomList(franchiseId).first().map { it.id })
    }

    @Test
    fun playthroughDaoTracksActiveSessionAndCompletedCountInRange() = runBlocking {
        database.gameDao().insertGame(
            Game(id = "g1", title = "Celeste", status = "PLAYING", license_type = "OWNED")
        )
        val dao = database.playthroughDao()

        val p2025 = dao.insertPlaythrough(
            PlaythroughEntity(
                game_id = "g1",
                start_timestamp_ms = 1_735_689_600_000L, // 2025-01-01
                end_timestamp_ms = 1_740_000_000_000L // 2025-02-19
            )
        )
        val p2026 = dao.insertPlaythrough(
            PlaythroughEntity(
                game_id = "g1",
                start_timestamp_ms = 1_767_225_600_000L, // 2026-01-01
                end_timestamp_ms = null,
                current_session_start_ms = 1_767_300_000_000L
            )
        )

        assertNotNull(p2025)
        assertEquals(p2026, dao.getActiveSessionPlaythrough()?.id)

        dao.updateCurrentSessionStart(p2026, null)
        assertNull(dao.getActiveSessionPlaythrough())

        val count2025 = dao.observeCompletedCountInRange(
            startTimestampMs = 1_735_689_600_000L,
            endTimestampMs = 1_767_225_600_000L
        ).first()
        assertEquals(1, count2025)
    }

    @Test
    fun hltbFallbackDaoSupportsPrefixSearch() = runBlocking {
        val matches = database.hltbFallbackDao().searchByTitlePrefix("god of war")
        assertTrue("Expected God of War entries in fallback prefix search", matches.size >= 2)
    }

    @Test
    fun gameRepositoryImplEndToEndCountsAndDeltaBaselineOnSqlite() = runBlocking {
        val repository = GameRepositoryImpl(database.gameDao())
        repository.upsertGames(
            listOf(
                GameItem(id = "r1", title = "Game 1", status = GameStatus.BACKLOG, licenseType = LicenseType.OWNED),
                GameItem(id = "r2", title = "Game 2", status = GameStatus.PAUSED, licenseType = LicenseType.OWNED),
                GameItem(
                    id = "r3",
                    title = "Game 3",
                    status = GameStatus.PLAYING,
                    licenseType = LicenseType.OWNED,
                    platformReportedTimeMs = 10_000_000L,
                    localLoggedTimeMs = 2_000_000L
                )
            )
        )

        assertEquals(3, repository.observeTotalGamesCount().first())
        val counts = repository.observeGameCountsByStatus().first()
        assertEquals(1, counts[GameStatus.BACKLOG])
        assertEquals(1, counts[GameStatus.PAUSED])
        assertEquals(1, counts[GameStatus.PLAYING])

        repository.updatePlatformReportedTime("r3", 25_000_000L)
        repository.addLocalLoggedTime("r3", 3_000_000L)
        val updated = repository.getGameById("r3")!!
        assertEquals(25_000_000L, updated.platformReportedTimeMs)
        assertEquals(5_000_000L, updated.localLoggedTimeMs)

        repository.deleteGameById("r1")
        assertEquals(2, repository.observeTotalGamesCount().first())
    }
}
