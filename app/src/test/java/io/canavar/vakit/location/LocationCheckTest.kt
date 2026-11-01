package io.canavar.vakit.location

import io.canavar.vakit.complication.ComplicationRefresher
import io.canavar.vakit.data.LocationFix
import io.canavar.vakit.data.Settings
import io.canavar.vakit.data.SettingsRepository
import io.canavar.vakit.data.TimetableRepository
import io.canavar.vakit.domain.DayTimes
import io.canavar.vakit.domain.District
import io.canavar.vakit.domain.DistrictSelector
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LocationCheckTest {
    private val ankara = District(9206, "ANKARA", "ANKARA", 0.0, 10.0)
    private val istanbul = District(9541, "İSTANBUL", "İSTANBUL", 0.0, 100.0)

    private class FakeLocation(var fix: LocationFix?) : LocationSource {
        override suspend fun current(): LocationFix? = fix
    }

    private class FakeSettings(var current: Settings) : SettingsRepository {
        var district: District? = null
        var blocked: Boolean? = null
        var auto: Boolean? = null
        override suspend fun read(): Settings = current
        override suspend fun setDistrict(district: District, auto: Boolean) {
            this.district = district
            this.auto = auto
            current = current.copy(districtId = district.id, autoLocation = auto)
        }
        override suspend fun setFix(fix: LocationFix) {
            current = current.copy(lastFix = fix)
        }
        override suspend fun setAutoLocation(enabled: Boolean) {
            current = current.copy(autoLocation = enabled)
        }
        override suspend fun setLocationBlocked(blocked: Boolean) {
            this.blocked = blocked
            current = current.copy(locationBlocked = blocked)
        }
        override suspend fun setPermissionAsked() = Unit
    }

    private class FakeTimetable : TimetableRepository {
        override fun districts(): List<District> = emptyList()
        override fun times(districtId: Int, month: Int, day: Int): DayTimes? = null
    }

    private class FakeRefresher : ComplicationRefresher {
        var count = 0
        override fun requestUpdate() { count++ }
    }

    private fun check(
        location: LocationSource,
        settings: FakeSettings,
        refresher: FakeRefresher,
        timetable: TimetableRepository,
    ): LocationCheck = LocationCheck(
        locationSource = location,
        timetable = timetable,
        settings = settings,
        selector = DistrictSelector(
            distance = { _, lon1, _, lon2 -> kotlin.math.abs(lon2 - lon1) * 1_000.0 },
            hysteresisMeters = 5_000.0,
        ),
        refresher = refresher,
    )

    @Test
    fun `a null fix blocks location and keeps the district`() = runTest {
        val settings = FakeSettings(Settings(districtId = 9206))
        val refresher = FakeRefresher()
        check(FakeLocation(null), settings, refresher, FakeTimetable()).run()
        assertEquals(true, settings.blocked)
        assertEquals(null, settings.district)
        assertEquals(1, refresher.count)
    }

    @Test
    fun `a nearby fix keeps the current district`() = runTest {
        val settings = FakeSettings(Settings(districtId = 9206))
        val refresher = FakeRefresher()
        val timetable = object : TimetableRepository {
            override fun districts(): List<District> = listOf(ankara, istanbul)
            override fun times(districtId: Int, month: Int, day: Int): DayTimes? = null
        }
        check(FakeLocation(LocationFix(0.0, 12.0, 1L)), settings, refresher, timetable).run()
        assertEquals(false, settings.blocked)
        assertEquals(null, settings.district)
    }

    @Test
    fun `a distant fix switches the district`() = runTest {
        val settings = FakeSettings(Settings(districtId = 9206))
        val refresher = FakeRefresher()
        val timetable = object : TimetableRepository {
            override fun districts(): List<District> = listOf(ankara, istanbul)
            override fun times(districtId: Int, month: Int, day: Int): DayTimes? = null
        }
        check(FakeLocation(LocationFix(0.0, 98.0, 1L)), settings, refresher, timetable).run()
        assertEquals(9541, settings.district?.id)
        assertEquals(true, settings.auto)
        assertFalse(settings.current.locationBlocked)
    }

    @Test
    fun `manual mode skips the location call`() = runTest {
        val settings = FakeSettings(Settings(districtId = 9206, autoLocation = false))
        val refresher = FakeRefresher()
        var called = false
        val location = object : LocationSource {
            override suspend fun current(): LocationFix? {
                called = true
                return null
            }
        }
        check(location, settings, refresher, FakeTimetable()).run()
        assertFalse(called)
        assertEquals(0, refresher.count)
    }
}
