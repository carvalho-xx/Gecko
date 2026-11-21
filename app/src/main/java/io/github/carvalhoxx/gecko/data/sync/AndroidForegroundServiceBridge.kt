package io.github.carvalhoxx.gecko.data.sync

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.carvalhoxx.gecko.domain.usecase.ActiveSessionSnapshot
import io.github.carvalhoxx.gecko.domain.usecase.ForegroundServiceBridge
import javax.inject.Inject

class AndroidForegroundServiceBridge @Inject constructor(@ApplicationContext private val context: Context) :
    ForegroundServiceBridge {

    override fun onSessionStarted(session: ActiveSessionSnapshot) {
        val intent = Intent(context, ActiveSessionForegroundService::class.java).apply {
            action = ActiveSessionForegroundService.ACTION_START_OR_UPDATE
        }
        ContextCompat.startForegroundService(context, intent)
    }

    override fun onSessionUpdated(session: ActiveSessionSnapshot) {
        val intent = Intent(context, ActiveSessionForegroundService::class.java).apply {
            action = ActiveSessionForegroundService.ACTION_START_OR_UPDATE
        }
        ContextCompat.startForegroundService(context, intent)
    }

    override fun onSessionStopped() {
        val intent = Intent(context, ActiveSessionForegroundService::class.java)
        context.stopService(intent)
    }
}
