package io.github.carvalhoxx.gecko.domain.repository

import io.github.carvalhoxx.gecko.domain.model.HltbEstimate

interface HltbRepository {
    suspend fun fetchEstimate(title: String, genreHint: String? = null): HltbEstimate

    suspend fun findOfflineFallback(title: String, genreHint: String? = null): HltbEstimate?
}
