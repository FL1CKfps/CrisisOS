package com.elv8.crisisos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey
    val crsId: String,
    val alias: String,
    val addedAt: Long,
    val groupId: String?,
    val trustLevel: String,
    val notes: String = "",
    val avatarColor: Int,
    val isFavorite: Boolean = false,
    val isBlocked: Boolean = false
)
