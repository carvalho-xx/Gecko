package io.github.carvalhoxx.gecko.presentation.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.carvalhoxx.gecko.domain.model.PlayStyle
import io.github.carvalhoxx.gecko.presentation.components.SelectableChip

private val PaceCardBg = Color(0xFF111815)
private val PaceCardBorder = Color(0xFF1E2E27)
private val PaceButtonBg = Color(0xFF18241F)
private val PaceButtonBorder = Color(0xFF2A3D34)
private val PaceEmerald = Color(0xFF34D399)
private val PacePreviewBg = Color(0xFF0C1310)
private val PacePreviewBorder = Color(0xFF1A2922)
private val PaceSubText = Color(0xFF8E9E96)
private val PaceMutedText = Color(0xFF7E8F86)

/**
 * Passo 2 de 3 (`ONB_02_Pace`, id 354 no protótipo FigCraft):
 * - Seletor de ritmo diário (`−` / `+` 48x48dp + chips de atalho) e simulador em linguagem natural.
 * - 3 cartões de estilo padrão (`Rushar` / `Normal` / `Platina`) respeitando o orçamento de caracteres
 *   de 360dp (`[UI: VIEWPORT_BUDGET(360dp_width) ! TEXT_OVERFLOW_3_COLS]`: título 1 palavra <= 10 chars,
 *   subtítulo <= 14 chars).
 * - Card de Meta Anual exibido exclusivamente quando `isAnnualGoalEnabled == true` (v1.1).
 */
@Composable
fun OnboardingPaceStep(
    uiState: OnboardingUiState,
    onIncrementPace: () -> Unit,
    onDecrementPace: () -> Unit,
    onSelectPacePreset: (Long) -> Unit,
    onSelectPlayStyle: (PlayStyle) -> Unit,
    onSelectAnnualGoal: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .testTag("onboarding_step_2_pace"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Qual é o seu ritmo, ${uiState.displayUserName}?",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("onboarding_pace_title")
            )
            Text(
                text = "Usamos isso para calcular quando você vai zerar cada jogo.",
                color = PaceSubText,
                fontSize = 12.sp
            )
        }

        // DailyPaceSelectorCard (id 364)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("onboarding_daily_pace_card"),
            shape = RoundedCornerShape(28.dp),
            color = PaceCardBg,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.2.dp, PaceCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Tempo médio que você joga por dia",
                    color = Color(0xFF9CA8A2),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onDecrementPace,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .size(48.dp)
                            .testTag("onboarding_pace_minus_button"),
                        shape = CircleShape,
                        color = PaceButtonBg,
                        border = BorderStroke(1.dp, PaceButtonBorder)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "−",
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = uiState.formattedDailyPace,
                            color = PaceEmerald,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("onboarding_pace_display")
                        )
                        Text(
                            text = uiState.formattedWeeklyPaceSubtitle,
                            color = Color(0xFFE3E3E3),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Surface(
                        onClick = onIncrementPace,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .size(48.dp)
                            .testTag("onboarding_pace_plus_button"),
                        shape = CircleShape,
                        color = PaceButtonBg,
                        border = BorderStroke(1.dp, PaceButtonBorder)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "+",
                                color = PaceEmerald,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 4 Chips de atalho (1h 00m, 1h 30m, 2h 00m, 3h 00m+)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OnboardingUiState.PACE_PRESETS_MS.forEach { (label, presetMs) ->
                        val isSelected = if (presetMs == 10_800_000L) {
                            uiState.dailyAvailabilityMs >= 10_800_000L
                        } else {
                            uiState.dailyAvailabilityMs == presetMs
                        }
                        SelectableChip(
                            label = label,
                            selected = isSelected,
                            onClick = { onSelectPacePreset(presetMs) },
                            modifier = Modifier.weight(1f),
                            testTag = "onboarding_pace_preset_$presetMs"
                        )
                    }
                }

                // LivePacePreviewBox (id 380)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_pace_simulator"),
                    shape = RoundedCornerShape(16.dp),
                    color = PacePreviewBg,
                    border = BorderStroke(1.dp, PacePreviewBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = uiState.livePaceSimulationText,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.testTag("onboarding_pace_simulator_text")
                        )
                        Text(
                            text = "Você pode ajustar esse tempo a qualquer momento.",
                            color = PaceMutedText,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // DefaultPlayStyleCard (id 383) — 3 colunas respeitando orçamento 360dp (<= 10 chars título, <= 14 chars subtítulo)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("onboarding_play_style_card"),
            shape = RoundedCornerShape(24.dp),
            color = PaceCardBg,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.2.dp, PaceCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Como você costuma jogar?",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Define a estimativa padrão para novas campanhas:",
                        color = PaceMutedText,
                        fontSize = 11.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PlayStyleCardOption(
                        title = "Rushar",
                        subtitle = "Só história",
                        selected = uiState.defaultPlayStyle == PlayStyle.RUSH,
                        onClick = { onSelectPlayStyle(PlayStyle.RUSH) },
                        modifier = Modifier.weight(1f),
                        testTag = "onboarding_style_RUSH"
                    )
                    PlayStyleCardOption(
                        title = "Normal",
                        subtitle = "Hist. + Extra",
                        selected = uiState.defaultPlayStyle == PlayStyle.NORMAL,
                        onClick = { onSelectPlayStyle(PlayStyle.NORMAL) },
                        modifier = Modifier.weight(1f),
                        testTag = "onboarding_style_NORMAL"
                    )
                    PlayStyleCardOption(
                        title = "Platina",
                        subtitle = "100% / Tudo",
                        selected = uiState.defaultPlayStyle == PlayStyle.PLATINUM,
                        onClick = { onSelectPlayStyle(PlayStyle.PLATINUM) },
                        modifier = Modifier.weight(1f),
                        testTag = "onboarding_style_PLATINUM"
                    )
                }
            }
        }

        // AnnualGoalSetupCard (id 395) — protegido por ReleaseFeatureFlags.isAnnualGoalEnabled (v1.1)
        if (uiState.isAnnualGoalEnabled) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding_annual_goal_card"),
                shape = RoundedCornerShape(24.dp),
                color = PaceCardBg,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                border = BorderStroke(1.2.dp, PaceCardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Meta de jogos zerados em 2026 (opcional)",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Quantos títulos você quer concluir neste ano?",
                            color = PaceMutedText,
                            fontSize = 11.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OnboardingUiState.ANNUAL_GOAL_PRESETS.forEach { target ->
                            SelectableChip(
                                label = "$target jogos",
                                selected = uiState.annualGoalTarget == target,
                                onClick = { onSelectAnnualGoal(target) },
                                modifier = Modifier.weight(1f),
                                testTag = "onboarding_goal_preset_$target"
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PlayStyleCardOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    require(title.length <= 10 && !title.contains(' ')) {
        "Viewport 360dp budget violation: 3-column title '$title' must be 1 word (<= 10 chars)"
    }
    require(subtitle.length <= 14) {
        "Viewport 360dp budget violation: 3-column subtitle '$subtitle' must be <= 14 chars"
    }

    SelectableChip(
        label = title,
        subtitle = subtitle,
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        testTag = testTag
    )
}
