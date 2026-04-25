package com.elv8.crisisos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.elv8.crisisos.domain.model.SafeZone
import com.elv8.crisisos.domain.model.SafeZoneType

/**
 * Persistent record of a safe zone (camp / hospital / supply / evac / safe house) for
 * the device's regional shard. Capacity is updated by NGO staff over the mesh.
 */
@Entity(tableName = "safe_zones")
data class SafeZoneEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val latitude: Double,
    val longitude: Double,
    val capacity: Int?,
    val currentOccupancy: Int?,
    val isOperational: Boolean,
    val operatedBy: String,
    val lastUpdated: Long
) {
    fun toDomain(): SafeZone = SafeZone(
        id = id,
        name = name,
        type = runCatching { SafeZoneType.valueOf(type) }.getOrDefault(SafeZoneType.CAMP),
        distance = "—",
        capacity = capacity,
        currentOccupancy = currentOccupancy,
        isOperational = isOperational,
        coordinates = latitude to longitude,
        lastVerified = formatRelative(lastUpdated),
        operatedBy = operatedBy
    )

    companion object {
        fun fromDomain(zone: SafeZone, lastUpdated: Long = System.currentTimeMillis()): SafeZoneEntity =
            SafeZoneEntity(
                id = zone.id,
                name = zone.name,
                type = zone.type.name,
                latitude = zone.coordinates.first,
                longitude = zone.coordinates.second,
                capacity = zone.capacity,
                currentOccupancy = zone.currentOccupancy,
                isOperational = zone.isOperational,
                operatedBy = zone.operatedBy,
                lastUpdated = lastUpdated
            )

        private fun formatRelative(ts: Long): String {
            val mins = (System.currentTimeMillis() - ts) / 60_000L
            return when {
                mins < 1   -> "Just now"
                mins < 60  -> "$mins min ago"
                mins < 1440 -> "${mins / 60} hr ago"
                else -> "${mins / 1440} d ago"
            }
        }
    }
}
