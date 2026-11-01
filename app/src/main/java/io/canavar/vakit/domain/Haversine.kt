package io.canavar.vakit.domain

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadius = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return 2 * earthRadius * asin(sqrt(a))
}
