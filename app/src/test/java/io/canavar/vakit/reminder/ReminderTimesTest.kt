package io.canavar.vakit.reminder

import io.canavar.vakit.domain.Prayer
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderTimesTest {
    private val zone = ZoneId.of("Europe/Istanbul")
    private val reminder = Reminder(
        prayer = Prayer.IKINDI,
        prayerAt = LocalDateTime.of(2026, 6, 15, 15, 27),
        at = LocalDateTime.of(2026, 6, 15, 15, 12),
        kind = ReminderKind.STANDARD,
    )

    @Test
    fun `trigger is exactly fifteen minutes before the prayer`() {
        val expected = LocalDateTime.of(2026, 6, 15, 15, 12)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        assertEquals(expected, ReminderTimes.triggerAtMillis(reminder, zone))
    }

    @Test
    fun `prayer epoch uses the prayer instant`() {
        val expected = LocalDateTime.of(2026, 6, 15, 15, 27)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        assertEquals(expected, ReminderTimes.prayerAtMillis(reminder, zone))
    }

    @Test
    fun `does not vibrate at or after the prayer`() {
        val prayerAt = ReminderTimes.prayerAtMillis(reminder, zone)
        assertTrue(ReminderTimes.shouldVibrate(prayerAt - 1, prayerAt))
        assertFalse(ReminderTimes.shouldVibrate(prayerAt, prayerAt))
        assertFalse(ReminderTimes.shouldVibrate(prayerAt + 1, prayerAt))
        assertFalse(ReminderTimes.shouldVibrate(prayerAt - 1, 0L))
    }

    @Test
    fun `resume skips an early alarm and guards a normal one`() {
        val prayerAt = ReminderTimes.prayerAtMillis(reminder, zone)
        val reminderAt = ReminderTimes.triggerAtMillis(reminder, zone)
        assertEquals(reminderAt + 5_000, ReminderTimes.resumeAtMillis(reminderAt - 10_000, prayerAt))
        assertEquals(reminderAt + 5_000, ReminderTimes.resumeAtMillis(reminderAt, prayerAt))
        assertEquals(reminderAt + 30_000, ReminderTimes.resumeAtMillis(reminderAt + 25_000, prayerAt))
        assertEquals(1_005_000L, ReminderTimes.resumeAtMillis(1_000_000L, 0L))
    }

    @Test
    fun `does not vibrate for an implausible future prayer`() {
        val now = 1_000_000L
        assertFalse(ReminderTimes.shouldVibrate(now, now + 25 * 60 * 60_000L))
    }

    @Test
    fun `resume ignores implausible extra timestamps`() {
        val now = 1_000_000L
        val window = 25 * 60 * 60_000L
        assertEquals(now + 5_000, ReminderTimes.resumeAtMillis(now, now + window))
        assertEquals(now + 5_000, ReminderTimes.resumeAtMillis(now, now - window))
    }
}
