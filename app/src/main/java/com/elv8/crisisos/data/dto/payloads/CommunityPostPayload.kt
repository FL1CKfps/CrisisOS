package com.elv8.crisisos.data.dto.payloads

import kotlinx.serialization.Serializable

/** Anonymous community post — no CRS ID is included on the wire. */
@Serializable
data class CommunityPostPayload(
    val id: String,
    val body: String,
    val category: String,
    val pinned: Boolean,
    val createdAt: Long,
    val expiresAt: Long
)
