package com.elv8.crisisos.domain.model.chat

enum class MessageRequestStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    DELETED
}

data class MessageRequest(
    val requestId: String,
    val fromCrsId: String,
    val fromAlias: String,
    val fromAvatarColor: Int,
    val previewText: String,
    val fullMessageJson: String,
    val sentAt: Long,
    val status: MessageRequestStatus,
    val threadId: String?
)
