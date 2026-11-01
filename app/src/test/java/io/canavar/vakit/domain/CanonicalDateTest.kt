package io.canavar.vakit.domain

import java.time.LocalDate
import java.time.MonthDay
import org.junit.Assert.assertEquals
import org.junit.Test

class CanonicalDateTest {
    @Test
    fun `february 29 maps to february 28`() {
        assertEquals(MonthDay.of(2, 28), canonicalMonthDay(LocalDate.of(2028, 2, 29)))
    }

    @Test
    fun `ordinary dates keep month and day`() {
        assertEquals(MonthDay.of(3, 1), canonicalMonthDay(LocalDate.of(2028, 3, 1)))
        assertEquals(MonthDay.of(12, 31), canonicalMonthDay(LocalDate.of(2026, 12, 31)))
    }
}
