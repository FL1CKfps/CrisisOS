package com.elv8.crisisos.data.dto.payloads

import kotlinx.serialization.Serializable

@Serializable
data class SosPayload(
    val sosType: String,
    val message: String,
    val locationHint: String?
)