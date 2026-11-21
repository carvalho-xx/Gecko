package io.github.carvalhoxx.gecko.domain.usecase

import io.github.carvalhoxx.gecko.domain.model.RemoteCatalogGame
import io.github.carvalhoxx.gecko.domain.repository.CatalogSyncRepository

/**
 * Busca remota de jogos em provedores BYOK configurados (IGDB / fallback RAWG).
 * Opera em Modo Sem Chaves (retornando lista vazia sem exceção) quando nenhuma chave está cadastrada.
 */
class SearchRemoteCatalogUseCase(private val catalogSyncRepository: CatalogSyncRepository) {

    suspend operator fun invoke(query: String): List<RemoteCatalogGame> {
        val sanitized = query.trim()
        if (sanitized.length < MIN_QUERY_LENGTH) {
            return emptyList()
        }
        return catalogSyncRepository.searchCatalog(sanitized)
    }

    companion object {
        const val MIN_QUERY_LENGTH = 2
    }
}
