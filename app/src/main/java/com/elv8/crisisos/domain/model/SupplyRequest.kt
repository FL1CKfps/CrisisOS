package com.elv8.crisisos.domain.model

enum class SupplyType {
    WATER, FOOD, MEDICINE, SHELTER, BLANKET, EVACUATION, EMERGENCY, OTHER
}

enum class RequestStatus {
    QUEUED, BROADCASTING, NGO_RECEIVED, CONFIRMED, DELIVERED, EXPIRED, CANCELLED
}

data class SupplyRequest(
    val id: String,
    val requestType: SupplyType,
    val quantity: Int,
    val location: String,
    val notes: String,
    val status: RequestStatus,
    val createdAt: Long,
    val estimatedDelivery: String? = null,
    val assignedNgo: String? = null,
    val packetId: String? = null
)
