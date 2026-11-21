package io.github.carvalhoxx.gecko.core

/**
 * Governança central de escopo por release (Matriz MoSCoW).
 *
 * Injetado via Hilt nas ViewModels e na navegação global para manter recursos de
 * releases futuras ocultos e desacoplados durante o MVP v1.0.
 */
data class ReleaseFeatureFlags(
    val enableV11Features: Boolean = ENABLE_V1_1_FEATURES,
    val enableV2Features: Boolean = ENABLE_V2_FEATURES
) {
    init {
        require(!enableV2Features || enableV11Features) {
            "enableV2Features requires enableV11Features to be enabled."
        }
    }

    val isAnalyticsHubEnabled: Boolean get() = enableV11Features
    val isAnnualGoalEnabled: Boolean get() = enableV11Features
    val isMultiPlaythroughAndJournalEnabled: Boolean get() = enableV11Features
    val isIdleDayOneRecommendationEnabled: Boolean get() = enableV11Features
    val isWishlistDynamicFiltersEnabled: Boolean get() = enableV11Features
    val isCollectionsAndFranchisesEnabled: Boolean get() = enableV11Features
    val isRetroAchievementsEnabled: Boolean get() = enableV11Features
    val isDataExportEnabled: Boolean get() = enableV11Features

    val isFinanceModuleEnabled: Boolean get() = enableV2Features
    val isPersonalCalibrationEnabled: Boolean get() = enableV2Features
    val isSaasExpiryAlertsEnabled: Boolean get() = enableV2Features
    val isGlanceWidgetsEnabled: Boolean get() = enableV2Features

    companion object {
        const val ENABLE_V1_1_FEATURES: Boolean = false
        const val ENABLE_V2_FEATURES: Boolean = false

        val MVP_V1_0: ReleaseFeatureFlags = ReleaseFeatureFlags(
            enableV11Features = false,
            enableV2Features = false
        )

        val EXPANSION_V1_1: ReleaseFeatureFlags = ReleaseFeatureFlags(
            enableV11Features = true,
            enableV2Features = false
        )

        val EVOLUTION_V2_0: ReleaseFeatureFlags = ReleaseFeatureFlags(
            enableV11Features = true,
            enableV2Features = true
        )

        val DEFAULT: ReleaseFeatureFlags = ReleaseFeatureFlags()
    }
}
