package com.elv8.crisisos.domain.model.connection

import com.elv8.crisisos.data.local.entity.ConnectionRequestStatus
import com.elv8.crisisos.data.local.entity.RequestDirection

data class ConnectionRequest(
    val requestId: String,
    val fromCrsId: String,
    val fromAlias: String,
    val fromAvatarColor: Int,
    val toCrsId: String,
    val message: String,
    val sentAt: Long,
    val respondedAt: Long?,
    val status: ConnectionRequestStatus,
    val direction: RequestDirection,
    val expiresAt: Long
) {
    val isExpired: Boolean
        get() = System.currentTimeMillis() > expiresAt
}
