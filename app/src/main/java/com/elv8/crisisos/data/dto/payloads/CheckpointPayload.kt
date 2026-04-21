package com.elv8.crisisos.data.dto.payloads

import kotlinx.serialization.Serializable

@Serializable
data class CheckpointPayload(
    val checkpointName: String,
    val location: String,
    val isOpen: Boolean,
    val safetyRating: Int,
    val notes: String?
)