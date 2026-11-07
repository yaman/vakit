package io.canavar.vakit.reminder

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderPatternTest {
    @Test
    fun `standard is two groups of three longer pulses`() {
        assertArrayEquals(
            longArrayOf(
                0,
                500, 300, 500, 300, 500,
                1_000,
                500, 300, 500, 300, 500,
            ),
            ReminderPattern.forKind(ReminderKind.STANDARD),
        )
    }

    @Test
    fun `gunes is three groups of three then ten`() {
        assertArrayEquals(
            longArrayOf(
                0,
                250, 150, 250, 150, 250,
                300,
                250, 150, 250, 150, 250,
                300,
                250, 150, 250, 150, 250,
                300,
                300, 250, 300, 250, 300, 250, 300, 250, 300, 250,
                300, 250, 300, 250, 300, 250, 300, 250, 300,
            ),
            ReminderPattern.forKind(ReminderKind.GUNES),
        )
    }

    @Test
    fun `duration is the sum of the waveform`() {
        assertEquals(5_200L, ReminderPattern.durationMillis(ReminderKind.STANDARD))
        assertEquals(9_300L, ReminderPattern.durationMillis(ReminderKind.GUNES))
    }

    @Test
    fun `amplitudes drive every pulse at maximum`() {
        assertArrayEquals(
            intArrayOf(0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255),
            ReminderPattern.amplitudesForKind(ReminderKind.STANDARD),
        )

        val gunes = ReminderPattern.amplitudesForKind(ReminderKind.GUNES)
        assertEquals(38, gunes.size)
        assertEquals(0, gunes[0])
        assertEquals(255, gunes[1])
        assertEquals(0, gunes[6])
        assertEquals(255, gunes[7])
        assertEquals(0, gunes[18])
        assertEquals(255, gunes[19])
        assertEquals(255, gunes[37])
        assertEquals(19, gunes.count { it == 255 })
    }
}
