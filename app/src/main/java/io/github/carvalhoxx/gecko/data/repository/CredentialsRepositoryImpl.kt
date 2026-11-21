package io.github.carvalhoxx.gecko.data.repository

import io.github.carvalhoxx.gecko.core.ByokCredentialValidator
import io.github.carvalhoxx.gecko.core.CredentialValidationResult
import io.github.carvalhoxx.gecko.data.local.IgdbCredentials
import io.github.carvalhoxx.gecko.data.local.PsnCredentials
import io.github.carvalhoxx.gecko.data.local.RawgCredentials
import io.github.carvalhoxx.gecko.data.local.RetroAchievementsCredentials
import io.github.carvalhoxx.gecko.data.local.SecureCredentialsStorage
import io.github.carvalhoxx.gecko.data.local.SteamCredentials
import io.github.carvalhoxx.gecko.domain.model.ByokConnectionState
import io.github.carvalhoxx.gecko.domain.model.ByokProvider
import io.github.carvalhoxx.gecko.domain.model.ByokProviderStatus
import io.github.carvalhoxx.gecko.domain.repository.CredentialsRepository
import javax.inject.Inject

/**
 * Implementação do repositório BYOK respaldada pelo cofre EncryptedSharedPreferences (RNF03).
 * Valida formato sintático antes de persistir sem jamais incluir segredos nas mensagens de erro.
 */
class CredentialsRepositoryImpl @Inject constructor(private val storage: SecureCredentialsStorage) :
    CredentialsRepository {

    override fun getProviderStatuses(): List<ByokProviderStatus> = listOf(
        statusFor(ByokProvider.IGDB, storage.getIgdbCredentials() != null),
        statusFor(ByokProvider.STEAM, storage.getSteamCredentials() != null),
        statusFor(ByokProvider.PSN, storage.getPsnCredentials() != null),
        statusFor(ByokProvider.RAWG, storage.getRawgCredentials() != null),
        statusFor(ByokProvider.RETRO_ACHIEVEMENTS, storage.getRetroAchievementsCredentials() != null)
    )

    override fun hasAnyConfiguredProvider(): Boolean = storage.hasAnyConfiguredProvider()

    override fun saveIgdbCredentials(clientId: String, clientSecret: String) {
        val trimmedId = clientId.trim()
        val trimmedSecret = clientSecret.trim()
        requireValid(
            provider = ByokProvider.IGDB,
            result = ByokCredentialValidator.validateIgdbCredentials(trimmedId, trimmedSecret)
        )
        storage.saveIgdbCredentials(IgdbCredentials(clientId = trimmedId, clientSecret = trimmedSecret))
    }

    override fun saveRawgApiKey(apiKey: String) {
        val trimmedKey = apiKey.trim()
        requireValid(
            provider = ByokProvider.RAWG,
            result = ByokCredentialValidator.validateRawgApiKey(trimmedKey)
        )
        storage.saveRawgCredentials(RawgCredentials(apiKey = trimmedKey))
    }

    override fun saveSteamCredentials(apiKey: String, steamId64: String) {
        val trimmedKey = apiKey.trim()
        val trimmedSteamId = steamId64.trim()
        requireValid(
            provider = ByokProvider.STEAM,
            result = ByokCredentialValidator.validateSteamApiKey(trimmedKey)
        )
        requireValid(
            provider = ByokProvider.STEAM,
            result = ByokCredentialValidator.validateSteamId64(trimmedSteamId)
        )
        storage.saveSteamCredentials(SteamCredentials(apiKey = trimmedKey, steamId64 = trimmedSteamId))
    }

    override fun savePsnNpsso(npsso: String) {
        val trimmedNpsso = npsso.trim()
        requireValid(
            provider = ByokProvider.PSN,
            result = ByokCredentialValidator.validatePsnNpsso(trimmedNpsso)
        )
        storage.savePsnCredentials(PsnCredentials(npsso = trimmedNpsso))
    }

    override fun saveRetroAchievementsCredentials(username: String, apiKey: String) {
        val trimmedUser = username.trim()
        val trimmedKey = apiKey.trim()
        requireValid(
            provider = ByokProvider.RETRO_ACHIEVEMENTS,
            result = ByokCredentialValidator.validateRetroAchievements(trimmedUser, trimmedKey)
        )
        storage.saveRetroAchievementsCredentials(
            RetroAchievementsCredentials(username = trimmedUser, apiKey = trimmedKey)
        )
    }

    override fun clearProvider(provider: ByokProvider) {
        when (provider) {
            ByokProvider.IGDB -> storage.clearIgdbCredentials()
            ByokProvider.RAWG -> storage.clearRawgCredentials()
            ByokProvider.STEAM -> storage.clearSteamCredentials()
            ByokProvider.PSN -> storage.clearPsnCredentials()
            ByokProvider.RETRO_ACHIEVEMENTS -> storage.clearRetroAchievementsCredentials()
        }
    }

    override fun clearAll() {
        storage.clearAll()
    }

    private fun statusFor(provider: ByokProvider, configured: Boolean): ByokProviderStatus = ByokProviderStatus(
        provider = provider,
        state = if (configured) ByokConnectionState.CONFIGURED else ByokConnectionState.NOT_CONFIGURED
    )

    private fun requireValid(provider: ByokProvider, result: CredentialValidationResult) {
        if (result is CredentialValidationResult.Invalid) {
            throw IllegalArgumentException("Invalid ${provider.name} credential format: ${result.reason.name}")
        }
    }
}
