package com.elv8.crisisos.data.repository

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.elv8.crisisos.core.event.AppEvent
import com.elv8.crisisos.core.event.EventBus
import com.elv8.crisisos.data.dto.MeshPacketType
import com.elv8.crisisos.data.dto.PacketFactory
import com.elv8.crisisos.data.dto.PacketParser
import com.elv8.crisisos.data.dto.payloads.CheckpointPayload
import com.elv8.crisisos.data.local.dao.CheckpointDao
import com.elv8.crisisos.data.local.entity.CheckpointEntity
import com.elv8.crisisos.data.mesh.MeshMessenger
import com.elv8.crisisos.domain.model.Checkpoint
import com.elv8.crisisos.domain.repository.CheckpointRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.firstOrNull
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CheckpointRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: CheckpointDao,
    private val eventBus: EventBus,
    private val messenger: MeshMessenger
) : CheckpointRepository {

    override fun getCheckpoints(): Flow<List<Checkpoint>> {
        return dao.getAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun submitUpdate(checkpoint: Checkpoint) {
        val entity = checkpoint.toEntity()
        dao.insert(entity)

        val senderId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: UUID.randomUUID().toString()
        val sharedPrefs = context.getSharedPreferences("crisisos_prefs", Context.MODE_PRIVATE)
        val senderAlias = sharedPrefs.getString("user_alias", "Survivor_" + Build.MODEL) ?: "Survivor"

        val payload = CheckpointPayload(
            checkpointName = checkpoint.name,
            location = checkpoint.location,
            isOpen = checkpoint.isOpen,
            safetyRating = checkpoint.safetyRating,
            notes = checkpoint.notes
        )

        val packet = PacketFactory.buildCheckpointUpdatePacket(
            senderId = senderId,
            senderAlias = senderAlias,
            payload = payload
        )
        messenger.send(packet)
    }

    override fun observeIncomingUpdates(): Flow<Unit> = flow {
        eventBus.events.filterIsInstance<AppEvent.MeshEvent.RawPacketReceived>().collect { event ->
            val packet = event.packet
            if (packet.type == MeshPacketType.CHECKPOINT_UPDATE) {
                try {
                    val payload = PacketParser.decodePayload(packet, CheckpointPayload.serializer())
                    if (payload != null) {
                        val existing = dao.getAll().firstOrNull()?.find {
                            it.name == payload.checkpointName && it.location == payload.location
                        }
                        
                        if (existing != null) {
                            dao.updateRating(
                                id = existing.id,
                                safetyRating = payload.safetyRating,
                                isOpen = payload.isOpen,
                                notes = payload.notes ?: "",
                                lastUpdated = packet.timestamp
                            )
                            dao.incrementReportCount(existing.id)
                        } else {
                            val newEntity = CheckpointEntity(
                                id = UUID.randomUUID().toString(),
                                name = payload.checkpointName,
                                location = payload.location,
                                controlledBy = "Unknown",
                                safetyRating = payload.safetyRating,
                                isOpen = payload.isOpen,
                                lastReport = "Just now",
                                reportCount = 1,
                                allowsCivilians = false,
                                requiresDocuments = false,
                                notes = payload.notes ?: "",
                                sourceAlias = packet.senderAlias,
                                lastUpdated = packet.timestamp
                            )
                            dao.insert(newEntity)
                        }
                        emit(Unit)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

        override suspend fun purgeStaleReports() {
        val staleTime = System.currentTimeMillis() - (12 * 60 * 60 * 1000L)
        dao.deleteOlderThan(staleTime)
    }

    private fun CheckpointEntity.toDomain() = Checkpoint(
        id = id,
        name = name,
        location = location,
        controlledBy = controlledBy,
        safetyRating = safetyRating,
        isOpen = isOpen,
        lastReport = "Updated recently",
        reportCount = reportCount,
        allowsCivilians = allowsCivilians,
        requiresDocuments = requiresDocuments,
        notes = notes
    )

    private fun Checkpoint.toEntity() = CheckpointEntity(
        id = id,
        name = name,
        location = location,
        controlledBy = controlledBy,
        safetyRating = safetyRating,
        isOpen = isOpen,
        lastReport = lastReport,
        reportCount = reportCount,
        allowsCivilians = allowsCivilians,
        requiresDocuments = requiresDocuments,
        notes = notes,
        sourceAlias = "Local User",
        lastUpdated = System.currentTimeMillis()
    )
}



