package io.canavar.vakit.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import io.canavar.vakit.domain.DayTimes
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TimetableRepositoryTest {
    private lateinit var db: SQLiteDatabase
    private lateinit var repository: SqliteTimetableRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        File(context.filesDir, "timetable.db").delete()
        File(context.filesDir, "timetable.version").delete()
        File(context.filesDir, "timetable.db.tmp").delete()
        TimetableDb(context).ensureReady()
        db = SQLiteDatabase.openDatabase(
            File(context.filesDir, "timetable.db").path,
            null,
            SQLiteDatabase.OPEN_READONLY,
        )
        repository = SqliteTimetableRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `lists both fixture districts`() {
        assertEquals(listOf(9206, 9541), repository.districts().map { it.id })
    }

    @Test
    fun `returns six times for Ankara in June`() {
        val times = repository.times(9206, 6, 15)
        assertEquals(DayTimes(346, 430, 757, 927, 1074, 1154), times)
    }

    @Test
    fun `returns null for February 29 in the canonical year`() {
        assertNull(repository.times(9206, 2, 29))
    }

    @Test
    fun `copies the asset to internal storage`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.filesDir, "timetable.db")
        assertEquals(true, file.exists())
    }
}
