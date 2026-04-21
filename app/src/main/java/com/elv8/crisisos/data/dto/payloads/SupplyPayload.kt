package com.elv8.crisisos.data.dto.payloads

import kotlinx.serialization.Serializable

@Serializable
data class SupplyPayload(
    val supplyType: String,
    val quantity: Int,
    val location: String,
    val notes: String?
)