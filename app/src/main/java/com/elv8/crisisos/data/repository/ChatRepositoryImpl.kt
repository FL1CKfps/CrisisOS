package com.elv8.crisisos.data.repository

import com.elv8.crisisos.core.event.AppEvent
import com.elv8.crisisos.core.event.EventBus
import com.elv8.crisisos.data.dto.MeshPacketType
import com.elv8.crisisos.data.dto.PacketFactory
import com.elv8.crisisos.data.dto.PacketParser
import com.elv8.crisisos.data.dto.payloads.ChatPayload
import com.elv8.crisisos.data.local.dao.ChatDao
import com.elv8.crisisos.data.local.entity.ChatMessageEntity
import com.elv8.crisisos.data.mesh.MeshMessenger
import com.elv8.crisisos.data.mesh.SendResult
import com.elv8.crisisos.domain.model.ChatMessage
import com.elv8.crisisos.domain.model.MessageStatus
import com.elv8.crisisos.domain.model.MessageType
import com.elv8.crisisos.domain.repository.MeshRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ChatRepositoryImpl @Inject constructor(
    private val chatDao: ChatDao,
    private val messenger: MeshMessenger,
    private val eventBus: EventBus
) : MeshRepository {

    override fun getMessages(): Flow<List<ChatMessage>> {
        return chatDao.getAllMessages().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun sendMessage(message: ChatMessage) {
        val chatPayload = ChatPayload(content = message.content, messageId = message.id)
        val packet = PacketFactory.buildChatPacket(
            senderId = message.senderId,
            senderAlias = message.senderAlias,
            payload = chatPayload
        )

        val entity = message.copy(deliveryStatus = MessageStatus.SENDING).toEntity()
        chatDao.insertMessage(entity)

        when (messenger.send(packet)) {
            is SendResult.Sent -> chatDao.markAsDelivered(message.id, MessageStatus.SENT)
            is SendResult.Queued -> chatDao.markAsDelivered(message.id, MessageStatus.SENDING)
            is SendResult.Failed -> chatDao.markAsDelivered(message.id, MessageStatus.FAILED)
        }
    }

    override fun observeIncomingMessages(): Flow<ChatMessage> {
        return eventBus.events
            .filterIsInstance<AppEvent.MeshEvent.MessageReceived>()
            .filter { it.packet.type == MeshPacketType.CHAT_MESSAGE }
            .map { event ->
                val payload = PacketParser.decodePayload(event.packet, ChatPayload.serializer()) ?: return@map null
                val message = ChatMessage(
                    id = payload.messageId,
                    senderId = event.packet.senderId,
                    senderAlias = event.packet.senderAlias,
                    content = payload.content,
                    timestamp = event.packet.timestamp,
                    deliveryStatus = MessageStatus.DELIVERED,
                    hopsCount = event.packet.hopCount,
                    isOwn = false,
                    messageType = MessageType.TEXT
                )
                chatDao.insertMessage(message.toEntity())
                message
            }
            .filterNotNull()
    }
}

// Mappers
fun ChatMessageEntity.toDomainModel(): ChatMessage {
    return ChatMessage(
        id = id,
        senderId = senderId,
        senderAlias = senderAlias,
        content = content,
        timestamp = timestamp,
        deliveryStatus = deliveryStatus,
        hopsCount = hopsCount,
        isOwn = isOwn,
        messageType = messageType,
        mediaId = mediaId,
        mediaThumbnailUri = mediaThumbnailUri,
        mediaDurationMs = mediaDurationMs
    )
}

fun ChatMessage.toEntity(): ChatMessageEntity {
    return ChatMessageEntity(
        id = id,
        senderId = senderId,
        senderAlias = senderAlias,
        content = content,
        timestamp = timestamp,
        deliveryStatus = deliveryStatus,
        hopsCount = hopsCount,
        isOwn = isOwn,
        messageType = messageType,
        mediaId = mediaId,
        mediaThumbnailUri = mediaThumbnailUri,
        mediaDurationMs = mediaDurationMs
    )
}