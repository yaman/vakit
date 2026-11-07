package io.canavar.vakit.reminder

import io.canavar.vakit.domain.DayTimes
import io.canavar.vakit.domain.Prayer
import java.time.LocalDate
import java.time.LocalDateTime

class ReminderPlanner {
    fun next(
        now: LocalDateTime,
        today: DayTimes?,
        tomorrow: DayTimes?,
    ): Reminder? {
        val todayDate = now.toLocalDate()
        val tomorrowDate = todayDate.plusDays(1)
        val candidates = buildList {
            today?.let { addAll(candidatesFor(todayDate, it)) }
            tomorrow?.let { addAll(candidatesFor(tomorrowDate, it)) }
        }
        return candidates.firstOrNull { it.at.isAfter(now) }
    }

    private fun candidatesFor(date: LocalDate, times: DayTimes): List<Reminder> =
        Prayer.entries.mapNotNull { prayer ->
            val kind = ReminderKind.forPrayer(prayer) ?: return@mapNotNull null
            val prayerAt = date.atStartOfDay().plusMinutes(times.minuteOf(prayer).toLong())
            Reminder(
                prayer = prayer,
                prayerAt = prayerAt,
                at = prayerAt.minusMinutes(ReminderTimes.LEAD_MINUTES.toLong()),
                kind = kind,
            )
        }
}
