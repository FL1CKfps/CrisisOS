package com.elv8.crisisos.domain.model

enum class ChildStatus {
    SEARCHING,
    LOCATED,
    REUNITED
}

data class ChildRecord(
    val crsChildId: String,
    val childName: String,
    val approximateAge: Int,
    val physicalDescription: String,
    val lastKnownLocation: String,
    val registeredBy: String,
    val registeredAt: Long,
    val status: ChildStatus,
    val locatedAt: String?,
    val broadcastCount: Int
)