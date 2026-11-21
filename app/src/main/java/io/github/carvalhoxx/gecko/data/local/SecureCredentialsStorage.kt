package io.github.carvalhoxx.gecko.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class IgdbCredentials(val clientId: String, val clientSecret: String) {
    override fun toString(): String = "IgdbCredentials(clientId=***, clientSecret=***)"
}

data class RawgCredentials(val apiKey: String) {
    override fun toString(): String = "RawgCredentials(apiKey=***)"
}

data class SteamCredentials(val apiKey: String, val steamId64: String) {
    override fun toString(): String = "SteamCredentials(apiKey=***, steamId64=***)"
}

data class PsnCredentials(
    val npsso: String,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresAtMs: Long? = null
) {
    override fun toString(): String {
        val maskedAccess = if (accessToken != null) "***" else "null"
        val maskedRefresh = if (refreshToken != null) "***" else "null"
        return "PsnCredentials(npsso=***, accessToken=$maskedAccess, " +
            "refreshToken=$maskedRefresh, expiresAtMs=$expiresAtMs)"
    }
}

data class RetroAchievementsCredentials(val username: String, val apiKey: String) {
    override fun toString(): String = "RetroAchievementsCredentials(username=***, apiKey=***)"
}

/**
 * Cofre criptográfico BYOK respaldado por EncryptedSharedPreferences e Android Keystore (RNF03).
 * Nenhum segredo é impresso em logs ou exposto via toString().
 */
class SecureCredentialsStorage(private val prefs: SharedPreferences) {

    fun saveIgdbCredentials(credentials: IgdbCredentials) {
        prefs.edit()
            .putString(KEY_IGDB_CLIENT_ID, credentials.clientId)
            .putString(KEY_IGDB_CLIENT_SECRET, credentials.clientSecret)
            .apply()
    }

    fun getIgdbCredentials(): IgdbCredentials? {
        val clientId = prefs.getString(KEY_IGDB_CLIENT_ID, null) ?: return null
        val clientSecret = prefs.getString(KEY_IGDB_CLIENT_SECRET, null) ?: return null
        return IgdbCredentials(clientId = clientId, clientSecret = clientSecret)
    }

    fun clearIgdbCredentials() {
        prefs.edit()
            .remove(KEY_IGDB_CLIENT_ID)
            .remove(KEY_IGDB_CLIENT_SECRET)
            .apply()
    }

    fun saveRawgCredentials(credentials: RawgCredentials) {
        prefs.edit()
            .putString(KEY_RAWG_API_KEY, credentials.apiKey)
            .apply()
    }

    fun getRawgCredentials(): RawgCredentials? {
        val apiKey = prefs.getString(KEY_RAWG_API_KEY, null) ?: return null
        return RawgCredentials(apiKey = apiKey)
    }

    fun clearRawgCredentials() {
        prefs.edit()
            .remove(KEY_RAWG_API_KEY)
            .apply()
    }

    fun saveSteamCredentials(credentials: SteamCredentials) {
        prefs.edit()
            .putString(KEY_STEAM_API_KEY, credentials.apiKey)
            .putString(KEY_STEAM_ID64, credentials.steamId64)
            .apply()
    }

    fun getSteamCredentials(): SteamCredentials? {
        val apiKey = prefs.getString(KEY_STEAM_API_KEY, null) ?: return null
        val steamId64 = prefs.getString(KEY_STEAM_ID64, null) ?: return null
        return SteamCredentials(apiKey = apiKey, steamId64 = steamId64)
    }

    fun clearSteamCredentials() {
        prefs.edit()
            .remove(KEY_STEAM_API_KEY)
            .remove(KEY_STEAM_ID64)
            .apply()
    }

