package io.canavar.vakit.reminder

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.canavar.vakit.domain.Prayer
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@Suppress("DEPRECATION")
class ReminderSchedulerTest {
    private val zone = ZoneId.of("Europe/Istanbul")
    private lateinit var context: Context
    private lateinit var alarmManager: AlarmManager
    private lateinit var scheduler: ReminderScheduler

    private val reminder = Reminder(
        prayer = Prayer.IKINDI,
        prayerAt = LocalDateTime.of(2026, 6, 15, 15, 27),
        at = LocalDateTime.of(2026, 6, 15, 15, 12),
        kind = ReminderKind.STANDARD,
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        alarmManager = context.getSystemService(AlarmManager::class.java)
        ShadowAlarmManager.reset()
        scheduler = ReminderScheduler(context, zone)
    }

    @Test
    fun `schedules the reminder at the lead instant`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)

        scheduler.schedule(reminder)

        val alarms = shadowOf(alarmManager).scheduledAlarms
        assertEquals(1, alarms.size)
        assertEquals(ReminderTimes.triggerAtMillis(reminder, zone), alarms[0].triggerAtTime)
        assertTrue(alarms[0].allowWhileIdle)
        val intent = shadowOf(alarms[0].operation).savedIntent
        assertEquals(Prayer.IKINDI.name, intent.getStringExtra(PrayerReminderReceiver.EXTRA_PRAYER))
        assertEquals(
            ReminderTimes.prayerAtMillis(reminder, zone),
            intent.getLongExtra(PrayerReminderReceiver.EXTRA_PRAYER_AT_EPOCH_MS, 0L),
        )
    }

    @Test
    fun `falls back to inexact when exact access is missing`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        scheduler.schedule(reminder)

        val alarms = shadowOf(alarmManager).scheduledAlarms
        assertEquals(1, alarms.size)
        assertEquals(ReminderTimes.triggerAtMillis(reminder, zone), alarms[0].triggerAtTime)
        assertTrue(alarms[0].allowWhileIdle)
    }

    @Test
    fun `null cancels the existing reminder`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        scheduler.schedule(reminder)
        assertEquals(1, shadowOf(alarmManager).scheduledAlarms.size)

        scheduler.schedule(null)

        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
    }
}
