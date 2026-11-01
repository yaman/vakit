package io.canavar.vakit.domain

class DistrictSelector(
    private val distance: (Double, Double, Double, Double) -> Double = ::haversineMeters,
    private val hysteresisMeters: Double = 5_000.0,
) {
    fun select(
        current: District?,
        lat: Double,
        lon: Double,
        districts: List<District>,
    ): District? {
        val nearest = districts.minWithOrNull(
            compareBy<District> { distance(lat, lon, it.lat, it.lon) }.thenBy { it.id },
        ) ?: return null
        if (current == null) return nearest
        val currentDistance = distance(lat, lon, current.lat, current.lon)
        val nearestDistance = distance(lat, lon, nearest.lat, nearest.lon)
        return if (currentDistance - nearestDistance > hysteresisMeters) nearest else current
    }
}