    fun savePsnCredentials(credentials: PsnCredentials) {
        prefs.edit().apply {
            putString(KEY_PSN_NPSSO, credentials.npsso)
            if (credentials.accessToken != null) {
                putString(KEY_PSN_ACCESS_TOKEN, credentials.accessToken)
            } else {
                remove(KEY_PSN_ACCESS_TOKEN)
            }
            if (credentials.refreshToken != null) {
                putString(KEY_PSN_REFRESH_TOKEN, credentials.refreshToken)
            } else {
                remove(KEY_PSN_REFRESH_TOKEN)
            }
            if (credentials.expiresAtMs != null) {
                putLong(KEY_PSN_EXPIRES_AT_MS, credentials.expiresAtMs)
            } else {
                remove(KEY_PSN_EXPIRES_AT_MS)
            }
        }.apply()
    }

    fun updatePsnTokens(accessToken: String, refreshToken: String?, expiresAtMs: Long) {
        prefs.edit().apply {
            putString(KEY_PSN_ACCESS_TOKEN, accessToken)
            if (refreshToken != null) {
                putString(KEY_PSN_REFRESH_TOKEN, refreshToken)
            } else {
                remove(KEY_PSN_REFRESH_TOKEN)
            }
            putLong(KEY_PSN_EXPIRES_AT_MS, expiresAtMs)
        }.apply()
    }

    fun getPsnCredentials(): PsnCredentials? {
        val npsso = prefs.getString(KEY_PSN_NPSSO, null) ?: return null
        val accessToken = prefs.getString(KEY_PSN_ACCESS_TOKEN, null)
        val refreshToken = prefs.getString(KEY_PSN_REFRESH_TOKEN, null)
        val expiresAtMs = if (prefs.contains(KEY_PSN_EXPIRES_AT_MS)) {
            prefs.getLong(KEY_PSN_EXPIRES_AT_MS, 0L)
        } else {
            null
        }
        return PsnCredentials(
            npsso = npsso,
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAtMs = expiresAtMs
        )
    }

    fun clearPsnCredentials() {
        prefs.edit()
            .remove(KEY_PSN_NPSSO)
            .remove(KEY_PSN_ACCESS_TOKEN)
            .remove(KEY_PSN_REFRESH_TOKEN)
            .remove(KEY_PSN_EXPIRES_AT_MS)
            .apply()
    }

    fun saveRetroAchievementsCredentials(credentials: RetroAchievementsCredentials) {
        prefs.edit()
            .putString(KEY_RA_USERNAME, credentials.username)
            .putString(KEY_RA_API_KEY, credentials.apiKey)
            .apply()
    }

    fun getRetroAchievementsCredentials(): RetroAchievementsCredentials? {
        val username = prefs.getString(KEY_RA_USERNAME, null) ?: return null
        val apiKey = prefs.getString(KEY_RA_API_KEY, null) ?: return null
        return RetroAchievementsCredentials(username = username, apiKey = apiKey)
    }

    fun clearRetroAchievementsCredentials() {
        prefs.edit()
            .remove(KEY_RA_USERNAME)
            .remove(KEY_RA_API_KEY)
            .apply()
    }

    fun hasAnyConfiguredProvider(): Boolean = getIgdbCredentials() != null ||
        getRawgCredentials() != null ||
        getSteamCredentials() != null ||
        getPsnCredentials() != null ||
        getRetroAchievementsCredentials() != null

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val PREFS_FILE_NAME = "gecko_byok_credentials"

        private const val KEY_IGDB_CLIENT_ID = "igdb_client_id"
        private const val KEY_IGDB_CLIENT_SECRET = "igdb_client_secret"
        private const val KEY_RAWG_API_KEY = "rawg_api_key"
        private const val KEY_STEAM_API_KEY = "steam_api_key"
        private const val KEY_STEAM_ID64 = "steam_id64"
        private const val KEY_PSN_NPSSO = "psn_npsso"
        private const val KEY_PSN_ACCESS_TOKEN = "psn_access_token"
        private const val KEY_PSN_REFRESH_TOKEN = "psn_refresh_token"
        private const val KEY_PSN_EXPIRES_AT_MS = "psn_expires_at_ms"
        private const val KEY_RA_USERNAME = "retro_achievements_username"
        private const val KEY_RA_API_KEY = "retro_achievements_api_key"

        @Suppress("DEPRECATION")
        fun createEncryptedPreferences(context: Context, fileName: String = PREFS_FILE_NAME): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                fileName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }
}
