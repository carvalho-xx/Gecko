package io.github.carvalhoxx.gecko.domain.model

data class UserProfile(val name: String, val avatarLocalUri: String? = null)

data class UserPaceSettings(
    val dailyAvailabilityMs: Long,
    val defaultPlayStyle: PlayStyle,
    val calibrationFactor: Float = 1.0f
)

data class UserSettings(
    val profile: UserProfile,
    val paceSettings: UserPaceSettings,
    val isFirstLaunchCompleted: Boolean,
    val enableFinanceModule: Boolean
)
