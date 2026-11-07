package io.canavar.vakit.reminder

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class ReminderVibrator(private val context: Context) {
    fun vibrate(kind: ReminderKind) {
        val vibrator = vibrator() ?: return
        if (!vibrator.hasVibrator()) return
        val effect = VibrationEffect.createWaveform(ReminderPattern.forKind(kind), -1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val attributes = VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_ALARM)
                .setFlags(
                    VibrationAttributes.FLAG_BYPASS_INTERRUPTION_POLICY,
                    VibrationAttributes.FLAG_BYPASS_INTERRUPTION_POLICY,
                )
                .build()
            vibrator.vibrate(effect, attributes)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(
                effect,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
            )
        }
    }

    private fun vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
}
