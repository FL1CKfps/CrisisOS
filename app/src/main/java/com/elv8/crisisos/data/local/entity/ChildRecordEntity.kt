package com.elv8.crisisos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.elv8.crisisos.domain.model.ChildStatus

@Entity(tableName = "child_records")
data class ChildRecordEntity(
    @PrimaryKey val crsChildId: String,
    val childName: String,
    val approximateAge: Int,
    val physicalDescription: String,
    val lastKnownLocation: String,
    val registeredBy: String,
    val registeredAt: Long,
    val status: String, // String representation of ChildStatus
    val locatedAt: String?,
    val broadcastCount: Int
)