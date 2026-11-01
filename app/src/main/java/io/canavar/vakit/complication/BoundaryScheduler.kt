package io.canavar.vakit.complication

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.canavar.vakit.domain.NextEntry
import java.time.ZoneId

object BoundaryTimes {
    fun triggerAtMillis(next: NextEntry, zone: ZoneId): Long =
        next.at.atZone(zone).toInstant().toEpochMilli() + 5_000
}

class BoundaryScheduler(
    private val context: Context,
    private val zone: ZoneId,
) {
    fun schedule(next: NextEntry?) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, PrayerBoundaryReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(pendingIntent)
        if (next == null) return
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            BoundaryTimes.triggerAtMillis(next, zone),
            pendingIntent,
        )
    }
}
