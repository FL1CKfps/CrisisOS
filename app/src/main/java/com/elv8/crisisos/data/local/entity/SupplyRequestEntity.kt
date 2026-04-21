package com.elv8.crisisos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.elv8.crisisos.domain.model.RequestStatus
import com.elv8.crisisos.domain.model.SupplyType

@Entity(tableName = "supply_requests")
data class SupplyRequestEntity(
    @PrimaryKey val id: String,
    val requestType: SupplyType,
    val quantity: Int,
    val location: String,
    val notes: String,
    val status: RequestStatus,
    val createdAt: Long,
    val estimatedDelivery: String?,
    val assignedNgo: String?,
    val packetId: String? = null
)