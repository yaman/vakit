package io.canavar.vakit.reminder

object ReminderPattern {
    const val PULSE_MS = 300L
    const val GAP_MS = 250L
    const val STRONG_PULSE_MS = 500L
    const val STRONG_GAP_MS = 300L
    const val REPEAT_GAP_MS = 1_000L
    const val GUNES_GROUP_PULSE_MS = 250L
    const val GUNES_GROUP_GAP_MS = 150L
    const val GUNES_GROUP_PAUSE_MS = 300L
    const val MAX_AMPLITUDE = 255

    fun forKind(kind: ReminderKind): LongArray = when (kind) {
        ReminderKind.STANDARD -> {
            val group = pulses(3, STRONG_PULSE_MS, STRONG_GAP_MS)
            waveform(group + REPEAT_GAP_MS + group)
        }

        ReminderKind.GUNES -> {
            val group = pulses(3, GUNES_GROUP_PULSE_MS, GUNES_GROUP_GAP_MS)
            waveform(
                group + GUNES_GROUP_PAUSE_MS +
                    group + GUNES_GROUP_PAUSE_MS +
                    group + GUNES_GROUP_PAUSE_MS +
                    pulses(10, PULSE_MS, GAP_MS),
            )
        }
    }

    fun durationMillis(kind: ReminderKind): Long = forKind(kind).sum()

    fun amplitudesForKind(kind: ReminderKind): IntArray =
        forKind(kind).mapIndexed { index, _ ->
            if (index == 0 || index % 2 == 0) 0 else MAX_AMPLITUDE
        }.toIntArray()

    private fun pulses(count: Int, pulseMs: Long, gapMs: Long): List<Long> = buildList {
        repeat(count) { index ->
            add(pulseMs)
            if (index != count - 1) add(gapMs)
        }
    }

    private fun waveform(body: List<Long>): LongArray = (listOf(0L) + body).toLongArray()
}
