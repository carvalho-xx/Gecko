package io.github.carvalhoxx.gecko.presentation.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.carvalhoxx.gecko.domain.model.ByokProvider
import io.github.carvalhoxx.gecko.domain.model.PlayStyle
import io.github.carvalhoxx.gecko.presentation.components.ByokCredentialsBottomSheet

private val WizardBg = Color(0xFF080A09)
private val StepDotActiveColor = Color(0xFF10B981)
private val StepDotDoneColor = Color(0xFF0E4F3A)
private val StepDotPendingColor = Color(0xFF1C2823)
private val StepLabelColor = Color(0xFF7E8F86)
private val PrimaryBtnBg = Color(0xFF10B981)
private val PrimaryBtnContent = Color(0xFF050706)
private val PrimaryBtnDisabledBg = Color(0xFF1A2822)
private val PrimaryBtnDisabledContent = Color(0xFF6E8077)
private val SecondaryBtnBg = Color(0xFF18201C)
private val SecondaryBtnBorder = Color(0xFF2A3530)
private val SecondaryBtnContent = Color(0xFFD5E2DC)

/**
 * Tela de Boas-Vindas e Configuração Inicial conectada via Hilt (`WelcomeOnboardingScreen`).
 */
@Composable
fun WelcomeOnboardingRoute(
    onOnboardingFinished: () -> Unit,
    onNavigateToCsvImport: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isCompleted) {
        if (uiState.isCompleted) {
            onOnboardingFinished()
        }
    }

    WelcomeOnboardingScreen(
        uiState = uiState,
        onNameChange = viewModel::onNameChange,
        onAvatarSelected = viewModel::onAvatarSelected,
        onIncrementPace = viewModel::onIncrementDailyPace,
        onDecrementPace = viewModel::onDecrementDailyPace,
        onSelectPacePreset = viewModel::onSelectDailyPacePreset,
        onSelectPlayStyle = viewModel::onSelectDefaultPlayStyle,
        onSelectAnnualGoal = viewModel::onSelectAnnualGoal,
        onPsnNpssoChange = viewModel::onPsnNpssoChange,
        onValidatePsnClick = viewModel::onValidatePsnNpsso,
        onOpenProviderSheet = viewModel::onOpenProviderBottomSheet,
        onDismissProviderSheet = viewModel::onDismissProviderBottomSheet,
        onSaveMultiFieldCredentials = viewModel::onSaveMultiFieldCredentials,
        onNextClick = viewModel::onNextStep,
        onBackClick = viewModel::onPreviousStep,
        onStepSwiped = viewModel::onStepChanged,
        onSkipOfflineClick = viewModel::onSkipOfflineMode,
        onNavigateToCsvImport = onNavigateToCsvImport
    )
}

/**
 * Componente stateless do Wizard de 3 Passos (`ONB_01_Welcome`, `ONB_02_Pace`, `ONB_03_Platforms`):
 * - Cabeçalho fixo com barra de progresso em 3 segmentos à esquerda e contador `Passo X de 3` à direita.
 * - `HorizontalPager` sincronizado bidirecionalmente com `uiState.currentStep`.
 * - Rodapé assimétrico obrigatório a partir do Passo 2: `← Voltar` (peso 1) + CTA `<= 3 palavras` (peso 2).
 */
@Composable
fun WelcomeOnboardingScreen(
    uiState: OnboardingUiState,
    onNameChange: (String) -> Unit,
    onAvatarSelected: (String?) -> Unit,
    onIncrementPace: () -> Unit,
    onDecrementPace: () -> Unit,
    onSelectPacePreset: (Long) -> Unit,
    onSelectPlayStyle: (PlayStyle) -> Unit,
    onSelectAnnualGoal: (Int) -> Unit,
    onPsnNpssoChange: (String) -> Unit,
    onValidatePsnClick: () -> Unit,
    onOpenProviderSheet: (ByokProvider) -> Unit,
    onDismissProviderSheet: () -> Unit,
    onSaveMultiFieldCredentials: (ByokProvider, String, String) -> Unit,
    onNextClick: () -> Unit,
    onBackClick: () -> Unit,
    onStepSwiped: (Int) -> Unit = {},
    onSkipOfflineClick: () -> Unit,
    onNavigateToCsvImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val pagerState = rememberPagerState(
        initialPage = uiState.currentStep,
        pageCount = { OnboardingUiState.TOTAL_STEPS }
    )

    LaunchedEffect(uiState.currentStep) {
        focusManager.clearFocus(force = true)
        if (pagerState.currentPage != uiState.currentStep) {
            pagerState.scrollToPage(uiState.currentStep)
        }
    }

    val isNextEnabled = uiState.currentStep != 0 || uiState.isNameValid

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("screen_welcome_onboarding"),
        color = WizardBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            OnboardingWizardHeader(
                currentStep = uiState.currentStep,
                totalSteps = uiState.totalSteps,
                onCloseOrSkipClick = onSkipOfflineClick
            )

            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("onboarding_horizontal_pager")
            ) { page ->
                when (page) {
                    0 -> OnboardingProfileStep(
                        userName = uiState.userName,
                        avatarInitial = uiState.avatarInitial,
                        avatarLocalUri = uiState.avatarLocalUri,
                        onNameChange = onNameChange,
                        onAvatarSelected = onAvatarSelected
                    )
                    1 -> OnboardingPaceStep(
                        uiState = uiState,
                        onIncrementPace = onIncrementPace,
                        onDecrementPace = onDecrementPace,
                        onSelectPacePreset = onSelectPacePreset,
                        onSelectPlayStyle = onSelectPlayStyle,
                        onSelectAnnualGoal = onSelectAnnualGoal
                    )
                    else -> OnboardingConnectionsStep(
                        uiState = uiState,
                        onPsnNpssoChange = onPsnNpssoChange,
                        onValidatePsnClick = onValidatePsnClick,
                        onOpenProviderSheet = onOpenProviderSheet,
                        onNavigateToCsvImport = onNavigateToCsvImport,
                        onSkipNowClick = onSkipOfflineClick
                    )
                }
            }

            OnboardingWizardFooter(
                currentStep = uiState.currentStep,
                isNextEnabled = isNextEnabled,
                onBackClick = onBackClick,
                onNextClick = onNextClick
            )
        }
    }

    val activeSheetProvider = uiState.activeBottomSheetProvider
    if (activeSheetProvider != null) {
        ByokCredentialsBottomSheet(
            provider = activeSheetProvider,
            errorMessage = uiState.bottomSheetErrorMessage,
            onDismiss = onDismissProviderSheet,
            onSaveAndValidate = { primary, secondary ->
                onSaveMultiFieldCredentials(activeSheetProvider, primary, secondary)
            }
        )
    }
}

