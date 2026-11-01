package io.canavar.vakit

import android.content.Context
import io.canavar.vakit.complication.BoundaryScheduler
import io.canavar.vakit.complication.ComplicationRefresher
import io.canavar.vakit.complication.ComplicationUpdater
import io.canavar.vakit.data.DataStoreSettingsRepository
import io.canavar.vakit.data.SettingsRepository
import io.canavar.vakit.data.SqliteTimetableRepository
import io.canavar.vakit.data.TimetableDb
import io.canavar.vakit.data.TimetableRepository
import io.canavar.vakit.data.settingsDataStore
import io.canavar.vakit.domain.DistrictSelector
import io.canavar.vakit.domain.NextEntry
import io.canavar.vakit.domain.NextEntryResolver
import io.canavar.vakit.domain.Prayer
import io.canavar.vakit.domain.TimeFormat
import io.canavar.vakit.domain.canonicalMonthDay
import io.canavar.vakit.location.FusedLocationSource
import io.canavar.vakit.location.LocationCheck
import io.canavar.vakit.location.LocationScheduler
import io.canavar.vakit.ui.StatusUiState
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppContainer(private val context: Context) {
    val zone: ZoneId = ZoneId.of("Europe/Istanbul")
    val db: TimetableDb = TimetableDb(context)
    val timetable: TimetableRepository by lazy { SqliteTimetableRepository(db.open()) }
    val settings: SettingsRepository = DataStoreSettingsRepository(context.settingsDataStore)
    val resolver = NextEntryResolver()
    val selector = DistrictSelector()
    val complicationRefresher: ComplicationRefresher = ComplicationUpdater(context)
    val locationCheck: LocationCheck by lazy {
        LocationCheck(
            locationSource = FusedLocationSource(context),
            timetable = timetable,
            settings = settings,
            selector = selector,
            refresher = complicationRefresher,
        )
    }
    val boundaryScheduler = BoundaryScheduler(context, zone)

    fun start() {
        LocationScheduler.ensurePeriodic(context)
        LocationScheduler.runNow(context)
        complicationRefresher.requestUpdate()
        CoroutineScope(Dispatchers.IO).launch { scheduleNextBoundary() }
    }

    suspend fun scheduleNextBoundary() {
        val districtId = settings.read().districtId ?: return
        boundaryScheduler.schedule(nextEntry(districtId))
    }

    suspend fun loadStatus(
        hasFinePermission: Boolean,
        hasBackgroundPermission: Boolean,
    ): StatusUiState = withContext(Dispatchers.IO) {
        val current = settings.read()
        val districtId = current.districtId
        val today = LocalDate.now(zone)
        val monthDay = canonicalMonthDay(today)
        val times = districtId?.let {
            timetable.times(it, monthDay.monthValue, monthDay.dayOfMonth)
        }
        StatusUiState(
            districtName = current.districtName,
            province = current.districtProvince,
            next = districtId?.let { nextEntry(it) },
            today = times?.let { value ->
                Prayer.entries.map { prayer ->
                    prayer to TimeFormat.hhmm(value.minuteOf(prayer))
                }
            } ?: emptyList(),
            lastFixAt = current.lastFix?.atEpochMs,
            locationBlocked = current.locationBlocked,
            autoLocation = current.autoLocation,
            hasFinePermission = hasFinePermission,
            hasBackgroundPermission = hasBackgroundPermission,
        )
    }

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
