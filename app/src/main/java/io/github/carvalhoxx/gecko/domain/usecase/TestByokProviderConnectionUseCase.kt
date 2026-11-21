package io.github.carvalhoxx.gecko.domain.usecase

import io.github.carvalhoxx.gecko.domain.model.ByokProvider
import io.github.carvalhoxx.gecko.domain.model.ByokProviderStatus
import io.github.carvalhoxx.gecko.domain.repository.CatalogSyncRepository

/**
 * Testa conectividade individual (ou em lote) de provedores BYOK para feedback
 * nas telas de Onboarding (Passo 3) e Configurações.
 */
class TestByokProviderConnectionUseCase(private val catalogSyncRepository: CatalogSyncRepository) {

    suspend operator fun invoke(provider: ByokProvider): ByokProviderStatus =
        catalogSyncRepository.testProviderConnection(provider)

    suspend fun testProviders(providers: List<ByokProvider> = ByokProvider.entries): List<ByokProviderStatus> =
        providers.map { provider -> catalogSyncRepository.testProviderConnection(provider) }
}
