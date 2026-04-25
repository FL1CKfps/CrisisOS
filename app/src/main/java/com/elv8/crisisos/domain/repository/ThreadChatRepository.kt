package com.elv8.crisisos.domain.repository

import com.elv8.crisisos.domain.model.chat.ChatThread
import com.elv8.crisisos.domain.model.chat.Message
import com.elv8.crisisos.domain.model.media.MediaItem
import kotlinx.coroutines.flow.Flow

sealed class SendMessageResult {
    object Success : SendMessageResult()
    data class Error(val reason: String) : SendMessageResult()
}

interface ThreadChatRepository {
    fun getAllThreads(): Flow<List<ChatThread>>
    suspend fun getThread(threadId: String): ChatThread?
    fun getMessagesForThread(threadId: String): Flow<List<Message>>
    suspend fun sendMessage(threadId: String, content: String, replyToId: String?): SendMessageResult
    suspend fun sendMediaMessage(threadId: String, mediaItem: MediaItem, replyToId: String? = null): SendMessageResult
    suspend fun markThreadRead(threadId: String)
    suspend fun deleteThread(threadId: String)
    suspend fun pinThread(threadId: String, pinned: Boolean)
    suspend fun getOrCreateDirectThread(peerCrsId: String, peerAlias: String, avatarColor: Int): String
}
