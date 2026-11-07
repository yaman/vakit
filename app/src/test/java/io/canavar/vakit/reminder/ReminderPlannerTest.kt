package io.canavar.vakit.reminder

import io.canavar.vakit.domain.DayTimes
import io.canavar.vakit.domain.Prayer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderPlannerTest {
    private val planner = ReminderPlanner()
    private val today = DayTimes(346, 430, 757, 927, 1074, 1154)
    private val tomorrow = DayTimes(347, 431, 757, 927, 1073, 1153)
    private val date = LocalDate.of(2026, 6, 15)

    private fun at(hour: Int, minute: Int) = LocalDateTime.of(date, LocalTime.of(hour, minute))

    @Test
    fun `skips imsak and selects gunes`() {
        val next = planner.next(at(5, 0), today, tomorrow)
        assertEquals(Prayer.GUNES, next?.prayer)
        assertEquals(at(7, 10), next?.prayerAt)
        assertEquals(at(6, 55), next?.at)
        assertEquals(ReminderKind.GUNES, next?.kind)
    }

    @Test
    fun `exactly at a reminder instant selects the following reminder`() {
        val next = planner.next(at(6, 55), today, tomorrow)
        assertEquals(Prayer.OGLE, next?.prayer)
        assertEquals(at(12, 22), next?.at)
        assertEquals(ReminderKind.STANDARD, next?.kind)
    }

    @Test
    fun `between a reminder and its prayer selects the following reminder`() {
        val next = planner.next(at(7, 0), today, tomorrow)
        assertEquals(Prayer.OGLE, next?.prayer)
    }

    @Test
    fun `after yatsi selects tomorrow gunes`() {
        val next = planner.next(at(19, 0), today, tomorrow)
        assertEquals(Prayer.GUNES, next?.prayer)
        assertEquals(LocalDateTime.of(2026, 6, 16, 7, 11), next?.prayerAt)
        assertEquals(LocalDateTime.of(2026, 6, 16, 6, 56), next?.at)
    }

    @Test
    fun `null today still finds tomorrow`() {
        val next = planner.next(at(21, 0), null, tomorrow)
        assertEquals(Prayer.GUNES, next?.prayer)
        assertEquals(LocalDateTime.of(2026, 6, 16, 6, 56), next?.at)
    }

    @Test
    fun `null tomorrow still finds today`() {
        val next = planner.next(at(5, 0), today, null)
        assertEquals(Prayer.GUNES, next?.prayer)
        assertEquals(at(6, 55), next?.at)
    }

    @Test
    fun `null rows return null`() {
        assertNull(planner.next(at(5, 0), null, null))
    }

    @Test
    fun `december 31 rolls into january 1`() {
        val next = planner.next(
            LocalDateTime.of(2026, 12, 31, 21, 0),
            today,
            tomorrow,
        )
        assertEquals(Prayer.GUNES, next?.prayer)
        assertEquals(LocalDateTime.of(2027, 1, 1, 7, 11), next?.prayerAt)
        assertEquals(LocalDateTime.of(2027, 1, 1, 6, 56), next?.at)
    }

    @Test
    fun `never returns imsak`() {
        listOf(at(5, 0), at(5, 31), at(5, 45), at(21, 0)).forEach { now ->
            assertNotEquals(Prayer.IMSAK, planner.next(now, today, tomorrow)?.prayer)
        }
    }

    @Test
    fun `selects the earliest reminder even when times are out of order`() {
        val outOfOrder = DayTimes(
            imsak = 100,
            gunes = 900,
            ogle = 200,
            ikindi = 300,
            aksam = 400,
            yatsi = 500,
        )

        val next = planner.next(at(0, 0), outOfOrder, null)

        assertEquals(Prayer.OGLE, next?.prayer)
        assertEquals(at(3, 5), next?.at)
    }
}
