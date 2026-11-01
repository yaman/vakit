package io.canavar.vakit.complication

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import io.canavar.vakit.MainActivity
import io.canavar.vakit.VakitApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NextEntryComplicationService : SuspendingComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        withContext(Dispatchers.IO) {
            val container = (applicationContext as VakitApp).container
            val content = try {
                val settings = container.settings.read()
                val next = settings.districtId?.let { container.nextEntry(it) }
                ComplicationRenderer.render(
                    districtId = settings.districtId,
                    next = next,
                    locationBlocked = settings.locationBlocked,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                ComplicationContent(
                    title = null,
                    text = ComplicationRenderer.NO_DATA,
                    description = ComplicationRenderer.NO_DATA,
                )
            }
            val tapAction = PendingIntent.getActivity(
                this@NextEntryComplicationService,
                0,
                Intent(this@NextEntryComplicationService, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            when (request.complicationType) {
                ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                    text = text(content.text),
                    contentDescription = text(content.description),
                )
                    .setTitle(content.title?.let { text(it) })
                    .setTapAction(tapAction)
                    .build()

                ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                    text = text(content.description),
                    contentDescription = text(content.description),
                )
                    .setTitle(
                        if (content.title != null) text("Sonraki vakit") else null,
                    )
                    .setTapAction(tapAction)
                    .build()

                else -> null
            }
        }

    override fun getPreviewData(type: ComplicationType): ComplicationData? = null

    private fun text(value: String): ComplicationText =
        PlainComplicationText.Builder(value).build()
}
