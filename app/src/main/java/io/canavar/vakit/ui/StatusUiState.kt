package io.canavar.vakit.ui

import io.canavar.vakit.domain.NextEntry
import io.canavar.vakit.domain.Prayer

data class StatusUiState(
    val districtName: String?,
    val province: String?,
    val next: NextEntry?,
    val today: List<Pair<Prayer, String>>,
    val lastFixAt: Long?,
    val lastCheckAt: Long?,
    val locationBlocked: Boolean,
    val autoLocation: Boolean,
    val hasLocationPermission: Boolean,
    val hasBackgroundPermission: Boolean,
)
