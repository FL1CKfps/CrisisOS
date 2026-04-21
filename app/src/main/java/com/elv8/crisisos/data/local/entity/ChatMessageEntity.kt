package com.elv8.crisisos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.elv8.crisisos.domain.model.MessageStatus
import com.elv8.crisisos.domain.model.MessageType

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val senderId: String,
    val senderAlias: String,
    val content: String,
    val timestamp: Long,
    val deliveryStatus: MessageStatus,
    val hopsCount: Int,
    val isOwn: Boolean,
    val messageType: MessageType,
    val threadId: String = "",
    val replyToMessageId: String? = null,
    val fromCrsId: String = "",
    val fromAlias: String = "",
    val mediaId: String? = null,
    val mediaThumbnailUri: String? = null,
    val mediaDurationMs: Long? = null
)