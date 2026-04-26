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
import com.elv8.crisisos.data.remote.mesh.MeshMessenger
import com.elv8.crisisos.domain.model.Checkpoint
import com.elv8.crisisos.domain.model.DocumentsRequired
import com.elv8.crisisos.domain.model.CheckpointThreat
import com.elv8.crisisos.domain.model.WaitTime
import com.elv8.crisisos.domain.repository.CheckpointRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    /**
     * Serializes the read-modify-write tally aggregation across all
     * callers (local submissions + incoming-mesh observers). The DAO's
     * @Transaction-wrapped `applyAggregateUpdate` covers atomicity of
     * the WRITE pair (updateThreatReport + incrementReportCount), and
     * this mutex covers the READ→COMPUTE→WRITE round-trip so two
     * concurrent reports cannot both read the same baseline tally and
     * silently lose one increment.
     */
    private val voteMutex = Mutex()

    override fun getCheckpoints(): Flow<List<Checkpoint>> {
        return dao.getAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Submits a Feature-7 checkpoint report. Persists locally first
     * (so the user sees their report instantly even when fully offline)
     * then broadcasts a CHECKPOINT_UPDATE packet on the mesh.
     *
     * Aggregation: every report is a single vote across each of the
     * three enum dimensions (threat / docs / wait). The displayed
     * aggregate is recomputed by argmax over the cumulative tally,
     * so a single newcomer cannot flip the threat status away from
     * an established majority — this is the spec's anti-misuse rule.
     *
     * Grid-level dedupe: when checkpointId == null (REPORT button) we
     * still try to fold into an existing row matching the same grid
     * label so reports coalesce instead of fragmenting.
     */
    override suspend fun submitUpdate(checkpoint: Checkpoint) {
        val now = System.currentTimeMillis()
        voteMutex.withLock {
            val all = dao.getAll().firstOrNull().orEmpty()

            val existingMatch = all.firstOrNull { it.id == checkpoint.id }
                ?: all.firstOrNull {
                    it.location.normalizedGrid() == checkpoint.location.normalizedGrid() &&
                        it.name.normalizedName() == checkpoint.name.normalizedName()
                }
                ?: all.firstOrNull {
                    it.location.normalizedGrid() == checkpoint.location.normalizedGrid()
                }

            if (existingMatch != null) {
                applyVoteLocked(
                    existing = existingMatch,
                    threat = checkpoint.threatLevel,
                    docs = checkpoint.docsRequired,
                    wait = checkpoint.waitTime,
                    notes = checkpoint.notes,
                    packetTimestamp = now,
                    ngoVerifiedThisReport = false // local user is never NGO-trusted
                )
            } else {
                // First-ever report for this grid — seed entity with vote count 1
                // on the chosen indices and reportCount = 1 (so the UI immediately
                // shows "UNVERIFIED · 1 report" rather than "0 reports").
                val seedThreat = bumpVotes(EMPTY_THREAT_VOTES, checkpoint.threatLevel.ordinal)
                val seedDocs = bumpVotes(EMPTY_DOCS_VOTES, checkpoint.docsRequired.ordinal)
                val seedWait = bumpVotes(EMPTY_WAIT_VOTES, checkpoint.waitTime.ordinal)
                dao.insert(
                    checkpoint.toEntity().copy(
                        lastUpdated = now,
                        reportCount = 1,
                        threatVotes = seedThreat,
                        docsVotes = seedDocs,
                        waitVotes = seedWait
                    )
                )
            }
        }

        val senderId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: UUID.randomUUID().toString()
        val sharedPrefs = context.getSharedPreferences("crisisos_prefs", Context.MODE_PRIVATE)
        val senderAlias = sharedPrefs.getString("user_alias", "Survivor_" + Build.MODEL) ?: "Survivor"

        val payload = CheckpointPayload(
            checkpointName = checkpoint.name,
            location = checkpoint.location,
            isOpen = checkpoint.isOpen,
            safetyRating = checkpoint.safetyRating,
            notes = checkpoint.notes,
            threatLevel = checkpoint.threatLevel.name,
            docsRequired = checkpoint.docsRequired.name,
            waitTime = checkpoint.waitTime.name
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
                        val incomingThreat = CheckpointThreat.fromStorage(payload.threatLevel)
                        val incomingDocs = DocumentsRequired.fromStorage(payload.docsRequired)
                        val incomingWait = WaitTime.fromStorage(payload.waitTime)

                        // NGO trust override (spec): an NGO sender flagging a
                        // checkpoint SAFE marks it NGO_VERIFIED for everyone.
                        val ngoVerifiedThisReport = isNgoAlias(packet.senderAlias) &&
                            incomingThreat == CheckpointThreat.SAFE

                        voteMutex.withLock {
                            val existing = dao.getAll().firstOrNull()?.firstOrNull {
                                it.location.normalizedGrid() == payload.location.normalizedGrid() &&
                                    it.name.normalizedName() == payload.checkpointName.normalizedName()
                            }

                            if (existing != null) {
                                applyVoteLocked(
                                    existing = existing,
                                    threat = incomingThreat,
                                    docs = incomingDocs,
                                    wait = incomingWait,
                                    notes = payload.notes ?: "",
                                    packetTimestamp = packet.timestamp,
                                    ngoVerifiedThisReport = ngoVerifiedThisReport
                                )
                            } else {
                                val seedThreat = bumpVotes(EMPTY_THREAT_VOTES, incomingThreat.ordinal)
                                val seedDocs = bumpVotes(EMPTY_DOCS_VOTES, incomingDocs.ordinal)
                                val seedWait = bumpVotes(EMPTY_WAIT_VOTES, incomingWait.ordinal)
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
                                    lastUpdated = packet.timestamp,
                                    threatLevel = incomingThreat.name,
                                    docsRequired = incomingDocs.name,
                                    waitTime = incomingWait.name,
                                    verifiedByNgo = ngoVerifiedThisReport,
                                    threatVotes = seedThreat,
                                    docsVotes = seedDocs,
                                    waitVotes = seedWait
                                )
                                dao.insert(newEntity)
                            }
                        }
                        emit(Unit)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * Per spec: Reports auto-expire after 2 hours. Because the cleanup
     * worker runs periodically (not continuously), the UI also re-checks
     * expiry on each render; this DB call is the durable backstop.
     */
    override suspend fun purgeStaleReports() {
        val staleTime = System.currentTimeMillis() - REPORT_TTL_MS
        dao.deleteOlderThan(staleTime)
    }

    /**
     * Caller MUST hold `voteMutex` before invoking this method.
     * Applies one vote to the existing aggregate, recomputes argmax
     * across each enum dimension, and persists the new aggregate +
     * the bumped tally CSVs + the report-count bump in a single Room
     * transaction (via `dao.applyAggregateUpdate`). The latest
     * report's `notes` overwrites the displayed note (most recent
     * wins for the one free-text field; anonymity is preserved
     * because the senderId is never persisted alongside the note).
     */
    private suspend fun applyVoteLocked(
        existing: CheckpointEntity,
        threat: CheckpointThreat,
        docs: DocumentsRequired,
        wait: WaitTime,
        notes: String,
        packetTimestamp: Long,
        ngoVerifiedThisReport: Boolean
    ) {
        val newThreatVotes = bumpVotes(existing.threatVotes, threat.ordinal)
        val newDocsVotes = bumpVotes(existing.docsVotes, docs.ordinal)
        val newWaitVotes = bumpVotes(existing.waitVotes, wait.ordinal)

        val winningThreat = CheckpointThreat.entries[argmaxIndex(newThreatVotes, fallback = CheckpointThreat.fromStorage(existing.threatLevel).ordinal)]
        val winningDocs = DocumentsRequired.entries[argmaxIndex(newDocsVotes, fallback = DocumentsRequired.fromStorage(existing.docsRequired).ordinal)]
        val winningWait = WaitTime.entries[argmaxIndex(newWaitVotes, fallback = WaitTime.fromStorage(existing.waitTime).ordinal)]

        val effectiveOpen = winningThreat != CheckpointThreat.HOSTILE && winningWait != WaitTime.BLOCKED
        val effectiveSafety = when (winningThreat) {
            CheckpointThreat.SAFE -> 5
            CheckpointThreat.UNKNOWN -> 3
            CheckpointThreat.HOSTILE -> 1
        }

        dao.applyAggregateUpdate(
            id = existing.id,
            threatLevel = winningThreat.name,
            docsRequired = winningDocs.name,
            waitTime = winningWait.name,
            threatVotes = newThreatVotes,
            docsVotes = newDocsVotes,
            waitVotes = newWaitVotes,
            isOpen = effectiveOpen,
            safetyRating = effectiveSafety,
            notes = notes,
            lastUpdated = packetTimestamp,
            verifiedByNgo = ngoVerifiedThisReport
        )
    }

    /**
     * Aligned 1:1 with NewsRepositoryImpl.isNgoAlias() — strict prefix /
     * suffix match, NOT substring containment. Substring matching would
     * let any user with the word "verified" or "official" anywhere in
     * their alias spoof an NGO trust override (which controls the
     * "NGO VERIFIED SAFE" badge and bypasses the ≥2-report threshold).
     */
    private fun isNgoAlias(alias: String?): Boolean {
        if (alias.isNullOrBlank()) return false
        return alias.startsWith("NGO_") || alias.endsWith("_OFFICIAL")
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
        notes = notes,
        threatLevel = CheckpointThreat.fromStorage(threatLevel),
        docsRequired = DocumentsRequired.fromStorage(docsRequired),
        waitTime = WaitTime.fromStorage(waitTime),
        verifiedByNgo = verifiedByNgo,
        lastUpdatedAt = lastUpdated
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
        lastUpdated = lastUpdatedAt,
        threatLevel = threatLevel.name,
        docsRequired = docsRequired.name,
        waitTime = waitTime.name,
        verifiedByNgo = verifiedByNgo
    )

    companion object {
        // 2 hours in milliseconds — Feature 7 spec.
        const val REPORT_TTL_MS: Long = 2L * 60L * 60L * 1000L

        private const val EMPTY_THREAT_VOTES = "0,0,0"        // SAFE,HOSTILE,UNKNOWN
        private const val EMPTY_DOCS_VOTES = "0,0,0,0"        // NONE,ID,PASSPORT,MULTIPLE
        private const val EMPTY_WAIT_VOTES = "0,0,0,0"        // UNDER_15M,15-60,OVER_60,BLOCKED

        // -- Vote-tally helpers --

        internal fun parseVotes(csv: String, expectedSize: Int): IntArray {
            val parts = csv.split(",")
            val out = IntArray(expectedSize)
            for (i in 0 until minOf(parts.size, expectedSize)) {
                out[i] = parts[i].toIntOrNull()?.coerceAtLeast(0) ?: 0
            }
            return out
        }

        internal fun bumpVotes(csv: String, index: Int): String {
            val expected = csv.count { it == ',' } + 1
            val arr = parseVotes(csv, expected)
            if (index in arr.indices) arr[index]++
            return arr.joinToString(",")
        }

        /**
         * Returns the index of the maximum-voted bucket. Ties are
         * broken by `fallback` — typically the previous aggregate's
         * index — so an even split between, say, SAFE and HOSTILE
         * leaves the established reading intact instead of letting
         * the order of arrival decide.
         */
        internal fun argmaxIndex(csv: String, fallback: Int): Int {
            val expected = csv.count { it == ',' } + 1
            val arr = parseVotes(csv, expected)
            val max = arr.maxOrNull() ?: 0
            if (max == 0) return fallback
            val winners = arr.indices.filter { arr[it] == max }
            return when {
                winners.size == 1 -> winners.first()
                fallback in winners -> fallback
                else -> winners.first()
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Grid + name normalization helpers                                 */
/* ------------------------------------------------------------------ */

private fun String.normalizedGrid(): String =
    trim().lowercase().replace(Regex("\\s+"), " ")

private fun String.normalizedName(): String =
    trim().lowercase().replace(Regex("\\s+"), " ")
