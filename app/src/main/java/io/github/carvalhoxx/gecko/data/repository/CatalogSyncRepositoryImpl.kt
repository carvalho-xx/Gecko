package io.github.carvalhoxx.gecko.data.repository

import io.github.carvalhoxx.gecko.core.ByokCredentialValidator
import io.github.carvalhoxx.gecko.core.CoroutineDispatchers
import io.github.carvalhoxx.gecko.core.CredentialValidationResult
import io.github.carvalhoxx.gecko.data.local.SecureCredentialsStorage
import io.github.carvalhoxx.gecko.data.local.SteamCredentials
import io.github.carvalhoxx.gecko.data.remote.igdb.IgdbApiClient
import io.github.carvalhoxx.gecko.data.remote.psn.PsnApiClient
import io.github.carvalhoxx.gecko.data.remote.psn.PsnTitlesFetchResult
import io.github.carvalhoxx.gecko.data.remote.rawg.RawgApiClient
import io.github.carvalhoxx.gecko.data.remote.steam.SteamApiClient
import io.github.carvalhoxx.gecko.domain.model.ByokConnectionState
import io.github.carvalhoxx.gecko.domain.model.ByokProvider
import io.github.carvalhoxx.gecko.domain.model.ByokProviderStatus
import io.github.carvalhoxx.gecko.domain.model.GameItem
import io.github.carvalhoxx.gecko.domain.model.GameStatus
import io.github.carvalhoxx.gecko.domain.model.LicenseType
import io.github.carvalhoxx.gecko.domain.model.RemoteCatalogGame
import io.github.carvalhoxx.gecko.domain.repository.CatalogSyncRepository
import io.github.carvalhoxx.gecko.domain.repository.GameRepository
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.withContext

/**
 * Orquestrador BYOK de busca remota de catálogo e sincronização Delta Baseline (US1.1, RF01, RF07):
 * - Modo Sem Chaves (RF01.1): nunca lança exceção quando chaves estão ausentes.
 * - Busca de catálogo: prioriza IGDB e utiliza RAWG como fallback ou enriquecimento de capas ausentes.
 * - Sync de plataformas (Steam + PSN MVP): atualiza EXCLUSIVAMENTE platform_reported_time_ms para jogos existentes,
 *   preservando intacto local_logged_time_ms e evitando sobrescritas destrutivas (ANIR Delta Baseline).
 */
