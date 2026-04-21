package com.elv8.crisisos.domain.repository

import com.elv8.crisisos.data.dto.MeshPacket
import kotlinx.coroutines.flow.Flow

interface OutboxRepository {
    suspend fun enqueue(packet: MeshPacket, ttlHours: Int = 72)
    fun getPendingPackets(): Flow<List<MeshPacket>>
    suspend fun markSent(packetId: String)
    suspend fun markFailed(packetId: String, reason: String)
    suspend fun purgeExpired()
    suspend fun retryFailed()
}