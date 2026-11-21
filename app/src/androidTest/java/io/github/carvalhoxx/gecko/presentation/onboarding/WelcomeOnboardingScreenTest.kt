package io.github.carvalhoxx.gecko.presentation.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.carvalhoxx.gecko.domain.model.ByokConnectionState
import io.github.carvalhoxx.gecko.domain.model.ByokProvider
import io.github.carvalhoxx.gecko.ui.theme.GeckoTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Testes Compose UI para `WelcomeOnboardingScreen` (Etapa 3.2):
 * - Fluxo completo de 3 passos (`ONB_01_Welcome`, `ONB_02_Pace`, `ONB_03_Platforms`).
 * - Cabeçalho fixo (`Passo X de 3`) e rodapé assimétrico obrigatório a partir do Passo 2 (`← Voltar` + CTA).
 * - Abertura do Bottom Sheet multi-campo para IGDB e mascaramento `PasswordVisualTransformation` (`TUI-02`).
 * - Exclusividade UDF entre botão `Validar` e badge `✓ Conectado`.
 */
@RunWith(AndroidJUnit4::class)
class WelcomeOnboardingScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun welcomeOnboarding_threeStepFlow_enforcesConsistentHeaderAsymmetricFooterAndByokMasking() {
        var state by mutableStateOf(OnboardingUiState())
        var completed by mutableStateOf(false)

        composeTestRule.setContent {
            GeckoTheme {
                WelcomeOnboardingScreen(
                    uiState = state,
                    onNameChange = { state = state.copy(userName = it) },
                    onAvatarSelected = { state = state.copy(avatarLocalUri = it) },
                    onIncrementPace = {
                        state = state.copy(
                            dailyAvailabilityMs = state.dailyAvailabilityMs + OnboardingUiState.PACE_STEP_INCREMENT_MS
                        )
                    },
                    onDecrementPace = {
                        state = state.copy(
                            dailyAvailabilityMs = (
                                state.dailyAvailabilityMs - OnboardingUiState.PACE_STEP_INCREMENT_MS
                                ).coerceAtLeast(OnboardingUiState.MIN_DAILY_AVAILABILITY_MS)
                        )
                    },
                    onSelectPacePreset = { state = state.copy(dailyAvailabilityMs = it) },
                    onSelectPlayStyle = { state = state.copy(defaultPlayStyle = it) },
                    onSelectAnnualGoal = { state = state.copy(annualGoalTarget = it) },
                    onPsnNpssoChange = { state = state.copy(psnNpssoInput = it) },
                    onValidatePsnClick = {
                        state = state.copy(
                            providerStates = state.providerStates + (ByokProvider.PSN to ByokConnectionState.CONNECTED)
                        )
                    },
                    onOpenProviderSheet = { provider ->
                        state = state.copy(activeBottomSheetProvider = provider)
                    },
                    onDismissProviderSheet = {
                        state = state.copy(activeBottomSheetProvider = null)
                    },
                    onSaveMultiFieldCredentials = { provider, _, _ ->
                        state = state.copy(
                            providerStates = state.providerStates + (provider to ByokConnectionState.CONNECTED),
                            activeBottomSheetProvider = null
                        )
                    },
                    onNextClick = {
                        if (state.currentStep < 2) {
                            state = state.copy(currentStep = state.currentStep + 1)
                        } else {
                            completed = true
                        }
                    },
                    onBackClick = {
                        if (state.currentStep > 0) {
                            state = state.copy(currentStep = state.currentStep - 1)
                        }
                    },
                    onStepSwiped = { page ->
                        state = state.copy(currentStep = page)
                    },
                    onSkipOfflineClick = { completed = true },
                    onNavigateToCsvImport = {}
                )
            }
        }

        // Passo 1 de 3: Cabeçalho fixo presente, botão Voltar ausente, botão Continuar desabilitado até preencher nome, Avatar picker >= 48dp
        composeTestRule.onNodeWithTag("onboarding_step_counter").assertTextEquals("Passo 1 de 3")
        composeTestRule.onNodeWithTag("onboarding_back_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("onboarding_next_button").assertIsNotEnabled()
        composeTestRule
            .onNodeWithTag("onboarding_avatar_picker_button")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)