class CatalogSyncRepositoryImpl @Inject constructor(
    private val credentialsStorage: SecureCredentialsStorage,
    private val gameRepository: GameRepository,
    private val igdbApiClient: IgdbApiClient,
    private val steamApiClient: SteamApiClient,
    private val psnApiClient: PsnApiClient,
    private val rawgApiClient: RawgApiClient,
    private val dispatchers: CoroutineDispatchers
) : CatalogSyncRepository {

    override suspend fun searchCatalog(query: String): List<RemoteCatalogGame> = withContext(dispatchers.io) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        val igdbCredentials = credentialsStorage.getIgdbCredentials()
        val rawgCredentials = credentialsStorage.getRawgCredentials()
        if (igdbCredentials == null && rawgCredentials == null) {
            return@withContext emptyList()
        }

        val igdbResults = if (igdbCredentials != null) {
            runCatching { igdbApiClient.searchGames(igdbCredentials, trimmed) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        if (igdbResults.isEmpty()) {
            return@withContext if (rawgCredentials != null) {
                runCatching { rawgApiClient.searchGames(rawgCredentials, trimmed) }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
        }

        val needsArtEnrichment = igdbResults.any { it.coverUrl == null || it.backdropUrl == null }
        if (!needsArtEnrichment || rawgCredentials == null) {
            return@withContext igdbResults
        }

        val rawgFallback = runCatching { rawgApiClient.searchGames(rawgCredentials, trimmed) }
            .getOrDefault(emptyList())
            .associateBy { normalizeTitleKey(it.title) }

        igdbResults.map { item ->
            if (item.coverUrl != null && item.backdropUrl != null) {
                item
            } else {
                val match = rawgFallback[normalizeTitleKey(item.title)]
                item.copy(
                    coverUrl = item.coverUrl ?: match?.coverUrl,
                    backdropUrl = item.backdropUrl ?: match?.backdropUrl
                )
            }
        }
    }

    override suspend fun syncPlatformPlaytimes(): Result<Int> = withContext(dispatchers.io) {
        val steamCredentials = credentialsStorage.getSteamCredentials()
        val psnCredentials = credentialsStorage.getPsnCredentials()

        if (steamCredentials == null && psnCredentials == null) {
            return@withContext Result.success(0)
        }

        var updatedCount = 0
        var successfulProviders = 0
        var lastError: Throwable? = null

        if (steamCredentials != null) {
            try {
                updatedCount += syncSteamLibrary(steamCredentials)
                successfulProviders++
            } catch (ex: IOException) {
                lastError = ex
            }
        }

        if (psnCredentials != null) {
            try {
                updatedCount += syncPsnLibrary()
                successfulProviders++
            } catch (ex: IOException) {
                lastError = ex
            } catch (ex: IllegalArgumentException) {
                lastError = ex
            }
        }

        if (successfulProviders == 0 && lastError != null) {
            Result.failure(lastError)
        } else {
            Result.success(updatedCount)
        }
    }

    override suspend fun testProviderConnection(provider: ByokProvider): ByokProviderStatus =
        withContext(dispatchers.io) {
            when (provider) {
                ByokProvider.IGDB -> {
                    val creds = credentialsStorage.getIgdbCredentials()
                        ?: return@withContext notConfigured(ByokProvider.IGDB)
                    igdbApiClient.testConnection(creds)
                }

                ByokProvider.STEAM -> {
                    val creds = credentialsStorage.getSteamCredentials()
                        ?: return@withContext notConfigured(ByokProvider.STEAM)
                    steamApiClient.testConnection(creds)
                }

                ByokProvider.PSN -> psnApiClient.testConnection(credentialsStorage)

                ByokProvider.RAWG -> {
                    val creds = credentialsStorage.getRawgCredentials()
                        ?: return@withContext notConfigured(ByokProvider.RAWG)
                    rawgApiClient.testConnection(creds)
                }

                ByokProvider.RETRO_ACHIEVEMENTS -> testRetroAchievementsLocalFormat()
            }
        }

    private suspend fun syncSteamLibrary(credentials: SteamCredentials): Int {
        val response = steamApiClient.fetchOwnedGames(credentials)
        var changes = 0
        for (remote in response.games) {
            val existing = gameRepository.findBySteamAppId(remote.appId)
            if (existing != null) {
                if (remote.playtimeMs > existing.platformReportedTimeMs) {
                    gameRepository.updatePlatformReportedTime(
                        gameId = existing.id,
                        reportedTimeMs = remote.playtimeMs
                    )
                    changes++
                }
            } else {
                gameRepository.upsertGame(
                    GameItem(
                        id = "steam_${remote.appId}",
                        steamAppId = remote.appId,
                        title = remote.title,
                        status = GameStatus.BACKLOG,
                        licenseType = LicenseType.OWNED,
                        platformReportedTimeMs = remote.playtimeMs,
                        localLoggedTimeMs = 0L,
                        coverUrl = remote.coverUrl
                    )
                )
                changes++
            }
        }
        return changes
    }

    private suspend fun syncPsnLibrary(): Int {
        val accessToken = psnApiClient.getOrRefreshAccessToken(credentialsStorage) ?: return 0
        val result = psnApiClient.fetchPlayedTitles(accessToken = accessToken)
        if (result !is PsnTitlesFetchResult.Success) {
            return 0
        }

        var changes = 0
        for (remote in result.titles) {
            val existing = gameRepository.findByPsnTitleId(remote.titleId)
            if (existing != null) {
                if (remote.playtimeMs > existing.platformReportedTimeMs) {
                    gameRepository.updatePlatformReportedTime(
                        gameId = existing.id,
                        reportedTimeMs = remote.playtimeMs
                    )
                    changes++
                }
            } else {
                gameRepository.upsertGame(
                    GameItem(
                        id = "psn_${remote.titleId}",
                        psnTitleId = remote.titleId,
                        title = remote.title,
                        status = GameStatus.BACKLOG,
                        licenseType = LicenseType.OWNED,
                        platformReportedTimeMs = remote.playtimeMs,
                        localLoggedTimeMs = 0L,
                        coverUrl = remote.coverUrl
                    )
                )
                changes++
            }
        }
        return changes
    }

    private fun testRetroAchievementsLocalFormat(): ByokProviderStatus {
        val creds = credentialsStorage.getRetroAchievementsCredentials()
            ?: return notConfigured(ByokProvider.RETRO_ACHIEVEMENTS)
        val validation = ByokCredentialValidator.validateRetroAchievements(
            username = creds.username,
            apiKey = creds.apiKey
        )
        return when (validation) {
            CredentialValidationResult.Valid -> ByokProviderStatus(
                provider = ByokProvider.RETRO_ACHIEVEMENTS,
                state = ByokConnectionState.CONFIGURED,
                message = "Credenciais válidas salvas. Sincronização RetroAchievements será ativada na v1.1."
            )

            is CredentialValidationResult.Invalid -> ByokProviderStatus(
                provider = ByokProvider.RETRO_ACHIEVEMENTS,
                state = ByokConnectionState.ERROR,
                message = "Formato de credenciais RetroAchievements inválido (${validation.reason.name})."
            )
        }
    }

    private fun notConfigured(provider: ByokProvider): ByokProviderStatus = ByokProviderStatus(
        provider = provider,
        state = ByokConnectionState.NOT_CONFIGURED,
        message = "Provedor não configurado (Modo Offline / Sem Chaves)."
    )

    private fun normalizeTitleKey(raw: String): String = raw
        .trim()
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
}
