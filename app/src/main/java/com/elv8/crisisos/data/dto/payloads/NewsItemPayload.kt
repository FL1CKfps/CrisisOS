package com.elv8.crisisos.data.dto.payloads

import kotlinx.serialization.Serializable

@Serializable
data class NewsItemPayload(
    val id: String,
    val headline: String,
    val body: String,
    val category: String,
    val sourceAlias: String,
    val sourceCrsId: String,
    val isOfficial: Boolean,
    val publishedAt: Long,
    val expiresAt: Long
)
