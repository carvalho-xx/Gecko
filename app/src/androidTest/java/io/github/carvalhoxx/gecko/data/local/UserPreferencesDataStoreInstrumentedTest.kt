package io.github.carvalhoxx.gecko.data.local

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserPreferencesDataStoreInstrumentedTest {

    private lateinit var testFile: File
    private lateinit var testScope: CoroutineScope
    private lateinit var preferencesDataStore: UserPreferencesDataStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        testFile = File(context.cacheDir, "test_prefs_${UUID.randomUUID()}.preferences_pb")
        testScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { testFile }
        )
        preferencesDataStore = UserPreferencesDataStore(dataStore)
    }

    @After
    fun tearDown() {
        testScope.cancel()
        testFile.delete()
    }

    @Test
    fun readWriteAndClearUserPreferencesFlow() = runBlocking {
        val defaults = preferencesDataStore.userPreferencesFlow.first()
        assertEquals("", defaults.profileName)
        assertNull(defaults.avatarLocalUri)
        assertEquals(UserPreferences.DEFAULT_DAILY_AVAILABILITY_MS, defaults.dailyAvailabilityMs)
        assertEquals(UserPreferences.DEFAULT_PLAY_STYLE, defaults.defaultPlayStyle)
        assertFalse(defaults.isFirstLaunchCompleted)
        assertFalse(defaults.enableFinanceModule)
        assertEquals(UserPreferences.DEFAULT_CALIBRATION_FACTOR, defaults.calibrationFactor, 0.001f)

        preferencesDataStore.updateProfile("Carlos", "content://media/avatar/42")
        preferencesDataStore.updateDailyAvailabilityMs(5_400_000L) // 1h 30m
        preferencesDataStore.updateDefaultPlayStyle("PLATINUM")
        preferencesDataStore.setFirstLaunchCompleted(true)
        preferencesDataStore.setFinanceModuleEnabled(true)
        preferencesDataStore.updateCalibrationFactor(1.25f)

        val updated = preferencesDataStore.userPreferencesFlow.first()
        assertEquals("Carlos", updated.profileName)
        assertEquals("content://media/avatar/42", updated.avatarLocalUri)
        assertEquals(5_400_000L, updated.dailyAvailabilityMs)
        assertEquals("PLATINUM", updated.defaultPlayStyle)
        assertTrue(updated.isFirstLaunchCompleted)
        assertTrue(updated.enableFinanceModule)
        assertEquals(1.25f, updated.calibrationFactor, 0.001f)

        preferencesDataStore.clearAll()
        val afterClear = preferencesDataStore.userPreferencesFlow.first()
        assertEquals("", afterClear.profileName)
        assertNull(afterClear.avatarLocalUri)
        assertFalse(afterClear.isFirstLaunchCompleted)
    }

    @Test(expected = IllegalArgumentException::class)
    fun updateDailyAvailabilityMsRejectsZeroOrNegative() = runBlocking {
        preferencesDataStore.updateDailyAvailabilityMs(0L)
    }
}
