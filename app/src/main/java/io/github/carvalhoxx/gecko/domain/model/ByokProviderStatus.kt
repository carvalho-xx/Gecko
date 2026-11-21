package io.github.carvalhoxx.gecko.domain.model

enum class ByokProvider {
    IGDB,
    RAWG,
    STEAM,
    PSN,
    RETRO_ACHIEVEMENTS
}

enum class ByokConnectionState {
    NOT_CONFIGURED,
    CONFIGURED,
    CONNECTED,
    EXPIRED,
    ERROR
}

data class ByokProviderStatus(val provider: ByokProvider, val state: ByokConnectionState, val message: String? = null)

data class HltbEstimate(
    val mainStoryMs: Long,
    val mainSidesMs: Long,
    val completionistMs: Long,
    val isFromFallback: Boolean
)

data class RemoteCatalogGame(
    val externalId: String,
    val title: String,
    val releaseDateMs: Long? = null,
    val releasePrecision: ReleasePrecision? = null,
    val coverUrl: String? = null,
    val backdropUrl: String? = null,
    val synopsis: String? = null,
    val developer: String? = null,
    val publisher: String? = null,
    val genres: List<String> = emptyList(),
    val platforms: List<String> = emptyList()
)
