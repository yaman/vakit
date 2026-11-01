package io.canavar.vakit.domain

data class DayTimes(
    val imsak: Int,
    val gunes: Int,
    val ogle: Int,
    val ikindi: Int,
    val aksam: Int,
    val yatsi: Int,
) {
    fun minuteOf(prayer: Prayer): Int = when (prayer) {
        Prayer.IMSAK -> imsak
        Prayer.GUNES -> gunes
        Prayer.OGLE -> ogle
        Prayer.IKINDI -> ikindi
        Prayer.AKSAM -> aksam
        Prayer.YATSI -> yatsi
    }
}
