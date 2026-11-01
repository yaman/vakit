package io.canavar.vakit.complication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.canavar.vakit.VakitApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PrayerBoundaryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext as VakitApp
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.container.complicationRefresher.requestUpdate()
                app.container.scheduleNextBoundary()
            } catch (error: Exception) {
                // The next hourly worker retries the schedule.
            } finally {
                pending.finish()
            }
        }
    }
}
