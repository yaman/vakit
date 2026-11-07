package io.canavar.vakit.reminder

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderPatternTest {
    @Test
    fun `standard is three pulses`() {
        assertArrayEquals(
            longArrayOf(0, 300, 250, 300, 250, 300),
            ReminderPattern.forKind(ReminderKind.STANDARD),
        )
    }

    @Test
    fun `gunes is three pulses paused then ten`() {
        assertArrayEquals(
            longArrayOf(
                0,
                300, 250, 300, 250, 300,
                800,
                300, 250, 300, 250, 300, 250, 300, 250, 300, 250,
                300, 250, 300, 250, 300, 250, 300, 250, 300,
            ),
            ReminderPattern.forKind(ReminderKind.GUNES),
        )
    }

    @Test
    fun `duration is the sum of the waveform`() {
        assertEquals(1_400L, ReminderPattern.durationMillis(ReminderKind.STANDARD))
        assertEquals(7_450L, ReminderPattern.durationMillis(ReminderKind.GUNES))
    }
}
