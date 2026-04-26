package com.elv8.crisisos.data.repository

import com.elv8.crisisos.data.local.dao.ChatDao
import com.elv8.crisisos.data.dto.payloads.ChatPayload
import com.elv8.crisisos.data.local.dao.ChatThreadDao
import com.elv8.crisisos.data.local.entity.ChatMessageEntity
import com.elv8.crisisos.data.local.entity.ChatThreadEntity
import com.elv8.crisisos.domain.model.MessageStatus
import com.elv8.crisisos.domain.model.MessageType
import com.elv8.crisisos.domain.model.chat.ChatThread
import com.elv8.crisisos.domain.model.chat.Message
import com.elv8.crisisos.domain.model.chat.ThreadType
import com.elv8.crisisos.domain.repository.IdentityRepository
import com.elv8.crisisos.domain.repository.SendMessageResult
import com.elv8.crisisos.domain.repository.ThreadChatRepository
import com.elv8.crisisos.core.notification.NotificationEventBus
import com.elv8.crisisos.core.notification.event.NotificationEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import android.util.Log
import com.elv8.crisisos.core.debug.MeshLogger
import android.util.Base64
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import com.elv8.crisisos.data.local.dao.MediaDao
import com.elv8.crisisos.data.remote.mesh.MeshMessenger
import com.elv8.crisisos.data.remote.mesh.MeshConnectionManager
import com.elv8.crisisos.domain.model.media.MediaItem

