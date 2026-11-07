package io.canavar.vakit.reminder

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.canavar.vakit.domain.Prayer
import io.canavar.vakit.ui.PrayerAlertActivity
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNotificationManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrayerAlertNotifierTest {
    private lateinit var context: Context
    private lateinit var manager: NotificationManager
    private lateinit var notifier: PrayerAlertNotifier

    private val prayerAt = LocalDateTime.of(2026, 11, 8, 18, 2)
        .atZone(ZoneId.of("Europe/Istanbul"))
        .toInstant()
        .toEpochMilli()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = context.getSystemService(NotificationManager::class.java)
        ShadowNotificationManager.reset()
        shadowOf(manager).setNotificationsEnabled(true)
        notifier = PrayerAlertNotifier(context)
    }

    @After
    fun tearDown() {
        notifier.cancel()
        ShadowNotificationManager.reset()
    }

    @Test
    fun `posts a prayer notification`() {
        notifier.show(Prayer.AKSAM, prayerAt)

        val notification = shadowOf(manager).getNotification(PrayerAlertNotifier.NOTIFICATION_ID)
        assertNotNull(notification)
        assertEquals("Akşam", shadowOf(notification).getContentTitle().toString())
        assertEquals("18:02", shadowOf(notification).getContentText().toString())

        val channel = manager.getNotificationChannel(PrayerAlertNotifier.CHANNEL_ID)
        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertFalse(channel.shouldVibrate())
    }

    @Test
    fun `does not post when notifications are disabled`() {
        shadowOf(manager).setNotificationsEnabled(false)

        notifier.show(Prayer.AKSAM, prayerAt)

        assertNull(shadowOf(manager).getNotification(PrayerAlertNotifier.NOTIFICATION_ID))
    }

    @Test
    fun `cancel removes the notification`() {
        notifier.show(Prayer.AKSAM, prayerAt)
        assertNotNull(shadowOf(manager).getNotification(PrayerAlertNotifier.NOTIFICATION_ID))

        notifier.cancel()

        assertNull(shadowOf(manager).getNotification(PrayerAlertNotifier.NOTIFICATION_ID))
    }

    @Test
    fun `ongoing notification stays and has a stop action`() {
        val stopIntent = PendingIntent.getService(
            context,
            1,
            GunesAlertService.stopIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = notifier.ongoingNotification(
            prayer = Prayer.GUNES,
            prayerAtEpochMs = prayerAt,
            contentIntent = PrayerAlertActivity.intent(
                context,
                Prayer.GUNES,
                prayerAt,
                ongoing = true,
            ),
            stopIntent = stopIntent,
        )

        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(1, notification.actions.size)
        assertEquals("Güneş", shadowOf(notification).getContentTitle().toString())
        assertEquals("18:02", shadowOf(notification).getContentText().toString())
    }
}
