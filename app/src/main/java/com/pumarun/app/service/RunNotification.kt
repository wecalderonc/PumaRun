package com.pumarun.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.pumarun.app.MainActivity
import com.pumarun.app.R
import com.pumarun.app.domain.SessionState
import com.pumarun.app.domain.SessionStatus
import com.pumarun.app.domain.Units

object RunNotification {
    const val CHANNEL_ID = "active_run"
    const val NOTIFICATION_ID = 1

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setShowBadge(false)
            setSound(null, null)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun build(context: Context, state: SessionState): Notification {
        val title = when (state.status) {
            SessionStatus.Paused -> context.getString(R.string.notif_title_paused)
            SessionStatus.Countdown, SessionStatus.Idle -> context.getString(R.string.notif_title_countdown)
            else -> context.getString(R.string.notif_title_running)
        }
        val text = context.getString(
            R.string.notif_text,
            Units.formatKm(state.distanceMeters),
            Units.formatDuration(state.elapsedMs),
            Units.formatPace(state.currentPaceSecPerKm),
        )

        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        // Stats go in the title: collapsed notifications with a progress bar hide the content text.
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_run)
            .setSubText(title)
            .setContentTitle(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setProgress(1000, (state.progress * 1000).toInt(), false)

        when (state.status) {
            SessionStatus.Running -> builder.addAction(
                0, context.getString(R.string.action_pause),
                serviceAction(context, RunTrackingService.ACTION_PAUSE, 1),
            )
            SessionStatus.Paused -> builder.addAction(
                0, context.getString(R.string.action_resume),
                serviceAction(context, RunTrackingService.ACTION_RESUME, 2),
            )
            else -> Unit
        }
        builder.addAction(
            0, context.getString(R.string.action_stop),
            serviceAction(context, RunTrackingService.ACTION_STOP, 3),
        )
        return builder.build()
    }

    private fun serviceAction(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            context, requestCode,
            Intent(context, RunTrackingService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
