package io.canavar.vakit.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.ZoneId

class ReminderScheduler(
    private val context: Context,
    private val zone: ZoneId,
) {
    fun schedule(reminder: Reminder?) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = pendingIntent(reminder)
        alarmManager.cancel(pendingIntent)
        if (reminder == null) return

        val triggerAt = ReminderTimes.triggerAtMillis(reminder, zone)
        val exact = ReminderScheduleMode.useExact(
            apiLevel = Build.VERSION.SDK_INT,
            canScheduleExact = canScheduleExact(alarmManager),
        )
        if (exact) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent,
                )
                return
            } catch (_: SecurityException) {
                // The permission can be revoked between the check and the call.
            }
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
    }

    private fun canScheduleExact(alarmManager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun pendingIntent(reminder: Reminder?): PendingIntent {
        val intent = Intent(context, PrayerReminderReceiver::class.java)
        if (reminder != null) {
            intent.putExtra(PrayerReminderReceiver.EXTRA_PRAYER, reminder.prayer.name)
            intent.putExtra(
                PrayerReminderReceiver.EXTRA_PRAYER_AT_EPOCH_MS,
                ReminderTimes.prayerAtMillis(reminder, zone),
            )
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val REQUEST_CODE = 1
    }
}
