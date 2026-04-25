package com.elv8.crisisos.data.dto

import com.elv8.crisisos.data.dto.payloads.*
import kotlinx.serialization.encodeToString
import java.util.UUID

object PacketFactory {

    fun buildMediaAnnouncePacket(
        senderId: String,
        alias: String,
        payload: MediaAnnouncePayload,
        targetId: String?
    ): MeshPacket {
        return createWrappedPacket(
            type = MeshPacketType.MEDIA_ANNOUNCE,
            senderId = senderId,
            senderAlias = alias,
            payloadString = kotlinx.serialization.json.Json.encodeToString(payload),
            priority = PacketPriority.NORMAL,
            targetId = targetId
        )
    }

    private fun createWrappedPacket(
        type: MeshPacketType,
        senderId: String,
        senderAlias: String,
        payloadString: String,
        priority: PacketPriority,
        targetId: String? = null
    ): MeshPacket {
        return MeshPacket(
            packetId = UUID.randomUUID().toString(),
            type = type,
            senderId = senderId,
            senderAlias = senderAlias,
            payload = payloadString,
            timestamp = System.currentTimeMillis(),
            ttl = 7,
            hopCount = 0,
            priority = priority,
            targetId = targetId,
            signature = null
        )
    }

    fun buildChatPacket(senderId: String, senderAlias: String, payload: ChatPayload, targetId: String? = null): MeshPacket {
        return createWrappedPacket(
            type = MeshPacketType.CHAT_MESSAGE,
            senderId = senderId,
            senderAlias = senderAlias,
            payloadString = MeshJson.encodeToString(payload),
            priority = PacketPriority.NORMAL,
            targetId = targetId
        )
    }

    fun buildSosPacket(senderId: String, senderAlias: String, payload: SosPayload, locationHint: String? = null): MeshPacket {
        return createWrappedPacket(
            type = MeshPacketType.SOS_ALERT,
            senderId = senderId,
            senderAlias = senderAlias,
            payloadString = MeshJson.encodeToString(payload),
            priority = PacketPriority.CRITICAL
        )
    }

    fun buildSosCancelPacket(senderId: String, senderAlias: String): MeshPacket {
        return createWrappedPacket(
            type = MeshPacketType.SOS_CANCEL,
            senderId = senderId,
            senderAlias = senderAlias,
            payloadString = "{}",
            priority = PacketPriority.HIGH
        )
    }

    fun buildDeadManPacket(senderId: String, senderAlias: String, payload: DeadManPayload): MeshPacket {
        return createWrappedPacket(
            type = MeshPacketType.DEAD_MAN_TRIGGER,
            senderId = senderId,
            senderAlias = senderAlias,
            payloadString = MeshJson.encodeToString(payload),
            priority = PacketPriority.CRITICAL
        )
    }

    fun buildMissingPersonQueryPacket(senderId: String, senderAlias: String, payload: MissingPersonPayload): MeshPacket {
        return createWrappedPacket(
            type = MeshPacketType.MISSING_PERSON_QUERY,
            senderId = senderId,
            senderAlias = senderAlias,
            payloadString = MeshJson.encodeToString(payload),
            priority = PacketPriority.HIGH
        )
    }

    fun buildMissingPersonResponsePacket(senderId: String, senderAlias: String, targetId: String, crsId: String, lastLocation: String, hopsAway: Int): MeshPacket {
        val payload = MissingPersonPayload(
            queryType = "RESPONSE",
            crsId = crsId,
            name = "",
            age = hopsAway,
            description = hopsAway.toString(),
            lastLocation = lastLocation
        )
        return createWrappedPacket(
            type = MeshPacketType.MISSING_PERSON_RESPONSE,
            senderId = senderId,
            senderAlias = senderAlias,
            payloadString = MeshJson.encodeToString(payload),
            priority = PacketPriority.HIGH,
            targetId = targetId
        )
    }

    fun buildSupplyRequestPacket(senderId: String, senderAlias: String, payload: SupplyPayload): MeshPacket {
        return createWrappedPacket(
            type = MeshPacketType.SUPPLY_REQUEST,
            senderId = senderId,
            senderAlias = senderAlias,
            payloadString = MeshJson.encodeToString(payload),
            priority = PacketPriority.NORMAL
        )
    }

