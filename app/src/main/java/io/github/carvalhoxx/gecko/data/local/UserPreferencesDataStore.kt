package io.github.carvalhoxx.gecko.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.carvalhoxx.gecko.core.ByokCredentialValidator
import io.github.carvalhoxx.gecko.core.CredentialValidationResult
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

data class UserPreferences(
    val profileName: String = "",
    val avatarLocalUri: String? = null,
    val dailyAvailabilityMs: Long = DEFAULT_DAILY_AVAILABILITY_MS,
    val defaultPlayStyle: String = DEFAULT_PLAY_STYLE,
    val isFirstLaunchCompleted: Boolean = false,
    val enableFinanceModule: Boolean = false,
    val calibrationFactor: Float = DEFAULT_CALIBRATION_FACTOR
) {
    companion object {
        const val DEFAULT_DAILY_AVAILABILITY_MS: Long = 7_200_000L // 2h 00m
        const val DEFAULT_PLAY_STYLE: String = "NORMAL"
        const val DEFAULT_CALIBRATION_FACTOR: Float = 1.0f
    }
}

/**
 * Persistência local de preferências não sensíveis do usuário via Preferences DataStore.
 * Todas as durações são armazenadas estritamente em Long (milissegundos) conforme RNF07.
 */
class UserPreferencesDataStore(private val dataStore: DataStore<Preferences>) {

    val userPreferencesFlow: Flow<UserPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            UserPreferences(
                profileName = prefs[KEY_PROFILE_NAME].orEmpty(),
                avatarLocalUri = prefs[KEY_AVATAR_LOCAL_URI],
                dailyAvailabilityMs = prefs[KEY_DAILY_AVAILABILITY_MS]
                    ?: UserPreferences.DEFAULT_DAILY_AVAILABILITY_MS,
                defaultPlayStyle = prefs[KEY_DEFAULT_PLAY_STYLE]
                    ?: UserPreferences.DEFAULT_PLAY_STYLE,
                isFirstLaunchCompleted = prefs[KEY_IS_FIRST_LAUNCH_COMPLETED] ?: false,
                enableFinanceModule = prefs[KEY_ENABLE_FINANCE_MODULE] ?: false,
                calibrationFactor = prefs[KEY_CALIBRATION_FACTOR]
                    ?: UserPreferences.DEFAULT_CALIBRATION_FACTOR
            )
        }

    suspend fun updateProfile(name: String, avatarLocalUri: String?) {
        dataStore.edit { prefs ->
            prefs[KEY_PROFILE_NAME] = name.trim()
            if (avatarLocalUri.isNullOrBlank()) {
                prefs.remove(KEY_AVATAR_LOCAL_URI)
            } else {
                prefs[KEY_AVATAR_LOCAL_URI] = avatarLocalUri
            }
        }
    }

    suspend fun updateDailyAvailabilityMs(dailyAvailabilityMs: Long) {
        val validation = ByokCredentialValidator.validateDailyAvailabilityMs(dailyAvailabilityMs)
        require(validation is CredentialValidationResult.Valid) {
            "Daily availability must be strictly greater than 0 ms and at most 24h."
        }
        dataStore.edit { prefs ->
            prefs[KEY_DAILY_AVAILABILITY_MS] = dailyAvailabilityMs
        }
    }

    suspend fun updateDefaultPlayStyle(playStyle: String) {
        require(playStyle.isNotBlank()) { "Default play style must not be blank." }
        dataStore.edit { prefs ->
            prefs[KEY_DEFAULT_PLAY_STYLE] = playStyle.trim()
        }
    }

    suspend fun setFirstLaunchCompleted(completed: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_IS_FIRST_LAUNCH_COMPLETED] = completed
        }
    }

    suspend fun setFinanceModuleEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_ENABLE_FINANCE_MODULE] = enabled
        }
    }

    suspend fun updateCalibrationFactor(factor: Float) {
        val validation = ByokCredentialValidator.validateCalibrationFactor(factor)
        require(validation is CredentialValidationResult.Valid) {
            "Calibration factor must be finite and within valid bounds."
        }
        dataStore.edit { prefs ->
            prefs[KEY_CALIBRATION_FACTOR] = factor
        }
    }

    suspend fun clearAll() {
        dataStore.edit { prefs ->
            prefs.clear()
        }
    }

    companion object {
        const val DATA_STORE_FILE_NAME = "gecko_user_preferences"

        private val KEY_PROFILE_NAME = stringPreferencesKey("profile_name")
        private val KEY_AVATAR_LOCAL_URI = stringPreferencesKey("avatar_local_uri")
        private val KEY_DAILY_AVAILABILITY_MS = longPreferencesKey("daily_availability_ms")
        private val KEY_DEFAULT_PLAY_STYLE = stringPreferencesKey("default_play_style")
        private val KEY_IS_FIRST_LAUNCH_COMPLETED = booleanPreferencesKey("is_first_launch_completed")
        private val KEY_ENABLE_FINANCE_MODULE = booleanPreferencesKey("enable_finance_module")
        private val KEY_CALIBRATION_FACTOR = floatPreferencesKey("calibration_factor")
    }
}
