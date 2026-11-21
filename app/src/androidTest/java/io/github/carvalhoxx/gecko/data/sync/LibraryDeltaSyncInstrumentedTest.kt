package io.github.carvalhoxx.gecko.data.sync

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.carvalhoxx.gecko.data.local.GeckoDatabase
import io.github.carvalhoxx.gecko.data.local.SecureCredentialsStorage
import io.github.carvalhoxx.gecko.data.repository.CredentialsRepositoryImpl
import io.github.carvalhoxx.gecko.data.repository.GameRepositoryImpl
import io.github.carvalhoxx.gecko.data.repository.PlaythroughRepositoryImpl
import io.github.carvalhoxx.gecko.domain.model.ByokConnectionState
import io.github.carvalhoxx.gecko.domain.model.ByokProvider
import io.github.carvalhoxx.gecko.domain.model.ByokProviderStatus
import io.github.carvalhoxx.gecko.domain.model.GameItem
import io.github.carvalhoxx.gecko.domain.model.GameStatus
import io.github.carvalhoxx.gecko.domain.model.LicenseType
import io.github.carvalhoxx.gecko.domain.model.Playthrough
import io.github.carvalhoxx.gecko.domain.model.RemoteCatalogGame
import io.github.carvalhoxx.gecko.domain.repository.CatalogSyncRepository
import io.github.carvalhoxx.gecko.domain.repository.GameRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryDeltaSyncInstrumentedTest {

    private lateinit var context: Context
    private lateinit var database: GeckoDatabase
    private lateinit var gameRepository: GameRepositoryImpl
    private lateinit var playthroughRepository: PlaythroughRepositoryImpl

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)

        database = Room.inMemoryDatabaseBuilder(context, GeckoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        gameRepository = GameRepositoryImpl(database.gameDao())
        playthroughRepository = PlaythroughRepositoryImpl(database.playthroughDao(), database.journalEntryDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun libraryDeltaSyncWorkerUpdatesOnlyPlatformTimeInSqliteAndPreservesLocalTimeAndPlaythroughs() = runBlocking {
        gameRepository.upsertGame(
            GameItem(
                id = "game_steam_1",
                steamAppId = 1245620L,
                title = "Elden Ring",
                status = GameStatus.PLAYING,
                licenseType = LicenseType.OWNED,
                platformReportedTimeMs = 10L * 3_600_000L,
                localLoggedTimeMs = 2L * 3_600_000L
            )
        )
        gameRepository.upsertGame(
            GameItem(
                id = "game_psn_1",
                psnTitleId = "PPSA01342_00",
                title = "Demon's Souls",
                status = GameStatus.PLAYING,
                licenseType = LicenseType.OWNED,
                platformReportedTimeMs = 5L * 3_600_000L,
                localLoggedTimeMs = 1L * 3_600_000L
            )
        )
        playthroughRepository.insertPlaythrough(
            Playthrough(
                gameId = "game_steam_1",
                startTimestampMs = 1_700_000_000_000L,
                endTimestampMs = null,
                currentSessionStartMs = null
            )
        )

        val deltaSyncRepository = SqliteBackedFakeCatalogSyncRepository(gameRepository)
        val workerFactory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker = LibraryDeltaSyncWorker(
                appContext = appContext,
                workerParams = workerParameters,
                catalogSyncRepository = deltaSyncRepository
            )
        }

        val worker = TestListenableWorkerBuilder<LibraryDeltaSyncWorker>(context)
            .setWorkerFactory(workerFactory)
            .build()

        val result = worker.doWork()
        assertTrue(result is ListenableWorker.Result.Success)
        val output = (result as ListenableWorker.Result.Success).outputData
        assertEquals(2, output.getInt(LibraryDeltaSyncWorker.KEY_UPDATED_COUNT, -1))

        val eldenAfter = gameRepository.findBySteamAppId(1245620L)
        assertNotNull(eldenAfter)
        assertEquals(18L * 3_600_000L, eldenAfter?.platformReportedTimeMs)
        assertEquals(2L * 3_600_000L, eldenAfter?.localLoggedTimeMs)
        assertEquals(20L * 3_600_000L, eldenAfter?.totalConsolidatedTimeMs)

        val demonsAfter = gameRepository.findByPsnTitleId("PPSA01342_00")
        assertNotNull(demonsAfter)
        assertEquals(12L * 3_600_000L, demonsAfter?.platformReportedTimeMs)
        assertEquals(1L * 3_600_000L, demonsAfter?.localLoggedTimeMs)

        // Playthrough child must remain intact (no destructive replace / cascade delete)
        val playthroughs = playthroughRepository.getPlaythroughsForGame("game_steam_1")
        assertEquals(1, playthroughs.size)
    }

    @Test
    fun workManagerLibraryDeltaSyncSchedulerEnqueuesUniqueWork() {
        val scheduler = WorkManagerLibraryDeltaSyncScheduler(context)
        scheduler.scheduleOneTimeSync()

        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(LibraryDeltaSyncWorker.UNIQUE_WORK_NAME)
            .get()

        assertEquals(1, infos.size)
        val state = infos.first().state
        assertTrue(state == WorkInfo.State.ENQUEUED || state == WorkInfo.State.RUNNING)
    }

    @Test
    fun credentialsRepositoryImplPersistsInRealEncryptedKeystoreAndClearsIndividually() {
        val prefs = SecureCredentialsStorage.createEncryptedPreferences(
            context = context,
            fileName = "gecko_byok_credentials_etapa23_test"
        )
        val storage = SecureCredentialsStorage(prefs)
        storage.clearAll()
        val repo = CredentialsRepositoryImpl(storage)

        repo.saveSteamCredentials(apiKey = "A1B2C3D4E5F60718293A4B5C6D7E8F90", steamId64 = "76561198000000001")
        repo.savePsnNpsso("a".repeat(64))

        val statuses = repo.getProviderStatuses().associateBy { it.provider }
        assertEquals(ByokConnectionState.CONFIGURED, statuses[ByokProvider.STEAM]?.state)
        assertEquals(ByokConnectionState.CONFIGURED, statuses[ByokProvider.PSN]?.state)
        assertEquals(ByokConnectionState.NOT_CONFIGURED, statuses[ByokProvider.IGDB]?.state)

        repo.clearProvider(ByokProvider.STEAM)
        val afterClear = repo.getProviderStatuses().associateBy { it.provider }
        assertEquals(ByokConnectionState.NOT_CONFIGURED, afterClear[ByokProvider.STEAM]?.state)
        assertEquals(ByokConnectionState.CONFIGURED, afterClear[ByokProvider.PSN]?.state)

        repo.clearAll()
    }
}

private class SqliteBackedFakeCatalogSyncRepository(private val gameRepository: GameRepository) :
    CatalogSyncRepository {
    override suspend fun searchCatalog(query: String): List<RemoteCatalogGame> = emptyList()

    override suspend fun syncPlatformPlaytimes(): Result<Int> {
        var updated = 0
        gameRepository.findBySteamAppId(1245620L)?.let { game ->
            gameRepository.updatePlatformReportedTime(game.id, 18L * 3_600_000L)
            updated++
        }
        gameRepository.findByPsnTitleId("PPSA01342_00")?.let { game ->
            gameRepository.updatePlatformReportedTime(game.id, 12L * 3_600_000L)
            updated++
        }
        return Result.success(updated)
    }

    override suspend fun testProviderConnection(provider: ByokProvider): ByokProviderStatus = ByokProviderStatus(
        provider = provider,
        state = ByokConnectionState.NOT_CONFIGURED
    )
}
