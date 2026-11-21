package io.github.carvalhoxx.gecko.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseFeatureFlagsTest {

    @Test
    fun `MVP_V1_0 preset disables all v1_1 and v2_0 features and matches default`() {
        val flags = ReleaseFeatureFlags.MVP_V1_0

        assertEquals(flags, ReleaseFeatureFlags.DEFAULT)
        assertFalse(flags.enableV11Features)
        assertFalse(flags.enableV2Features)

        assertFalse(flags.isAnalyticsHubEnabled)
        assertFalse(flags.isAnnualGoalEnabled)
        assertFalse(flags.isMultiPlaythroughAndJournalEnabled)
        assertFalse(flags.isIdleDayOneRecommendationEnabled)
        assertFalse(flags.isWishlistDynamicFiltersEnabled)
        assertFalse(flags.isCollectionsAndFranchisesEnabled)
        assertFalse(flags.isRetroAchievementsEnabled)
        assertFalse(flags.isDataExportEnabled)

        assertFalse(flags.isFinanceModuleEnabled)
        assertFalse(flags.isPersonalCalibrationEnabled)
        assertFalse(flags.isSaasExpiryAlertsEnabled)
        assertFalse(flags.isGlanceWidgetsEnabled)
    }

    @Test
    fun `EXPANSION_V1_1 preset enables v1_1 features while keeping v2_0 disabled`() {
        val flags = ReleaseFeatureFlags.EXPANSION_V1_1

        assertTrue(flags.enableV11Features)
        assertFalse(flags.enableV2Features)

        assertTrue(flags.isAnalyticsHubEnabled)
        assertTrue(flags.isAnnualGoalEnabled)
        assertTrue(flags.isMultiPlaythroughAndJournalEnabled)
        assertTrue(flags.isIdleDayOneRecommendationEnabled)
        assertTrue(flags.isWishlistDynamicFiltersEnabled)
        assertTrue(flags.isCollectionsAndFranchisesEnabled)
        assertTrue(flags.isRetroAchievementsEnabled)
        assertTrue(flags.isDataExportEnabled)

        assertFalse(flags.isFinanceModuleEnabled)
        assertFalse(flags.isPersonalCalibrationEnabled)
        assertFalse(flags.isSaasExpiryAlertsEnabled)
        assertFalse(flags.isGlanceWidgetsEnabled)
    }

    @Test
    fun `EVOLUTION_V2_0 preset enables both v1_1 and v2_0 features`() {
        val flags = ReleaseFeatureFlags.EVOLUTION_V2_0

        assertTrue(flags.enableV11Features)
        assertTrue(flags.enableV2Features)

        assertTrue(flags.isAnalyticsHubEnabled)
        assertTrue(flags.isAnnualGoalEnabled)
        assertTrue(flags.isMultiPlaythroughAndJournalEnabled)
        assertTrue(flags.isIdleDayOneRecommendationEnabled)
        assertTrue(flags.isWishlistDynamicFiltersEnabled)
        assertTrue(flags.isCollectionsAndFranchisesEnabled)
        assertTrue(flags.isRetroAchievementsEnabled)
        assertTrue(flags.isDataExportEnabled)

        assertTrue(flags.isFinanceModuleEnabled)
        assertTrue(flags.isPersonalCalibrationEnabled)
        assertTrue(flags.isSaasExpiryAlertsEnabled)
        assertTrue(flags.isGlanceWidgetsEnabled)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `enabling v2_0 without v1_1 throws IllegalArgumentException`() {
        ReleaseFeatureFlags(
            enableV11Features = false,
            enableV2Features = true
        )
    }
}
