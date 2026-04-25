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

    /**
     * Map-ready danger zones. Crowdsourced reports are aggregated by 1 km²
     * grid + 2 h sliding window per CrisisOS_Context.md Feature 2:
     *   - 3+ unique CRS IDs in same cell → CRITICAL (red)
     *   - 1-2 reports → MEDIUM (orange, "unverified")
     * ACLED entries are emitted one-per-event (always CRITICAL, not aggregated).
     */
    fun aggregateForMap(): kotlinx.coroutines.flow.Flow<List<com.elv8.crisisos.domain.model.AggregatedDangerZone>>

    suspend fun reportZone(zone: com.elv8.crisisos.domain.model.DangerZone)

    /**
     * Pull recent ACLED conflict events for [country] over the last [lookbackDays]
     * days and persist them as danger-zone entries (reportedBy = "ACLED").
     * Safe to call repeatedly — duplicates are de-duplicated by event id.
     * Returns the number of entries inserted/updated, 0 on failure.
     */
    suspend fun syncFromAcled(country: String, lookbackDays: Int): Int

    fun observeIncomingReports()
    suspend fun purgeStaleReports()
}



