package io.canavar.vakit.complication

import io.canavar.vakit.domain.NextEntry
import io.canavar.vakit.domain.TimeFormat

data class ComplicationContent(
    val title: String?,
    val locationLabel: String?,
    val text: String,
    val description: String,
    val accessibilityDescription: String,
)

object ComplicationRenderer {
    const val SETUP = "Ayarla"
    const val LOCATION_OFF = "Konum kapalı"
    const val NO_DATA = "Veri yok"

    fun render(
        districtId: Int?,
        districtName: String?,
        next: NextEntry?,
        locationBlocked: Boolean,
    ): ComplicationContent {
        if (districtId == null) {
            val label = if (locationBlocked) LOCATION_OFF else SETUP
            return ComplicationContent(
                title = null,
                locationLabel = null,
                text = label,
                description = label,
                accessibilityDescription = label,
            )
        }
        if (next == null) {
            return ComplicationContent(
                title = null,
                locationLabel = null,
                text = NO_DATA,
                description = NO_DATA,
                accessibilityDescription = NO_DATA,
            )
        }
        val name = next.prayer.displayName
        val time = TimeFormat.hhmm(next.at)
        return ComplicationContent(
            title = name,
            locationLabel = districtName,
            text = time,
            description = "$name $time",
            accessibilityDescription = "Sonraki vakit: $name $time",
        )
    }
}
