package com.elv8.crisisos.data.dto.payloads

import kotlinx.serialization.Serializable

@Serializable
data class SupplyAckPayload(
    val requestId: String,
    val ngoId: String,
    val eta: String,
    val meetingPoint: String? = null
)
