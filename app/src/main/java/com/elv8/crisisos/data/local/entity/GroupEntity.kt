package com.elv8.crisisos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey
    val groupId: String,
    val name: String,
    val description: String,
    val createdAt: Long,
    val createdByCrsId: String,
    val type: String,
    val memberCrsIds: List<String>,
    val avatarColor: Int
)
