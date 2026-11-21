package io.github.carvalhoxx.gecko.presentation.onboarding

import io.github.carvalhoxx.gecko.core.ReleaseFeatureFlags
import io.github.carvalhoxx.gecko.domain.model.ByokConnectionState
import io.github.carvalhoxx.gecko.domain.model.ByokProvider
import io.github.carvalhoxx.gecko.domain.model.ByokProviderStatus
import io.github.carvalhoxx.gecko.domain.model.PlayStyle
import io.github.carvalhoxx.gecko.domain.model.RemoteCatalogGame
import io.github.carvalhoxx.gecko.domain.model.UserPaceSettings
import io.github.carvalhoxx.gecko.domain.model.UserProfile
import io.github.carvalhoxx.gecko.domain.model.UserSettings
import io.github.carvalhoxx.gecko.domain.repository.CatalogSyncRepository
import io.github.carvalhoxx.gecko.domain.repository.CredentialsRepository
import io.github.carvalhoxx.gecko.domain.repository.UserPreferencesRepository
import io.github.carvalhoxx.gecko.domain.usecase.TestByokProviderConnectionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakePreferencesRepo: FakeOnboardingUserPreferencesRepository
    private lateinit var fakeCredentialsRepo: FakeOnboardingCredentialsRepository
    private lateinit var fakeCatalogSyncRepo: FakeOnboardingCatalogSyncRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakePreferencesRepo = FakeOnboardingUserPreferencesRepository()
        fakeCredentialsRepo = FakeOnboardingCredentialsRepository()
        fakeCatalogSyncRepo = FakeOnboardingCatalogSyncRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(flags: ReleaseFeatureFlags = ReleaseFeatureFlags.MVP_V1_0): OnboardingViewModel =
        OnboardingViewModel(
            userPreferencesRepository = fakePreferencesRepo,
            credentialsRepository = fakeCredentialsRepo,
            testByokProviderConnectionUseCase = TestByokProviderConnectionUseCase(fakeCatalogSyncRepo),
            releaseFeatureFlags = flags
        )

    @Test
    fun `navigates across 3 steps, updates pace simulator, and completes onboarding`() = runTest {
        val viewModel = createViewModel(ReleaseFeatureFlags.MVP_V1_0)

        assertEquals(0, viewModel.uiState.value.currentStep)
        assertFalse(viewModel.uiState.value.isAnnualGoalEnabled)

        // Passo 1 bloqueia avanço enquanto o nome estiver vazio
        viewModel.onNextStep()
        assertEquals(0, viewModel.uiState.value.currentStep)

        // Passo 1: define nome e avança para Passo 2
        viewModel.onNameChange("Carvs")
        viewModel.onNextStep()
        assertEquals(1, viewModel.uiState.value.currentStep)
        assertEquals("Carvs", viewModel.uiState.value.displayUserName)

        // Passo 2: altera ritmo de 2h 00m (10 dias p/ 20h) para 1h 00m (20 dias p/ 20h) e estilo Platina
        assertEquals(10, viewModel.uiState.value.estimatedDaysFor20hGame)
        viewModel.onSelectDailyPacePreset(3_600_000L)
        viewModel.onSelectDefaultPlayStyle(PlayStyle.PLATINUM)
        assertEquals("1h 00m", viewModel.uiState.value.formattedDailyPace)
        assertEquals(20, viewModel.uiState.value.estimatedDaysFor20hGame)

        // Avança para Passo 3 e volta para Passo 2 para testar navegação bidirecional
        viewModel.onNextStep()
        assertEquals(2, viewModel.uiState.value.currentStep)
        viewModel.onPreviousStep()
        assertEquals(1, viewModel.uiState.value.currentStep)
        viewModel.onNextStep()
        assertEquals(2, viewModel.uiState.value.currentStep)

        // Conclui no Passo 3
        viewModel.onNextStep()
        assertTrue(viewModel.uiState.value.isCompleted)

        val persisted = fakePreferencesRepo.observeUserSettings().first()
        assertEquals("Carvs", persisted.profile.name)
        assertEquals(3_600_000L, persisted.paceSettings.dailyAvailabilityMs)
        assertEquals(PlayStyle.PLATINUM, persisted.paceSettings.defaultPlayStyle)
        assertTrue(persisted.isFirstLaunchCompleted)
    }

    @Test
    fun `validates and persists PSN npsso and multi-field BYOK credentials`() = runTest {
        val viewModel = createViewModel(ReleaseFeatureFlags.EXPANSION_V1_1)
        assertTrue(viewModel.uiState.value.isAnnualGoalEnabled)

        // PSN inválido (< 64 chars)
        viewModel.onPsnNpssoChange("curto123")
        viewModel.onValidatePsnNpsso()
        assertNotNull(viewModel.uiState.value.psnValidationError)
        assertEquals(
            ByokConnectionState.NOT_CONFIGURED,
            viewModel.uiState.value.connectionStateFor(ByokProvider.PSN)
        )

        // PSN válido (64 chars alfanuméricos)
        val validNpsso = "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6a7b8c9d0e1f2"
        viewModel.onPsnNpssoChange(validNpsso)
        viewModel.onValidatePsnNpsso()
        assertNull(viewModel.uiState.value.psnValidationError)
        assertEquals(
            ByokConnectionState.CONNECTED,
            viewModel.uiState.value.connectionStateFor(ByokProvider.PSN)
        )
        assertEquals(validNpsso, fakeCredentialsRepo.savedPsnNpsso)

        // Multi-campo IGDB via Bottom Sheet
        viewModel.onOpenProviderBottomSheet(ByokProvider.IGDB)
        assertEquals(ByokProvider.IGDB, viewModel.uiState.value.activeBottomSheetProvider)

        viewModel.onSaveMultiFieldCredentials(
            provider = ByokProvider.IGDB,
            primaryValue = "igdb_client_id_12345",
            secondaryValue = "igdb_secret_67890"
        )
        assertNull(viewModel.uiState.value.activeBottomSheetProvider)
        assertEquals(
            ByokConnectionState.CONNECTED,
            viewModel.uiState.value.connectionStateFor(ByokProvider.IGDB)
        )
        assertEquals("igdb_client_id_12345", fakeCredentialsRepo.savedIgdbClientId)
    }

    @Test
    fun `skipOfflineMode completes onboarding immediately without requiring BYOK keys`() = runTest {
        val viewModel = createViewModel()
        viewModel.onSkipOfflineMode()

        assertTrue(viewModel.uiState.value.isCompleted)
        val persisted = fakePreferencesRepo.observeUserSettings().first()
        assertTrue(persisted.isFirstLaunchCompleted)
        assertEquals("Jogador", persisted.profile.name)
    }
}

