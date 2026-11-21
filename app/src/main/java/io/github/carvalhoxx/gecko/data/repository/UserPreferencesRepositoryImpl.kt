package io.github.carvalhoxx.gecko.data.repository

import io.github.carvalhoxx.gecko.data.local.UserPreferencesDataStore
import io.github.carvalhoxx.gecko.data.repository.mapper.toDomain
import io.github.carvalhoxx.gecko.domain.model.PlayStyle
import io.github.carvalhoxx.gecko.domain.model.UserSettings
import io.github.carvalhoxx.gecko.domain.repository.UserPreferencesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UserPreferencesRepositoryImpl @Inject constructor(
    private val userPreferencesDataStore: UserPreferencesDataStore
) : UserPreferencesRepository {

    override fun observeUserSettings(): Flow<UserSettings> =
        userPreferencesDataStore.userPreferencesFlow.map { it.toDomain() }

    override suspend fun updateProfile(name: String, avatarLocalUri: String?) {
        userPreferencesDataStore.updateProfile(name = name, avatarLocalUri = avatarLocalUri)
    }

    override suspend fun updateDailyAvailabilityMs(dailyAvailabilityMs: Long) {
        userPreferencesDataStore.updateDailyAvailabilityMs(dailyAvailabilityMs)
    }

    override suspend fun updateDefaultPlayStyle(playStyle: PlayStyle) {
        userPreferencesDataStore.updateDefaultPlayStyle(playStyle.name)
    }

    override suspend fun setFirstLaunchCompleted(completed: Boolean) {
        userPreferencesDataStore.setFirstLaunchCompleted(completed)
    }

    override suspend fun setFinanceModuleEnabled(enabled: Boolean) {
        userPreferencesDataStore.setFinanceModuleEnabled(enabled)
    }

    override suspend fun updateCalibrationFactor(factor: Float) {
        userPreferencesDataStore.updateCalibrationFactor(factor)
    }

    override suspend fun clearAll() {
        userPreferencesDataStore.clearAll()
    }
}
