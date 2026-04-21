package com.elv8.crisisos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notification_log")
data class NotificationLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val eventType: String,
    val groupKey: String,
    val channelId: String,
    val title: String,
    val body: String,
    val timestamp: Long,
    val wasShown: Boolean,
    val wasDismissed: Boolean = false,
    val associatedId: String? = null
)
