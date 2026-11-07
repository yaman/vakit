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

    @Test
    fun `amplitudes drive every pulse at maximum`() {
        assertArrayEquals(
            intArrayOf(0, 255, 0, 255, 0, 255),
            ReminderPattern.amplitudesForKind(ReminderKind.STANDARD),
        )

        val gunes = ReminderPattern.amplitudesForKind(ReminderKind.GUNES)
        assertEquals(26, gunes.size)
        assertEquals(0, gunes[0])
        assertEquals(255, gunes[1])
        assertEquals(0, gunes[6])
        assertEquals(255, gunes[25])
        assertEquals(13, gunes.count { it == 255 })
    }
}
