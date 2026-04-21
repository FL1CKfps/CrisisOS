package com.elv8.crisisos.data.dto.payloads

import kotlinx.serialization.Serializable

@Serializable
data class ConnectionRequestPayload(
    val requestId: String,
    val fromAlias: String,
    val fromAvatarColor: Int,
    val message: String
)

