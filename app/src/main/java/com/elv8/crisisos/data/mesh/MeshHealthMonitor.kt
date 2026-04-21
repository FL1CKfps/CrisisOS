package com.elv8.crisisos.data.mesh

import android.util.Log
import com.elv8.crisisos.core.debug.MeshLogger
import com.elv8.crisisos.data.local.dao.OutboxDao
import com.elv8.crisisos.domain.repository.OutboxRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class MeshHealthStats(
    val pendingCount: Int,
    val failedCount: Int,
    val sentCount: Int,
    val connectedPeers: Int,
    val isHealthy: Boolean
)

@Singleton
class MeshHealthMonitor @Inject constructor(
    private val outboxRepository: OutboxRepository,
    private val outboxDao: OutboxDao,
    private val connectionManager: MeshConnectionManager,
    private val scope: CoroutineScope
) {
    private val _stats = MutableStateFlow(MeshHealthStats(0, 0, 0, 0, true))
    val stats: StateFlow<MeshHealthStats> = _stats.asStateFlow()

    init {
        scope.launch {
            while (true) {
                runHealthCheck()
                delay(60_000L) // 60 seconds
            }
        }
    }

    suspend fun runHealthCheck() {
        outboxRepository.purgeExpired()

        val pending = outboxDao.countByStatus("PENDING")
        val failed = outboxDao.countByStatus("FAILED")
        val sent = outboxDao.countByStatus("SENT")
        val peers = connectionManager.connectedPeers.value.size

        val healthy = pending < 50 && peers >= 0

        _stats.value = MeshHealthStats(
            pendingCount = pending,
            failedCount = failed,
            sentCount = sent,
            connectedPeers = peers,
            isHealthy = healthy
        )

        MeshLogger.heartbeat("Health check: stats=${_stats.value}")
    }
}