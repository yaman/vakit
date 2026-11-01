package io.canavar.vakit

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppContainerTest {
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        File(context.filesDir, "timetable.db").delete()
        File(context.filesDir, "timetable.version").delete()
        File(context.filesDir, "timetable.db.tmp").delete()
        container = AppContainer(context)
    }

    @Test
    fun `resolves the next entry from the fixture database`() = runTest {
        val next = container.nextEntryAt(
            districtId = 9206,
            now = LocalDateTime.of(2026, 6, 15, 13, 0),
        )
        assertNotNull(next)
        assertEquals("İkindi", next?.prayer?.displayName)
    }

    @Test
    fun `uses the Istanbul zone`() {
        assertEquals(ZoneId.of("Europe/Istanbul"), container.zone)
    }

    @Test
    fun `copies the database before the first query`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        container.timetable.districts()
        assertEquals(true, File(context.filesDir, "timetable.db").exists())
    }
}
