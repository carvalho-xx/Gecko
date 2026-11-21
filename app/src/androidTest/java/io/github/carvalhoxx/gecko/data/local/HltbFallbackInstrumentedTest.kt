package io.github.carvalhoxx.gecko.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HltbFallbackInstrumentedTest {

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
    fun databaseCallbackPrePopulatesHltbFallbackTable() = runBlocking {
        val dao = database.hltbFallbackDao()
        val count = dao.countEntries()
        assertTrue("HLTB_FALLBACK should be pre-populated on open", count >= 20)

        val eldenRing = dao.findByNormalizedTitle("elden ring")
        assertNotNull(eldenRing)
        assertTrue(eldenRing!!.main_story_ms > 86_400_000L)

        val defaultMedian = dao.findByGenreCategory("genre:default")
        assertNotNull(defaultMedian)
    }
}
