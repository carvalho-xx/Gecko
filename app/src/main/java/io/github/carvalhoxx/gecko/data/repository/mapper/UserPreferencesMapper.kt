package io.github.carvalhoxx.gecko.data.repository.mapper

import io.github.carvalhoxx.gecko.data.local.UserPreferences
import io.github.carvalhoxx.gecko.domain.model.PlayStyle
import io.github.carvalhoxx.gecko.domain.model.UserPaceSettings
import io.github.carvalhoxx.gecko.domain.model.UserProfile
import io.github.carvalhoxx.gecko.domain.model.UserSettings

fun UserPreferences.toDomain(): UserSettings = UserSettings(
    profile = UserProfile(
        name = profileName,
        avatarLocalUri = avatarLocalUri
    ),
    paceSettings = UserPaceSettings(
        dailyAvailabilityMs = dailyAvailabilityMs,
        defaultPlayStyle = PlayStyle.fromStorageValue(defaultPlayStyle),
        calibrationFactor = calibrationFactor
    ),
    isFirstLaunchCompleted = isFirstLaunchCompleted,
    enableFinanceModule = enableFinanceModule
)
