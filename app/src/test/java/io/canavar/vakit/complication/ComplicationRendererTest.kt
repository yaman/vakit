package io.canavar.vakit.complication

import io.canavar.vakit.domain.NextEntry
import io.canavar.vakit.domain.Prayer
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComplicationRendererTest {
    private val next = NextEntry(Prayer.IKINDI, LocalDateTime.of(2026, 6, 15, 14, 47))

    @Test
    fun `renders the prayer name as title and the clock as text`() {
        val content = ComplicationRenderer.render(9206, next, locationBlocked = false)
        assertEquals("İkindi", content.title)
        assertEquals("14:47", content.text)
        assertEquals("İkindi 14:47", content.description)
        assertEquals("Sonraki vakit: İkindi 14:47", content.accessibilityDescription)
    }

    @Test
    fun `uses the state label as the accessibility description`() {
        val content = ComplicationRenderer.render(null, null, locationBlocked = true)
        assertEquals("Konum kapalı", content.accessibilityDescription)
    }

    @Test
    fun `renders setup when no district is selected`() {
        val content = ComplicationRenderer.render(null, null, locationBlocked = false)
        assertEquals("Ayarla", content.text)
        assertNull(content.title)
    }

    @Test
    fun `renders location off when no district and location is blocked`() {
        val content = ComplicationRenderer.render(null, null, locationBlocked = true)
        assertEquals("Konum kapalı", content.text)
    }

    @Test
    fun `renders no data when a district has no row`() {
        val content = ComplicationRenderer.render(9206, null, locationBlocked = false)
        assertEquals("Veri yok", content.text)
    }

    @Test
    fun `formats single digit hours with a leading zero`() {
        val early = NextEntry(Prayer.IMSAK, LocalDateTime.of(2026, 6, 15, 5, 6))
        val content = ComplicationRenderer.render(9206, early, locationBlocked = false)
        assertEquals("05:06", content.text)
        assertEquals("İmsak", content.title)
    }
}
