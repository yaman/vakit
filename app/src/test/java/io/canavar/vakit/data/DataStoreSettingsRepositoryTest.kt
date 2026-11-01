package io.canavar.vakit.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.canavar.vakit.domain.District
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DataStoreSettingsRepositoryTest {
    private lateinit var scope: CoroutineScope
    private lateinit var repository: DataStoreSettingsRepository
    private lateinit var file: File

    @Before
    fun setUp() {
        file = File.createTempFile("settings", ".preferences_pb")
        file.delete()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        repository = DataStoreSettingsRepository(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
        file.delete()
    }

    @Test
    fun `defaults to automatic location with no district`() = runTest {
        val settings = repository.read()
        assertEquals(null, settings.districtId)
        assertTrue(settings.autoLocation)
        assertFalse(settings.locationBlocked)
    }

    @Test
    fun `stores a manual district and disables automatic location`() = runTest {
        repository.setDistrict(
            District(9206, "ANKARA", "ANKARA", 39.9334, 32.8597),
            auto = false,
        )
        val settings = repository.read()
        assertEquals(9206, settings.districtId)
        assertEquals("ANKARA", settings.districtName)
        assertFalse(settings.autoLocation)
    }

    @Test
    fun `stores a fix and a blocked flag`() = runTest {
        repository.setFix(LocationFix(39.9, 32.8, 123L))
        repository.setLocationBlocked(true)
        val settings = repository.read()
        assertEquals(123L, settings.lastFix?.atEpochMs)
        assertTrue(settings.locationBlocked)
    }

    @Test
    fun `stores the last check time`() = runTest {
        repository.setLastCheck(456L)
        assertEquals(456L, repository.read().lastCheckAt)
    }
}
