package io.canavar.vakit.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.canavar.vakit.VakitApp
import io.canavar.vakit.domain.Prayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal fun parsePrayer(raw: String?): Prayer? =
    raw?.let { runCatching { Prayer.valueOf(it) }.getOrNull() }

class PrayerReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val now = System.currentTimeMillis()
        val prayerAt = intent.getLongExtra(EXTRA_PRAYER_AT_EPOCH_MS, 0L)
        val prayer = parsePrayer(intent.getStringExtra(EXTRA_PRAYER))
        val kind = prayer?.let { ReminderKind.forPrayer(it) }
        if (kind != null && ReminderTimes.shouldVibrate(now, prayerAt)) {
            runCatching { ReminderVibrator(context).vibrate(kind) }
        }

        val pending = goAsync()
        val app = context.applicationContext as VakitApp
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.container.scheduleNextReminderAfter(
                    ReminderTimes.resumeAtMillis(now, prayerAt),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // The next scheduled run retries.
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
