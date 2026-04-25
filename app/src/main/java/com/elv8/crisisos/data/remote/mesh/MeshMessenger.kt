package com.elv8.crisisos.data.remote.mesh

import com.elv8.crisisos.core.network.mesh.IMeshMessenger
import android.content.Context
import com.elv8.crisisos.data.dto.PacketFactory
import com.elv8.crisisos.core.network.mesh.DomainSendResult
import com.elv8.crisisos.domain.model.ChatMessage
import com.elv8.crisisos.core.network.mesh.IMeshConnectionManager
import android.util.Log
import com.elv8.crisisos.core.debug.MeshLogger
import com.elv8.crisisos.core.event.AppEvent
import com.elv8.crisisos.core.event.EventBus
import com.elv8.crisisos.data.dto.MeshPacket
import com.elv8.crisisos.data.dto.MeshPacketType
import com.elv8.crisisos.data.dto.PacketParser
import com.elv8.crisisos.data.dto.PacketParser.decodePayload
import com.elv8.crisisos.data.dto.MeshJson
import kotlinx.serialization.encodeToString
import com.elv8.crisisos.data.dto.PacketPriority
import com.elv8.crisisos.data.dto.payloads.ChatPayload
import com.elv8.crisisos.data.dto.payloads.ChildAlertPayload
import com.elv8.crisisos.data.dto.payloads.DeadManPayload
import com.elv8.crisisos.data.dto.payloads.MediaAnnouncePayload
import com.elv8.crisisos.data.dto.payloads.MissingPersonPayload
import com.elv8.crisisos.data.dto.payloads.SosPayload
import com.elv8.crisisos.data.dto.payloads.SupplyPayload
import com.elv8.crisisos.data.dto.payloads.SupplyAckPayload
import com.elv8.crisisos.data.local.dao.MediaDao
import com.elv8.crisisos.device.media.MediaFileManager
import com.elv8.crisisos.domain.model.MessageStatus
import com.elv8.crisisos.domain.model.media.MediaStatus
import com.elv8.crisisos.domain.model.media.MediaType
import com.elv8.crisisos.domain.repository.OutboxRepository
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.Payload
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

sealed class SendResult {
    object Queued : SendResult()
    object Sent : SendResult()
    data class Failed(val reason: String) : SendResult()
}

