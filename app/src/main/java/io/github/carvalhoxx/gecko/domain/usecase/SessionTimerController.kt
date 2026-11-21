package io.github.carvalhoxx.gecko.domain.usecase

import io.github.carvalhoxx.gecko.domain.model.Playthrough
import io.github.carvalhoxx.gecko.domain.repository.GameRepository
import io.github.carvalhoxx.gecko.domain.repository.PlaythroughRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ActiveSessionSnapshot(
    val gameId: String,
    val gameTitle: String,
    val playthroughId: Long,
    val startedAtMs: Long,
    val accumulatedBeforeResumeMs: Long = 0L,
    val isPaused: Boolean = false
) {
    fun elapsedMs(nowMs: Long): Long {
        val runningDelta = if (isPaused) 0L else (nowMs - startedAtMs).coerceAtLeast(0L)
        return accumulatedBeforeResumeMs + runningDelta
    }
}

data class SessionConflict(
    val activeSession: ActiveSessionSnapshot,
    val requestedGameId: String,
    val requestedGameTitle: String
)

sealed interface SessionTimerState {
    data object Idle : SessionTimerState

    data class Active(val session: ActiveSessionSnapshot, val pendingConflict: SessionConflict? = null) :
        SessionTimerState
}

sealed interface StartSessionResult {
    data class Started(val session: ActiveSessionSnapshot) : StartSessionResult

    data class ConflictDetected(val conflict: SessionConflict) : StartSessionResult

    data class GameNotFound(val gameId: String) : StartSessionResult
}

data class CompletedSessionSummary(val gameId: String, val playthroughId: Long, val addedLocalTimeMs: Long)

interface ForegroundServiceBridge {
    fun onSessionStarted(session: ActiveSessionSnapshot)
    fun onSessionUpdated(session: ActiveSessionSnapshot)
    fun onSessionStopped()

    companion object {
        val NO_OP: ForegroundServiceBridge = object : ForegroundServiceBridge {
            override fun onSessionStarted(session: ActiveSessionSnapshot) = Unit
            override fun onSessionUpdated(session: ActiveSessionSnapshot) = Unit
            override fun onSessionStopped() = Unit
        }
    }
}

interface SessionTimerController {
    val timerState: StateFlow<SessionTimerState>

    suspend fun startSession(gameId: String): StartSessionResult

    suspend fun confirmSwitchSession(targetGameId: String): StartSessionResult

    fun dismissConflict()

    suspend fun pauseSession(): ActiveSessionSnapshot?

    suspend fun resumeSession(): ActiveSessionSnapshot?

    suspend fun finishSession(): CompletedSessionSummary?

    suspend fun recoverInterruptedSession(): ActiveSessionSnapshot?
}

/**
 * RF21 / RF07: Coordenador Singleton de sessão cronometrada ativa.
 *
 * - Garante estritamente 1 sessão ativa por vez (mesmo com N >= 2 em Jogando Atualmente).
 * - Persiste current_session_start_ms no Room ao iniciar/retomar (resiliência ao LMK).
 * - Incrementa exclusivamente local_logged_time_ms (nunca sobrescreve platform_reported_time_ms).
 * - Expõe SessionConflict para colisão Jogo A vs Jogo B e realiza troca atômica sob Mutex.
 */
