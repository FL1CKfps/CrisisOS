package com.elv8.crisisos.data.dto.payloads

import kotlinx.serialization.Serializable

@Serializable
data class ConnectionResponsePayload(
    val requestId: String,
    val accepted: Boolean,
    val fromAlias: String,
    val fromAvatarColor: Int
)
