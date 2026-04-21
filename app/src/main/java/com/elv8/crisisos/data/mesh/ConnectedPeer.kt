package com.elv8.crisisos.data.mesh

data class ConnectedPeer(
    val endpointId: String,
    val crsId: String,
    val alias: String,
    val connectedAt: Long,
    val lastSeenAt: Long,
    val signalStrength: Int? = null,
    val isAuthenticated: Boolean = false
)