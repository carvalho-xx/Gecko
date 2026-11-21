package io.github.carvalhoxx.gecko.data.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import io.github.carvalhoxx.gecko.MainActivity
import io.github.carvalhoxx.gecko.R
import io.github.carvalhoxx.gecko.domain.usecase.ActiveSessionSnapshot
import java.util.Locale

/**
 * Construtor da notificação persistente do cronômetro ativo (RF21 / Android 14+).
 * Ações usam PendingIntent.getService(..., FLAG_IMMUTABLE) sem abrir Activity;
 * toque no corpo abre MainActivity direcionada à GameDetailScreen.
 */
object SessionNotificationBuilder {

    const val CHANNEL_ID = "gecko_active_session_timer"
    const val NOTIFICATION_ID = 2101
    const val EXTRA_NAVIGATE_GAME_ID = "extra_navigate_game_id"

    private const val REQUEST_CODE_OPEN_DETAIL = 2100
    private const val REQUEST_CODE_TOGGLE_PAUSE = 2101
    private const val REQUEST_CODE_FINISH = 2102

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.timer_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.timer_notification_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun buildNotification(context: Context, session: ActiveSessionSnapshot, nowMs: Long): Notification {
        ensureChannel(context)

        val formattedTime = formatElapsedDuration(session.elapsedMs(nowMs))
        val statusLabel = if (session.isPaused) {
            context.getString(R.string.timer_status_paused, formattedTime)
        } else {
            context.getString(R.string.timer_status_running, formattedTime)
        }

        val openDetailIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAVIGATE_GAME_ID, session.gameId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_OPEN_DETAIL,
            openDetailIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = Intent(context, ActiveSessionForegroundService::class.java).apply {
            action = ActiveSessionForegroundService.ACTION_TOGGLE_PAUSE
        }
        val togglePendingIntent = PendingIntent.getService(
            context,
            REQUEST_CODE_TOGGLE_PAUSE,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val finishIntent = Intent(context, ActiveSessionForegroundService::class.java).apply {
            action = ActiveSessionForegroundService.ACTION_FINISH_SESSION
        }
        val finishPendingIntent = PendingIntent.getService(
            context,
            REQUEST_CODE_FINISH,
            finishIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleLabel = if (session.isPaused) {
            context.getString(R.string.timer_action_resume)
        } else {
            context.getString(R.string.timer_action_pause)
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(session.gameTitle)
            .setContentText(statusLabel)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, toggleLabel, togglePendingIntent)
            .addAction(0, context.getString(R.string.timer_action_finish), finishPendingIntent)
            .build()
    }

    /**
     * Formata duração absoluta em Long ms como HH:MM:SS sem truncamento em 24h (RNF07).
     */
    fun formatElapsedDuration(elapsedMs: Long): String {
        val safeSeconds = (elapsedMs.coerceAtLeast(0L)) / 1_000L
        val hours = safeSeconds / 3_600L
        val minutes = (safeSeconds % 3_600L) / 60L
        val seconds = safeSeconds % 60L
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }
}
