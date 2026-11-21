package io.github.carvalhoxx.gecko.data.repository

import io.github.carvalhoxx.gecko.core.CoroutineDispatchers
import io.github.carvalhoxx.gecko.data.local.HltbFallbackDao
import io.github.carvalhoxx.gecko.data.local.HltbFallbackEntity
import io.github.carvalhoxx.gecko.data.remote.hltb.HltbApiClient
import io.github.carvalhoxx.gecko.data.sync.HltbRetryScheduler
import io.github.carvalhoxx.gecko.domain.model.HltbEstimate
import io.github.carvalhoxx.gecko.domain.repository.HltbRepository
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlinx.coroutines.withContext

/**
 * Repositório HLTB com Circuit Breaker e Degradação Graciosa:
 * - Em sucesso HTTP 200: atualiza cache perpétuo no Room (Long ms) e retorna isFromFallback = false.
 * - Em HTTP 403/404 ou falha de rede: abre Circuit Breaker, agenda HltbSyncWorker com BackoffPolicy.EXPONENTIAL
 *   e devolve imediatamente medianas históricas da tabela HLTB_FALLBACK.
 */
class HltbRepositoryImpl @Inject constructor(
    private val apiClient: HltbApiClient,
    private val fallbackDao: HltbFallbackDao,
    private val retryScheduler: HltbRetryScheduler,
    private val dispatchers: CoroutineDispatchers
) : HltbRepository {

    internal var clockMs: () -> Long = { System.currentTimeMillis() }
    private val circuitOpenUntilMs = AtomicLong(0L)

    override suspend fun fetchEstimate(title: String, genreHint: String?): HltbEstimate = withContext(dispatchers.io) {
        val nowMs = clockMs()
        val circuitOpen = nowMs < circuitOpenUntilMs.get()

        if (!circuitOpen) {
            try {
                val remote = apiClient.searchGame(title)
                if (remote != null && remote.mainStoryMs > 0L) {
                    val normalizedTitle = normalizeTitle(title)
                    fallbackDao.upsertEntry(
                        HltbFallbackEntity(
                            normalized_title = normalizedTitle,
                            display_title = remote.title,
                            genre_category = normalizeGenreKey(genreHint) ?: DEFAULT_GENRE_CATEGORY,
                            main_story_ms = remote.mainStoryMs,
                            main_sides_ms = remote.mainSidesMs,
                            completionist_ms = remote.completionistMs
                        )
                    )
                    circuitOpenUntilMs.set(0L)
                    return@withContext HltbEstimate(
                        mainStoryMs = remote.mainStoryMs,
                        mainSidesMs = remote.mainSidesMs,
                        completionistMs = remote.completionistMs,
                        isFromFallback = false
                    )
                }
            } catch (_: IOException) {
                circuitOpenUntilMs.set(nowMs + CIRCUIT_OPEN_DURATION_MS)
                retryScheduler.scheduleRetry(title = title, genreHint = genreHint)
            }
        } else {
            retryScheduler.scheduleRetry(title = title, genreHint = genreHint)
        }

        findOfflineFallback(title = title, genreHint = genreHint) ?: DEFAULT_SAFE_ESTIMATE
    }

    override suspend fun findOfflineFallback(title: String, genreHint: String?): HltbEstimate? =
        withContext(dispatchers.io) {
            val normalizedTitle = normalizeTitle(title)
            val entity = if (normalizedTitle.isNotEmpty()) {
                fallbackDao.findByNormalizedTitle(normalizedTitle)
                    ?: fallbackDao.searchByTitlePrefix(normalizedTitle, limit = 1).firstOrNull()
            } else {
                null
            } ?: normalizeGenreKey(genreHint)?.let { fallbackDao.findByGenreCategory(it) }
                ?: fallbackDao.findByGenreCategory(DEFAULT_GENRE_CATEGORY)

            entity?.let {
                HltbEstimate(
                    mainStoryMs = it.main_story_ms,
                    mainSidesMs = it.main_sides_ms,
                    completionistMs = it.completionist_ms,
                    isFromFallback = true
                )
            }
        }

    private fun normalizeTitle(raw: String): String = raw.trim().lowercase()

    private fun normalizeGenreKey(genreHint: String?): String? {
        val cleaned = genreHint?.trim()?.lowercase()?.replace(' ', '_') ?: return null
        if (cleaned.isEmpty()) return null
        return if (cleaned.startsWith("genre:")) cleaned else "genre:$cleaned"
    }

    companion object {
        const val CIRCUIT_OPEN_DURATION_MS: Long = 5L * 60_000L // 5 min
        private const val DEFAULT_GENRE_CATEGORY = "genre:default"

        private val DEFAULT_SAFE_ESTIMATE = HltbEstimate(
            mainStoryMs = 54_000_000L, // 15h
            mainSidesMs = 86_400_000L, // 24h
            completionistMs = 144_000_000L, // 40h
            isFromFallback = true
        )
    }
}
