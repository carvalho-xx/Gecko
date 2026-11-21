package io.github.carvalhoxx.gecko.domain.repository

import io.github.carvalhoxx.gecko.domain.model.PlayStyle
import io.github.carvalhoxx.gecko.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    fun observeUserSettings(): Flow<UserSettings>

    suspend fun updateProfile(name: String, avatarLocalUri: String?)

    suspend fun updateDailyAvailabilityMs(dailyAvailabilityMs: Long)

    suspend fun updateDefaultPlayStyle(playStyle: PlayStyle)

    suspend fun setFirstLaunchCompleted(completed: Boolean)

    suspend fun setFinanceModuleEnabled(enabled: Boolean)

    suspend fun updateCalibrationFactor(factor: Float)

    suspend fun clearAll()
}
