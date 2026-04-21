package com.elv8.crisisos.domain.repository

import kotlinx.coroutines.flow.Flow
import com.elv8.crisisos.domain.model.ChatMessage
import com.elv8.crisisos.domain.model.SupplyRequest
import com.elv8.crisisos.ui.screens.missingperson.RegisteredPerson

interface MeshRepository {
    fun getMessages(): Flow<List<ChatMessage>>
    suspend fun sendMessage(message: ChatMessage)
    fun observeIncomingMessages(): Flow<ChatMessage>
}

interface SosRepository {
    // TODO: Define SOS broadcast and trigger operations
}

interface MissingPersonRepository {
    fun getRegisteredPersons(): Flow<List<RegisteredPerson>>
    fun searchPersons(query: String): Flow<List<RegisteredPerson>>
    suspend fun registerPerson(person: RegisteredPerson)
    fun observeIncomingPersonData(): Flow<Unit>
}

interface SupplyRepository {
    fun getActiveRequests(): Flow<List<SupplyRequest>>
    suspend fun submitRequest(request: SupplyRequest): SupplyRequest
    fun observeIncomingAcks(): Flow<com.elv8.crisisos.data.dto.payloads.SupplyAckPayload>
    suspend fun cancelRequest(requestId: String)
}

interface DeadManRepository {
    // TODO: Define dead man switch scheduling and execution
}

interface CheckpointRepository {
    fun getCheckpoints(): kotlinx.coroutines.flow.Flow<List<com.elv8.crisisos.domain.model.Checkpoint>>
    suspend fun submitUpdate(checkpoint: com.elv8.crisisos.domain.model.Checkpoint)
    fun observeIncomingUpdates(): kotlinx.coroutines.flow.Flow<Unit>
    suspend fun purgeStaleReports()
}

interface DangerZoneRepository {
    fun getDangerZones(): kotlinx.coroutines.flow.Flow<List<com.elv8.crisisos.domain.model.DangerZone>>
    suspend fun reportZone(zone: com.elv8.crisisos.domain.model.DangerZone)
    fun observeIncomingReports()
    suspend fun purgeStaleReports()
}



