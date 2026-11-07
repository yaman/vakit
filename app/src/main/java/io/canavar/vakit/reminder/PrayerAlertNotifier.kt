package io.canavar.vakit.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.canavar.vakit.R
import io.canavar.vakit.domain.Prayer
import io.canavar.vakit.domain.TimeFormat
import io.canavar.vakit.ui.PrayerAlertActivity
import java.time.Instant
import java.time.ZoneId

class PrayerAlertNotifier(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)
    private val zone = ZoneId.of("Europe/Istanbul")

    fun show(prayer: Prayer, prayerAtEpochMs: Long) {
        ensureChannel()
        if (!manager.areNotificationsEnabled()) return

        val time = TimeFormat.hhmm(
            Instant.ofEpochMilli(prayerAtEpochMs).atZone(zone).toLocalDateTime(),
        )
        val contentIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            PrayerAlertActivity.intent(context, prayer, prayerAtEpochMs),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_complication)
            .setContentTitle(prayer.displayName)
            .setContentText(time)
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setTimeoutAfter(30_000L)
            .build()

        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    fun ongoingNotification(
        prayer: Prayer,
        prayerAtEpochMs: Long,
        contentIntent: Intent,
        stopIntent: PendingIntent,
    ): Notification {
        ensureChannel()
        val time = TimeFormat.hhmm(
            Instant.ofEpochMilli(prayerAtEpochMs).atZone(zone).toLocalDateTime(),
        )
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_complication)
            .setContentTitle(prayer.displayName)
            .setContentText(time)
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(stopIntent)
            .addAction(R.drawable.ic_complication, "Kapat", stopIntent)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun cancel() {
        runCatching { manager.cancel(NOTIFICATION_ID) }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.reminder_channel_description)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "prayer-alerts"
        const val NOTIFICATION_ID = 2001
        private const val REQUEST_CODE = 2001
    }
}
