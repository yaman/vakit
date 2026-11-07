package io.canavar.vakit.reminder

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import io.canavar.vakit.domain.Prayer
import io.canavar.vakit.ui.PrayerAlertActivity

class GunesAlertService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val stopRunnable = Runnable { stopAlert() }
    private var wakeLock: PowerManager.WakeLock? = null
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAlert()
            return START_NOT_STICKY
        }

        val prayer = parsePrayer(intent?.getStringExtra(EXTRA_PRAYER))
        val prayerAt = intent?.getLongExtra(EXTRA_PRAYER_AT_EPOCH_MS, 0L) ?: 0L
        if (prayer != Prayer.GUNES || prayerAt <= 0L) {
            stopAlert()
            return START_NOT_STICKY
        }

        if (!started) {
            started = true
            startAlert(prayer, prayerAt)
        }
        return START_NOT_STICKY
    }

    private fun startAlert(prayer: Prayer, prayerAt: Long) {
        val notifier = PrayerAlertNotifier(this)
        val stopPendingIntent = PendingIntent.getService(
            this,
            REQUEST_STOP,
            stopIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = notifier.ongoingNotification(
            prayer = prayer,
            prayerAtEpochMs = prayerAt,
            contentIntent = PrayerAlertActivity.intent(
                context = this,
                prayer = prayer,
                prayerAtEpochMs = prayerAt,
                ongoing = true,
            ),
            stopIntent = stopPendingIntent,
        )

        val foregroundStarted = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    PrayerAlertNotifier.NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(PrayerAlertNotifier.NOTIFICATION_ID, notification)
            }
        }.isSuccess
        if (!foregroundStarted) {
            PrayerAlertNotifier(this).show(prayer, prayerAt)
        }

        ReminderVibrator(this).vibrateRepeatingGunes()
        if (Settings.canDrawOverlays(this)) {
            runCatching {
                startActivity(
                    PrayerAlertActivity.intent(
                        context = this,
                        prayer = prayer,
                        prayerAtEpochMs = prayerAt,
                        ongoing = true,
                    ),
                )
            }
        }

        wakeLock = getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Vakit:GunesAlert")
            ?.apply { acquire(MAX_DURATION_MS + 10_000L) }
        handler.postDelayed(stopRunnable, MAX_DURATION_MS)
    }

    private fun stopAlert() {
        handler.removeCallbacks(stopRunnable)
        if (!started) {
            stopSelf()
            return
        }
        started = false
        ReminderVibrator(this).cancel()
        PrayerAlertNotifier(this).cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(stopRunnable)
        ReminderVibrator(this).cancel()
        PrayerAlertNotifier(this).cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    companion object {
        private const val ACTION_START = "io.canavar.vakit.action.GUNES_ALERT_START"
        private const val ACTION_STOP = "io.canavar.vakit.action.GUNES_ALERT_STOP"
        private const val EXTRA_PRAYER = "prayer"
        private const val EXTRA_PRAYER_AT_EPOCH_MS = "prayer_at_epoch_ms"
        private const val REQUEST_STOP = 3001
        private const val MAX_DURATION_MS = 5 * 60_000L

        fun startIntent(
            context: Context,
            prayer: Prayer,
            prayerAtEpochMs: Long,
        ): Intent = Intent(context, GunesAlertService::class.java)
            .setAction(ACTION_START)
            .putExtra(EXTRA_PRAYER, prayer.name)
            .putExtra(EXTRA_PRAYER_AT_EPOCH_MS, prayerAtEpochMs)

        fun stopIntent(context: Context): Intent =
            Intent(context, GunesAlertService::class.java).setAction(ACTION_STOP)
    }
}
