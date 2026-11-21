package io.github.carvalhoxx.gecko.data.repository

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.carvalhoxx.gecko.data.local.GeckoDatabase
import io.github.carvalhoxx.gecko.data.local.HltbFallbackCallback
import io.github.carvalhoxx.gecko.data.local.HltbFallbackEntity
import io.github.carvalhoxx.gecko.data.sync.HltbSyncWorker
import io.github.carvalhoxx.gecko.data.sync.WorkManagerHltbRetryScheduler
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HltbRepositoryInstrumentedTest {

    private lateinit var context: Context
    private lateinit var database: GeckoDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)

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
    fun upsertEntryUpdatesPerpetualHltbCacheInSqliteWithoutAffectingSeededEntries() = runBlocking {
        val dao = database.hltbFallbackDao()
        val initialCount = dao.countEntries()
        assertTrue(initialCount >= 20)

        dao.upsertEntry(
            HltbFallbackEntity(
                normalized_title = "clair obscur: expedition 33",
                display_title = "Clair Obscur: Expedition 33",
                genre_category = "genre:rpg",
                main_story_ms = 111_600_000L, // 31h
                main_sides_ms = 162_000_000L, // 45h
                completionist_ms = 234_000_000L // 65h
            )
        )

        assertEquals(initialCount + 1, dao.countEntries())
        assertEquals(111_600_000L, dao.findByNormalizedTitle("clair obscur: expedition 33")?.main_story_ms)
    }

    @Test
    fun workManagerHltbRetrySchedulerEnqueuesUniqueExponentialBackoffWork() {
        val scheduler = WorkManagerHltbRetryScheduler(context)
        scheduler.scheduleRetry(title = "Hollow Knight: Silksong", genreHint = "metroidvania")

        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork("${HltbSyncWorker.UNIQUE_WORK_PREFIX}hollow knight: silksong")
            .get()

        assertEquals(1, workInfos.size)
        val state = workInfos.first().state
        assertTrue(state == WorkInfo.State.ENQUEUED || state == WorkInfo.State.RUNNING)
    }
}
