package com.elv8.crisisos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Hyperlocal CrisisNews entry — propagated through the mesh, expires after 24 hours. */
@Entity(tableName = "news_items")
data class NewsItemEntity(
    @PrimaryKey val id: String,
    val headline: String,
    val body: String,
    val category: String,
    val sourceAlias: String,
    val sourceCrsId: String,
    val isOfficial: Boolean,
    val publishedAt: Long,
    val expiresAt: Long
)
