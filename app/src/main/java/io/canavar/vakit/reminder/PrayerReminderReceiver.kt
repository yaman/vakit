package io.canavar.vakit.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat
import io.canavar.vakit.VakitApp
import io.canavar.vakit.domain.Prayer
import io.canavar.vakit.ui.PrayerAlertActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal fun parsePrayer(raw: String?): Prayer? =
    raw?.let { runCatching { Prayer.valueOf(it) }.getOrNull() }

class PrayerReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val now = System.currentTimeMillis()
        val prayerAt = intent.getLongExtra(EXTRA_PRAYER_AT_EPOCH_MS, 0L)
        val prayer = parsePrayer(intent.getStringExtra(EXTRA_PRAYER))
        val kind = prayer?.let { ReminderKind.forPrayer(it) }
        val shouldVibrate = kind != null && ReminderTimes.shouldVibrate(now, prayerAt)

        val pending = goAsync()
        var gunesServiceStarted = false
        if (prayer != null && kind != null && shouldVibrate) {
            if (kind == ReminderKind.GUNES) {
                gunesServiceStarted = runCatching {
                    ContextCompat.startForegroundService(
                        context,
                        GunesAlertService.startIntent(context, prayer, prayerAt),
                    )
                }.isSuccess
            }
            if (!gunesServiceStarted) {
                PrayerAlertNotifier(context).show(prayer, prayerAt)
                if (Settings.canDrawOverlays(context)) {
                    runCatching {
                        context.startActivity(
                            PrayerAlertActivity.intent(context, prayer, prayerAt),
                        )
                    }
                }
                runCatching { ReminderVibrator(context).vibrate(kind) }
            }
        }
        val app = context.applicationContext as VakitApp
        CoroutineScope(Dispatchers.IO).launch {
            try {
                try {
                    app.container.scheduleNextReminderAfter(
                        ReminderTimes.resumeAtMillis(now, prayerAt),
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // The hourly worker or the next prayer-boundary alarm retries.
                }
                if (
                    kind != null &&
                    shouldVibrate &&
                    (kind != ReminderKind.GUNES || !gunesServiceStarted)
                ) {
                    delay(ReminderPattern.durationMillis(kind))
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_PRAYER = "prayer"
        const val EXTRA_PRAYER_AT_EPOCH_MS = "prayer_at_epoch_ms"
    }
}
