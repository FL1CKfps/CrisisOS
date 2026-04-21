package com.elv8.crisisos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dead_man_settings")
data class DeadManSwitchEntity(
    @PrimaryKey val id: String,
    val intervalMinutes: Long,
    val alertMessage: String,
    val isActive: Boolean,
    val lastCheckIn: Long,
    val escalationContacts: String // JSON string
)