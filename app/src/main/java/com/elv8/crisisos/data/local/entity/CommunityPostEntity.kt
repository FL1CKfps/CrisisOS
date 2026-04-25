package com.elv8.crisisos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Anonymous mesh-distributed post. No CRS ID is stored — author identity is
 * never persisted. NGOs can mark posts pinned to elevate verified info.
 */
@Entity(tableName = "community_posts")
data class CommunityPostEntity(
    @PrimaryKey val id: String,
    val body: String,
    val category: String,
    val pinned: Boolean,
    val createdAt: Long,
    val expiresAt: Long
)
