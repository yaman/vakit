package io.canavar.vakit.location

import io.canavar.vakit.complication.ComplicationRefresher
import io.canavar.vakit.data.SettingsRepository
import io.canavar.vakit.data.TimetableRepository
import io.canavar.vakit.domain.DistrictSelector

class LocationCheck(
    private val locationSource: LocationSource,
    private val timetable: TimetableRepository,
    private val settings: SettingsRepository,
    private val selector: DistrictSelector,
    private val refresher: ComplicationRefresher,
) {
    suspend fun run() {
        val current = settings.read()
        if (!current.autoLocation) return
        settings.setLastCheck(System.currentTimeMillis())

        val fix = locationSource.current()
        if (fix == null) {
            settings.setLocationBlocked(true)
            refresher.requestUpdate()
            return
        }

        settings.setLocationBlocked(false)
        settings.setFix(fix)

        val catalog = timetable.districts()
        val currentDistrict = current.districtId?.let { id ->
            catalog.firstOrNull { it.id == id }
        }
        val selected = selector.select(currentDistrict, fix.lat, fix.lon, catalog)
        if (selected != null && selected.id != currentDistrict?.id) {
            settings.setDistrict(selected, auto = true)
        }
        refresher.requestUpdate()
    }
}
