package com.elv8.crisisos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ConnectionRequestStatus {
    PENDING, ACCEPTED, REJECTED, CANCELLED, EXPIRED
}

enum class RequestDirection {
    OUTGOING, INCOMING
}

@Entity(tableName = "connection_requests")
data class ConnectionRequestEntity(
    @PrimaryKey
    val requestId: String,
    val fromCrsId: String,
    val fromAlias: String,
    val fromAvatarColor: Int,
    val toCrsId: String,
    val message: String,
    val sentAt: Long,
    val respondedAt: Long?,
    val status: String,
    val direction: String,
    val expiresAt: Long
)
