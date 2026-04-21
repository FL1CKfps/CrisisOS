package com.elv8.crisisos.domain.model.chat

enum class ThreadType {
    DIRECT, GROUP
}

data class ChatThread(
    val threadId: String,
    val type: ThreadType,
    val peerCrsId: String?,
    val groupId: String?,
    val displayName: String,
    val avatarColor: Int,
    val lastMessagePreview: String,
    val lastMessageAt: Long,
    val unreadCount: Int,
    val isPinned: Boolean,
    val isMuted: Boolean,
    val isMock: Boolean = false,
    val createdAt: Long,
    val connectionRequestId: String
)