    fun buildSupplyAckPacket(senderId: String, senderAlias: String, targetId: String, requestId: String, ngoId: String, eta: String, meetingPoint: String?): MeshPacket {
        val payload = SupplyAckPayload(requestId, ngoId, eta, meetingPoint)     
        return createWrappedPacket(
            type = MeshPacketType.SUPPLY_ACK,
            senderId = senderId,
            senderAlias = senderAlias,
            payloadString = MeshJson.encodeToString(payload),
            priority = PacketPriority.NORMAL,
            targetId = targetId
        )
    }

    fun buildDangerReportPacket(senderId: String, senderAlias: String, payload: DangerPayload): MeshPacket {
        return createWrappedPacket(
            type = MeshPacketType.DANGER_REPORT,
            senderId = senderId,
            senderAlias = senderAlias,
            payloadString = MeshJson.encodeToString(payload),
            priority = PacketPriority.HIGH
        )
    }

    fun buildCheckpointUpdatePacket(senderId: String, senderAlias: String, payload: CheckpointPayload): MeshPacket {
        return createWrappedPacket(
            type = MeshPacketType.CHECKPOINT_UPDATE,
            senderId = senderId,
            senderAlias = senderAlias,
            payloadString = MeshJson.encodeToString(payload),
            priority = PacketPriority.NORMAL
        )
    }

    fun buildChildAlertPacket(senderId: String, senderAlias: String, payload: ChildAlertPayload): MeshPacket {
        return createWrappedPacket(
            type = MeshPacketType.CHILD_ALERT,
            senderId = senderId,
            senderAlias = senderAlias,
            payloadString = MeshJson.encodeToString(payload),
            priority = PacketPriority.CRITICAL
        )
    }

    fun buildChildLocatedPacket(senderId: String, senderAlias: String, payload: ChildAlertPayload): MeshPacket {
        return createWrappedPacket(
            type = MeshPacketType.CHILD_LOCATED,
            senderId = senderId,
            senderAlias = senderAlias,
            payloadString = MeshJson.encodeToString(payload),
            priority = PacketPriority.HIGH
        )
    }

    fun buildDeconflictionReportPacket(senderId: String, senderAlias: String, payloadString: String = "{}"): MeshPacket {
        return createWrappedPacket(
            type = MeshPacketType.DECONFLICTION_REPORT,
            senderId = senderId,
            senderAlias = senderAlias,
            payloadString = payloadString,
            priority = PacketPriority.NORMAL
        )
    }

    fun buildPingPacket(senderId: String, senderAlias: String): MeshPacket {    
        return createWrappedPacket(
            type = MeshPacketType.SYSTEM_PING,
            senderId = senderId,
            senderAlias = senderAlias,
            payloadString = "{}",
            priority = PacketPriority.LOW
        )
    }

    fun buildSystemAckPacket(senderId: String, senderAlias: String): MeshPacket {
        return createWrappedPacket(
            type = MeshPacketType.SYSTEM_ACK,
            senderId = senderId,
            senderAlias = senderAlias,
            payloadString = "{}",
            priority = PacketPriority.LOW
        )
    }
    fun buildNewsItemPacket(senderId: String, senderAlias: String, payload: NewsItemPayload): MeshPacket {
        return createWrappedPacket(
            type = MeshPacketType.CRISIS_NEWS,
            senderId = senderId,
            senderAlias = senderAlias,
            payloadString = MeshJson.encodeToString(payload),
            priority = PacketPriority.NORMAL
        )
    }

    fun buildCommunityPostPacket(senderId: String, senderAlias: String, payload: CommunityPostPayload): MeshPacket {
        return createWrappedPacket(
            type = MeshPacketType.COMMUNITY_POST,
            senderId = senderId,
            senderAlias = senderAlias,
            payloadString = MeshJson.encodeToString(payload),
            priority = PacketPriority.LOW
        )
    }

    fun buildAckPacket(senderId: String, senderAlias: String, targetPacketId: String): MeshPacket {
        return createWrappedPacket(
            type = MeshPacketType.SYSTEM_ACK,
            senderId = senderId,
            senderAlias = senderAlias,
            payloadString = "{\"target\":\"${targetPacketId}\"}",
            priority = PacketPriority.LOW
        )
    }

}
