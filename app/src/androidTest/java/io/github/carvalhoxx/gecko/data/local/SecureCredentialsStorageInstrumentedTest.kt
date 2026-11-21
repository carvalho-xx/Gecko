package io.github.carvalhoxx.gecko.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureCredentialsStorageInstrumentedTest {

    private lateinit var context: Context
    private lateinit var prefsFileName: String
    private lateinit var storage: SecureCredentialsStorage

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefsFileName = "test_gecko_byok_${UUID.randomUUID()}"
        val encryptedPrefs = SecureCredentialsStorage.createEncryptedPreferences(context, prefsFileName)
        storage = SecureCredentialsStorage(encryptedPrefs)
    }

    @After
    fun tearDown() {
        storage.clearAll()
        context.deleteSharedPreferences(prefsFileName)
    }

    @Test
    fun saveReadAndClearAllByokProvidersUsingEncryptedSharedPreferences() {
        assertFalse(storage.hasAnyConfiguredProvider())

        val igdb = IgdbCredentials("igdb_client_1234", "igdb_secret_5678")
        val rawg = RawgCredentials("rawg_api_key_9999999999")
        val steam = SteamCredentials("0123456789ABCDEFabcdef0123456789", "76561198012345678")
        val psn = PsnCredentials(
            npsso = "A".repeat(64),
            accessToken = "psn_access_token_v1",
            refreshToken = "psn_refresh_token_v1",
            expiresAtMs = 1_900_000_000_000L
        )
        val ra = RetroAchievementsCredentials("gecko_player", "raKey12345678901234")

        storage.saveIgdbCredentials(igdb)
        storage.saveRawgCredentials(rawg)
        storage.saveSteamCredentials(steam)
        storage.savePsnCredentials(psn)
        storage.saveRetroAchievementsCredentials(ra)

        assertTrue(storage.hasAnyConfiguredProvider())
        assertEquals(igdb, storage.getIgdbCredentials())
        assertEquals(rawg, storage.getRawgCredentials())
        assertEquals(steam, storage.getSteamCredentials())
        assertEquals(psn, storage.getPsnCredentials())
        assertEquals(ra, storage.getRetroAchievementsCredentials())

        storage.updatePsnTokens(
            accessToken = "psn_access_token_v2",
            refreshToken = "psn_refresh_token_v2",
            expiresAtMs = 1_950_000_000_000L
        )
        val updatedPsn = storage.getPsnCredentials()
        assertEquals("A".repeat(64), updatedPsn?.npsso)
        assertEquals("psn_access_token_v2", updatedPsn?.accessToken)
        assertEquals("psn_refresh_token_v2", updatedPsn?.refreshToken)
        assertEquals(1_950_000_000_000L, updatedPsn?.expiresAtMs)

        // Verifica que toString() nunca expõe segredos em texto plano
        assertFalse(updatedPsn.toString().contains("psn_access_token_v2"))
        assertFalse(updatedPsn.toString().contains("psn_refresh_token_v2"))
        assertFalse(updatedPsn.toString().contains("AAAAAAAAAA"))
        assertFalse(igdb.toString().contains("igdb_secret_5678"))
        assertFalse(steam.toString().contains("0123456789ABCDEF"))

        storage.clearIgdbCredentials()
        assertNull(storage.getIgdbCredentials())
        assertTrue(storage.hasAnyConfiguredProvider())

        storage.clearAll()
        assertNull(storage.getRawgCredentials())
        assertNull(storage.getSteamCredentials())
        assertNull(storage.getPsnCredentials())
        assertNull(storage.getRetroAchievementsCredentials())
        assertFalse(storage.hasAnyConfiguredProvider())
    }
}
