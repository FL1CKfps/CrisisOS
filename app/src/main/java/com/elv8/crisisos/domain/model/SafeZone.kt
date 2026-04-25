package com.elv8.crisisos.domain.model

enum class SafeZoneType {
    CAMP,
    HOSPITAL,
    WATER_POINT,
    FOOD_DISTRIBUTION,
    EVACUATION_POINT,
    SAFE_HOUSE
}

/**
 * Operational status used to color-code map pins, per CrisisOS spec:
 *  - OPEN           = green ring  (camp open, < 70% capacity, or no capacity tracking)
 *  - NEAR_FULL      = orange ring (>= 70% and < 95%)
 *  - FULL_OR_CLOSED = red ring    (>= 95% capacity OR not operational)
 */
enum class ZoneStatus { OPEN, NEAR_FULL, FULL_OR_CLOSED }

data class SafeZone(
    val id: String,
    val name: String,
    val type: SafeZoneType,
    /** Display string, e.g. "1.2 km away". Computed from [distanceKm] when a user location is known. */
    val distance: String,
    /** Numeric distance from user in kilometers. Null until a GPS fix is available — used for sorting. */
    val distanceKm: Double? = null,
    val capacity: Int?,
    val currentOccupancy: Int?,
    val isOperational: Boolean,
    val coordinates: Pair<Double, Double>,
    val lastVerified: String,
    val operatedBy: String
)

fun SafeZone.status(): ZoneStatus {
    if (!isOperational) return ZoneStatus.FULL_OR_CLOSED
    val cap = capacity
    val occ = currentOccupancy
    if (cap == null || occ == null || cap <= 0) return ZoneStatus.OPEN
    val ratio = occ.toFloat() / cap.toFloat()
    return when {
        ratio >= 0.95f -> ZoneStatus.FULL_OR_CLOSED
        ratio >= 0.70f -> ZoneStatus.NEAR_FULL
        else           -> ZoneStatus.OPEN
    }
}
