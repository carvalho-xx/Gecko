package io.github.carvalhoxx.gecko.domain.repository

import io.github.carvalhoxx.gecko.domain.model.ByokProvider
import io.github.carvalhoxx.gecko.domain.model.ByokProviderStatus
import io.github.carvalhoxx.gecko.domain.model.RemoteCatalogGame

interface CatalogSyncRepository {
    suspend fun searchCatalog(query: String): List<RemoteCatalogGame>

    suspend fun syncPlatformPlaytimes(): Result<Int>

    suspend fun testProviderConnection(provider: ByokProvider): ByokProviderStatus
}
