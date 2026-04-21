package com.elv8.crisisos.data.repository

import com.elv8.crisisos.data.local.dao.ChatDao
import com.elv8.crisisos.data.local.dao.ChatThreadDao
import com.elv8.crisisos.data.local.dao.MessageRequestDao
import com.elv8.crisisos.data.local.entity.ChatThreadEntity
import com.elv8.crisisos.data.local.entity.MessageRequestEntity
import com.elv8.crisisos.domain.model.chat.MessageRequest
import com.elv8.crisisos.domain.model.chat.MessageRequestStatus
import com.elv8.crisisos.domain.repository.ContactRepository
import com.elv8.crisisos.domain.repository.MessageRequestRepository
import com.elv8.crisisos.domain.repository.RouteResult
import com.elv8.crisisos.core.notification.NotificationEventBus
import com.elv8.crisisos.core.notification.event.NotificationEvent
import android.util.Log
import com.elv8.crisisos.core.debug.MeshLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRequestRepositoryImpl @Inject constructor(
    private val messageRequestDao: MessageRequestDao,
    private val chatThreadDao: ChatThreadDao,
    private val chatDao: ChatDao,
    private val contactRepository: ContactRepository,
    private val notificationBus: NotificationEventBus,
    private val scope: CoroutineScope
) : MessageRequestRepository {

    override suspend fun routeIncomingMessage(
        fromCrsId: String,
        fromAlias: String,
        fromAvatarColor: Int,
        previewText: String,
        fullMessageJson: String
    ): RouteResult = withContext(Dispatchers.IO) {
        if (com.elv8.crisisos.core.debug.MeshDebugConfig.isMockCrsId(fromCrsId)) {
            return@withContext RouteResult.Blocked
        }

        // Check if blocked
        val contact = contactRepository.getContact(fromCrsId)
        if (contact?.isBlocked == true) {
            return@withContext RouteResult.Blocked
        }

        // Check if thread exists
        val existingThread = chatThreadDao.getDirectThread(fromCrsId)
        if (existingThread != null) {
            return@withContext RouteResult.RoutedToThread(existingThread.threadId)
        }

        // Check if contact (but no thread yet)
        if (contact != null) {
            val newThreadId = UUID.randomUUID().toString()
            chatThreadDao.insert(
                ChatThreadEntity(
                    threadId = newThreadId,
                    type = "DIRECT",
                    peerCrsId = fromCrsId,
                    groupId = null,
                    displayName = fromAlias,
                    avatarColor = fromAvatarColor,
                    lastMessagePreview = previewText,
                    lastMessageAt = System.currentTimeMillis(),
                    unreadCount = 0,
                    isMock = false,
                    createdAt = System.currentTimeMillis(),
                    connectionRequestId = ""
                )
            )
            return@withContext RouteResult.RoutedToThread(newThreadId)
        }

        // Unknown contact without thread, queue as pending request
        val requestId = UUID.randomUUID().toString()
        val requestEntity = MessageRequestEntity(
            requestId = requestId,
            fromCrsId = fromCrsId,
            fromAlias = fromAlias,
            fromAvatarColor = fromAvatarColor,
            previewText = previewText,
            fullMessageJson = fullMessageJson,
            sentAt = System.currentTimeMillis(),
                    status = MessageRequestStatus.PENDING.name,
                threadId = null
            )
            messageRequestDao.insert(requestEntity)
            
            scope.launch {
                notificationBus.emitRequest(
                    NotificationEvent.Request.MessageRequestReceived(
                        requestId = requestEntity.requestId,
                        fromCrsId = fromCrsId,
                        fromAlias = fromAlias,
                        fromAvatarColor = requestEntity.fromAvatarColor,
                        previewText = requestEntity.previewText
                    )
                )
                Log.d("CrisisOS_MsgReqRepo", "MessageRequestReceived emitted fromCrsId=${fromCrsId}")
            }
            
            return@withContext RouteResult.QueuedAsRequest(requestId)
    }

    override fun getPendingRequests(): Flow<List<MessageRequest>> {
        return messageRequestDao.getPending().map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getPendingRequestCount(): Flow<Int> {
        return messageRequestDao.getPendingCount()
    }

    override suspend fun acceptRequest(requestId: String): MessageRequestRepository.AcceptResult = withContext(Dispatchers.IO) {
        val request = messageRequestDao.getById(requestId) ?: return@withContext MessageRequestRepository.AcceptResult.Error
        
        // When accepted, ensure a thread exists explicitly or create one
        var thread = chatThreadDao.getDirectThread(request.fromCrsId)
        val threadIdToUse: String
        if (thread == null) {
            threadIdToUse = UUID.randomUUID().toString()
            thread = ChatThreadEntity(
                threadId = threadIdToUse,
                type = "DIRECT",
                peerCrsId = request.fromCrsId,
                groupId = null,
                displayName = request.fromAlias,
                avatarColor = request.fromAvatarColor,
                lastMessagePreview = request.previewText,
                lastMessageAt = request.sentAt,
                unreadCount = 0,
                isMock = false,
                createdAt = System.currentTimeMillis(),
                connectionRequestId = ""
            )
            chatThreadDao.insert(thread)
        } else {
            threadIdToUse = thread.threadId
        }

        messageRequestDao.updateStatus(requestId, MessageRequestStatus.ACCEPTED.name, threadIdToUse)
        
        // Add to contacts
        if (!contactRepository.isContact(request.fromCrsId)) {
            contactRepository.addContact(request.fromCrsId, request.fromAlias, request.fromAvatarColor)
        }

        MessageRequestRepository.AcceptResult.Success(threadIdToUse)
    }

    override suspend fun rejectRequest(requestId: String) = withContext(Dispatchers.IO) {
        val request = messageRequestDao.getById(requestId) ?: return@withContext
        messageRequestDao.updateStatus(requestId, MessageRequestStatus.REJECTED.name, null)
    }

    override suspend fun deleteRequest(requestId: String) = withContext(Dispatchers.IO) {
        val request = messageRequestDao.getById(requestId) ?: return@withContext
        messageRequestDao.updateStatus(requestId, "DELETED", null)
    }

    override suspend fun clearExpired() = withContext<Unit>(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000) // 7 days old
        messageRequestDao.deleteExpired(cutoff)
    }

    private fun MessageRequestEntity.toDomain() = MessageRequest(
        requestId = requestId,
        fromCrsId = fromCrsId,
        fromAlias = fromAlias,
        fromAvatarColor = fromAvatarColor,
        previewText = previewText,
        fullMessageJson = fullMessageJson,
        sentAt = sentAt,
        status = try { MessageRequestStatus.valueOf(status) } catch (e: Exception) { MessageRequestStatus.PENDING },
        threadId = threadId
    )
}
