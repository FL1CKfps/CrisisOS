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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import android.util.Log
import com.elv8.crisisos.core.debug.MeshLogger
import android.util.Base64
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import com.elv8.crisisos.core.event.AppEvent
import com.elv8.crisisos.core.event.EventBus
import com.elv8.crisisos.data.dto.PacketFactory
import com.elv8.crisisos.data.dto.payloads.MediaAnnouncePayload
import com.elv8.crisisos.data.local.dao.MediaDao
import com.elv8.crisisos.domain.model.media.MediaItem
import com.elv8.crisisos.domain.model.media.MediaStatus
import com.elv8.crisisos.domain.model.media.MediaType
import com.elv8.crisisos.data.mesh.MeshMessenger
import com.elv8.crisisos.data.mesh.MeshConnectionManager
import com.elv8.crisisos.data.mesh.SendResult

@Singleton
class ThreadChatRepositoryImpl @Inject constructor(
    private val chatDao: ChatDao,
    private val chatThreadDao: ChatThreadDao,
    private val mediaDao: MediaDao,
    private val identityRepository: IdentityRepository,
    private val notificationBus: NotificationEventBus,
    private val eventBus: EventBus,
    private val messenger: com.elv8.crisisos.data.mesh.MeshMessenger,
    private val connectionManager: MeshConnectionManager,
    private val scope: CoroutineScope
) : ThreadChatRepository {

    init {
        eventBus.observe(scope, AppEvent.MeshEvent.MessageReceived::class) { event ->
            scope.launch(Dispatchers.IO) {
                try {
                    val payload = com.elv8.crisisos.data.dto.MeshJson.decodeFromString(
                        com.elv8.crisisos.data.dto.payloads.ChatPayload.serializer(),
                        event.packet.payload
                    )
                    
                    // Determine thread details: First check if targetId matches a group, else check direct thread for sender.
                    var thread = event.packet.targetId?.let { chatThreadDao.getGroupThread(it) }
                    if (thread == null) {
                        thread = chatThreadDao.getDirectThread(event.packet.senderId)
                    }

                    if (thread != null) {
                        val messageEntity = ChatMessageEntity(
                            id = payload.messageId,
                            threadId = thread.threadId,
                            fromCrsId = event.packet.senderId,
                            fromAlias = event.packet.senderAlias,
                            content = payload.content,
                            timestamp = event.packet.timestamp,
                            deliveryStatus = MessageStatus.DELIVERED,
                            isOwn = false,
                            messageType = MessageType.TEXT,
                            replyToMessageId = payload.replyToMessageId,
                            hopsCount = event.packet.hopCount,
                            senderId = event.packet.senderId,
                            senderAlias = event.packet.senderAlias
                        )
                        chatDao.insertMessage(messageEntity)
                        chatThreadDao.updateLastMessage(thread.threadId, payload.content.take(60), event.packet.timestamp)
                        chatThreadDao.incrementUnread(thread.threadId)

                        if (com.elv8.crisisos.core.debug.MeshDebugConfig.isMockCrsId(event.packet.senderId)) {
                            Log.d("CrisisOS_Chat", "Suppressing notification for mock message")
                            return@launch
                        }

                        notificationBus.emitChat(
                             NotificationEvent.Chat.MessageReceived(
                                threadId = thread.threadId,
                                fromCrsId = event.packet.senderId,
                                fromAlias = event.packet.senderAlias,
                                avatarColor = thread.avatarColor,
                                messagePreview = payload.content.take(80),
                                messageId = payload.messageId,
                                timestamp = event.packet.timestamp,
                                isGroupChat = thread.type == ThreadType.GROUP.name,
                                groupName = thread.displayName.takeIf { thread.type == ThreadType.GROUP.name }
                            )
                        )
                        Log.d("CrisisOS_ChatRepo", "NotificationEvent.Chat.MessageReceived emitted for thread=${thread.threadId}")
                    }
                } catch (e: Exception) {
                    Log.e("CrisisOS_ChatRepo", "Failed to parse incoming chat message for notification", e)
                }
            }
        }
        
        // --- Mock "vishal" chat setup ---
        scope.launch(Dispatchers.IO) {
            val existing = chatThreadDao.getById("mock_vishal_thread")
            if (existing == null) {
                val mockThread = ChatThreadEntity(
                    threadId = "mock_vishal_thread",
                    type = ThreadType.DIRECT.name,
                    peerCrsId = "mock_vishal_id",
                    groupId = null,
                    displayName = "vishal",
                    avatarColor = android.graphics.Color.BLUE,
                    lastMessagePreview = "Hi, I'm a mock chat!",
                    lastMessageAt = System.currentTimeMillis(),
                    isMock = true,
                    createdAt = System.currentTimeMillis(),
                    connectionRequestId = "mock_conn_id"
                )
                chatThreadDao.insert(mockThread)
                Log.d("CrisisOS_ChatRepo", "Mock vishal chat thread created.")
            }
        }

        // --- File transfer completion observer ---
        eventBus.observe(scope, AppEvent.MeshEvent.FileSendCompleted::class) { event ->
            scope.launch(Dispatchers.IO) {
                mediaDao.updateStatus(event.fileId, MediaStatus.SENT.name)
                val media = mediaDao.getById(event.fileId) ?: return@launch
                media.messageId?.let { msgId ->
                    chatDao.updateMessageStatus(msgId, MessageStatus.SENT.name)
                }
                Log.i("CrisisOS_ChatRepo", "File send confirmed: ${event.fileId}")
            }
        }

        eventBus.observe(scope, AppEvent.MeshEvent.FileSendFailed::class) { event ->
            scope.launch(Dispatchers.IO) {
                mediaDao.updateStatus(event.fileId, MediaStatus.FAILED.name)
                val media = mediaDao.getById(event.fileId) ?: return@launch
                media.messageId?.let { msgId ->
                    chatDao.updateMessageStatus(msgId, MessageStatus.FAILED.name)
                }
                Log.e("CrisisOS_ChatRepo", "File send failed: ${event.fileId}")
            }
        }
    }

    override fun getAllThreads(): Flow<List<ChatThread>> {
        return chatThreadDao.getAllThreads().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getThread(threadId: String): ChatThread? = withContext(Dispatchers.IO) {
        chatThreadDao.getById(threadId)?.toDomain()
    }

    override fun getMessagesForThread(threadId: String): Flow<List<Message>> {
        return chatDao.getMessagesForThread(threadId).map { entities ->
            entities.map { it.toDomain() }
        }
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
            val payload = com.elv8.crisisos.data.dto.payloads.ChatPayload(
                content = content,
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
                is com.elv8.crisisos.data.mesh.SendResult.Sent -> chatDao.updateMessageStatus(messageId, MessageStatus.SENT.name)
                is com.elv8.crisisos.data.mesh.SendResult.Queued -> chatDao.updateMessageStatus(messageId, MessageStatus.SENDING.name)
                is com.elv8.crisisos.data.mesh.SendResult.Failed -> chatDao.updateMessageStatus(messageId, MessageStatus.FAILED.name)
            }
        } else {
            chatDao.updateMessageStatus(messageId, MessageStatus.SENT.name)
        }
        
        if (threadId == "mock_vishal_thread") {
            scope.launch(Dispatchers.IO) {
                kotlinx.coroutines.delay(1500)
                
                val replyId = java.util.UUID.randomUUID().toString()
                val replyNow = System.currentTimeMillis()
                
                val replyEntity = ChatMessageEntity(
                    id = replyId,
                    threadId = "mock_vishal_thread",
                    fromCrsId = "mock_vishal_id",
                    fromAlias = "vishal",
                    content = "Mock reply to: \"${content}\"",
                    timestamp = replyNow,
                    deliveryStatus = com.elv8.crisisos.domain.model.MessageStatus.DELIVERED,
                    isOwn = false,
                    messageType = com.elv8.crisisos.domain.model.MessageType.TEXT,
                    replyToMessageId = messageId,
                    hopsCount = 0,
                    senderId = "mock_vishal_id",
                    senderAlias = "vishal"
                )
                
                chatDao.insertMessage(replyEntity)
                chatThreadDao.updateLastMessage(threadId, replyEntity.content.take(60), replyNow)
                
                // Trigger notification directly because the mesh won't do it for mock
                notificationBus.emitChat(
                    NotificationEvent.Chat.MessageReceived(
                        threadId = "mock_vishal_thread",
                        fromCrsId = "mock_vishal_id",
                        fromAlias = "vishal",
                        avatarColor = android.graphics.Color.BLUE,
                        messagePreview = replyEntity.content.take(80),      
                        messageId = replyId,
                        timestamp = replyNow,
                        isGroupChat = false,
                        groupName = null
                    )
                )
            }
        }
        SendMessageResult.Success
    }

    override suspend fun sendMediaMessage(
        threadId: String,
        mediaItem: MediaItem,
        replyToId: String?
    ): SendMessageResult = withContext(Dispatchers.IO) {
        Log.e("MEDIA_DEBUG", "=== sendMediaMessage CALLED === threadId=$threadId mediaId=${mediaItem.mediaId} localUri=${mediaItem.localUri}")
        Log.d("CrisisOS_SendMedia", "Step 1: Starting sendMediaMessage threadId=$threadId mediaId=${mediaItem.mediaId}")

        val identity = identityRepository.getIdentity().firstOrNull() ?: return@withContext SendMessageResult.Error("No identity")
        val messageId = UUID.randomUUID().toString()

        val contentPreview = when (mediaItem.type) {
            MediaType.IMAGE -> "📷 Photo"
            MediaType.VIDEO -> "🎬 Video"
            MediaType.AUDIO -> "🎤 Voice message (${formatDuration(mediaItem.durationMs ?: 0)})"
        }

        val messageType = when (mediaItem.type) {
            MediaType.IMAGE -> MessageType.IMAGE
            MediaType.VIDEO -> MessageType.VIDEO
            MediaType.AUDIO -> MessageType.AUDIO
        }

        Log.d("CrisisOS_SendMedia", "Step 2: Message entity being inserted messageId=$messageId type=$messageType")

        val messageEntity = ChatMessageEntity(
            id = messageId,
            threadId = threadId,
            fromCrsId = identity.crsId,
            fromAlias = identity.alias,
            content = contentPreview,
            timestamp = System.currentTimeMillis(),
            deliveryStatus = MessageStatus.SENDING,
            isOwn = true,
            messageType = messageType,
            replyToMessageId = replyToId,
            hopsCount = 0,
            senderId = identity.crsId,
            senderAlias = identity.alias,
            mediaId = mediaItem.mediaId,
            mediaThumbnailUri = mediaItem.thumbnailUri ?: mediaItem.localUri,
            mediaDurationMs = mediaItem.durationMs
        )

        val thread = chatThreadDao.getById(threadId)
        if (thread?.peerCrsId != null && com.elv8.crisisos.core.debug.MeshDebugConfig.isMockCrsId(thread.peerCrsId)) {
            Log.d("CrisisOS_Chat", "Mock thread detected — skipping real send pipeline")
            chatDao.insertMessage(messageEntity.copy(deliveryStatus = MessageStatus.SENT))
            mediaDao.updateStatus(mediaItem.mediaId, MediaStatus.SENT.name)
            chatThreadDao.updateLastMessage(threadId, contentPreview, System.currentTimeMillis())
            return@withContext SendMessageResult.Success
        }

        chatDao.insertMessage(messageEntity)

        Log.d("CrisisOS_SendMedia", "Step 3: Message inserted to Room")

        mediaDao.updateStatus(mediaItem.mediaId, MediaStatus.SENDING.name)
        chatThreadDao.updateLastMessage(threadId, contentPreview, System.currentTimeMillis())

        Log.d("CrisisOS_SendMedia", "Step 4: MediaStatus=SENDING, thread updated")

        // --- Resolve local file and target endpoint ---
        val localFile = mediaItem.localUri?.let { uri ->
            val path = uri.removePrefix("file://")
            File(path)
        }
        Log.e("MEDIA_DEBUG", "FILE CHECK: path=${localFile?.absolutePath} exists=${localFile?.exists()} size=${localFile?.length()}")

        val targetEndpointId = connectionManager.connectedPeers.value.entries
            .find { (_, peer) -> 
                peer.crsId == thread?.peerCrsId || peer.alias == thread?.peerCrsId 
            }?.key
        
        Log.e("MEDIA_DEBUG", "ENDPOINT CHECK: targetEndpointId=$targetEndpointId connectedPeers=${connectionManager.connectedPeers.value.keys}")
        Log.d("CrisisOS_SendMedia", "Step 5: resolved targetEndpointId=$targetEndpointId for peerCrsId=${thread?.peerCrsId}")

        // --- Create file payload to get payloadId BEFORE building announce ---
        var filePayloadId = 0L
        if (localFile != null && localFile.exists() && targetEndpointId != null) {
            try {
                val pfd = android.os.ParcelFileDescriptor.open(localFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                Log.e("MEDIA_DEBUG", "PFD OPENED: pfd=$pfd")
                val filePayload = com.google.android.gms.nearby.connection.Payload.fromFile(pfd)
                Log.e("MEDIA_DEBUG", "FILE PAYLOAD CREATED: payloadId=${filePayload.id}")
                filePayloadId = filePayload.id

                // Send file payload first — it starts transferring while announce is built
                Log.i("CrisisOS_SendMedia", "Step 5a: Sending file: ${localFile.name} size=${localFile.length()} payloadId=$filePayloadId to endpoint=$targetEndpointId")
                connectionManager.sendFilePayload(targetEndpointId, filePayload, mediaItem.mediaId)
                Log.e("MEDIA_DEBUG", "sendFilePayload RETURNED: payloadId=$filePayloadId")
                Log.d("CrisisOS_SendMedia", "Step 5b: File payload dispatched successfully")
            } catch (e: Exception) {
                Log.e("CrisisOS_SendMedia", "Step 5-ERR: File send failed: ${e.message}", e)
                // DO NOT delete the message — keep it visible with FAILED status
                mediaDao.updateStatus(mediaItem.mediaId, MediaStatus.FAILED.name)
                chatDao.updateMessageStatus(messageId, MessageStatus.FAILED.name)
            }
        } else {
            if (localFile == null || !localFile.exists()) {
                Log.e("CrisisOS_SendMedia", "Step 5-ERR: file not found at ${mediaItem.localUri}")
            }
            if (targetEndpointId == null) {
                Log.e("CrisisOS_SendMedia", "Step 5-WARN: No connected endpoint for thread \"${thread?.displayName}\" — file not sent yet, queuing announce")
            }
        }

        // --- Build and send announce packet WITH filePayloadId ---
        var encodedThumb: String? = null
        if (mediaItem.thumbnailUri != null) {
            try {
                val file = File(mediaItem.thumbnailUri.removePrefix("file://"))
                val bytes = file.readBytes()
                if (bytes.size <= 8 * 1024) {
                    encodedThumb = Base64.encodeToString(bytes, Base64.DEFAULT)
                }
            } catch (e: Exception) {
                // Ignore thumbnail encoding errors
            }
        }

        val payload = MediaAnnouncePayload(
            mediaId = mediaItem.mediaId,
            messageId = messageId,
            mediaType = mediaItem.type.name,
            mimeType = mediaItem.mimeType,
            fileSizeBytes = mediaItem.fileSizeBytes,
            compressedSizeBytes = mediaItem.compressedSizeBytes,
            thumbnailBase64 = encodedThumb,
            durationMs = mediaItem.durationMs,
            chunkCount = 0,
            fileName = mediaItem.fileName,
            filePayloadId = filePayloadId,
            threadId = threadId
        )

        val targetId = thread?.peerCrsId
        Log.d("CrisisOS_SendMedia", "Step 5c: targetId resolved to $targetId")
        val packet = PacketFactory.buildMediaAnnouncePacket(
            identity.crsId, identity.alias, payload, targetId
        )

        val sendResult = messenger.send(packet)
        Log.e("MEDIA_DEBUG", "ANNOUNCE SENT: filePayloadId=$filePayloadId")

        // FIX: Handle all three SendResult cases correctly.
        // Queued means the packet is saved for later delivery — NOT a failure.
        // The message should remain visible with SENDING status until FileSendCompleted fires.
        val finalStatus = when (sendResult) {
            is SendResult.Sent -> MessageStatus.SENDING  // Keep SENDING until FileSendCompleted
            is SendResult.Queued -> MessageStatus.SENDING // Queued is NOT failed — packet is in outbox
            is SendResult.Failed -> MessageStatus.FAILED
        }

        Log.d("CrisisOS_SendMedia", "Step 6: sendResult=${sendResult::class.simpleName} finalStatus=$finalStatus")

        chatDao.updateMessageStatus(messageId, finalStatus.name)
        mediaDao.updateStatus(mediaItem.mediaId,
            if (sendResult is SendResult.Failed) MediaStatus.FAILED.name else MediaStatus.SENDING.name
        )

        when (sendResult) {
            is SendResult.Sent -> SendMessageResult.Success
            is SendResult.Queued -> SendMessageResult.Success  // Queued is success from user perspective
            is SendResult.Failed -> SendMessageResult.Error("Send failed: ${sendResult.reason}")
        }
    }

    private fun formatDuration(ms: Long): String {
        val seconds = ms / 1000
        return "%d:%02d".format(seconds / 60, seconds % 60)
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
        connectionRequestId = connectionRequestId
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