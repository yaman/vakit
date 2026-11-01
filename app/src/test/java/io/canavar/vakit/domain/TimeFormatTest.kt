package io.canavar.vakit.domain

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {
    @Test
    fun `formats minutes with leading zeros`() {
        assertEquals("00:00", TimeFormat.hhmm(0))
        assertEquals("05:46", TimeFormat.hhmm(346))
        assertEquals("23:59", TimeFormat.hhmm(1439))
    }

    @Test
    fun `formats a local date time`() {
        assertEquals("14:47", TimeFormat.hhmm(LocalDateTime.of(2026, 6, 15, 14, 47)))
    }
}
