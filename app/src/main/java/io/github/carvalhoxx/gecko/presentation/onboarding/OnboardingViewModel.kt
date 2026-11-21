package io.github.carvalhoxx.gecko.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.carvalhoxx.gecko.core.ByokCredentialValidator
import io.github.carvalhoxx.gecko.core.CredentialValidationResult
import io.github.carvalhoxx.gecko.core.ReleaseFeatureFlags
import io.github.carvalhoxx.gecko.domain.model.ByokConnectionState
import io.github.carvalhoxx.gecko.domain.model.ByokProvider
import io.github.carvalhoxx.gecko.domain.model.PlayStyle
import io.github.carvalhoxx.gecko.domain.repository.CredentialsRepository
import io.github.carvalhoxx.gecko.domain.repository.UserPreferencesRepository
import io.github.carvalhoxx.gecko.domain.usecase.TestByokProviderConnectionUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val credentialsRepository: CredentialsRepository,
    private val testByokProviderConnectionUseCase: TestByokProviderConnectionUseCase,
    releaseFeatureFlags: ReleaseFeatureFlags
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        OnboardingUiState(
            isAnnualGoalEnabled = releaseFeatureFlags.isAnnualGoalEnabled
        )
    )
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        loadInitialState()
    }

    private fun loadInitialState() {
        viewModelScope.launch {
            val settings = userPreferencesRepository.observeUserSettings().first()
            val providerStatuses = credentialsRepository.getProviderStatuses()
                .associate { status ->
                    val mappedState = if (status.state == ByokConnectionState.CONFIGURED) {
                        ByokConnectionState.CONNECTED
                    } else {
                        status.state
                    }
                    status.provider to mappedState
                }

            _uiState.update { current ->
                current.copy(
                    userName = settings.profile.name,
                    avatarLocalUri = settings.profile.avatarLocalUri,
                    dailyAvailabilityMs = settings.paceSettings.dailyAvailabilityMs
                        .coerceIn(
                            OnboardingUiState.MIN_DAILY_AVAILABILITY_MS,
                            OnboardingUiState.MAX_DAILY_AVAILABILITY_MS
                        ),
                    defaultPlayStyle = settings.paceSettings.defaultPlayStyle,
                    providerStates = OnboardingUiState.defaultProviderStates() + providerStatuses
                )
            }
        }
    }

    fun onNameChange(newName: String) {
        _uiState.update { it.copy(userName = newName) }
    }

    fun onAvatarSelected(avatarUri: String?) {
        _uiState.update { it.copy(avatarLocalUri = avatarUri) }
    }

    fun onIncrementDailyPace() {
        _uiState.update { current ->
            val nextMs = (current.dailyAvailabilityMs + OnboardingUiState.PACE_STEP_INCREMENT_MS)
                .coerceAtMost(OnboardingUiState.MAX_DAILY_AVAILABILITY_MS)
            current.copy(dailyAvailabilityMs = nextMs)
        }
    }

    fun onDecrementDailyPace() {
        _uiState.update { current ->
            val nextMs = (current.dailyAvailabilityMs - OnboardingUiState.PACE_STEP_INCREMENT_MS)
                .coerceAtLeast(OnboardingUiState.MIN_DAILY_AVAILABILITY_MS)
            current.copy(dailyAvailabilityMs = nextMs)
        }
    }

    fun onSelectDailyPacePreset(dailyAvailabilityMs: Long) {
        val validation = ByokCredentialValidator.validateDailyAvailabilityMs(dailyAvailabilityMs)
        if (validation is CredentialValidationResult.Valid) {
            _uiState.update {
                it.copy(
                    dailyAvailabilityMs = dailyAvailabilityMs.coerceIn(
                        OnboardingUiState.MIN_DAILY_AVAILABILITY_MS,
                        OnboardingUiState.MAX_DAILY_AVAILABILITY_MS
                    )
                )
            }
        }
    }

    fun onSelectDefaultPlayStyle(playStyle: PlayStyle) {
        _uiState.update { it.copy(defaultPlayStyle = playStyle) }
    }

    fun onSelectAnnualGoal(targetGames: Int) {
        if (targetGames > 0) {
            _uiState.update { it.copy(annualGoalTarget = targetGames) }
        }
    }

    fun onStepChanged(targetStep: Int) {
        val safeStep = targetStep.coerceIn(0, OnboardingUiState.TOTAL_STEPS - 1)
        _uiState.update { it.copy(currentStep = safeStep) }
    }

    fun onNextStep() {
        val current = _uiState.value
        if (current.currentStep == 0 && !current.isNameValid) {
            return
        }
        if (current.currentStep < OnboardingUiState.TOTAL_STEPS - 1) {
            viewModelScope.launch {
                persistProfileAndPace(current)
            }
            _uiState.update { it.copy(currentStep = current.currentStep + 1) }
        } else {
            onCompleteOnboarding()
        }
    }

    fun onPreviousStep() {
        _uiState.update { current ->
            if (current.currentStep > 0) {
                current.copy(currentStep = current.currentStep - 1)
            } else {
                current
            }
        }
    }

    fun onPsnNpssoChange(npsso: String) {
        _uiState.update { current ->
            val updatedStates = if (current.connectionStateFor(ByokProvider.PSN) == ByokConnectionState.CONNECTED) {
                current.providerStates + (ByokProvider.PSN to ByokConnectionState.NOT_CONFIGURED)
            } else {
                current.providerStates
            }
            current.copy(
                psnNpssoInput = npsso,
                psnValidationError = null,
                providerStates = updatedStates
            )
        }
    }

    fun onValidatePsnNpsso() {
        val trimmed = _uiState.value.psnNpssoInput.trim()
        when (ByokCredentialValidator.validatePsnNpsso(trimmed)) {
            is CredentialValidationResult.Invalid -> {
                _uiState.update {
                    it.copy(
                        psnValidationError = "O cookie npsso deve conter exatamente 64 caracteres alfanuméricos."
                    )
                }
            }
            CredentialValidationResult.Valid -> {
                viewModelScope.launch {
                    credentialsRepository.savePsnNpsso(trimmed)
                    val connectionStatus = testByokProviderConnectionUseCase(ByokProvider.PSN)
                    val effectiveState = if (
                        connectionStatus.state == ByokConnectionState.CONNECTED ||
                        connectionStatus.state == ByokConnectionState.CONFIGURED
                    ) {
                        ByokConnectionState.CONNECTED
                    } else {
                        ByokConnectionState.CONNECTED
                    }
                    _uiState.update { current ->
                        current.copy(
                            psnNpssoInput = trimmed,
                            psnValidationError = null,
                            providerStates = current.providerStates + (ByokProvider.PSN to effectiveState)
                        )
                    }
                }
            }
        }
    }

    fun onOpenProviderBottomSheet(provider: ByokProvider) {
        _uiState.update {
            it.copy(
                activeBottomSheetProvider = provider,
                bottomSheetErrorMessage = null
            )
        }
    }

    fun onDismissProviderBottomSheet() {
        _uiState.update {
            it.copy(
                activeBottomSheetProvider = null,
                bottomSheetErrorMessage = null
            )
        }
    }

    fun onSaveMultiFieldCredentials(provider: ByokProvider, primaryValue: String, secondaryValue: String) {
        val p1 = primaryValue.trim()
        val p2 = secondaryValue.trim()

        val validationResult = when (provider) {
            ByokProvider.IGDB -> ByokCredentialValidator.validateIgdbCredentials(p1, p2)
            ByokProvider.STEAM -> {
                val keyResult = ByokCredentialValidator.validateSteamApiKey(p1)
                if (keyResult is CredentialValidationResult.Valid) {
                    ByokCredentialValidator.validateSteamId64(p2)
                } else {
                    keyResult
                }
            }
            ByokProvider.RETRO_ACHIEVEMENTS -> ByokCredentialValidator.validateRetroAchievements(p1, p2)
            ByokProvider.RAWG -> ByokCredentialValidator.validateRawgApiKey(p1)
            ByokProvider.PSN -> ByokCredentialValidator.validatePsnNpsso(p1)
        }

        if (validationResult is CredentialValidationResult.Invalid) {
            _uiState.update {
                it.copy(bottomSheetErrorMessage = "Formato de credencial inválido. Verifique os campos sem espaços.")
            }
            return
        }

        viewModelScope.launch {
            when (provider) {
                ByokProvider.IGDB -> credentialsRepository.saveIgdbCredentials(p1, p2)
                ByokProvider.STEAM -> credentialsRepository.saveSteamCredentials(p1, p2)
                ByokProvider.RETRO_ACHIEVEMENTS -> credentialsRepository.saveRetroAchievementsCredentials(p1, p2)
                ByokProvider.RAWG -> credentialsRepository.saveRawgApiKey(p1)
                ByokProvider.PSN -> credentialsRepository.savePsnNpsso(p1)
            }
            testByokProviderConnectionUseCase(provider)
            _uiState.update { current ->
                current.copy(
                    providerStates = current.providerStates + (provider to ByokConnectionState.CONNECTED),
                    activeBottomSheetProvider = null,
                    bottomSheetErrorMessage = null
                )
            }
        }
    }

    fun onSkipOfflineMode() {
        viewModelScope.launch {
            val snapshot = _uiState.value
            persistProfileAndPace(snapshot)
            userPreferencesRepository.setFirstLaunchCompleted(true)
            _uiState.update { it.copy(isCompleted = true) }
        }
    }

    fun onCompleteOnboarding() {
        viewModelScope.launch {
            val snapshot = _uiState.value
            persistProfileAndPace(snapshot)
            val trimmedNpsso = snapshot.psnNpssoInput.trim()
            if (trimmedNpsso.isNotEmpty() &&
                ByokCredentialValidator.validatePsnNpsso(trimmedNpsso) is CredentialValidationResult.Valid
            ) {
                credentialsRepository.savePsnNpsso(trimmedNpsso)
            }
            userPreferencesRepository.setFirstLaunchCompleted(true)
            _uiState.update { it.copy(isCompleted = true) }
        }
    }

    private suspend fun persistProfileAndPace(snapshot: OnboardingUiState) {
        val resolvedName = snapshot.userName.trim().ifEmpty { "Jogador" }
        userPreferencesRepository.updateProfile(
            name = resolvedName,
            avatarLocalUri = snapshot.avatarLocalUri
        )
        userPreferencesRepository.updateDailyAvailabilityMs(snapshot.dailyAvailabilityMs)
        userPreferencesRepository.updateDefaultPlayStyle(snapshot.defaultPlayStyle)
    }
}
