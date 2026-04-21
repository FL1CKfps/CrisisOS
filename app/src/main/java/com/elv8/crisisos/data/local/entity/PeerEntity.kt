package com.elv8.crisisos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "peers")
data class PeerEntity(
    // 6 requested fields from user prompt
    @PrimaryKey val endpointId: String = "",
    val crsId: String = "",
    val alias: String = "",
    val status: String = "",       // store PeerStatus.name
    val lastSeen: Long = System.currentTimeMillis(),       // System.currentTimeMillis()
    val rssi: Int = 0,
    
    // Legacy fields needed so Domain and UI do not break:
    val deviceId: String = crsId,
    val discoveredAt: Long = lastSeen,
    @ColumnInfo(name = "lastSeenAt") val lastSeenAt: Long = lastSeen,
    val signalStrength: Int = rssi,
    val distanceMeters: Float = 5.0f,
    val isNearby: Boolean = true,
    val avatarColor: Int = 0,
    val publicKey: String? = null
)
