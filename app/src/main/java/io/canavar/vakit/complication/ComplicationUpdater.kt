package io.canavar.vakit.complication

import android.content.ComponentName
import android.content.Context
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester

class ComplicationUpdater(
    private val context: Context,
) : ComplicationRefresher {
    override fun requestUpdate() {
        ComplicationDataSourceUpdateRequester.create(
            context,
            ComponentName(context, NextEntryComplicationService::class.java),
        ).requestUpdateAll()
    }
}
