package com.elv8.crisisos.data.repository

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.elv8.crisisos.core.event.AppEvent
import com.elv8.crisisos.core.event.EventBus
import com.elv8.crisisos.data.dto.MeshPacketType
import com.elv8.crisisos.data.dto.PacketFactory
import com.elv8.crisisos.data.dto.PacketParser
import com.elv8.crisisos.data.dto.payloads.DangerPayload
import com.elv8.crisisos.data.local.dao.DangerZoneDao
import com.elv8.crisisos.data.local.entity.DangerZoneEntity
import com.elv8.crisisos.data.remote.mesh.MeshMessenger
import com.elv8.crisisos.domain.model.DangerZone
import com.elv8.crisisos.domain.model.ThreatLevel
import com.elv8.crisisos.domain.repository.DangerZoneRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DangerZoneRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DangerZoneDao,
    private val eventBus: EventBus,
    private val messenger: MeshMessenger,
    private val scope: CoroutineScope
) : DangerZoneRepository {

    override fun getDangerZones(): Flow<List<DangerZone>> {
        return dao.getAllDangerZones().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun reportZone(zone: DangerZone) {
        val entity = zone.toEntity()
        dao.insertDangerZone(entity)

        val senderId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: UUID.randomUUID().toString()
        val sharedPrefs = context.getSharedPreferences("crisisos_prefs", Context.MODE_PRIVATE)
        val senderAlias = sharedPrefs.getString("user_alias", "Survivor_" + Build.MODEL) ?: "Survivor"

        // Create Payload
        val coords = "${zone.coordinates.first},${zone.coordinates.second}"
        val payload = DangerPayload(
            title = zone.title,
            description = zone.description,
            threatLevel = zone.threatLevel.name,
            coordinates = coords
        )

        val packet = PacketFactory.buildDangerReportPacket(
            senderId = senderId,
            senderAlias = senderAlias,
            payload = payload
        )
        messenger.send(packet)
    }

    override fun observeIncomingReports() {
        scope.launch(Dispatchers.IO) {
            eventBus.events.collect { event ->
                if (event is AppEvent.MeshEvent.RawPacketReceived) {
                    val packet = event.packet
                    if (packet.type == MeshPacketType.DANGER_REPORT) {
                        try {
                            val payload = PacketParser.decodePayload(packet, DangerPayload.serializer())
                            if (payload != null) {
                                val coords = payload.coordinates?.split(",")
                                val lat = coords?.getOrNull(0)?.toDoubleOrNull() ?: 0.0
                                val lng = coords?.getOrNull(1)?.toDoubleOrNull() ?: 0.0
                                
                                val entity = DangerZoneEntity(
                                    id = packet.packetId,
                                    title = payload.title,
                                    description = payload.description,
                                    threatLevel = ThreatLevel.valueOf(payload.threatLevel),
                                    reportedBy = packet.senderAlias,
                                    timestamp = packet.timestamp,
                                    latitude = lat,
                                    longitude = lng
                                )
                                dao.insertDangerZone(entity)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    override suspend fun purgeStaleReports() {
        // Spec: a danger report ages out after 6 hours unless re-confirmed.
        val staleTime = System.currentTimeMillis() - (6 * 60 * 60 * 1000L)
        dao.deleteOlderThan(staleTime)
    }

    private fun DangerZoneEntity.toDomain() = DangerZone(
        id = id,
        title = title,
        description = description,
        threatLevel = threatLevel,
        distance = "Unknown",
        reportedBy = reportedBy,
        timestamp = timestamp.toString(),
        coordinates = Pair(latitude, longitude)
    )

    private fun DangerZone.toEntity() = DangerZoneEntity(
        id = id,
        title = title,
        description = description,
        threatLevel = threatLevel,
        reportedBy = reportedBy,
        timestamp = timestamp.toLongOrNull() ?: System.currentTimeMillis(),
        latitude = coordinates.first,
        longitude = coordinates.second
    )
}