        // Preenche nome no Passo 1 (em foco: exibe botão limpar X, não exibe ✓, e habilita botão Continuar)
        composeTestRule.onNodeWithTag("onboarding_name_field").performTextInput("Carvs")
        composeTestRule.onNodeWithTag("onboarding_name_clear_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_name_valid_icon").assertDoesNotExist()
        composeTestRule.onNodeWithTag("onboarding_next_button").assertIsEnabled()

        // Avança para Passo 2 de 3
        composeTestRule.onNodeWithTag("onboarding_next_button").performClick()
        composeTestRule.waitForIdle()

        // Passo 2 de 3: Cabeçalho "Passo 2 de 3", rodapé assimétrico com botão "← Voltar" + "Continuar →"
        composeTestRule.onNodeWithTag("onboarding_step_counter").assertTextEquals("Passo 2 de 3")
        composeTestRule.onNodeWithTag("onboarding_back_button").assertIsDisplayed()
        composeTestRule
            .onNodeWithTag("onboarding_pace_title")
            .assertTextEquals("Qual é o seu ritmo, Carvs?")
        composeTestRule.onNodeWithTag("onboarding_pace_display").assertTextEquals("2h 00m")
        composeTestRule
            .onNodeWithTag("onboarding_pace_simulator_text")
            .assertTextContains("Nesse ritmo, um jogo de 20h leva ~10 dias", substring = true)

        // Altera ritmo para 1h 00m e verifica atualização reativa do simulador (~20 dias)
        composeTestRule.onNodeWithTag("onboarding_pace_preset_3600000").performClick()
        composeTestRule.onNodeWithTag("onboarding_pace_display").assertTextEquals("1h 00m")
        composeTestRule
            .onNodeWithTag("onboarding_pace_simulator_text")
            .assertTextContains("Nesse ritmo, um jogo de 20h leva ~20 dias", substring = true)

        // Avança para Passo 3 de 3
        composeTestRule.onNodeWithTag("onboarding_next_button").performClick()
        composeTestRule.waitForIdle()

        // Passo 3 de 3: Cabeçalho "Passo 3 de 3", botão "← Voltar" preservado no último passo, CTA "Concluir →"
        composeTestRule.onNodeWithTag("onboarding_step_counter").assertTextEquals("Passo 3 de 3")
        composeTestRule.onNodeWithTag("onboarding_back_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_skip_now_button").assertIsDisplayed()

        // Abre Bottom Sheet multi-campo do IGDB e preenche Client ID e Client Secret mascarados
        composeTestRule.onNodeWithTag("byok_configure_button_IGDB").performClick()
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithTag("byok_sheet_primary_field_IGDB")
            .performTextInput("twitch_client_id_123")
        composeTestRule
            .onNodeWithTag("byok_sheet_secondary_field_IGDB")
            .performTextInput("twitch_client_secret_456")
        composeTestRule.onNodeWithTag("byok_sheet_save_IGDB").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("byok_connected_badge_IGDB").assertIsDisplayed()

        // TUI-02: Campo PSN npsso mascarado com PasswordVisualTransformation e exclusividade Validar vs Conectado
        val validNpsso = "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6a7b8c9d0e1f2"
        composeTestRule.onNodeWithTag("byok_token_field").performScrollTo().performTextInput(validNpsso)
        composeTestRule
            .onNodeWithTag("byok_token_field")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
            .assert(
                SemanticsMatcher("EditableText is masked with 64 bullets") { node ->
                    val maskedText = node.config[SemanticsProperties.EditableText].text
                    maskedText.length == 64 && maskedText != validNpsso
                }
            )

        composeTestRule.onNodeWithTag("byok_token_field_validate_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("byok_token_field_connected_badge").assertDoesNotExist()

        composeTestRule.onNodeWithTag("byok_token_field_validate_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("byok_token_field_connected_badge").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("byok_token_field_validate_button").assertDoesNotExist()

        // Conclui Onboarding no botão primário do rodapé assimétrico
        composeTestRule.onNodeWithTag("onboarding_next_button").performClick()
        assertTrue(completed)
    }
}
