package com.elv8.crisisos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "outbox_messages")
data class OutboxMessageEntity(
    @PrimaryKey
    val id: String,
    val packetJson: String,
    val packetType: String,
    val priority: Int,
    val targetId: String?,
    val createdAt: Long,
    val scheduledAt: Long = createdAt,
    val lastAttemptAt: Long? = null,
    val attemptCount: Int = 0,
    val maxAttempts: Int = 5,
    val ttlExpiry: Long = createdAt + (72L * 60 * 60 * 1000), // Default 72 hours
    val status: String,
    val failureReason: String? = null
)