package com.elv8.crisisos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "checkpoints")
data class CheckpointEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val location: String,
    val controlledBy: String,
    val safetyRating: Int,
    val isOpen: Boolean,
    val lastReport: String,
    val reportCount: Int,
    val allowsCivilians: Boolean,
    val requiresDocuments: Boolean,
    val notes: String,
    val sourceAlias: String,
    val lastUpdated: Long
)