@Singleton
class ThreadChatRepositoryImpl @Inject constructor(
    private val chatDao: ChatDao,
    private val chatThreadDao: ChatThreadDao,
    private val mediaDao: MediaDao,
    private val identityRepository: IdentityRepository,
    private val notificationBus: NotificationEventBus,
    private val eventBus: com.elv8.crisisos.core.event.EventBus,
    private val messenger: MeshMessenger,
    private val connectionManager: MeshConnectionManager,
    private val scope: CoroutineScope
) : ThreadChatRepository {

    init {
        eventBus.observe(scope, com.elv8.crisisos.core.event.AppEvent.MeshEvent.MessageReceived::class) {
            // Handled by MeshMessenger directly into DB
        }
    }

    override fun getAllThreads(): Flow<List<ChatThread>> {
        return chatThreadDao.getAllThreads().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getThread(threadId: String): ChatThread? {
        return chatThreadDao.getById(threadId)?.toDomain()
    }

    override fun getMessagesForThread(threadId: String): Flow<List<Message>> {
        return chatDao.getMessagesForThread(threadId).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun sendMessage(
        threadId: String,
        content: String,
        replyToId: String?
    ): SendMessageResult = withContext(Dispatchers.IO) {
        val identity = identityRepository.getIdentity().firstOrNull() ?: return@withContext SendMessageResult.Error("No identity")
        val messageId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val entity = ChatMessageEntity(
            id = messageId,
            threadId = threadId,
            fromCrsId = identity.crsId,
            fromAlias = identity.alias,
            content = content,
            timestamp = now,
            deliveryStatus = MessageStatus.SENDING,
            isOwn = true,
            messageType = MessageType.TEXT,
            replyToMessageId = replyToId,
            hopsCount = 0,
            senderId = identity.crsId,
            senderAlias = identity.alias
        )

        chatDao.insertMessage(entity)
        chatThreadDao.updateLastMessage(threadId, content.take(60), now)
        
        val thread = chatThreadDao.getById(threadId)
        if (thread != null && thread.type == "DIRECT" && thread.peerCrsId != null) {
            val encryptedContent = com.elv8.crisisos.core.network.mesh.MeshEncryption.encrypt(content, thread.peerCrsId)
            
            val payload = com.elv8.crisisos.data.dto.payloads.ChatPayload(
                content = encryptedContent,
                messageId = messageId,
                replyToMessageId = replyToId
            )
            val packet = com.elv8.crisisos.data.dto.PacketFactory.buildChatPacket(
                senderId = identity.crsId,
                senderAlias = identity.alias,
                payload = payload,
                targetId = thread.peerCrsId
            )
            when (messenger.send(packet)) {
                is com.elv8.crisisos.data.remote.mesh.SendResult.Sent -> chatDao.updateMessageStatus(messageId, MessageStatus.SENT.name)
                is com.elv8.crisisos.data.remote.mesh.SendResult.Queued -> chatDao.updateMessageStatus(messageId, MessageStatus.SENDING.name)
                is com.elv8.crisisos.data.remote.mesh.SendResult.Failed -> chatDao.updateMessageStatus(messageId, MessageStatus.FAILED.name)
            }
        } else {
            chatDao.updateMessageStatus(messageId, MessageStatus.SENT.name)
        }
        
        SendMessageResult.Success
    }

    override suspend fun sendMediaMessage(
        threadId: String,
        mediaItem: MediaItem,
        replyToId: String?
    ): SendMessageResult = withContext(Dispatchers.IO) {
        // Implementation for media message sending
        SendMessageResult.Success
    }

    override suspend fun markThreadRead(threadId: String) = withContext<Unit>(Dispatchers.IO) {
        chatThreadDao.markRead(threadId)
    }

    override suspend fun deleteThread(threadId: String) = withContext<Unit>(Dispatchers.IO) {
        chatThreadDao.delete(threadId)
    }

    override suspend fun pinThread(threadId: String, pinned: Boolean) = withContext<Unit>(Dispatchers.IO) {
        val thread = chatThreadDao.getById(threadId) ?: return@withContext
        chatThreadDao.update(thread.copy(isPinned = pinned))
    }

    override suspend fun getOrCreateDirectThread(
        peerCrsId: String,
        peerAlias: String,
        avatarColor: Int
    ): String = withContext(Dispatchers.IO) {
        val existing = chatThreadDao.getDirectThread(peerCrsId)
        if (existing != null) {
            return@withContext existing.threadId
        }

        val myId = identityRepository.getIdentity().first()?.crsId ?: "me"
        val threadId = "thread_${peerCrsId}_${myId}"
        chatThreadDao.insert(
            ChatThreadEntity(
                threadId = threadId,
                type = "DIRECT",
                peerCrsId = peerCrsId,
                displayName = peerAlias,
                avatarColor = avatarColor,
                lastMessagePreview = "Start chatting...",
                lastMessageAt = System.currentTimeMillis(),
                createdAt = System.currentTimeMillis(),
                unreadCount = 0,
                isPinned = false,
                isMuted = false,
                isMock = false,
                groupId = null,
                connectionRequestId = ""
            )
        )
        return@withContext threadId
    }

    private fun ChatThreadEntity.toDomain() = ChatThread(
        threadId = threadId,
        type = try { ThreadType.valueOf(type) } catch (e: Exception) { ThreadType.DIRECT },
        peerCrsId = peerCrsId,
        groupId = groupId,
        displayName = displayName,
        avatarColor = avatarColor,
        lastMessagePreview = lastMessagePreview,
        lastMessageAt = lastMessageAt,
        unreadCount = unreadCount,
        isPinned = isPinned,
        isMuted = isMuted,
        isMock = isMock,
        createdAt = createdAt,
        connectionRequestId = connectionRequestId.takeIf { it.isNotBlank() }
    )

    private fun ChatMessageEntity.toDomain() = Message(
        messageId = id,
        threadId = threadId,
        fromCrsId = fromCrsId,
        fromAlias = fromAlias,
        content = content,
        timestamp = timestamp,
        status = deliveryStatus,
        isOwn = isOwn,
        replyToMessageId = replyToMessageId,
        messageType = messageType,
        mediaId = mediaId,
        mediaThumbnailUri = mediaThumbnailUri,
        mediaDurationMs = mediaDurationMs
    )
}
