package com.elv8.crisisos.domain.location

data class CrisisLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: Long,
    val altitude: Double?
) {
    fun toLatLngString(): String = "%.6f,%.6f".format(latitude, longitude)
    fun toHumanReadable(): String = "%.4f°N, %.4f°E".format(latitude, longitude)
}
