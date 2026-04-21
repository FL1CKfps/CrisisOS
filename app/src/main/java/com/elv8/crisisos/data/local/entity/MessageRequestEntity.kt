package com.elv8.crisisos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "message_requests")
data class MessageRequestEntity(
    @PrimaryKey
    val requestId: String,
    val fromCrsId: String,
    val fromAlias: String,
    val fromAvatarColor: Int,
    val previewText: String,
    val fullMessageJson: String,
    val sentAt: Long,
    val status: String,
    val threadId: String?
)