/**
 * Cabeçalho fixo do Wizard (`[WIZARD: CONSISTENT_CHROME(Header + Asymmetric_Footer) ! ORPHAN_STEPS]`):
 * Botão fechar/pular `✕` (48x48dp) + indicador de progresso à esquerda e contador `Passo X de 3` à direita.
 */
@Composable
fun OnboardingWizardHeader(
    currentStep: Int,
    totalSteps: Int,
    onCloseOrSkipClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .testTag("onboarding_wizard_header"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                onClick = onCloseOrSkipClick,
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .size(48.dp)
                    .testTag("onboarding_close_skip_button"),
                shape = CircleShape,
                color = Color.Transparent
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "✕",
                        color = Color(0xFFC4C7C5),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Row(
                modifier = Modifier.testTag("onboarding_progress_indicator"),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (index in 0 until totalSteps) {
                    val isCurrent = index == currentStep
                    val isDone = index < currentStep
                    val dotWidth = if (isCurrent) 32.dp else 16.dp
                    val dotColor = when {
                        isCurrent -> StepDotActiveColor
                        isDone -> StepDotDoneColor
                        else -> StepDotPendingColor
                    }
                    Box(
                        modifier = Modifier
                            .width(dotWidth)
                            .height(5.dp)
                            .background(
                                color = dotColor,
                                shape = RoundedCornerShape(2.5.dp)
                            )
                            .testTag("onboarding_step_dot_$index")
                    )
                }
            }
        }

        Text(
            text = "Passo ${currentStep + 1} de $totalSteps",
            color = StepLabelColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .padding(end = 12.dp)
                .testTag("onboarding_step_counter")
        )
    }
}

/**
 * Rodapé do Wizard:
 * - Passo 1: Botão primário único `"Continuar →"`.
 * - Passo 2 e Passo 3: Rodapé assimétrico obrigatório (`← Voltar` peso 1 à esquerda + CTA `<= 3 palavras` peso 2 à direita).
 */
@Composable
fun OnboardingWizardFooter(
    currentStep: Int,
    onBackClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier,
    isNextEnabled: Boolean = true
) {
    val primaryCtaLabel = if (currentStep == OnboardingUiState.TOTAL_STEPS - 1) {
        "Concluir →"
    } else {
        "Continuar →"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("onboarding_wizard_footer"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (currentStep >= 1) {
            Surface(
                onClick = onBackClick,
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minWidth = 48.dp, minHeight = 52.dp)
                    .testTag("onboarding_back_button"),
                shape = RoundedCornerShape(18.dp),
                color = SecondaryBtnBg,
                border = BorderStroke(1.dp, SecondaryBtnBorder)
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "← Voltar",
                        color = SecondaryBtnContent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Button(
            onClick = onNextClick,
            enabled = isNextEnabled,
            modifier = Modifier
                .weight(if (currentStep >= 1) 2f else 1f)
                .defaultMinSize(minWidth = 48.dp, minHeight = 52.dp)
                .testTag("onboarding_next_button"),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryBtnBg,
                contentColor = PrimaryBtnContent,
                disabledContainerColor = PrimaryBtnDisabledBg,
                disabledContentColor = PrimaryBtnDisabledContent
            )
        ) {
            Text(
                text = primaryCtaLabel,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
