package io.canavar.vakit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DistrictSelectorTest {
    private val selector = DistrictSelector(
        distance = { _, _, _, lon2 -> lon2 * 1_000.0 },
        hysteresisMeters = 5_000.0,
    )

    private fun district(id: Int, lon: Double) = District(id, "D$id", "P", 0.0, lon)

    @Test
    fun `empty catalog returns null`() {
        assertNull(selector.select(null, 0.0, 0.0, emptyList()))
    }

    @Test
    fun `picks the nearest district`() {
        val result = selector.select(null, 0.0, 0.0, listOf(district(1, 10.0), district(2, 2.0)))
        assertEquals(2, result?.id)
    }

    @Test
    fun `keeps current district when the gain is under five kilometres`() {
        val current = district(1, 4.0)
        val nearest = district(2, 1.0)
        assertEquals(1, selector.select(current, 0.0, 0.0, listOf(current, nearest))?.id)
    }

    @Test
    fun `keeps current district at exactly five kilometres`() {
        val current = district(1, 6.0)
        val nearest = district(2, 1.0)
        assertEquals(1, selector.select(current, 0.0, 0.0, listOf(current, nearest))?.id)
    }

    @Test
    fun `switches when the gain is over five kilometres`() {
        val current = district(1, 7.0)
        val nearest = district(2, 1.0)
        assertEquals(2, selector.select(current, 0.0, 0.0, listOf(current, nearest))?.id)
    }

    @Test
    fun `breaks distance ties on the lower id`() {
        val result = selector.select(null, 0.0, 0.0, listOf(district(9, 1.0), district(3, 1.0)))
        assertEquals(3, result?.id)
    }

    @Test
    fun `haversine returns a realistic Ankara Konya distance`() {
        val meters = haversineMeters(39.9334, 32.8597, 37.8746, 32.4921)
        assertTrue("was $meters", meters in 225_000.0..240_000.0)
    }
}
