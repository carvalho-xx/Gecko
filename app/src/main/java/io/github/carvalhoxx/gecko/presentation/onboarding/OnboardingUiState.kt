package io.github.carvalhoxx.gecko.presentation.onboarding

import io.github.carvalhoxx.gecko.domain.model.ByokConnectionState
import io.github.carvalhoxx.gecko.domain.model.ByokProvider
import io.github.carvalhoxx.gecko.domain.model.PlayStyle
import java.util.Locale
import kotlin.math.ceil

/**
 * Estado imutável unidirecional (UDF) do Wizard de Boas-Vindas e Configuração Inicial (Etapa 3.2).
 * Todas as durações são mantidas em milissegundos primitivos Long (RNF07).
 */
data class OnboardingUiState(
    val currentStep: Int = 0,
    val userName: String = "",
    val avatarLocalUri: String? = null,
    val dailyAvailabilityMs: Long = DEFAULT_DAILY_AVAILABILITY_MS,
    val defaultPlayStyle: PlayStyle = PlayStyle.NORMAL,
    val annualGoalTarget: Int = DEFAULT_ANNUAL_GOAL_TARGET,
    val isAnnualGoalEnabled: Boolean = false,
    val psnNpssoInput: String = "",
    val psnValidationError: String? = null,
    val providerStates: Map<ByokProvider, ByokConnectionState> = defaultProviderStates(),
    val activeBottomSheetProvider: ByokProvider? = null,
    val bottomSheetErrorMessage: String? = null,
    val isCompleted: Boolean = false
) {
    val stepNumber: Int
        get() = (currentStep + 1).coerceIn(1, TOTAL_STEPS)

    val totalSteps: Int
        get() = TOTAL_STEPS

    val displayUserName: String
        get() = userName.trim().ifEmpty { "Jogador" }

    val avatarInitial: String
        get() = userName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "G"

    val isNameValid: Boolean
        get() = userName.trim().isNotEmpty()

    val isPsnFormatValid: Boolean
        get() = psnNpssoInput.trim().length == 64 && psnNpssoInput.trim().all { it.isLetterOrDigit() }

    val formattedDailyPace: String
        get() {
            val totalMinutes = (dailyAvailabilityMs.coerceAtLeast(MIN_DAILY_AVAILABILITY_MS)) / 60_000L
            val hours = totalMinutes / 60L
            val minutes = totalMinutes % 60L
            return String.format(Locale.US, "%dh %02dm", hours, minutes)
        }

    val formattedWeeklyPaceSubtitle: String
        get() {
            val weeklyHours = ((dailyAvailabilityMs * 7L) / 3_600_000.0)
            val roundedWeekly = ceil(weeklyHours).toInt().coerceAtLeast(1)
            return "por dia (~${roundedWeekly}h por semana)"
        }

    val estimatedDaysFor20hGame: Int
        get() {
            val safeDailyMs = dailyAvailabilityMs.coerceAtLeast(MIN_DAILY_AVAILABILITY_MS)
            val referenceGameMs = 20L * 3_600_000L
            return ceil(referenceGameMs.toDouble() / safeDailyMs.toDouble()).toInt().coerceAtLeast(1)
        }

    val livePaceSimulationText: String
        get() = "✦ Nesse ritmo, um jogo de 20h leva ~$estimatedDaysFor20hGame dias."

    fun connectionStateFor(provider: ByokProvider): ByokConnectionState =
        providerStates[provider] ?: ByokConnectionState.NOT_CONFIGURED

    companion object {
        const val TOTAL_STEPS = 3
        const val MIN_DAILY_AVAILABILITY_MS = 1_800_000L // 30m
        const val MAX_DAILY_AVAILABILITY_MS = 43_200_000L // 12h
        const val PACE_STEP_INCREMENT_MS = 1_800_000L // 30m
        const val DEFAULT_DAILY_AVAILABILITY_MS = 7_200_000L // 2h 00m
        const val DEFAULT_ANNUAL_GOAL_TARGET = 24

        val PACE_PRESETS_MS: List<Pair<String, Long>> = listOf(
            "1h 00m" to 3_600_000L,
            "1h 30m" to 5_400_000L,
            "2h 00m" to 7_200_000L,
            "3h 00m+" to 10_800_000L
        )

        val ANNUAL_GOAL_PRESETS: List<Int> = listOf(12, 24, 36)

        fun defaultProviderStates(): Map<ByokProvider, ByokConnectionState> =
            ByokProvider.entries.associateWith { ByokConnectionState.NOT_CONFIGURED }
    }
}
