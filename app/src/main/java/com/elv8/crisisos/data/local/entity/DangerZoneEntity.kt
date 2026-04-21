package com.elv8.crisisos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.elv8.crisisos.domain.model.ThreatLevel

@Entity(tableName = "danger_zones")
data class DangerZoneEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val description: String,
    val threatLevel: ThreatLevel,
    val reportedBy: String,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double
)
