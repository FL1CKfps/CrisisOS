package com.elv8.crisisos.data.dto.payloads

import kotlinx.serialization.Serializable

@Serializable
data class ChatPayload(
    val content: String,
    val messageId: String,
    val replyToMessageId: String? = null
)