package com.elv8.crisisos.domain.model.chat

import com.elv8.crisisos.domain.model.MessageStatus
import com.elv8.crisisos.domain.model.MessageType

data class Message(
    val messageId: String,
    val threadId: String,
    val fromCrsId: String,
    val fromAlias: String,
    val content: String,
    val timestamp: Long,
    val status: MessageStatus,
    val isOwn: Boolean,
    val replyToMessageId: String?,
    val messageType: MessageType,
    val mediaId: String? = null,
    val mediaThumbnailUri: String? = null,
    val mediaDurationMs: Long? = null
)