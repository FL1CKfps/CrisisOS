package com.elv8.crisisos.data.dto.payloads

import kotlinx.serialization.Serializable

@Serializable
data class ChildAlertPayload(
    val crsChildId: String,
    val childName: String,
    val approximateAge: Int,
    val description: String,
    val lastLocation: String,
    val registeredBy: String
)