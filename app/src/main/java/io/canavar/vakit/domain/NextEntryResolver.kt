package io.canavar.vakit.domain

import java.time.LocalDate
import java.time.LocalDateTime

class NextEntryResolver {
    fun resolve(
        now: LocalDateTime,
        today: DayTimes?,
        tomorrow: DayTimes?,
    ): NextEntry? {
        val todayDate = now.toLocalDate()
        val tomorrowDate = todayDate.plusDays(1)
        val candidates = buildList {
            if (today != null) {
                Prayer.entries.forEach { prayer ->
                    add(NextEntry(prayer, at(todayDate, today.minuteOf(prayer))))
                }
            }
            if (tomorrow != null) {
                Prayer.entries.forEach { prayer ->
                    add(NextEntry(prayer, at(tomorrowDate, tomorrow.minuteOf(prayer))))
                }
            }
        }
        return candidates.firstOrNull { it.at.isAfter(now) }
    }

    private fun at(date: LocalDate, minuteOfDay: Int): LocalDateTime =
        date.atStartOfDay().plusMinutes(minuteOfDay.toLong())
}
