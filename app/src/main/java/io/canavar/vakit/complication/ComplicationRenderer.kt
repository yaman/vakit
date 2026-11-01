package io.canavar.vakit.complication

import io.canavar.vakit.domain.NextEntry
import io.canavar.vakit.domain.TimeFormat

data class ComplicationContent(
    val title: String?,
    val text: String,
    val description: String,
)

object ComplicationRenderer {
    const val SETUP = "Ayarla"
    const val LOCATION_OFF = "Konum kapalı"
    const val NO_DATA = "Veri yok"

    fun render(
        districtId: Int?,
        next: NextEntry?,
        locationBlocked: Boolean,
    ): ComplicationContent {
        if (districtId == null) {
            val label = if (locationBlocked) LOCATION_OFF else SETUP
            return ComplicationContent(title = null, text = label, description = label)
        }
        if (next == null) {
            return ComplicationContent(title = null, text = NO_DATA, description = NO_DATA)
        }
        val name = next.prayer.displayName
        val time = TimeFormat.hhmm(next.at)
        return ComplicationContent(title = name, text = time, description = "$name $time")
    }
}