class ActiveTimerCoordinator(
    private val gameRepository: GameRepository,
    private val playthroughRepository: PlaythroughRepository,
    private val foregroundServiceBridge: ForegroundServiceBridge = ForegroundServiceBridge.NO_OP,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) : SessionTimerController {

    private val mutex = Mutex()
    private val _timerState = MutableStateFlow<SessionTimerState>(SessionTimerState.Idle)
    override val timerState: StateFlow<SessionTimerState> = _timerState.asStateFlow()

    override suspend fun startSession(gameId: String): StartSessionResult = mutex.withLock {
        val requestedGame = gameRepository.getGameById(gameId)
            ?: return@withLock StartSessionResult.GameNotFound(gameId)

        val current = _timerState.value
        if (current is SessionTimerState.Active) {
            if (current.session.gameId == gameId) {
                return@withLock if (current.session.isPaused) {
                    val resumed = resumeLocked(current.session)
                    StartSessionResult.Started(resumed)
                } else {
                    StartSessionResult.Started(current.session)
                }
            }

            val conflict = SessionConflict(
                activeSession = current.session,
                requestedGameId = requestedGame.id,
                requestedGameTitle = requestedGame.title
            )
            _timerState.value = current.copy(pendingConflict = conflict)
            return@withLock StartSessionResult.ConflictDetected(conflict)
        }

        val started = startFreshLocked(gameId = requestedGame.id, gameTitle = requestedGame.title)
        StartSessionResult.Started(started)
    }

    override suspend fun confirmSwitchSession(targetGameId: String): StartSessionResult = mutex.withLock {
        val targetGame = gameRepository.getGameById(targetGameId)
            ?: return@withLock StartSessionResult.GameNotFound(targetGameId)

        val current = _timerState.value
        if (current is SessionTimerState.Active) {
            if (current.session.gameId == targetGameId) {
                _timerState.value = current.copy(pendingConflict = null)
                return@withLock StartSessionResult.Started(current.session)
            }
            finishLocked(current.session, notifyStop = false)
        }

        val started = startFreshLocked(gameId = targetGame.id, gameTitle = targetGame.title)
        StartSessionResult.Started(started)
    }

    override fun dismissConflict() {
        val current = _timerState.value
        if (current is SessionTimerState.Active && current.pendingConflict != null) {
            _timerState.value = current.copy(pendingConflict = null)
        }
    }

    override suspend fun pauseSession(): ActiveSessionSnapshot? = mutex.withLock {
        val current = _timerState.value as? SessionTimerState.Active ?: return@withLock null
        val session = current.session
        if (session.isPaused) return@withLock session

        val nowMs = clockMs()
        val runningDelta = (nowMs - session.startedAtMs).coerceAtLeast(0L)
        if (runningDelta > 0L) {
            gameRepository.addLocalLoggedTime(session.gameId, runningDelta)
        }
        playthroughRepository.updateCurrentSessionStart(session.playthroughId, null)

        val pausedSession = session.copy(
            startedAtMs = nowMs,
            accumulatedBeforeResumeMs = session.accumulatedBeforeResumeMs + runningDelta,
            isPaused = true
        )
        _timerState.value = current.copy(session = pausedSession)
        foregroundServiceBridge.onSessionUpdated(pausedSession)
        pausedSession
    }

    override suspend fun resumeSession(): ActiveSessionSnapshot? = mutex.withLock {
        val current = _timerState.value as? SessionTimerState.Active ?: return@withLock null
        val session = current.session
        if (!session.isPaused) return@withLock session
        resumeLocked(session)
    }

    override suspend fun finishSession(): CompletedSessionSummary? = mutex.withLock {
        val current = _timerState.value as? SessionTimerState.Active ?: return@withLock null
        finishLocked(current.session, notifyStop = true)
    }

    override suspend fun recoverInterruptedSession(): ActiveSessionSnapshot? = mutex.withLock {
        val current = _timerState.value
        if (current is SessionTimerState.Active) return@withLock current.session

        val persistedPlaythrough = playthroughRepository.getActiveSessionPlaythrough()
            ?: return@withLock null
        val persistedStartMs = persistedPlaythrough.currentSessionStartMs
            ?: return@withLock null
        val game = gameRepository.getGameById(persistedPlaythrough.gameId)
            ?: return@withLock null

        val recovered = ActiveSessionSnapshot(
            gameId = game.id,
            gameTitle = game.title,
            playthroughId = persistedPlaythrough.id,
            startedAtMs = persistedStartMs,
            accumulatedBeforeResumeMs = 0L,
            isPaused = false
        )
        _timerState.value = SessionTimerState.Active(session = recovered)
        foregroundServiceBridge.onSessionStarted(recovered)
        recovered
    }

    private suspend fun startFreshLocked(gameId: String, gameTitle: String): ActiveSessionSnapshot {
        val nowMs = clockMs()
        val playthrough = playthroughRepository.getActivePlaythroughForGame(gameId)
            ?: createActivePlaythrough(gameId, nowMs)

        playthroughRepository.updateCurrentSessionStart(playthrough.id, nowMs)

        val session = ActiveSessionSnapshot(
            gameId = gameId,
            gameTitle = gameTitle,
            playthroughId = playthrough.id,
            startedAtMs = nowMs,
            accumulatedBeforeResumeMs = 0L,
            isPaused = false
        )
        _timerState.value = SessionTimerState.Active(session = session, pendingConflict = null)
        foregroundServiceBridge.onSessionStarted(session)
        return session
    }

    private suspend fun resumeLocked(pausedSession: ActiveSessionSnapshot): ActiveSessionSnapshot {
        val nowMs = clockMs()
        playthroughRepository.updateCurrentSessionStart(pausedSession.playthroughId, nowMs)

        val resumed = pausedSession.copy(
            startedAtMs = nowMs,
            isPaused = false
        )
        val current = _timerState.value as? SessionTimerState.Active
        _timerState.value = SessionTimerState.Active(
            session = resumed,
            pendingConflict = current?.pendingConflict
        )
        foregroundServiceBridge.onSessionUpdated(resumed)
        return resumed
    }

    private suspend fun finishLocked(session: ActiveSessionSnapshot, notifyStop: Boolean): CompletedSessionSummary {
        val nowMs = clockMs()
        val unflushedDelta = if (session.isPaused) {
            0L
        } else {
            (nowMs - session.startedAtMs).coerceAtLeast(0L)
        }
        if (unflushedDelta > 0L) {
            gameRepository.addLocalLoggedTime(session.gameId, unflushedDelta)
        }
        playthroughRepository.updateCurrentSessionStart(session.playthroughId, null)

        val totalSessionAddedMs = session.accumulatedBeforeResumeMs + unflushedDelta
        _timerState.value = SessionTimerState.Idle
        if (notifyStop) {
            foregroundServiceBridge.onSessionStopped()
        }

        return CompletedSessionSummary(
            gameId = session.gameId,
            playthroughId = session.playthroughId,
            addedLocalTimeMs = totalSessionAddedMs
        )
    }

    private suspend fun createActivePlaythrough(gameId: String, nowMs: Long): Playthrough {
        val newPlaythrough = Playthrough(
            gameId = gameId,
            startTimestampMs = nowMs,
            currentSessionStartMs = nowMs
        )
        val generatedId = playthroughRepository.insertPlaythrough(newPlaythrough)
        return newPlaythrough.copy(id = generatedId)
    }
}
