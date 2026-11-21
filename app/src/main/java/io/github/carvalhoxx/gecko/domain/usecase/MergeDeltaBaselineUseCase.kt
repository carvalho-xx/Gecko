package io.github.carvalhoxx.gecko.domain.usecase

import io.github.carvalhoxx.gecko.domain.model.GameItem

data class ConsolidatedPlaytime(
    val platformReportedTimeMs: Long,
    val localLoggedTimeMs: Long,
    val activeSessionElapsedMs: Long
) {
    val totalTimeMs: Long
        get() = platformReportedTimeMs + localLoggedTimeMs + activeSessionElapsedMs
}

/**
 * RF07 (Delta Baseline): consolida tempo de plataforma + sessões locais exclusivamente em memória,
 * preservando colunas isoladas sem sobrescritas destrutivas.
 */
class MergeDeltaBaselineUseCase {

    operator fun invoke(game: GameItem, activeSessionElapsedMs: Long = 0L): ConsolidatedPlaytime = ConsolidatedPlaytime(
        platformReportedTimeMs = game.platformReportedTimeMs.coerceAtLeast(0L),
        localLoggedTimeMs = game.localLoggedTimeMs.coerceAtLeast(0L),
        activeSessionElapsedMs = activeSessionElapsedMs.coerceAtLeast(0L)
    )

    /**
     * Garante monotonicidade do baseline remoto: uma sincronização de API com valor menor
     * nunca regride o tempo já reportado pela plataforma e nunca altera localLoggedTimeMs.
     */
    fun resolvePlatformBaseline(currentPlatformTimeMs: Long, incomingRemoteTimeMs: Long): Long =
        maxOf(currentPlatformTimeMs.coerceAtLeast(0L), incomingRemoteTimeMs.coerceAtLeast(0L))

    fun applyRemoteSync(game: GameItem, incomingRemoteTimeMs: Long): GameItem = game.copy(
        platformReportedTimeMs = resolvePlatformBaseline(
            currentPlatformTimeMs = game.platformReportedTimeMs,
            incomingRemoteTimeMs = incomingRemoteTimeMs
        )
    )
}
