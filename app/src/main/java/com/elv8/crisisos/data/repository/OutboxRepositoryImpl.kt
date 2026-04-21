package com.elv8.crisisos.data.repository

import com.elv8.crisisos.core.debug.MeshLogger

import com.elv8.crisisos.data.dto.MeshJson
import com.elv8.crisisos.data.dto.MeshPacket
import com.elv8.crisisos.data.local.dao.OutboxDao
import com.elv8.crisisos.data.local.entity.OutboxMessageEntity
import com.elv8.crisisos.domain.repository.OutboxRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class OutboxRepositoryImpl @Inject constructor(
    private val outboxDao: OutboxDao
) : OutboxRepository {

    override suspend fun enqueue(packet: MeshPacket, ttlHours: Int) {
        val now = System.currentTimeMillis()
        val entity = OutboxMessageEntity(
            id = packet.packetId,
            packetJson = MeshJson.encodeToString(MeshPacket.serializer(), packet),
            packetType = packet.type.name,
            priority = packet.priority.ordinal,
            targetId = packet.targetId,
            createdAt = now,
            scheduledAt = now,
            lastAttemptAt = null,
            attemptCount = 0,
            maxAttempts = 5,
            ttlExpiry = now + (ttlHours * 60L * 60 * 1000),
            status = "PENDING",
            failureReason = null
        )
        outboxDao.insert(entity)
    }

    override fun getPendingPackets(): Flow<List<MeshPacket>> {
        return outboxDao.getPendingMessages(System.currentTimeMillis()).map { entities ->
            entities.mapNotNull { entity ->
                try {
                    MeshJson.decodeFromString(MeshPacket.serializer(), entity.packetJson)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    override suspend fun markSent(packetId: String) {
        outboxDao.markSent(packetId)
    }

    override suspend fun markFailed(packetId: String, reason: String) {
        outboxDao.markFailed(packetId, reason)
    }

    override suspend fun purgeExpired() {
        val now = System.currentTimeMillis()
        val deletedCount = outboxDao.deleteExpired(now)
        outboxDao.markExhausted()
        android.util.Log.d("CrisisOS_Outbox", "Purged $deletedCount expired packets")
    }

    override suspend fun retryFailed() {
        outboxDao.resetFailedToPending()
    }
}