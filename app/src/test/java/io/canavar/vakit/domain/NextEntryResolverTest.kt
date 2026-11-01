package io.canavar.vakit.domain

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextEntryResolverTest {
    private val resolver = NextEntryResolver()
    private val today = DayTimes(346, 430, 757, 927, 1074, 1154)
    private val tomorrow = DayTimes(347, 431, 757, 927, 1073, 1153)

    @Test
    fun `before imsak returns imsak`() {
        val next = resolver.resolve(LocalDateTime.of(2026, 6, 15, 5, 0), today, tomorrow)
        assertEquals(Prayer.IMSAK, next?.prayer)
        assertEquals(LocalDateTime.of(2026, 6, 15, 5, 46), next?.at)
    }

    @Test
    fun `exactly at imsak returns gunes`() {
        val next = resolver.resolve(LocalDateTime.of(2026, 6, 15, 5, 46), today, tomorrow)
        assertEquals(Prayer.GUNES, next?.prayer)
        assertEquals(LocalDateTime.of(2026, 6, 15, 7, 10), next?.at)
    }

    @Test
    fun `between imsak and gunes returns gunes`() {
        val next = resolver.resolve(LocalDateTime.of(2026, 6, 15, 6, 0), today, tomorrow)
        assertEquals(Prayer.GUNES, next?.prayer)
    }

    @Test
    fun `exactly at yatsi returns tomorrow imsak`() {
        val next = resolver.resolve(LocalDateTime.of(2026, 6, 15, 19, 14), today, tomorrow)
        assertEquals(Prayer.IMSAK, next?.prayer)
        assertEquals(LocalDateTime.of(2026, 6, 16, 5, 47), next?.at)
    }

    @Test
    fun `after yatsi returns tomorrow imsak`() {
        val next = resolver.resolve(LocalDateTime.of(2026, 6, 15, 21, 0), today, tomorrow)
        assertEquals(Prayer.IMSAK, next?.prayer)
        assertEquals(LocalDateTime.of(2026, 6, 16, 5, 47), next?.at)
    }

    @Test
    fun `null today still finds tomorrow`() {
        val next = resolver.resolve(LocalDateTime.of(2026, 6, 15, 21, 0), null, tomorrow)
        assertEquals(Prayer.IMSAK, next?.prayer)
    }

    @Test
    fun `december 31 rolls into january 1`() {
        val next = resolver.resolve(
            LocalDateTime.of(2026, 12, 31, 21, 0),
            today,
            tomorrow,
        )
        assertEquals(LocalDateTime.of(2027, 1, 1, 5, 47), next?.at)
    }

    @Test
    fun `null rows return null`() {
        assertNull(resolver.resolve(LocalDateTime.of(2026, 6, 15, 12, 0), null, null))
    }
}
