package com.elv8.crisisos.data.repository

import com.elv8.crisisos.core.event.AppEvent
import com.elv8.crisisos.core.event.EventBus
import com.elv8.crisisos.data.dto.PacketFactory
import com.elv8.crisisos.data.dto.payloads.SupplyPayload
import com.elv8.crisisos.data.local.dao.SupplyDao
import com.elv8.crisisos.data.local.entity.SupplyRequestEntity
import com.elv8.crisisos.data.mesh.MeshMessenger
import com.elv8.crisisos.data.mesh.SendResult
import com.elv8.crisisos.domain.model.RequestStatus
import com.elv8.crisisos.domain.model.SupplyRequest
import com.elv8.crisisos.data.dto.payloads.SupplyAckPayload
import com.elv8.crisisos.domain.repository.SupplyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupplyRepositoryImpl @Inject constructor(
    private val supplyDao: SupplyDao,
    private val messenger: MeshMessenger,
    private val eventBus: EventBus,
    private val notificationBus: com.elv8.crisisos.core.notification.NotificationEventBus,
    private val scope: kotlinx.coroutines.CoroutineScope
) : SupplyRepository {

    override fun getActiveRequests(): Flow<List<SupplyRequest>> {
        return supplyDao.getAllRequests().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun submitRequest(request: SupplyRequest): SupplyRequest = withContext(Dispatchers.IO) {
        val finalId = if (request.id.isBlank()) UUID.randomUUID().toString() else request.id
        val finalRequest = request.copy(id = finalId, status = RequestStatus.QUEUED)

        supplyDao.insertRequest(finalRequest.toEntity())

        val payload = SupplyPayload(
            supplyType = finalRequest.requestType.name,
            quantity = finalRequest.quantity,
            location = finalRequest.location,
            notes = finalRequest.notes
        )

        val packet = PacketFactory.buildSupplyRequestPacket("local_device", "Local User", payload)

        // Update entity with packetId so we can match ACKs returning from Mesh network
        val updatedRequest = finalRequest.copy(packetId = packet.packetId)
        supplyDao.insertRequest(updatedRequest.toEntity())

        val result = messenger.send(packet)

        val newStatus = when (result) {
            is SendResult.Sent, is SendResult.Queued -> RequestStatus.BROADCASTING
            is SendResult.Failed -> RequestStatus.QUEUED
        }

        val finalRes = updatedRequest.copy(status = newStatus)
        supplyDao.insertRequest(finalRes.toEntity())

        if (finalRes.status == RequestStatus.BROADCASTING) {
            scope.launch {
                notificationBus.emitSupply(
                    com.elv8.crisisos.core.notification.event.NotificationEvent.Supply.RequestQueued(
                        requestId = finalRes.id,
                        supplyType = finalRes.requestType.name
                    )
                )
            }
        }

        finalRes
    }

    override fun observeIncomingAcks(): Flow<SupplyAckPayload> {
        return eventBus.events
            .filterIsInstance<AppEvent.SupplyEvent.AckReceived>()
            .onEach { event ->
                withContext(Dispatchers.IO) {
                    val entity = supplyDao.getRequestByPacketId(event.requestId)
                    if (entity != null) {
                        val updated = entity.copy(
                            status = RequestStatus.CONFIRMED,
                            estimatedDelivery = event.eta,
                            assignedNgo = event.ngoId
                        )
                        supplyDao.insertRequest(updated)

                        scope.launch {
                            notificationBus.emitSupply(
                                com.elv8.crisisos.core.notification.event.NotificationEvent.Supply.RequestFulfilled(
                                    requestId = event.requestId,
                                    supplyType = entity.requestType.name,
                                    meetingPoint = "TBD"
                                )
                            )
                        }
                    }
                }
            }
            .map {
                SupplyAckPayload(
                    requestId = it.requestId,
                    ngoId = it.ngoId,
                    eta = it.eta
                )
            }
    }

    override suspend fun cancelRequest(requestId: String) = withContext(Dispatchers.IO) {
        val entity = supplyDao.getRequestById(requestId)
        if (entity != null) {
            supplyDao.insertRequest(entity.copy(status = RequestStatus.CANCELLED))
        }
    }
}

fun SupplyRequestEntity.toDomainModel(): SupplyRequest {
    return SupplyRequest(
        id = id,
        requestType = requestType,
        quantity = quantity,
        location = location,
        notes = notes,
        status = status,
        createdAt = createdAt,
        estimatedDelivery = estimatedDelivery,
        assignedNgo = assignedNgo,
        packetId = packetId
    )
}

fun SupplyRequest.toEntity(): SupplyRequestEntity {
    return SupplyRequestEntity(
        id = id,
        requestType = requestType,
        quantity = quantity,
        location = location,
        notes = notes,
        status = status,
        createdAt = createdAt,
        estimatedDelivery = estimatedDelivery,
        assignedNgo = assignedNgo,
        packetId = packetId
    )
}
