package com.elv8.crisisos.domain.model.peer

data class Peer(
    val crsId: String,
    val alias: String,
    val deviceId: String,
    val discoveredAt: Long,
    val lastSeenAt: Long,
    val signalStrength: Int,
    val distanceMeters: Float,
    val status: PeerStatus,
    val isNearby: Boolean,
    val avatarColor: Int,
    val publicKey: String?
)
