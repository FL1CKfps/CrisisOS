package com.elv8.crisisos.data.dto

import kotlinx.serialization.Serializable

@Serializable
enum class MeshPacketType {
    CHAT_MESSAGE,
    SOS_ALERT,
    SOS_CANCEL,
    DEAD_MAN_TRIGGER,
    MISSING_PERSON_QUERY,
    MISSING_PERSON_RESPONSE,
    SUPPLY_REQUEST,
    SUPPLY_ACK,
    DANGER_REPORT,
    CHECKPOINT_UPDATE,
    CHILD_ALERT,
    CHILD_LOCATED,
    DECONFLICTION_REPORT,
    SYSTEM_PING,
    SYSTEM_ACK,
    CONNECTION_REQUEST,
    CONNECTION_RESPONSE,
    MEDIA_ANNOUNCE,
    MEDIA_CHUNK,
    MEDIA_CHUNK_ACK,
    CRISIS_NEWS,
    COMMUNITY_POST
}

@Serializable
enum class PacketPriority {
    LOW,
    NORMAL,
    HIGH,
    CRITICAL
}

@Serializable
data class MeshPacket(
    val packetId: String,
    val type: MeshPacketType,
    val senderId: String,
    val senderAlias: String,
    val payload: String,
    val timestamp: Long,
    val ttl: Int = 7,
    val hopCount: Int = 0,
    val priority: PacketPriority,
    val targetId: String? = null,
    val signature: String? = null
)
