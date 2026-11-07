package io.canavar.vakit.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import io.canavar.vakit.domain.Prayer
import io.canavar.vakit.domain.TimeFormat
import io.canavar.vakit.reminder.PrayerAlertNotifier
import io.canavar.vakit.reminder.ReminderVibrator
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.delay

class PrayerAlertActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val prayer = intent.getStringExtra(EXTRA_PRAYER)
            ?.let { runCatching { Prayer.valueOf(it) }.getOrNull() }
        val prayerAt = intent.getLongExtra(EXTRA_PRAYER_AT_EPOCH_MS, 0L)
        val time = if (prayerAt > 0L) {
            TimeFormat.hhmm(
                Instant.ofEpochMilli(prayerAt).atZone(ZONE).toLocalDateTime(),
            )
        } else {
            "--:--"
        }

        val dismiss = {
            ReminderVibrator(this).cancel()
            finish()
        }
        setContent {
            MaterialTheme {
                AlertScreen(
                    prayer = prayer?.displayName ?: "Vakit",
                    time = time,
                    onDismiss = dismiss,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        PrayerAlertNotifier(this).cancel()
    }

    companion object {
        private const val EXTRA_PRAYER = "prayer"
        private const val EXTRA_PRAYER_AT_EPOCH_MS = "prayer_at_epoch_ms"
        private val ZONE: ZoneId = ZoneId.of("Europe/Istanbul")

        fun intent(
            context: Context,
            prayer: Prayer,
            prayerAtEpochMs: Long,
        ): Intent = Intent(context, PrayerAlertActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_PRAYER, prayer.name)
            .putExtra(EXTRA_PRAYER_AT_EPOCH_MS, prayerAtEpochMs)
    }
}

@Composable
private fun AlertScreen(
    prayer: String,
    time: String,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(Unit) {
        delay(30_000L)
        onDismiss()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Vakit", style = MaterialTheme.typography.title3)
            Text(prayer, style = MaterialTheme.typography.display1)
            Text(time, style = MaterialTheme.typography.display2)
            Spacer(Modifier.height(12.dp))
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Kapat") },
                onClick = onDismiss,
            )
        }
    }
}