private class FakeOnboardingUserPreferencesRepository : UserPreferencesRepository {
    private val state = MutableStateFlow(
        UserSettings(
            profile = UserProfile(name = "", avatarLocalUri = null),
            paceSettings = UserPaceSettings(
                dailyAvailabilityMs = 7_200_000L,
                defaultPlayStyle = PlayStyle.NORMAL,
                calibrationFactor = 1.0f
            ),
            isFirstLaunchCompleted = false,
            enableFinanceModule = false
        )
    )

    override fun observeUserSettings(): Flow<UserSettings> = state

    override suspend fun updateProfile(name: String, avatarLocalUri: String?) {
        state.update { it.copy(profile = UserProfile(name, avatarLocalUri)) }
    }

    override suspend fun updateDailyAvailabilityMs(dailyAvailabilityMs: Long) {
        state.update { it.copy(paceSettings = it.paceSettings.copy(dailyAvailabilityMs = dailyAvailabilityMs)) }
    }

    override suspend fun updateDefaultPlayStyle(playStyle: PlayStyle) {
        state.update { it.copy(paceSettings = it.paceSettings.copy(defaultPlayStyle = playStyle)) }
    }

    override suspend fun setFirstLaunchCompleted(completed: Boolean) {
        state.update { it.copy(isFirstLaunchCompleted = completed) }
    }

    override suspend fun setFinanceModuleEnabled(enabled: Boolean) {
        state.update { it.copy(enableFinanceModule = enabled) }
    }

    override suspend fun updateCalibrationFactor(factor: Float) {
        state.update { it.copy(paceSettings = it.paceSettings.copy(calibrationFactor = factor)) }
    }

    override suspend fun clearAll() = Unit
}

private class FakeOnboardingCredentialsRepository : CredentialsRepository {
    var savedIgdbClientId: String? = null
    var savedPsnNpsso: String? = null
    var savedSteamId64: String? = null

    override fun getProviderStatuses(): List<ByokProviderStatus> = ByokProvider.entries.map {
        ByokProviderStatus(it, ByokConnectionState.NOT_CONFIGURED)
    }

    override fun hasAnyConfiguredProvider(): Boolean =
        savedIgdbClientId != null || savedPsnNpsso != null || savedSteamId64 != null

    override fun saveIgdbCredentials(clientId: String, clientSecret: String) {
        savedIgdbClientId = clientId
    }

    override fun saveRawgApiKey(apiKey: String) = Unit

    override fun saveSteamCredentials(apiKey: String, steamId64: String) {
        savedSteamId64 = steamId64
    }

    override fun savePsnNpsso(npsso: String) {
        savedPsnNpsso = npsso
    }

    override fun saveRetroAchievementsCredentials(username: String, apiKey: String) = Unit

    override fun clearProvider(provider: ByokProvider) = Unit

    override fun clearAll() = Unit
}

private class FakeOnboardingCatalogSyncRepository : CatalogSyncRepository {
    override suspend fun searchCatalog(query: String): List<RemoteCatalogGame> = emptyList()

    override suspend fun syncPlatformPlaytimes(): Result<Int> = Result.success(0)

    override suspend fun testProviderConnection(provider: ByokProvider): ByokProviderStatus =
        ByokProviderStatus(provider = provider, state = ByokConnectionState.CONNECTED)
}