@Singleton
class MeshMessenger @Inject constructor(
    @ApplicationContext private val context: Context,
    private val connectionManager: IMeshConnectionManager,
    private val outboxRepository: OutboxRepository,
    private val eventBus: EventBus,
    private val notificationBus: com.elv8.crisisos.core.notification.NotificationEventBus,
    private val chatDao: com.elv8.crisisos.data.local.dao.ChatDao,
    private val chatThreadDao: com.elv8.crisisos.data.local.dao.ChatThreadDao,
    private val mediaRepository: com.elv8.crisisos.domain.repository.MediaRepository,
    private val mediaDao: MediaDao,
    private val fileManager: MediaFileManager,
    private val scope: CoroutineScope
) : IMeshMessenger  {

    override suspend fun sendChatMessage(message: ChatMessage): DomainSendResult {
        val targetId = message.targetId
        val content = if (targetId != null) {
            com.elv8.crisisos.core.network.mesh.MeshEncryption.encrypt(message.content, targetId)
        } else {
            message.content
        }
        
        val chatPayload = ChatPayload(content = content, messageId = message.id)
        val packet = PacketFactory.buildChatPacket(
            senderId = message.senderId,
            senderAlias = message.senderAlias,
            payload = chatPayload,
            targetId = targetId
        )

        return when (send(packet)) {
            is SendResult.Sent -> DomainSendResult.Sent
            is SendResult.Queued -> DomainSendResult.Queued
            is SendResult.Failed -> DomainSendResult.Failed
        }
    }

    private var localDeviceId: String = ""

    private val seenPacketIds: MutableSet<String> = Collections.synchronizedSet(
        object : java.util.LinkedHashSet<String>() {
            override fun add(element: String): Boolean {
                if (size >= 500) remove(iterator().next())
                return super.add(element)
            }
        }
    )

    private val _relayCount = MutableStateFlow(0)
    val relayCount: StateFlow<Int> = _relayCount.asStateFlow()

    // File transfer matching: link announce metadata to Nearby file payload
    private val pendingAnnounces: MutableMap<String, MediaAnnouncePayload> = ConcurrentHashMap()  // mediaId → announce
    private val pendingFiles: MutableMap<Long, Payload.File> = ConcurrentHashMap()  // payloadId → PayloadFile
    private val pendingFileEndpoints: MutableMap<Long, String> = ConcurrentHashMap()  // payloadId → senderEndpointId

    init {
        eventBus.observe(scope, AppEvent.MeshEvent.RawPacketReceived::class) { event ->
            handleIncoming(event.packet, event.incomingEndpointId)
        }
        eventBus.observe(scope, AppEvent.MeshEvent.PeerConnected::class) {
            drainOutbox()
        }
        eventBus.observe(scope, AppEvent.ConnectionEvent.SendOutboundRequest::class) { event ->
            scope.launch {
                val payload = com.elv8.crisisos.data.dto.payloads.ConnectionRequestPayload(
                    requestId = event.requestId,
                    fromAlias = event.fromAlias,
                    fromAvatarColor = event.fromAvatarColor,
                    message = event.message
                )
                val json = MeshJson.encodeToString(com.elv8.crisisos.data.dto.payloads.ConnectionRequestPayload.serializer(), payload)
                val packet = MeshPacket(
                    packetId = java.util.UUID.randomUUID().toString(),
                    type = MeshPacketType.CONNECTION_REQUEST,
                    senderId = connectionManager.getLocalCrsId(),
                    senderAlias = connectionManager.getLocalAlias(),
                    payload = json,
                    timestamp = System.currentTimeMillis(),
                    priority = PacketPriority.HIGH,
                    targetId = event.toCrsId
                )
                send(packet)
            }
        }
        eventBus.observe(scope, com.elv8.crisisos.core.event.AppEvent.ConnectionEvent.SendOutboundResponse::class) { event ->
            scope.launch {
                val payload = com.elv8.crisisos.data.dto.payloads.ConnectionResponsePayload(
                    requestId = event.requestId,
                    accepted = event.accepted,
                    fromAlias = event.fromAlias,
                    fromAvatarColor = event.fromAvatarColor
                )
                val json = MeshJson.encodeToString(com.elv8.crisisos.data.dto.payloads.ConnectionResponsePayload.serializer(), payload)
                val packet = MeshPacket(
                    packetId = java.util.UUID.randomUUID().toString(),
                    type = MeshPacketType.CONNECTION_RESPONSE,
                    senderId = connectionManager.getLocalCrsId(),
                    senderAlias = connectionManager.getLocalAlias(),
                    payload = json,
                    timestamp = System.currentTimeMillis(),
                    priority = PacketPriority.HIGH,
                    targetId = event.toCrsId
                )
                send(packet)
            }
        }

        // Observe incoming file payloads from MeshConnectionManager
        eventBus.observe(scope, AppEvent.MeshEvent.MediaFileReceived::class) { event ->
            Log.i("CrisisOS_Messenger", "MediaFileReceived: payloadId=${event.payloadId} from ${event.endpointId}")
            pendingFiles[event.payloadId] = event.payloadFile
            pendingFileEndpoints[event.payloadId] = event.endpointId
            scope.launch(Dispatchers.IO) {
                tryMatchAndSaveFile(event.payloadId, event.endpointId)
            }
        }
    }

    override     fun setLocalDeviceId(id: String) {
        localDeviceId = id
    }

    override     suspend fun send(packet: MeshPacket): SendResult {
        if (System.currentTimeMillis() > packet.timestamp + (packet.ttl * 3_600_000L)) {
            log("Refused to send expired packet ${packet.packetId}")
            return SendResult.Failed("Packet TTL exceeded before send")
        }
        
        if (connectionManager.connectedPeers.value.isEmpty()) {
            outboxRepository.enqueue(packet)
            return SendResult.Queued
        }
        
        // Broadcast all packets to connected neighbors because ConnectedPeer keys are endpointIds, not crsIds.
        // Neighbors will check if targetId == localDeviceId in handleIncoming.
        val targets = connectionManager.connectedPeers.value.keys.toList()
        
        if (targets.isEmpty()) {
            outboxRepository.enqueue(packet)
            return SendResult.Queued
        }
        
        var allSucceeded = true
        for (endpointId in targets) {
            sendToEndpoint(endpointId, packet)
        }
        
        return SendResult.Sent
    }

    private fun sendToEndpoint(endpointId: String, packet: MeshPacket) {
        val json = MeshJson.encodeToString(MeshPacket.serializer(), packet)
        val bytes = json.toByteArray(Charsets.UTF_8)
        if (bytes.size > MeshConfig.MAX_PAYLOAD_SIZE) {
            log("Packet too large: ${bytes.size} bytes")
            return
        }
        val payload = Payload.fromBytes(bytes)
        Nearby.getConnectionsClient(context).sendPayload(endpointId, payload)
            .addOnSuccessListener {
                scope.launch { eventBus.emit(AppEvent.MeshEvent.MessageSent(packet.packetId)) }
            }
            .addOnFailureListener { e ->
                scope.launch {
                    outboxRepository.markFailed(packet.packetId, e.message ?: "Unknown")
                    eventBus.emit(AppEvent.MeshEvent.MessageFailed(packet.packetId, e.message ?: "Unknown"))
                }
            }
    }

    private suspend fun relayPacket(packet: MeshPacket, incomingEndpointId: String) {
        val relayed = packet.copy(
            hopCount = packet.hopCount + 1,
            ttl = packet.ttl - 1
        )
        val targets = connectionManager.connectedPeers.value.keys.filter { it != incomingEndpointId }
        
        if (targets.isEmpty()) {
            outboxRepository.enqueue(relayed, 2)
        } else {
            for (target in targets) {
                sendToEndpoint(target, relayed)
            }
        }
        _relayCount.value += 1
        log("Relayed packet ${packet.packetId} (hop ${relayed.hopCount}) to ${targets.size} peers")
    }

    private suspend fun handleIncoming(packet: MeshPacket, incomingEndpointId: String) {
        if (com.elv8.crisisos.core.debug.MeshDebugConfig.isMockCrsId(packet.senderId)) {
            Log.d("CrisisOS_Messenger", "Ignoring packet from mock sender: ${packet.senderId}")
            return
        }
        if (seenPacketIds.contains(packet.packetId)) return
        seenPacketIds.add(packet.packetId)
        if (packet.ttl <= 0) {
            log("Packet TTL expired: ${packet.packetId}")
            return
        }

        if (packet.targetId == null) {
            relayPacket(packet, incomingEndpointId)
        } else if (packet.targetId != connectionManager.getLocalCrsId()) {
            relayPacket(packet, incomingEndpointId)
            return
        }
        when (packet.type) {
            MeshPacketType.MEDIA_ANNOUNCE -> {
                val payload = decodePayload(packet, com.elv8.crisisos.data.dto.payloads.MediaAnnouncePayload.serializer()) ?: return
                Log.e("MEDIA_DEBUG", "=== MEDIA_ANNOUNCE RECEIVED === mediaId=${payload.mediaId} filePayloadId=${payload.filePayloadId} threadId=${payload.threadId}")
                log("Media announce received: mediaId=${payload.mediaId} type=${payload.mediaType}")
                
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val thumbFile: java.io.File? = if (payload.thumbnailBase64 != null) {
                        try {
                            val bytes = android.util.Base64.decode(payload.thumbnailBase64, android.util.Base64.DEFAULT)
                            val thumbDir = java.io.File(context.filesDir, "crisisos/thumbnails").also { it.mkdirs() }
                            val tFile = java.io.File(thumbDir, "recv_thumb_${payload.mediaId}.jpg")
                            tFile.writeBytes(bytes)
                            tFile
                        } catch (e: Exception) { null }
                    } else null
                    
                    val mediaItem = com.elv8.crisisos.domain.model.media.MediaItem(
                        mediaId = payload.mediaId,
                        threadId = resolveThreadIdFromPacket(packet),
                        senderCrsId = packet.senderId,
                        receiverCrsId = localDeviceId,
                        type = com.elv8.crisisos.domain.model.media.MediaType.valueOf(payload.mediaType),
                        localUri = null,
                        remoteUri = null,
                        fileName = payload.fileName,
                        mimeType = payload.mimeType,
                        fileSizeBytes = payload.fileSizeBytes,
                        compressedSizeBytes = payload.compressedSizeBytes,
                        durationMs = payload.durationMs,
                        thumbnailUri = thumbFile?.let { "file://${it.absolutePath}" },
                        timestamp = packet.timestamp,
                        status = com.elv8.crisisos.domain.model.media.MediaStatus.RECEIVED,
                        isOwn = false,
                        messageId = payload.messageId,
                        chunkCount = payload.chunkCount
                    )
                    
                    mediaRepository.receiveIncomingMedia(mediaItem)
                    
                    val threadId = resolveThreadIdFromPacket(packet)
                    val contentPreview = when (com.elv8.crisisos.domain.model.media.MediaType.valueOf(payload.mediaType)) {
                        com.elv8.crisisos.domain.model.media.MediaType.IMAGE -> "📷 Photo"
                        com.elv8.crisisos.domain.model.media.MediaType.VIDEO -> "🎬 Video"
                        com.elv8.crisisos.domain.model.media.MediaType.AUDIO -> "🎤 Voice message"
                    }

                    chatDao.insertMessage(com.elv8.crisisos.data.local.entity.ChatMessageEntity(
                        id = payload.messageId,
                        threadId = threadId,
                        fromCrsId = packet.senderId,
                        fromAlias = packet.senderAlias,
                        content = contentPreview,
                        timestamp = packet.timestamp,
                        deliveryStatus = com.elv8.crisisos.domain.model.MessageStatus.DELIVERED,
                        isOwn = false,
                        messageType = com.elv8.crisisos.domain.model.MessageType.valueOf(payload.mediaType),
                        hopsCount = packet.hopCount,
                        mediaId = payload.mediaId,
                        mediaThumbnailUri = thumbFile?.let { "file://${it.absolutePath}" },
                        mediaDurationMs = payload.durationMs,
                        senderId = packet.senderId,
                        senderAlias = packet.senderAlias
                    ))

                    chatThreadDao.updateLastMessage(threadId, contentPreview, packet.timestamp)
                    chatThreadDao.incrementUnread(threadId)
                    
                    eventBus.tryEmit(AppEvent.MeshEvent.MessageReceived(packet, incomingEndpointId))

                    // Store announce for file matching
                    pendingAnnounces[payload.mediaId] = payload
                    Log.i("CrisisOS_Messenger", "Announce stored: mediaId=${payload.mediaId} filePayloadId=${payload.filePayloadId}")

                    // Try to match if file already arrived
                    if (payload.filePayloadId != 0L) {
                        tryMatchAndSaveFile(payload.filePayloadId, incomingEndpointId)
                    }
                }
            }
            MeshPacketType.CONNECTION_REQUEST -> {
                val payload = decodePayload(packet, com.elv8.crisisos.data.dto.payloads.ConnectionRequestPayload.serializer())
                if (payload != null) {
                    log("Received ConnectionRequest from: ${payload.fromAlias}")
                    eventBus.emit(AppEvent.ConnectionEvent.RequestReceived(
                        requestId = payload.requestId,
                        fromCrsId = packet.senderId,
                        fromAlias = payload.fromAlias,
                        fromAvatarColor = payload.fromAvatarColor,
                        message = payload.message,
                        timestamp = packet.timestamp
                    ))
                }
            }
            MeshPacketType.CONNECTION_RESPONSE -> {
                val payload = decodePayload(packet, com.elv8.crisisos.data.dto.payloads.ConnectionResponsePayload.serializer())
                if (payload != null) {
                    log("Received ConnectionResponse from: ${payload.fromAlias} (accepted=${payload.accepted})")
                    eventBus.emit(AppEvent.ConnectionEvent.ResponseReceived(
                        requestId = payload.requestId,
                        fromCrsId = packet.senderId,
                        fromAlias = payload.fromAlias,
                        fromAvatarColor = payload.fromAvatarColor,
                        accepted = payload.accepted
                    ))
                }
            }
            MeshPacketType.CHAT_MESSAGE -> {
                val payload = decodePayload(packet, ChatPayload.serializer())
                if (payload != null) {
                    log("Received ChatMessage: ${payload.content}")
                    
                    scope.launch(Dispatchers.IO) {
                        val threadId = resolveThreadIdFromPacket(packet)
                        val myId = connectionManager.getLocalCrsId()
                        val decryptedContent = com.elv8.crisisos.core.network.mesh.MeshEncryption.decrypt(payload.content, myId)
                        
                        val existing = chatThreadDao.getById(threadId)
                        if (existing == null) {
                            chatThreadDao.insert(
                                com.elv8.crisisos.data.local.entity.ChatThreadEntity(
                                    threadId = threadId,
                                    type = "DIRECT",
                                    peerCrsId = packet.senderId,
                                    displayName = packet.senderAlias,
                                    avatarColor = com.elv8.crisisos.domain.model.identity.CrsIdGenerator.generateAvatarColor(packet.senderId),
                                    lastMessagePreview = decryptedContent,
                                    lastMessageAt = packet.timestamp,
                                    createdAt = packet.timestamp,
                                    unreadCount = 1,
                                    isPinned = false,
                                    isMuted = false,
                                    isMock = false,
                                    groupId = null,
                                    connectionRequestId = null
                                )
                            )
                        }
                        
                        chatDao.insertMessage(com.elv8.crisisos.data.local.entity.ChatMessageEntity(
                            id = payload.messageId,
                            threadId = threadId,
                            fromCrsId = packet.senderId,
                            fromAlias = packet.senderAlias,
                            content = decryptedContent,
                            timestamp = packet.timestamp,
                            deliveryStatus = com.elv8.crisisos.domain.model.MessageStatus.DELIVERED,
                            isOwn = false,
                            messageType = com.elv8.crisisos.domain.model.MessageType.TEXT,
                            hopsCount = packet.hopCount,
                            senderId = packet.senderId,
                            senderAlias = packet.senderAlias
                        ))
                        
                        chatThreadDao.updateLastMessage(threadId, decryptedContent, packet.timestamp)
                        chatThreadDao.incrementUnread(threadId)
                        
                        eventBus.emit(AppEvent.MeshEvent.MessageReceived(packet, incomingEndpointId))
                        
                        notificationBus.emitChat(
                            com.elv8.crisisos.core.notification.event.NotificationEvent.Chat.MessageReceived(
                                threadId = threadId,
                                fromCrsId = packet.senderId,
                                fromAlias = packet.senderAlias,
                                avatarColor = com.elv8.crisisos.domain.model.identity.CrsIdGenerator.generateAvatarColor(packet.senderId),
                                messagePreview = decryptedContent,
                                messageId = payload.messageId,
                                timestamp = packet.timestamp,
                                isGroupChat = false,
                                groupName = null
                            )
                        )
                    }
                }
            }
            MeshPacketType.SOS_ALERT -> {
                val payload = decodePayload(packet, SosPayload.serializer())
                if (payload != null) {
                    eventBus.emit(AppEvent.SosEvent.SosReceivedFromPeer(packet.senderId, packet.senderAlias, payload.sosType, payload.message))
                    scope.launch {
                        notificationBus.emitSos(
                            com.elv8.crisisos.core.notification.event.NotificationEvent.Sos.IncomingAlert(
                                alertId = packet.packetId,
                                fromCrsId = packet.senderId,
                                fromAlias = payload.senderName ?: packet.senderAlias,
                                sosType = payload.sosType,
                                message = payload.message,
                                locationHint = payload.locationHint,
                                hopsAway = packet.hopCount,
                                latitude = payload.latitude,
                                longitude = payload.longitude
                            )
                        )
                        MeshLogger.connection("SOS notification event emitted from ${packet.senderAlias}")
                    }
                }
            }
            MeshPacketType.MISSING_PERSON_QUERY -> {
                val payload = decodePayload(packet, MissingPersonPayload.serializer())
                if (payload != null) {
                    eventBus.emit(
                        AppEvent.MissingPersonEvent.QueryBroadcast(
                            senderId = packet.senderId,
                            queryType = payload.queryType,
                            crsId = payload.crsId,
                            name = payload.name,
                            age = payload.age,
                            description = payload.description,
                            lastLocation = payload.lastLocation
                        )
                    )
                }
            }
            MeshPacketType.MISSING_PERSON_RESPONSE -> {
                val payload = decodePayload(packet, MissingPersonPayload.serializer())
                if (payload != null) {
                    val hops = payload.description?.toIntOrNull() ?: payload.age ?: 1
                    eventBus.emit(AppEvent.MissingPersonEvent.ResponseReceived(payload.crsId, payload.lastLocation ?: "Unknown", hops))
                    if (payload.queryType == "RESPONSE") {
                        scope.launch {
                            notificationBus.emitMissingPerson(
                                com.elv8.crisisos.core.notification.event.NotificationEvent.MissingPerson.PersonLocated(
                                    crsId = payload.crsId,
                                    name = payload.name ?: "Unknown",
                                    lastLocation = payload.lastLocation ?: "Unknown",
                                    hopsAway = packet.hopCount
                                )
                            )
                        }
                    }
                }
            }
            MeshPacketType.SUPPLY_REQUEST -> {
                val payload = decodePayload(packet, SupplyPayload.serializer())
                if (payload != null) {
                    eventBus.emit(AppEvent.SupplyEvent.RequestBroadcast(payload.supplyType, payload.location))
                }
            }
            MeshPacketType.SUPPLY_ACK -> {
                val payload = decodePayload(packet, SupplyAckPayload.serializer())
                if (payload != null) {
                    eventBus.emit(AppEvent.SupplyEvent.AckReceived(payload.requestId, payload.ngoId, payload.eta))
                    scope.launch {
                        notificationBus.emitSupply(
                            com.elv8.crisisos.core.notification.event.NotificationEvent.Supply.RequestAcknowledged(
                                requestId = payload.requestId,
                                supplyType = "Supply",
                                ngoAlias = packet.senderAlias,
                                estimatedEta = payload.eta
                            )
                        )
                    }
                }
            }
            MeshPacketType.DEAD_MAN_TRIGGER -> {
                val payload = decodePayload(packet, DeadManPayload.serializer())
                if (payload != null) {
                    eventBus.emit(AppEvent.DeadManEvent.AlertTriggered(payload.alertMessage))
                }
            }
            MeshPacketType.CHILD_ALERT -> {
                val payload = decodePayload(packet, ChildAlertPayload.serializer())
                if (payload != null) {
                    eventBus.emit(AppEvent.ChildAlertEvent.AlertBroadcast(payload.crsChildId, payload.childName, payload.lastLocation))
                }
            }
            MeshPacketType.SYSTEM_PING -> {
                val ackPacket = MeshPacket(
                    packetId = java.util.UUID.randomUUID().toString(),
                    type = MeshPacketType.SYSTEM_ACK,
                    senderId = connectionManager.getLocalCrsId(),
                    senderAlias = connectionManager.getLocalAlias(),
                    payload = "",
                    timestamp = System.currentTimeMillis(),
                    priority = com.elv8.crisisos.data.dto.PacketPriority.NORMAL,
                    targetId = packet.senderId
                )
                send(ackPacket)
            }
            else -> {
                log("Unhandled packet type: ${packet.type}")
            }
        }
    }

    private suspend fun drainOutbox() {
        outboxRepository.getPendingPackets().collect { pendingList ->
            for (packet in pendingList) {
                val result = send(packet)
                if (result is SendResult.Sent) {
                    outboxRepository.markSent(packet.packetId)
                } else if (result is SendResult.Queued) {
                    break
                }
            }
        }
    }

    private fun resolveThreadIdFromPacket(packet: com.elv8.crisisos.data.dto.MeshPacket): String {
        return chatThreadDao.getDirectThread(packet.senderId)?.threadId
            ?: "thread_${packet.senderId}_${localDeviceId}"
    }

    private fun log(msg: String) {
        MeshLogger.connection(msg)
    }

    /**
     * Try to match a file payload with its announce metadata.
     * Called from both announce arrival and file arrival — whichever comes second triggers the save.
     */
    private suspend fun tryMatchAndSaveFile(payloadId: Long, endpointId: String) {
        Log.e("MEDIA_DEBUG", "=== tryMatchAndSaveFile === payloadId=$payloadId pendingFiles_keys=${pendingFiles.keys} pendingAnnounces_keys=${pendingAnnounces.values.map { it.filePayloadId }}")
        val pendingFile = pendingFiles[payloadId] ?: run {
            Log.d("CrisisOS_Messenger", "tryMatch: file not yet arrived for payloadId=$payloadId")
            return
        }
        val announce = pendingAnnounces.values.find { it.filePayloadId == payloadId } ?: run {
            Log.d("CrisisOS_Messenger", "tryMatch: announce not yet arrived for payloadId=$payloadId — waiting")
            return
        }

        // Both matched — remove from pending
        pendingFiles.remove(payloadId)
        pendingFileEndpoints.remove(payloadId)
        pendingAnnounces.remove(announce.mediaId)

        Log.e("MEDIA_DEBUG", "ANNOUNCE LOOKUP: found=true mediaId=${announce.mediaId} filePayloadId=${announce.filePayloadId}")
        Log.i("CrisisOS_Messenger", "File matched to announce: mediaId=${announce.mediaId} payloadId=$payloadId")
        saveReceivedFile(announce, pendingFile, endpointId)
    }

    /**
     * Copy the received Nearby file to internal scoped storage, update Room localUri.
     */
    private suspend fun saveReceivedFile(
        announce: MediaAnnouncePayload,
        payloadFile: Payload.File,
        senderEndpointId: String
    ) = withContext(Dispatchers.IO) {
        try {
            val type = MediaType.valueOf(announce.mediaType)
            val destDir = fileManager.getOrCreateMediaDir(type)
            val destFile = File(destDir, "recv_${announce.mediaId}_${announce.fileName}")

            // Payload.File gives us a Java File from Nearby's temp storage
            val nearbyFile = payloadFile.asJavaFile()
            Log.e("MEDIA_DEBUG", "NEARBY FILE: $nearbyFile exists=${nearbyFile?.exists()} size=${nearbyFile?.length()}")
            if (nearbyFile != null && nearbyFile.exists()) {
                nearbyFile.copyTo(destFile, overwrite = true)
                Log.e("MEDIA_DEBUG", "FILE COPIED TO: ${destFile.absolutePath} exists=${destFile.exists()} size=${destFile.length()}")
                Log.i("CrisisOS_Messenger", "File saved (javaFile): ${destFile.absolutePath} size=${destFile.length()} bytes")
            } else {
                // Fallback: use ParcelFileDescriptor stream copy
                val pfd = payloadFile.asParcelFileDescriptor()
                if (pfd != null) {
                    FileInputStream(pfd.fileDescriptor).use { input ->
                        destFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    pfd.close()
                    Log.i("CrisisOS_Messenger", "File saved (PFD): ${destFile.absolutePath} size=${destFile.length()} bytes")
                } else {
                    Log.e("CrisisOS_Messenger", "Cannot extract file from payload: both asJavaFile and asParcelFileDescriptor returned null")
                    mediaDao.updateStatus(announce.mediaId, MediaStatus.FAILED.name)
                    return@withContext
                }
            }

            val finalUri = "file://${destFile.absolutePath}"

            // Update Room: set localUri on media_items
            mediaDao.updateLocalUri(announce.mediaId, finalUri, MediaStatus.RECEIVED.name)
            Log.e("MEDIA_DEBUG", "ROOM UPDATED: mediaId=${announce.mediaId} uri=$finalUri")

            // Update Room: set mediaThumbnailUri on chat_messages
            val mediaEntity = mediaDao.getById(announce.mediaId)
            mediaEntity?.messageId?.let { msgId ->
                chatDao.linkMedia(msgId, announce.mediaId, finalUri, MessageStatus.DELIVERED.name)
            }

            // Update thread last message to force Flow re-emission
            mediaEntity?.let {
                val contentPreview = when (type) {
                    MediaType.IMAGE -> "\uD83D\uDCF7 Photo received"
                    MediaType.VIDEO -> "\uD83C\uDFAC Video received"
                    MediaType.AUDIO -> "\uD83C\uDF99\uFE0F Voice received"
                }
                chatThreadDao.updateLastMessage(it.threadId, contentPreview, System.currentTimeMillis())
            }

            Log.i("CrisisOS_Messenger", "Media fully received and saved: mediaId=${announce.mediaId} uri=$finalUri")

        } catch (e: Exception) {
            Log.e("CrisisOS_Messenger", "saveReceivedFile failed: ${e.message}", e)
            mediaDao.updateStatus(announce.mediaId, MediaStatus.FAILED.name)
        }
    }
}
