package io.canavar.vakit.reminder

import io.canavar.vakit.domain.Prayer
import java.time.LocalDateTime
import java.time.ZoneId

enum class ReminderKind {
    STANDARD,
    GUNES,
    ;

    companion object {
        fun forPrayer(prayer: Prayer): ReminderKind? = when (prayer) {
            Prayer.IMSAK -> null
            Prayer.GUNES -> GUNES
            Prayer.OGLE,
            Prayer.IKINDI,
            Prayer.AKSAM,
            Prayer.YATSI,
            -> STANDARD
        }
    }
}

data class Reminder(
    val prayer: Prayer,
    val prayerAt: LocalDateTime,
    val at: LocalDateTime,
    val kind: ReminderKind,
)

object ReminderTimes {
    const val LEAD_MINUTES = 15
    const val RESUME_GUARD_MS = 5_000L
    private const val MAX_PRAYER_WINDOW_MS = 24 * 60 * 60_000L

    fun triggerAtMillis(reminder: Reminder, zone: ZoneId): Long =
        reminder.at.atZone(zone).toInstant().toEpochMilli()

    fun prayerAtMillis(reminder: Reminder, zone: ZoneId): Long =
        reminder.prayerAt.atZone(zone).toInstant().toEpochMilli()

    fun shouldVibrate(nowEpochMs: Long, prayerAtEpochMs: Long): Boolean =
        prayerAtEpochMs > nowEpochMs &&
            prayerAtEpochMs - nowEpochMs <= MAX_PRAYER_WINDOW_MS

    fun resumeAtMillis(nowEpochMs: Long, prayerAtEpochMs: Long): Long {
        if (prayerAtEpochMs <= 0L) return nowEpochMs + RESUME_GUARD_MS
        if (prayerAtEpochMs > nowEpochMs + MAX_PRAYER_WINDOW_MS) {
            return nowEpochMs + RESUME_GUARD_MS
        }
        if (prayerAtEpochMs < nowEpochMs - MAX_PRAYER_WINDOW_MS) {
            return nowEpochMs + RESUME_GUARD_MS
        }
        val reminderAt = prayerAtEpochMs - LEAD_MINUTES * 60_000L
        return maxOf(nowEpochMs, reminderAt) + RESUME_GUARD_MS
    }
}
