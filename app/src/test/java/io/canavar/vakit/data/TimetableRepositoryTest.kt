package io.canavar.vakit.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import io.canavar.vakit.domain.DayTimes
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        val file = File(context.filesDir, "test-timetable.db")
        file.delete()
        db = SQLiteDatabase.openOrCreateDatabase(file, null)
        db.execSQL(
            """
            CREATE TABLE districts (
              id INTEGER PRIMARY KEY,
              name TEXT NOT NULL,
              province TEXT NOT NULL,
              lat REAL NOT NULL,
              lon REAL NOT NULL
            ) WITHOUT ROWID
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE times (
              district_id INTEGER NOT NULL,
              month INTEGER NOT NULL,
              day INTEGER NOT NULL,
              imsak INTEGER NOT NULL,
              gunes INTEGER NOT NULL,
              ogle INTEGER NOT NULL,
              ikindi INTEGER NOT NULL,
              aksam INTEGER NOT NULL,
              yatsi INTEGER NOT NULL,
              PRIMARY KEY (district_id, month, day)
            ) WITHOUT ROWID
            """.trimIndent(),
        )
        db.execSQL("INSERT INTO districts VALUES (1, 'ANKARA', 'ANKARA', 39.9, 32.8)")
        db.execSQL("INSERT INTO districts VALUES (2, 'İSTANBUL', 'İSTANBUL', 41.0, 29.0)")
        db.execSQL("INSERT INTO times VALUES (1, 6, 15, 346, 430, 757, 927, 1074, 1154)")
        db.execSQL("INSERT INTO times VALUES (2, 6, 15, 347, 431, 757, 927, 1073, 1153)")
        repository = SqliteTimetableRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `lists both districts`() {
        assertEquals(listOf(1, 2), repository.districts().map { it.id })
    }

    @Test
    fun `returns six times for Ankara in June`() {
        assertEquals(DayTimes(346, 430, 757, 927, 1074, 1154), repository.times(1, 6, 15))
    }

    @Test
    fun `returns null for an absent day`() {
        assertNull(repository.times(1, 2, 29))
    }

    @Test
    fun `copies the bundled asset to internal storage`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        File(context.filesDir, "timetable.db").delete()
        TimetableDb(context).ensureReady()
        assertTrue(File(context.filesDir, "timetable.db").exists())
    }
}
