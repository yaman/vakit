package io.canavar.vakit.complication

import io.canavar.vakit.domain.NextEntry
import io.canavar.vakit.domain.Prayer
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class BoundarySchedulerTest {
    @Test
    fun `adds five seconds to the next entry`() {
        val next = NextEntry(Prayer.IKINDI, LocalDateTime.of(2026, 6, 15, 14, 47))
        val expected = LocalDateTime.of(2026, 6, 15, 14, 47, 5)
            .atZone(ZoneId.of("Europe/Istanbul"))
            .toInstant()
            .toEpochMilli()
        assertEquals(expected, BoundaryTimes.triggerAtMillis(next, ZoneId.of("Europe/Istanbul")))
    }
}
