package io.canavar.vakit.reminder

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import android.os.Vibrator
import android.os.VibratorManager
import androidx.test.core.app.ApplicationProvider
import io.canavar.vakit.VakitApp
import io.canavar.vakit.domain.Prayer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.shadows.ShadowVibrator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrayerReminderReceiverTest {
    private companion object {
        const val ACTION = "io.canavar.vakit.reminder.TEST"
        const val LEAD_MS = 15 * 60_000L
    }

    private lateinit var context: Context
    private lateinit var container: VakitApp
    private lateinit var receiver: PrayerReminderReceiver
    private lateinit var alarmManager: AlarmManager
    private lateinit var vibrator: Vibrator

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val app = context.applicationContext as VakitApp
        container = app
        val district = app.container.timetable.districts().first()
        runBlocking { app.container.settings.setDistrict(district, auto = false) }
        alarmManager = context.getSystemService(AlarmManager::class.java)
        vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator
        ShadowAlarmManager.reset()
        ShadowVibrator.reset()
        shadowOf(vibrator).setHasVibrator(true)
        receiver = PrayerReminderReceiver()
        context.registerReceiver(
            receiver,
            IntentFilter(ACTION),
            Context.RECEIVER_NOT_EXPORTED,
        )
    }

    @After
    fun tearDown() {
        context.unregisterReceiver(receiver)
        runBlocking { (context.applicationContext as VakitApp).container.settings.clearDistrict() }
        ShadowAlarmManager.reset()
        ShadowVibrator.reset()
    }

    @Test
    fun `parses a known prayer`() {
        assertEquals(Prayer.IKINDI, parsePrayer("IKINDI"))
    }

    @Test
    fun `unknown or null names return null`() {
        assertNull(parsePrayer("NOT_A_PRAYER"))
        assertNull(parsePrayer(null))
    }

    @Test
    fun `valid standard extras vibrate and reschedule`() {
        send(Prayer.IKINDI.name, System.currentTimeMillis() + LEAD_MS)

        awaitVibration(ReminderPattern.forKind(ReminderKind.STANDARD))
        assertTrue(shadowOf(alarmManager).scheduledAlarms.isNotEmpty())
    }

    @Test
    fun `gunes uses the long pattern`() {
        send(Prayer.GUNES.name, System.currentTimeMillis() + LEAD_MS)

        awaitVibration(ReminderPattern.forKind(ReminderKind.GUNES))
    }

    @Test
    fun `missing extras do not vibrate and still reschedule`() {
        send(prayer = null, prayerAt = 0L)

        assertNull(shadowOf(vibrator).pattern)
        assertTrue(shadowOf(alarmManager).scheduledAlarms.isNotEmpty())
    }

    @Test
    fun `unknown prayer does not vibrate`() {
        send("NOT_A_PRAYER", System.currentTimeMillis() + LEAD_MS)

        assertNull(shadowOf(vibrator).pattern)
        assertTrue(shadowOf(alarmManager).scheduledAlarms.isNotEmpty())
    }

    @Test
    fun `past prayer does not vibrate and still reschedules`() {
        send(Prayer.IKINDI.name, System.currentTimeMillis() - 60_000L)

        assertNull(shadowOf(vibrator).pattern)
        assertTrue(shadowOf(alarmManager).scheduledAlarms.isNotEmpty())
    }

    @Test
    fun `imsak does not vibrate`() {
        send(Prayer.IMSAK.name, System.currentTimeMillis() + LEAD_MS)

        assertNull(shadowOf(vibrator).pattern)
        assertTrue(shadowOf(alarmManager).scheduledAlarms.isNotEmpty())
    }

    private fun send(prayer: String?, prayerAt: Long) {
        val intent = Intent(ACTION).setPackage(context.packageName)
        if (prayer != null) {
            intent.putExtra(PrayerReminderReceiver.EXTRA_PRAYER, prayer)
        }
        if (prayerAt != 0L) {
            intent.putExtra(PrayerReminderReceiver.EXTRA_PRAYER_AT_EPOCH_MS, prayerAt)
        }
        context.sendBroadcast(intent)
        shadowOf(Looper.getMainLooper()).idle()
        awaitAlarm()
    }

    private fun awaitAlarm() {
        val deadline = System.currentTimeMillis() + 2_000
        while (System.currentTimeMillis() < deadline) {
            if (shadowOf(alarmManager).scheduledAlarms.isNotEmpty()) return
            Thread.sleep(10)
        }
        throw AssertionError("No reminder alarm was scheduled")
    }

    private fun awaitVibration(expected: LongArray) {
        val deadline = System.currentTimeMillis() + 2_000
        while (System.currentTimeMillis() < deadline) {
            val pattern = shadowOf(vibrator).pattern
            if (pattern != null && pattern.contentEquals(expected)) return
            Thread.sleep(10)
        }
        throw AssertionError("Expected vibration pattern ${expected.toList()}")
    }
}
