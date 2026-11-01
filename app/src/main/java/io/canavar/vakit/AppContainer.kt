package io.canavar.vakit

import android.content.Context
import io.canavar.vakit.data.DataStoreSettingsRepository
import io.canavar.vakit.data.SettingsRepository
import io.canavar.vakit.data.SqliteTimetableRepository
import io.canavar.vakit.data.TimetableDb
import io.canavar.vakit.data.TimetableRepository
import io.canavar.vakit.data.settingsDataStore
import io.canavar.vakit.domain.DistrictSelector
import io.canavar.vakit.domain.NextEntry
import io.canavar.vakit.domain.NextEntryResolver
import io.canavar.vakit.domain.canonicalMonthDay
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppContainer(private val context: Context) {
    val zone: ZoneId = ZoneId.of("Europe/Istanbul")
    val db: TimetableDb = TimetableDb(context)
    val timetable: TimetableRepository by lazy { SqliteTimetableRepository(db.open()) }
    val settings: SettingsRepository = DataStoreSettingsRepository(context.settingsDataStore)
    val resolver = NextEntryResolver()
    val selector = DistrictSelector()

    suspend fun nextEntry(districtId: Int): NextEntry? =
        nextEntryAt(districtId, LocalDateTime.now(zone))

    suspend fun nextEntryAt(districtId: Int, now: LocalDateTime): NextEntry? =
        withContext(Dispatchers.IO) {
            val today = now.toLocalDate()
            val tomorrow = today.plusDays(1)
            val todayMonthDay = canonicalMonthDay(today)
            val tomorrowMonthDay = canonicalMonthDay(tomorrow)
            resolver.resolve(
                now = now,
                today = timetable.times(
                    districtId,
                    todayMonthDay.monthValue,
                    todayMonthDay.dayOfMonth,
                ),
                tomorrow = timetable.times(
                    districtId,
                    tomorrowMonthDay.monthValue,
                    tomorrowMonthDay.dayOfMonth,
                ),
            )
        }
}
