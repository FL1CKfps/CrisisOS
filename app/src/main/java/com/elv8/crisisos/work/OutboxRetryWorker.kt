package com.elv8.crisisos.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import com.elv8.crisisos.data.remote.mesh.MeshMessenger
import com.elv8.crisisos.data.remote.mesh.SendResult
import com.elv8.crisisos.domain.repository.OutboxRepository
import com.elv8.crisisos.domain.repository.NewsRepository
import com.elv8.crisisos.domain.repository.CommunityBoardRepository
import com.elv8.crisisos.domain.repository.DangerZoneRepository
import com.elv8.crisisos.domain.repository.CheckpointRepository
import com.elv8.crisisos.data.local.dao.NotificationLogDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class OutboxRetryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val outboxRepository: OutboxRepository,
    private val messenger: MeshMessenger,
    private val notificationLogDao: NotificationLogDao,
    private val newsRepository: NewsRepository,
    private val communityBoardRepository: CommunityBoardRepository,
    private val dangerZoneRepository: DangerZoneRepository,
    private val checkpointRepository: CheckpointRepository
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_TAG = "outbox_retry"

        fun buildPeriodicRequest(): PeriodicWorkRequest {
            return PeriodicWorkRequestBuilder<OutboxRetryWorker>(15, TimeUnit.MINUTES)
                .addTag(WORK_TAG)
                .build()
        }
    }

    override suspend fun doWork(): Result {
        outboxRepository.purgeExpired()
        
        // Cleanup old notifications (older than 7 days)
        notificationLogDao.deleteOlderThan(System.currentTimeMillis() - 7 * 86_400_000L)

        // Spec-driven cleanup of time-windowed feature data.
        runCatching { newsRepository.purgeExpired() }
        runCatching { communityBoardRepository.purgeExpired() }
        runCatching { dangerZoneRepository.purgeStaleReports() }
        runCatching { checkpointRepository.purgeStaleReports() }

        val pendingCount = outboxRepository.getPendingPackets().first().size
        android.util.Log.d("CrisisOS_Outbox", "Outbox retry: $pendingCount pending, <unknown> failed")
        
        outboxRepository.retryFailed()
        
        val pending = outboxRepository.getPendingPackets().first()
        for (packet in pending) {
            val result = messenger.send(packet)
            if (result is SendResult.Sent) {
                outboxRepository.markSent(packet.packetId)
            }
        }
        
        return Result.success()
    }
}
