package io.canavar.vakit

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.canavar.vakit.domain.Prayer
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
    fun `resolves the next entry from the bundled database`() = runTest {
        val district = container.timetable.districts().first()
        val next = container.nextEntryAt(
            districtId = district.id,
            now = LocalDateTime.of(2026, 6, 15, 14, 0),
        )
        assertNotNull(next)
        assertEquals(Prayer.IKINDI, next?.prayer)
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
