package com.elv8.crisisos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_threads")
data class ChatThreadEntity(
    @PrimaryKey
    val threadId: String,
    val type: String,
    val peerCrsId: String?,
    val groupId: String?,
    val displayName: String,
    val avatarColor: Int,
    val lastMessagePreview: String,
    val lastMessageAt: Long,
    val unreadCount: Int = 0,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val isMock: Boolean = false,
    val createdAt: Long,
    val connectionRequestId: String?
)
