package io.canavar.vakit.data

import android.database.sqlite.SQLiteDatabase
import io.canavar.vakit.domain.DayTimes
import io.canavar.vakit.domain.District

interface TimetableRepository {
    fun districts(): List<District>
    fun times(districtId: Int, month: Int, day: Int): DayTimes?
}

class SqliteTimetableRepository(
    private val db: SQLiteDatabase,
) : TimetableRepository {
    override fun districts(): List<District> =
        db.rawQuery(
            "SELECT id, name, province, lat, lon FROM districts ORDER BY id",
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        District(
                            id = cursor.getInt(0),
                            name = cursor.getString(1),
                            province = cursor.getString(2),
                            lat = cursor.getDouble(3),
                            lon = cursor.getDouble(4),
                        ),
                    )
                }
            }
        }

    override fun times(districtId: Int, month: Int, day: Int): DayTimes? =
        db.rawQuery(
            "SELECT imsak, gunes, ogle, ikindi, aksam, yatsi FROM times " +
                "WHERE district_id = ? AND month = ? AND day = ?",
            arrayOf(districtId.toString(), month.toString(), day.toString()),
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                null
            } else {
                DayTimes(
                    imsak = cursor.getInt(0),
                    gunes = cursor.getInt(1),
                    ogle = cursor.getInt(2),
                    ikindi = cursor.getInt(3),
                    aksam = cursor.getInt(4),
                    yatsi = cursor.getInt(5),
                )
            }
        }
}
