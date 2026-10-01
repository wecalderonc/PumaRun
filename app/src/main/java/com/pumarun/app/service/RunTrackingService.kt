package com.pumarun.app.service

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.pumarun.app.domain.SessionConfig
import com.pumarun.app.domain.SessionStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Keeps the run alive in the background and mirrors its state in an ongoing notification. */
@AndroidEntryPoint
class RunTrackingService : LifecycleService() {

    @Inject lateinit var sessionManager: SessionManager

    private var wakeLock: PowerManager.WakeLock? = null
    private var observing = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                val config = SessionConfig.fromIntent(intent)
                if (config == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                goForeground()
                sessionManager.start(config)
                observe()
            }
            ACTION_PAUSE -> sessionManager.pause()
            ACTION_RESUME -> sessionManager.resume()
            ACTION_STOP -> sessionManager.stop()
        }
        if (!sessionManager.state.value.isActive) shutDown()
        return START_NOT_STICKY
    }

    private fun goForeground() {
        ServiceCompat.startForeground(
            this,
            RunNotification.NOTIFICATION_ID,
            RunNotification.build(this, sessionManager.state.value),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
        )
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "puma:run")
                .apply { acquire(MAX_WAKE_LOCK_MS) }
        }
    }

    private fun observe() {
        if (observing) return
        observing = true
        val notificationManager = getSystemService(NotificationManager::class.java)
        lifecycleScope.launch {
            sessionManager.state
                .map { it.copy(elapsedMs = it.elapsedMs / 1000) }
                .distinctUntilChanged()
                .collect {
                    val state = sessionManager.state.value
                    if (state.status == SessionStatus.Finished || state.status == SessionStatus.Idle) {
                        shutDown()
                    } else {
                        notificationManager.notify(
                            RunNotification.NOTIFICATION_ID,
                            RunNotification.build(this@RunTrackingService, state),
                        )
                    }
                }
        }
    }

    private fun shutDown() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        wakeLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.pumarun.app.START"
        const val ACTION_PAUSE = "com.pumarun.app.PAUSE"
        const val ACTION_RESUME = "com.pumarun.app.RESUME"
        const val ACTION_STOP = "com.pumarun.app.STOP"

        private const val MAX_WAKE_LOCK_MS = 6 * 60 * 60 * 1000L

        fun start(context: Context, config: SessionConfig) {
            val intent = config.writeTo(Intent(context, RunTrackingService::class.java).setAction(ACTION_START))
            ContextCompat.startForegroundService(context, intent)
        }

        fun send(context: Context, action: String) {
            context.startService(Intent(context, RunTrackingService::class.java).setAction(action))
        }
    }
}
