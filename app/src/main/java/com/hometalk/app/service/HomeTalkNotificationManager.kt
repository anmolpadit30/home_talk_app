package com.hometalk.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.hometalk.app.MainActivity
import com.hometalk.app.R
import com.hometalk.app.domain.model.ChannelState
import com.hometalk.app.domain.model.NetworkDefaults
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class NotificationSnapshot(
    val wifiConnected: Boolean,
    val connectedCount: Int,
    val isTransmitting: Boolean,
    val channel: ChannelState,
    val speakerEnabled: Boolean = true,
    val sleepState: WifiSleepState = WifiSleepState.Active,
    val notificationsEnabled: Boolean,
)

@Singleton
class HomeTalkNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        ensureChannel()
    }

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            setShowBadge(false)
            enableVibration(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun buildNotification(snapshot: NotificationSnapshot): Notification {
        val contentTitle = context.getString(R.string.notification_title)
        val contentText = buildContentText(snapshot)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_hometalk)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppPendingIntent())
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (snapshot.isTransmitting) {
            builder.addAction(
                0,
                context.getString(R.string.notification_action_stop),
                servicePendingIntent(ServiceActions.ACTION_STOP_TALK, REQUEST_STOP_TALK),
            )
        } else {
            builder.addAction(
                0,
                context.getString(R.string.notification_action_talk),
                servicePendingIntent(ServiceActions.ACTION_TALK, REQUEST_TALK),
            )
        }
        builder.addAction(
            0,
            context.getString(
                if (snapshot.speakerEnabled) {
                    R.string.notification_action_mute
                } else {
                    R.string.notification_action_unmute
                },
            ),
            servicePendingIntent(ServiceActions.ACTION_TOGGLE_SPEAKER, REQUEST_TOGGLE_SPEAKER),
        )
        return builder.build()
    }

    fun notify(snapshot: NotificationSnapshot) {
        if (!snapshot.notificationsEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Still required for FGS; keep posting while service runs.
        }
        notificationManager.notify(NOTIFICATION_ID, buildNotification(snapshot))
    }

    fun cancel() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun buildContentText(snapshot: NotificationSnapshot): String {
        if (snapshot.sleepState is WifiSleepState.GracePeriod) {
            val elapsed = System.currentTimeMillis() - snapshot.sleepState.disconnectedAtEpochMs
            val remainingMs =
                (NetworkDefaults.WIFI_SLEEP_AFTER_MS - elapsed).coerceAtLeast(0L)
            val remainingMin = ((remainingMs + 59_999L) / 60_000L).coerceAtLeast(1L)
            return "Wi-Fi disconnected\nSleeping in about ${remainingMin} min to save battery"
        }
        val connection = when {
            !snapshot.wifiConnected -> "Wi-Fi disconnected"
            snapshot.connectedCount <= 0 -> "Listening · no paired phones online"
            else -> "Listening · ${snapshot.connectedCount} connected"
        }
        val remoteName = snapshot.channel.currentTransmitterName
        val transmitter = when {
            snapshot.isTransmitting && remoteName != null ->
                "You + $remoteName (both talking)"
            snapshot.isTransmitting -> "You are talking"
            remoteName != null -> "$remoteName is talking"
            else -> "Ready · hold Talk (both can speak)"
        }
        val speaker = if (snapshot.speakerEnabled) "Speaker on" else "Speaker muted"
        return "$connection\n$transmitter\n$speaker"
    }

    private fun openAppPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, WalkieTalkieService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_ID = "hometalk_status"
        const val NOTIFICATION_ID = 1001
        private const val REQUEST_OPEN = 2001
        private const val REQUEST_TALK = 2002
        private const val REQUEST_STOP_TALK = 2003
        private const val REQUEST_TOGGLE_SPEAKER = 2004
    }
}
