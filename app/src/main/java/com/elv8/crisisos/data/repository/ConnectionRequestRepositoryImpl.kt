package com.elv8.crisisos.data.repository

import com.elv8.crisisos.data.local.dao.ChatThreadDao
import com.elv8.crisisos.data.local.dao.ConnectionRequestDao
import com.elv8.crisisos.data.local.dao.ContactDao
import com.elv8.crisisos.data.local.entity.ChatThreadEntity
import com.elv8.crisisos.data.local.entity.ConnectionRequestEntity
import com.elv8.crisisos.data.local.entity.ConnectionRequestStatus
import com.elv8.crisisos.data.local.entity.ContactEntity
import com.elv8.crisisos.data.local.entity.RequestDirection
import com.elv8.crisisos.domain.model.connection.ConnectionRequest
import com.elv8.crisisos.domain.model.peer.Peer
import com.elv8.crisisos.domain.repository.AcceptResult
import com.elv8.crisisos.domain.repository.ConnectionRequestRepository
import com.elv8.crisisos.domain.repository.IdentityRepository
import com.elv8.crisisos.domain.repository.SendRequestResult
import com.elv8.crisisos.core.notification.NotificationEventBus
import com.elv8.crisisos.core.notification.event.NotificationEvent
import android.util.Log
import com.elv8.crisisos.core.debug.MeshLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionRequestRepositoryImpl @Inject constructor(
    private val connectionRequestDao: ConnectionRequestDao,
    private val chatThreadDao: ChatThreadDao,
    private val contactDao: ContactDao,
    private val identityRepository: IdentityRepository,
    private val eventBus: com.elv8.crisisos.core.event.EventBus,
    private val notificationBus: NotificationEventBus,
    private val scope: CoroutineScope
) : ConnectionRequestRepository {

    init {
        eventBus.observe(scope, com.elv8.crisisos.core.event.AppEvent.ConnectionEvent.RequestReceived::class) { event ->
            scope.launch {
                val entity = com.elv8.crisisos.data.local.entity.ConnectionRequestEntity(
                    requestId = event.requestId,
                    fromCrsId = event.fromCrsId,
                    fromAlias = event.fromAlias,
                    fromAvatarColor = event.fromAvatarColor,
                    toCrsId = identityRepository.getIdentity().firstOrNull()?.crsId ?: "UNKNOWN", // Us as the receiver
                    message = event.message,
                    sentAt = event.timestamp,
                    respondedAt = null,
                    status = "PENDING",
                    direction = "INCOMING",
                    expiresAt = event.timestamp + 72L * 3600000L
                )
                connectionRequestDao.insert(entity)
                
                if (com.elv8.crisisos.core.debug.MeshDebugConfig.isMockCrsId(event.fromCrsId)) {
                    Log.d("CrisisOS_ConnRepo", "Suppressing notification for mock request")
                    return@launch
                }
                
                notificationBus.emitRequest(
                    NotificationEvent.Request.ConnectionRequestReceived(
                        requestId = entity.requestId,
                        fromCrsId = entity.fromCrsId,
                        fromAlias = entity.fromAlias,
                        fromAvatarColor = entity.fromAvatarColor,
                        introMessage = entity.message
                    )
                )
                Log.d("CrisisOS_ConnRepo", "ConnectionRequestReceived emitted fromCrsId=${entity.fromCrsId}")
            }
        }
    }

    override fun getIncomingRequests(): Flow<List<ConnectionRequest>> {
        return connectionRequestDao.getIncoming().map { list -> list.map { it.toDomain() } }
    }

    override fun getOutgoingRequests(): Flow<List<ConnectionRequest>> {
        return connectionRequestDao.getOutgoing().map { list -> list.map { it.toDomain() } }
    }

    override fun getPendingIncomingCount(): Flow<Int> {
        return connectionRequestDao.getPendingCount()
    }

override suspend fun sendRequest(toPeer: Peer, message: String): SendRequestResult = withContext(Dispatchers.IO) {
        // Fallback or read directly if you have an extension
        val localIdentity = identityRepository.getIdentity().firstOrNull() ?: return@withContext SendRequestResult.Error("No identity")

        val count = connectionRequestDao.existsBetween(localIdentity.crsId, toPeer.crsId)
        if (count > 0) return@withContext SendRequestResult.AlreadyRequested("UNKNOWN_REQ_ID")

        val sentAt = System.currentTimeMillis()
        val requestId = UUID.randomUUID().toString()

        val entity = ConnectionRequestEntity(
            requestId = requestId,
            fromCrsId = localIdentity.crsId,
            fromAlias = localIdentity.alias,
            fromAvatarColor = localIdentity.avatarColor,
            toCrsId = toPeer.crsId,
            message = message.take(120),
            sentAt = sentAt,
            respondedAt = null,
            status = "PENDING",
            direction = "OUTGOING",
            expiresAt = sentAt + 72L * 3600000L
        )

        connectionRequestDao.insert(entity)
        eventBus.emit(com.elv8.crisisos.core.event.AppEvent.ConnectionEvent.SendOutboundRequest(requestId, toPeer.crsId, message.take(120), localIdentity.avatarColor, localIdentity.alias))
        return@withContext SendRequestResult.Success
    }

    override suspend fun acceptRequest(requestId: String): AcceptResult = withContext(Dispatchers.IO) {        
        val request = connectionRequestDao.getById(requestId) ?: return@withContext AcceptResult.Error("Not found")
        val now = System.currentTimeMillis()

        connectionRequestDao.updateStatus(requestId, "ACCEPTED", now)

        contactDao.insertContact(
            ContactEntity(
                crsId = request.fromCrsId,
                alias = request.fromAlias,
                addedAt = now,
                groupId = null,
                trustLevel = "BASIC",
                notes = "",
                avatarColor = request.fromAvatarColor,
                isFavorite = false,
                isBlocked = false
            )
        )

        val threadId = UUID.randomUUID().toString()
        chatThreadDao.insert(
            ChatThreadEntity(
                threadId = threadId,
                type = "DIRECT",
                peerCrsId = request.fromCrsId,
                groupId = null,
                displayName = request.fromAlias,
                avatarColor = request.fromAvatarColor,
                lastMessagePreview = "Connection established",
                lastMessageAt = now,
                unreadCount = 0,
                isPinned = false,
                isMuted = false,
                isMock = false,
                createdAt = now,
                connectionRequestId = requestId
            )
        )

        scope.launch {
            notificationBus.emitRequest(
                NotificationEvent.Request.ConnectionRequestAccepted(
                    requestId = requestId,
                    byAlias = request.fromAlias,
                    byCrsId = request.fromCrsId,
                    newThreadId = threadId
                )
            )
            Log.d("CrisisOS_ConnRepo", "ConnectionRequestAccepted emitted requestId=$requestId")
        }

        return@withContext AcceptResult.Success(threadId)
    }

    override suspend fun rejectRequest(requestId: String) = withContext<Unit>(Dispatchers.IO) {
        connectionRequestDao.updateStatus(requestId, "REJECTED", System.currentTimeMillis())
    }

    suspend fun notifyRequestRejected(requestId: String, byAlias: String) {
        notificationBus.emitRequest(
            NotificationEvent.Request.ConnectionRequestRejected(
                requestId = requestId,
                byAlias = byAlias
            )
        )
    }

    override suspend fun cancelRequest(requestId: String) = withContext<Unit>(Dispatchers.IO) {
        connectionRequestDao.updateStatus(requestId, "CANCELLED", System.currentTimeMillis())
    }

    override suspend fun expireOldRequests() = withContext<Unit>(Dispatchers.IO) {
        connectionRequestDao.expireOld(System.currentTimeMillis())
    }

    private fun ConnectionRequestEntity.toDomain(): ConnectionRequest {
        return ConnectionRequest(
            requestId = this.requestId,
            fromCrsId = this.fromCrsId,
            fromAlias = this.fromAlias,
            fromAvatarColor = this.fromAvatarColor,
            toCrsId = this.toCrsId,
            message = this.message,
            sentAt = this.sentAt,
            respondedAt = this.respondedAt,
            status = try { ConnectionRequestStatus.valueOf(this.status) } catch (e: Exception) { ConnectionRequestStatus.PENDING },
            direction = try { RequestDirection.valueOf(this.direction) } catch (e: Exception) { RequestDirection.INCOMING },
            expiresAt = this.expiresAt
        )
    }
}

