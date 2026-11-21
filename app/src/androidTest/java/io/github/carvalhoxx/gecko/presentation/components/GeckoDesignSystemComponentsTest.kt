package io.github.carvalhoxx.gecko.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.carvalhoxx.gecko.core.ReleaseFeatureFlags
import io.github.carvalhoxx.gecko.domain.model.ByokConnectionState
import io.github.carvalhoxx.gecko.domain.model.ByokProvider
import io.github.carvalhoxx.gecko.domain.model.LicenseType
import io.github.carvalhoxx.gecko.presentation.navigation.GeckoBottomNavigationBar
import io.github.carvalhoxx.gecko.presentation.navigation.GeckoRoutes
import io.github.carvalhoxx.gecko.ui.theme.GeckoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Testes Compose UI (Etapa 3.1):
 * - Renderização distinta de LicenseBadge (Possuo vs SaaS vs Quero Comprar)
 * - Exclusividade de estado visual do ByokSecretTextField (foco X vs validado ✓, Validar vs ✓ Conectado)
 * - Mascaramento PasswordVisualTransformation na Semantics Tree (TUI-02)
 * - Alvos de toque mínimos (>= 48.dp) em SelectableChip e botões BYOK
 * - Adaptação da Bottom Navigation Bar conforme ReleaseFeatureFlags (4 abas v1.0 vs 5 abas v1.1)
 */
@RunWith(AndroidJUnit4::class)
class GeckoDesignSystemComponentsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun licenseBadge_rendersDistinctOwnedAndSaasBadges() {
        composeTestRule.setContent {
            GeckoTheme {
                Column {
                    LicenseBadge(licenseType = LicenseType.OWNED)
                    LicenseBadge(licenseType = LicenseType.SUBSCRIPTION)
                    LicenseBadge(licenseType = LicenseType.WISHLIST)
                }
            }
        }

        composeTestRule
            .onNodeWithTag("license_badge_OWNED")
            .assertIsDisplayed()
            .assertTextEquals("Possuo")

        composeTestRule
            .onNodeWithTag("license_badge_SUBSCRIPTION")
            .assertIsDisplayed()
            .assertTextEquals("SaaS")

        composeTestRule
            .onNodeWithTag("license_badge_WISHLIST")
            .assertIsDisplayed()
            .assertTextEquals("Quero Comprar")
    }

    @Test
    fun byokSecretTextField_masksInputAndEnforcesExclusiveVisualStates() {
        var token by mutableStateOf("")
        var connectionState by mutableStateOf(ByokConnectionState.NOT_CONFIGURED)

        composeTestRule.setContent {
            GeckoTheme {
                Column {
                    ByokSecretTextField(
                        label = "Cookie npsso (64 caracteres)",
                        value = token,
                        onValueChange = { token = it },
                        isValidated = token.length == 64,
                        connectionState = connectionState,
                        onValidateClick = { connectionState = ByokConnectionState.CONNECTED },
                        testTag = "byok_token_field"
                    )
                    SelectableChip(
                        label = "Normal",
                        subtitle = "Hist. + Extras",
                        selected = true,
                        onClick = {},
                        testTag = "focus_sink_chip"
                    )
                }
            }
        }

        val validNpsso = "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6a7b8c9d0e1f2"

        // Ao digitar no campo, o campo ganha foco: deve mostrar botão limpar (X) e NÃO mostrar ícone (✓)
        composeTestRule
            .onNodeWithTag("byok_token_field")
            .performTextInput(validNpsso)

        // Valida mascaramento na Semantics Tree via onNodeWithTag (proibido onNodeWithText para segredos)
        composeTestRule
            .onNodeWithTag("byok_token_field")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
            .assert(
                SemanticsMatcher("EditableText is masked with 64 bullets") { node ->
                    val maskedText = node.config[SemanticsProperties.EditableText].text
                    maskedText.length == 64 && maskedText != validNpsso
                }
            )

        composeTestRule.onNodeWithTag("byok_token_field_clear_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("byok_token_field_valid_icon").assertDoesNotExist()

        // Antes de conectar: mostra botão "Validar" e NÃO mostra "✓ Conectado"
        composeTestRule.onNodeWithTag("byok_token_field_validate_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("byok_token_field_connected_badge").assertDoesNotExist()

        // Ao validar/conectar: oculta botão "Validar" e exibe exclusivamente "✓ Conectado"
        composeTestRule.onNodeWithTag("byok_token_field_validate_button").performClick()
        composeTestRule.onNodeWithTag("byok_token_field_connected_badge").assertIsDisplayed()
        composeTestRule.onNodeWithTag("byok_token_field_validate_button").assertDoesNotExist()
    }

    @Test
    fun selectableChip_andByokMultiInputCard_respect48dpMinimumTouchTargets() {
        var selected by mutableStateOf(false)

        composeTestRule.setContent {
            GeckoTheme {
                Column {
                    SelectableChip(
                        label = "Platina",
                        subtitle = "100% / Tudo",
                        selected = selected,
                        onClick = { selected = !selected },
                        testTag = "play_style_chip_platinum"
                    )
                    ByokMultiInputProviderCard(
                        provider = ByokProvider.IGDB,
                        connectionState = ByokConnectionState.NOT_CONFIGURED,
                        onOpenSheetClick = {}
                    )
                }
            }
        }

        composeTestRule
            .onNodeWithTag("play_style_chip_platinum")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        composeTestRule
            .onNodeWithTag("play_style_chip_platinum")
            .assertIsSelected()

        composeTestRule
            .onNodeWithTag("byok_configure_button_IGDB")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun geckoBottomNavigationBar_adaptsTabsToReleaseFeatureFlags() {
        var flags by mutableStateOf(ReleaseFeatureFlags.MVP_V1_0)

        composeTestRule.setContent {
            GeckoTheme {
                GeckoBottomNavigationBar(
                    currentRoute = GeckoRoutes.DASHBOARD,
                    featureFlags = flags,
                    onNavigateToDestination = {}
                )
            }
        }

        // MVP v1.0: 4 abas visíveis, Analytics oculta
        composeTestRule.onNodeWithTag("nav_tab_dashboard").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_tab_library").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_tab_scheduler").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_tab_settings").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_tab_analytics").assertDoesNotExist()

        // Expansão v1.1: 5ª aba Analytics habilitada
        flags = ReleaseFeatureFlags.EXPANSION_V1_1
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("nav_tab_analytics").assertIsDisplayed()
    }
}
