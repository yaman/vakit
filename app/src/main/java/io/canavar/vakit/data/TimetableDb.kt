package io.canavar.vakit.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

class TimetableDb(private val context: Context) {
    private val dbFile = File(context.filesDir, "timetable.db")
    private val versionFile = File(context.filesDir, "timetable.version")

    @Synchronized
    fun ensureReady() {
        val assetVersion = context.assets
            .open("timetable.version")
            .bufferedReader()
            .use { it.readText() }
            .trim()
        val installedVersion = if (versionFile.exists()) {
            versionFile.readText().trim()
        } else {
            ""
        }
        if (installedVersion == assetVersion && dbFile.exists()) return

        val temporary = File(context.filesDir, "timetable.db.tmp")
        context.assets.open("timetable.db").use { input ->
            temporary.outputStream().use { output -> input.copyTo(output) }
        }
        if (!temporary.renameTo(dbFile)) {
            temporary.copyTo(dbFile, overwrite = true)
            temporary.delete()
        }
        versionFile.writeText(assetVersion)
    }

    fun open(): SQLiteDatabase {
        ensureReady()
        return SQLiteDatabase.openDatabase(
            dbFile.path,
            null,
            SQLiteDatabase.OPEN_READONLY,
        )
    }
}
