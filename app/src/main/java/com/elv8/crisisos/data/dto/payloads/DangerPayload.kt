package com.elv8.crisisos.data.dto.payloads

import kotlinx.serialization.Serializable

@Serializable
data class DangerPayload(
    val title: String,
    val description: String,
    val threatLevel: String,
    val coordinates: String?
)