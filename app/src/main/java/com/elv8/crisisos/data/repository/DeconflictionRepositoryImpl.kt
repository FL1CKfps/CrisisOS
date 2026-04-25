package com.elv8.crisisos.data.repository

import com.elv8.crisisos.core.event.AppEvent
import com.elv8.crisisos.core.event.EventBus
import com.elv8.crisisos.data.dto.MeshJson
import com.elv8.crisisos.data.dto.MeshPacketType
import com.elv8.crisisos.data.dto.PacketFactory
import com.elv8.crisisos.data.dto.PacketParser
import com.elv8.crisisos.data.dto.payloads.DeconflictionPayload
import com.elv8.crisisos.data.local.dao.DeconflictionDao
import com.elv8.crisisos.data.local.entity.DeconflictionReportEntity
import com.elv8.crisisos.data.remote.mesh.MeshMessenger
import com.elv8.crisisos.domain.model.DeconflictionReport
import com.elv8.crisisos.domain.model.ProtectionStatus
import com.elv8.crisisos.domain.model.ReportType
import com.elv8.crisisos.domain.repository.DeconflictionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeconflictionRepositoryImpl @Inject constructor(
    private val dao: DeconflictionDao,
    private val eventBus: EventBus,
    private val messenger: MeshMessenger,
    private val scope: CoroutineScope
) : DeconflictionRepository {

    private val incomingStarted = AtomicBoolean(false)

    override fun observe(): Flow<List<DeconflictionReport>> =
        dao.getAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun submit(report: DeconflictionReport, submittedBy: String) {
        val entity = DeconflictionReportEntity(
            id = report.id,
            reportType = report.reportType.name,
            facilityName = report.facilityName,
            coordinates = report.coordinates,
            protectionStatus = report.protectionStatus.name,
            genevaArticle = report.genevaArticle,
            submittedAt = parseSubmittedAt(report.submittedAt),
            broadcastHash = report.broadcastHash,
            submittedBy = submittedBy
        )
        dao.insert(entity)

        val payload = DeconflictionPayload(
            id = report.id,
            reportType = report.reportType.name,
            facilityName = report.facilityName,
            coordinates = report.coordinates,
            protectionStatus = report.protectionStatus.name,
            genevaArticle = report.genevaArticle,
            submittedAt = entity.submittedAt,
            broadcastHash = report.broadcastHash,
            submittedBy = submittedBy
        )
        val packet = PacketFactory.buildDeconflictionReportPacket(
            senderId = submittedBy,
            senderAlias = submittedBy,
            payloadString = MeshJson.encodeToString(payload)
        )
        messenger.send(packet)
    }

    override fun observeIncoming() {
        if (!incomingStarted.compareAndSet(false, true)) return
        scope.launch(Dispatchers.IO) {
            eventBus.events
                .filterIsInstance<AppEvent.MeshEvent.RawPacketReceived>()
                .collect { event ->
                    if (event.packet.type != MeshPacketType.DECONFLICTION_REPORT) return@collect
                    val payload = PacketParser.decodePayload(event.packet, DeconflictionPayload.serializer())
                        ?: return@collect
                    val existing = dao.getById(payload.id)
                    if (existing != null) return@collect
                    dao.insert(
                        DeconflictionReportEntity(
                            id = payload.id,
                            reportType = payload.reportType,
                            facilityName = payload.facilityName,
                            coordinates = payload.coordinates,
                            protectionStatus = payload.protectionStatus,
                            genevaArticle = payload.genevaArticle,
                            submittedAt = payload.submittedAt,
                            broadcastHash = payload.broadcastHash,
                            submittedBy = payload.submittedBy
                        )
                    )
                }
        }
    }

    private fun parseSubmittedAt(label: String): Long = runCatching {
        SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).parse(label)?.time
    }.getOrNull() ?: System.currentTimeMillis()

    private fun DeconflictionReportEntity.toDomain(): DeconflictionReport {
        val type = runCatching { ReportType.valueOf(reportType) }.getOrDefault(ReportType.MEDICAL_FACILITY)
        val status = runCatching { ProtectionStatus.valueOf(protectionStatus) }.getOrDefault(ProtectionStatus.PROTECTED)
        val label = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(submittedAt))
        return DeconflictionReport(
            id = id,
            reportType = type,
            facilityName = facilityName,
            coordinates = coordinates,
            protectionStatus = status,
            genevaArticle = genevaArticle,
            submittedAt = label,
            broadcastHash = broadcastHash
        )
    }
}
