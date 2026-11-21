package io.github.carvalhoxx.gecko.data.sync

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import io.github.carvalhoxx.gecko.core.CoroutineDispatchers
import io.github.carvalhoxx.gecko.domain.usecase.ActiveSessionSnapshot
import io.github.carvalhoxx.gecko.domain.usecase.SessionTimerController
import io.github.carvalhoxx.gecko.domain.usecase.SessionTimerState
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * RF21: Foreground Service Singleton (specialUse) para proteção do cronômetro contra o LMK.
 */
@AndroidEntryPoint
class ActiveSessionForegroundService : Service() {

    @Inject
    lateinit var sessionTimerController: SessionTimerController

    @Inject
    lateinit var dispatchers: CoroutineDispatchers

    private lateinit var serviceScope: CoroutineScope
    private var tickerJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        serviceScope = CoroutineScope(SupervisorJob() + dispatchers.mainImmediate)
        SessionNotificationBuilder.ensureChannel(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_PAUSE -> handleTogglePause()
            ACTION_FINISH_SESSION -> handleFinishSession()
            ACTION_STOP_SERVICE -> stopForegroundAndSelf()
            ACTION_START_OR_UPDATE, null -> handleStartOrRecover()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        tickerJob?.cancel()
        if (::serviceScope.isInitialized) {
            serviceScope.cancel()
        }
        super.onDestroy()
    }

    private fun handleStartOrRecover() {
        serviceScope.launch {
            val activeSession = when (val state = sessionTimerController.timerState.value) {
                is SessionTimerState.Active -> state.session
                SessionTimerState.Idle -> sessionTimerController.recoverInterruptedSession()
            }

            if (activeSession == null) {
                stopForegroundAndSelf()
                return@launch
            }

            promoteToForeground(activeSession)
            restartTicker(activeSession)
        }
    }

    private fun handleTogglePause() {
        serviceScope.launch {
            val current = sessionTimerController.timerState.value as? SessionTimerState.Active
            if (current == null) {
                stopForegroundAndSelf()
                return@launch
            }

            val updated = if (current.session.isPaused) {
                sessionTimerController.resumeSession()
            } else {
                sessionTimerController.pauseSession()
            }

            if (updated == null) {
                stopForegroundAndSelf()
            } else {
                updateNotification(updated)
                restartTicker(updated)
            }
        }
    }

    private fun handleFinishSession() {
        serviceScope.launch {
            sessionTimerController.finishSession()
            stopForegroundAndSelf()
        }
    }

    private fun promoteToForeground(session: ActiveSessionSnapshot) {
        val notification = SessionNotificationBuilder.buildNotification(
            context = this,
            session = session,
            nowMs = System.currentTimeMillis()
        )
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            SessionNotificationBuilder.NOTIFICATION_ID,
            notification,
            serviceType
        )
    }

    private fun updateNotification(session: ActiveSessionSnapshot) {
        val notification = SessionNotificationBuilder.buildNotification(
            context = this,
            session = session,
            nowMs = System.currentTimeMillis()
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(SessionNotificationBuilder.NOTIFICATION_ID, notification)
    }

    private fun restartTicker(session: ActiveSessionSnapshot) {
        tickerJob?.cancel()
        if (session.isPaused) return

        tickerJob = serviceScope.launch {
            while (isActive) {
                delay(TICK_INTERVAL_MS)
                val latest = (sessionTimerController.timerState.value as? SessionTimerState.Active)?.session
                if (latest == null) {
                    stopForegroundAndSelf()
                    break
                }
                updateNotification(latest)
                if (latest.isPaused) break
            }
        }
    }

    private fun stopForegroundAndSelf() {
        tickerJob?.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ACTION_START_OR_UPDATE = "io.github.carvalhoxx.gecko.action.TIMER_START_OR_UPDATE"
        const val ACTION_TOGGLE_PAUSE = "io.github.carvalhoxx.gecko.action.TIMER_TOGGLE_PAUSE"
        const val ACTION_FINISH_SESSION = "io.github.carvalhoxx.gecko.action.TIMER_FINISH_SESSION"
        const val ACTION_STOP_SERVICE = "io.github.carvalhoxx.gecko.action.TIMER_STOP_SERVICE"

        private const val TICK_INTERVAL_MS = 1_000L
    }
}
