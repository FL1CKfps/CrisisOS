package com.elv8.crisisos.domain.model

enum class MessageStatus {
    SENDING, SENT, DELIVERED, READ, FAILED
}

enum class MessageType {
    TEXT, SOS_ALERT, SYSTEM, IMAGE_PLACEHOLDER, IMAGE, VIDEO, AUDIO
}

data class ChatMessage(
    val id: String,
    val senderId: String,
    val senderAlias: String,
    val targetId: String? = null,
    val content: String,
    val timestamp: Long,
    val deliveryStatus: MessageStatus,
    val hopsCount: Int,
    val isOwn: Boolean,
    val messageType: MessageType,
    val mediaId: String? = null,
    val mediaThumbnailUri: String? = null,
    val mediaDurationMs: Long? = null
)
