package io.github.carvalhoxx.gecko.presentation.navigation

import io.github.carvalhoxx.gecko.core.ReleaseFeatureFlags
import io.github.carvalhoxx.gecko.domain.model.ByokProvider
import io.github.carvalhoxx.gecko.presentation.components.ByokFieldTrailingVisualState
import io.github.carvalhoxx.gecko.presentation.components.multiFieldSpecFor
import io.github.carvalhoxx.gecko.presentation.components.resolveByokFieldTrailingState
import io.github.carvalhoxx.gecko.ui.theme.GeckoBackground
import io.github.carvalhoxx.gecko.ui.theme.GeckoOledDarkColorScheme
import io.github.carvalhoxx.gecko.ui.theme.GeckoOutlineVariant
import io.github.carvalhoxx.gecko.ui.theme.GeckoPrimary
import io.github.carvalhoxx.gecko.ui.theme.GeckoStatusPaused
import io.github.carvalhoxx.gecko.ui.theme.GeckoSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeckoNavigationAndDesignTokensTest {

    @Test
    fun `topLevelDestinations returns 4 tabs in MVP v1_0 and hides Analytics`() {
        val destinations = GeckoRoutes.topLevelDestinations(ReleaseFeatureFlags.MVP_V1_0)

        assertEquals(4, destinations.size)
        assertEquals(
            listOf(
                GeckoTopLevelDestination.DASHBOARD,
                GeckoTopLevelDestination.LIBRARY,
                GeckoTopLevelDestination.SCHEDULER,
                GeckoTopLevelDestination.SETTINGS
            ),
            destinations
        )
        assertFalse(GeckoRoutes.isTopLevelRoute(GeckoRoutes.ANALYTICS, ReleaseFeatureFlags.MVP_V1_0))
    }

    @Test
    fun `topLevelDestinations returns 5 tabs in Expansion v1_1 including Analytics`() {
        val destinations = GeckoRoutes.topLevelDestinations(ReleaseFeatureFlags.EXPANSION_V1_1)

        assertEquals(5, destinations.size)
        assertTrue(destinations.contains(GeckoTopLevelDestination.ANALYTICS))
        assertTrue(GeckoRoutes.isTopLevelRoute(GeckoRoutes.ANALYTICS, ReleaseFeatureFlags.EXPANSION_V1_1))
    }

    @Test
    fun `resolveByokFieldTrailingState enforces visual state exclusivity between focus and validated`() {
        // Em foco com texto: apenas botão limpar (X), nunca ícone validado (✓)
        assertEquals(
            ByokFieldTrailingVisualState.FOCUSED_CLEARABLE,
            resolveByokFieldTrailingState(isFocused = true, value = "secret123", isValidated = true)
        )

        // Em foco sem texto: nenhum ícone
        assertEquals(
            ByokFieldTrailingVisualState.NONE,
            resolveByokFieldTrailingState(isFocused = true, value = "", isValidated = true)
        )

        // Sem foco e validado: apenas ícone validado (✓)
        assertEquals(
            ByokFieldTrailingVisualState.UNFOCUSED_VALIDATED,
            resolveByokFieldTrailingState(isFocused = false, value = "secret123", isValidated = true)
        )

        // Sem foco e não validado: nenhum ícone
        assertEquals(
            ByokFieldTrailingVisualState.NONE,
            resolveByokFieldTrailingState(isFocused = false, value = "secret123", isValidated = false)
        )
    }

    @Test
    fun `OLED Dark palette and multi-field BYOK specs match M3 invariants`() {
        assertEquals(0xFF0D1210, GeckoBackground.value.toLong() ushr 32)
        assertEquals(0xFF18201C, GeckoSurface.value.toLong() ushr 32)
        assertEquals(0xFF00E676, GeckoPrimary.value.toLong() ushr 32)
        assertEquals(0xFF2A3530, GeckoOutlineVariant.value.toLong() ushr 32)
        assertEquals(0xFFF59E0B, GeckoStatusPaused.value.toLong() ushr 32)
        assertEquals(GeckoPrimary, GeckoOledDarkColorScheme.primary)
        assertEquals(GeckoBackground, GeckoOledDarkColorScheme.background)

        val igdbSpec = multiFieldSpecFor(ByokProvider.IGDB)
        assertEquals("Client ID", igdbSpec.primaryFieldLabel)
        assertEquals("Client Secret", igdbSpec.secondaryFieldLabel)

        val steamSpec = multiFieldSpecFor(ByokProvider.STEAM)
        assertEquals("Web API Key", steamSpec.primaryFieldLabel)
        assertEquals("SteamID64", steamSpec.secondaryFieldLabel)
    }
}
