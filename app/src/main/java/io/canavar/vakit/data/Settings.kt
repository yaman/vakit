package io.canavar.vakit.data

data class LocationFix(
    val lat: Double,
    val lon: Double,
    val atEpochMs: Long,
)

data class Settings(
    val districtId: Int? = null,
    val districtName: String? = null,
    val districtProvince: String? = null,
    val autoLocation: Boolean = true,
    val lastFix: LocationFix? = null,
    val lastCheckAt: Long? = null,
    val locationBlocked: Boolean = false,
    val permissionAsked: Boolean = false,
)
