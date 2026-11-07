package io.canavar.vakit.reminder

object ReminderPattern {
    const val PULSE_MS = 300L
    const val GAP_MS = 250L
    const val GROUP_GAP_MS = 800L

    fun forKind(kind: ReminderKind): LongArray = when (kind) {
        ReminderKind.STANDARD -> waveform(pulses(3))
        ReminderKind.GUNES -> waveform(pulses(3) + GROUP_GAP_MS + pulses(10))
    }

    private fun pulses(count: Int): List<Long> = buildList {
        repeat(count) { index ->
            add(PULSE_MS)
            if (index != count - 1) add(GAP_MS)
        }
    }

    private fun waveform(body: List<Long>): LongArray = (listOf(0L) + body).toLongArray()
}
