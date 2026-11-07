package io.canavar.vakit.reminder

import io.canavar.vakit.domain.Prayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrayerReminderReceiverTest {
    @Test
    fun `parses a known prayer`() {
        assertEquals(Prayer.IKINDI, parsePrayer("IKINDI"))
    }

    @Test
    fun `unknown or null names return null`() {
        assertNull(parsePrayer("NOT_A_PRAYER"))
        assertNull(parsePrayer(null))
    }
}
