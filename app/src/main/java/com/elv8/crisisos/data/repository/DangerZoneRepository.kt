package com.elv8.crisisos.data.repository

import com.elv8.crisisos.core.event.AppEvent
import com.elv8.crisisos.core.event.EventBus
import com.elv8.crisisos.data.dto.MeshPacketType
import com.elv8.crisisos.data.dto.PacketFactory
import com.elv8.crisisos.data.dto.PacketParser
import com.elv8.crisisos.data.dto.payloads.DangerPayload
import com.elv8.crisisos.data.local.dao.DangerZoneDao
import com.elv8.crisisos.data.local.entity.DangerZoneEntity
import com.elv8.crisisos.data.remote.api.dto.AcledEvent
import com.elv8.crisisos.data.remote.mesh.MeshMessenger
import com.elv8.crisisos.domain.model.AggregatedDangerZone
import com.elv8.crisisos.domain.model.DangerSource
import com.elv8.crisisos.domain.model.DangerZone
import com.elv8.crisisos.domain.model.ThreatLevel
import com.elv8.crisisos.domain.repository.DangerZoneRepository
import com.elv8.crisisos.domain.repository.IdentityRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos
import kotlin.math.floor

@Singleton
class DangerZoneRepositoryImpl @Inject constructor(
    private val dao: DangerZoneDao,
    private val eventBus: EventBus,
    private val messenger: MeshMessenger,
    private val intel: CrisisIntelRepository,
    private val identityRepository: IdentityRepository,
    private val scope: CoroutineScope
) : DangerZoneRepository {

    override fun getDangerZones(): Flow<List<DangerZone>> {
        return dao.getAllDangerZones().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Aggregate raw entries into map-ready zones.
     *
     * Crowdsourced (reportedBy != "ACLED"):
     *   - Filter to last [CROWDSOURCED_WINDOW_MS] (= 2 h).
     *   - Group by 1 km² grid cell.
     *   - Count unique reporters per cell.
     *   - 3+ unique → CRITICAL (red, 600 m).
     *   - 1-2     → MEDIUM   (orange, 300 m, "unverified").
     *
     * ACLED rows are emitted one-per-event (CRITICAL, 800 m radius).
     */
    override fun aggregateForMap(): Flow<List<AggregatedDangerZone>> {
        return dao.getAllDangerZones().map { entities ->
            val now = System.currentTimeMillis()
            val (acled, crowd) = entities.partition { it.reportedBy == ACLED_TAG }

            val acledZones = acled.map { e ->
                AggregatedDangerZone(
                    id = "ACLED:${e.id}",
                    centerLat = e.latitude,
                    centerLon = e.longitude,
                    radiusMeters = ACLED_RADIUS_M,
                    severity = ThreatLevel.CRITICAL,
                    title = e.title.ifBlank { "Conflict event" },
                    description = e.description,
                    source = DangerSource.ACLED,
                    confirmedReports = 1,
                    firstReportedAt = e.timestamp,
                    lastReportedAt = e.timestamp
                )
            }

            val recentCrowd = crowd.filter { now - it.timestamp <= CROWDSOURCED_WINDOW_MS }
            val byCell = recentCrowd.groupBy { gridKey(it.latitude, it.longitude) }
            val crowdZones = byCell.map { (cellKey, reports) ->
                val uniqueReporters = reports.map { it.reportedBy }.toSet().size
                val isRed = uniqueReporters >= RED_THRESHOLD
                val centerLat = reports.map { it.latitude }.average()
                val centerLon = reports.map { it.longitude }.average()
                val newest = reports.maxByOrNull { it.timestamp }
                val oldest = reports.minByOrNull { it.timestamp }
                AggregatedDangerZone(
                    id = "GRID:$cellKey",
                    centerLat = centerLat,
                    centerLon = centerLon,
                    radiusMeters = if (isRed) CROWD_RED_RADIUS_M else CROWD_ORANGE_RADIUS_M,
                    severity = if (isRed) ThreatLevel.CRITICAL else ThreatLevel.MEDIUM,
                    title = if (isRed) {
                        "Confirmed danger zone"
                    } else {
                        "Unverified danger report"
                    },
                    description = newest?.description.orEmpty().ifBlank {
                        "$uniqueReporters report(s) in last 2h"
                    },
                    source = DangerSource.CROWDSOURCED,
                    confirmedReports = uniqueReporters,
                    firstReportedAt = oldest?.timestamp ?: now,
                    lastReportedAt = newest?.timestamp ?: now
                )
            }

            acledZones + crowdZones
        }
    }

    override suspend fun reportZone(zone: DangerZone) {
        // Use the local CRS ID (the same pseudonymous mesh identifier the rest
        // of the app uses) instead of Settings.Secure.ANDROID_ID — that one is
        // a persistent, cross-app device identifier and would leak the device
        // across peers and the local Room mirror.
        val identity = identityRepository.getIdentity().first()
        val senderId = identity?.crsId ?: LOCAL_FALLBACK_ID
        val senderAlias = identity?.alias ?: LOCAL_FALLBACK_ALIAS

        // Stamp the report with the sender's CRS id so the aggregation rule can
        // count "unique reporters per 1 km² cell" — without this every device
        // would only ever count once toward the 3-confirmation threshold.
        val entity = DangerZoneEntity(
            id = zone.id.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            title = zone.title,
            description = zone.description,
            threatLevel = zone.threatLevel,
            reportedBy = senderId,
            timestamp = System.currentTimeMillis(),
            latitude = zone.coordinates.first,
            longitude = zone.coordinates.second
        )
        dao.insertDangerZone(entity)

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

    override suspend fun syncFromAcled(country: String, lookbackDays: Int): Int {
        val until = todayIso()
        val since = isoDaysAgo(lookbackDays.coerceAtLeast(1))
        val events = intel.recentConflictEvents(country, since, until)
        if (events.isEmpty()) return 0

        val rows = events.mapNotNull { it.toEntityOrNull() }
        if (rows.isEmpty()) return 0

        // Refresh window: drop ACLED rows older than the lookback window before
        // re-inserting, so the dataset always reflects the latest pull.
        val cutoff = System.currentTimeMillis() - lookbackDays * 86_400_000L
        dao.deleteAcledOlderThan(cutoff)
        dao.insertAll(rows)
        return rows.size
    }

    private val observerStarted = AtomicBoolean(false)

    override fun observeIncomingReports() {
        // Atomic compare-and-set so concurrent ViewModel inits (config change,
        // re-entry) cannot race past a plain @Volatile flag and double-subscribe.
        if (!observerStarted.compareAndSet(false, true)) return
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
                                    threatLevel = runCatching {
                                        ThreatLevel.valueOf(payload.threatLevel)
                                    }.getOrDefault(ThreatLevel.MEDIUM),
                                    // Keep the originating sender so the aggregator can count
                                    // unique CRS IDs across the mesh, not just the local device.
                                    reportedBy = packet.senderId,
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
        // Spec: a crowdsourced danger report ages out after 6 h unless re-confirmed.
        // ACLED rows are kept for 7 days and replaced on the next sync.
        val crowdCutoff = System.currentTimeMillis() - CROWDSOURCED_TTL_MS
        val acledCutoff = System.currentTimeMillis() - ACLED_TTL_MS
        dao.deleteCrowdsourcedOlderThan(crowdCutoff)
        dao.deleteAcledOlderThan(acledCutoff)
    }

    // ---------- helpers ----------

    private fun AcledEvent.toEntityOrNull(): DangerZoneEntity? {
        val lat = latitude.toDoubleOrNull() ?: return null
        val lon = longitude.toDoubleOrNull() ?: return null
        if (lat == 0.0 && lon == 0.0) return null
        val ts = parseEventDate(eventDate) ?: System.currentTimeMillis()
        val title = listOfNotNull(eventType.takeIf { it.isNotBlank() }, location.takeIf { it.isNotBlank() })
            .joinToString(" — ")
            .ifBlank { "ACLED conflict event" }
        val description = listOfNotNull(
            subEventType.takeIf { it.isNotBlank() },
            notes.takeIf { it.isNotBlank() }?.take(240)
        ).joinToString(" · ")
        return DangerZoneEntity(
            id = "ACLED-$eventIdCnty",
            title = title,
            description = description,
            threatLevel = ThreatLevel.CRITICAL,
            reportedBy = ACLED_TAG,
            timestamp = ts,
            latitude = lat,
            longitude = lon
        )
    }

    private fun parseEventDate(value: String): Long? {
        if (value.isBlank()) return null
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            fmt.parse(value)?.time
        } catch (_: Throwable) { null }
    }

    private fun todayIso(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return fmt.format(Date())
    }

    private fun isoDaysAgo(days: Int): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return fmt.format(Date(System.currentTimeMillis() - days * 86_400_000L))
    }

    /**
     * Approximate a 1 km² grid cell key.
     * 1° latitude ≈ 111 km → 1 km ≈ 0.009°.
     * Longitude is corrected for latitude with cos(lat) so cells stay ~square.
     */
    private fun gridKey(lat: Double, lon: Double): String {
        val latStep = 1.0 / 111.0
        val cosLat = cos(Math.toRadians(lat)).let { if (it < 0.01) 0.01 else it }
        val lonStep = latStep / cosLat
        val gLat = floor(lat / latStep).toLong()
        val gLon = floor(lon / lonStep).toLong()
        return "$gLat:$gLon"
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

    companion object {
        const val ACLED_TAG = "ACLED"
        const val CROWDSOURCED_WINDOW_MS = 2L * 60L * 60L * 1000L          // 2 h aggregation window
        const val CROWDSOURCED_TTL_MS    = 6L * 60L * 60L * 1000L          // 6 h auto-expire
        const val ACLED_TTL_MS           = 7L * 24L * 60L * 60L * 1000L    // 7 d retention for ACLED
        const val RED_THRESHOLD = 3                                        // unique reporters needed
        const val ACLED_RADIUS_M       = 800.0
        const val CROWD_RED_RADIUS_M   = 600.0
        const val CROWD_ORANGE_RADIUS_M = 300.0
        private const val LOCAL_FALLBACK_ID = "local_device"
        private const val LOCAL_FALLBACK_ALIAS = "Survivor"
    }
}
