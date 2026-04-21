package com.elv8.crisisos.domain.model

enum class SafeZoneType {
    CAMP,
    HOSPITAL,
    WATER_POINT,
    FOOD_DISTRIBUTION,
    EVACUATION_POINT,
    SAFE_HOUSE
}

data class SafeZone(
    val id: String,
    val name: String,
    val type: SafeZoneType,
    val distance: String,
    val capacity: Int?,
    val currentOccupancy: Int?,
    val isOperational: Boolean,
    val coordinates: Pair<Double, Double>,
    val lastVerified: String,
    val operatedBy: String
)