package com.elv8.crisisos.ui.screens.maps

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elv8.crisisos.core.event.AppEvent
import com.elv8.crisisos.core.event.EventBus
import com.elv8.crisisos.domain.location.CrisisLocation
import com.elv8.crisisos.domain.model.AggregatedDangerZone
import com.elv8.crisisos.domain.model.DangerSource
import com.elv8.crisisos.domain.model.DangerZone
import com.elv8.crisisos.domain.model.SafeZone
import com.elv8.crisisos.domain.model.ThreatLevel
import com.elv8.crisisos.domain.model.ZoneStatus
import com.elv8.crisisos.domain.model.status
import com.elv8.crisisos.domain.repository.DangerZoneRepository
import com.elv8.crisisos.domain.repository.LocationRepository
import com.elv8.crisisos.domain.repository.SafeZoneRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class MapMode { MAP, LIST }

data class CapacityHint(
    val zoneId: String,
    val zoneName: String,
    val ratio: Float,
    val timestamp: Long
)

data class MapsUiState(
    val safeZones: List<SafeZone> = emptyList(),
    val dangerZones: List<AggregatedDangerZone> = emptyList(),
    val selectedZone: SafeZone? = null,
    val mapMode: MapMode = MapMode.MAP,
    val isOffline: Boolean = true,
    val userLocation: CrisisLocation? = null,
    /** Coordinate the map should animate to — paired with [centerRequest] which forces re-trigger. */
    val mapCenter: Pair<Double, Double>? = null,
    /** Monotonic counter incremented every time we want the map to animate to [mapCenter]. */
    val centerRequest: Long = 0L,
    /** Optional zoom level paired with [centerRequest]; null = keep current zoom. */
    val centerZoom: Double? = null,
    /** Nearest open zone after danger-zone avoidance — used to render the suggested-route polyline. */
    val nearestOpenZone: SafeZone? = null,
    /** True when the suggested route had to skip a closer zone because the direct line crossed a red zone. */
    val routeWasRerouted: Boolean = false,
    /** Most recent "camp near capacity" hint (Feature 2 § 95% broadcast). Auto-clears after 30s. */
    val capacityHint: CapacityHint? = null,
    /** Last user-facing toast / status message — consumed and cleared by the screen. */
    val transientMessage: String? = null
)

@HiltViewModel
class MapsViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
    private val safeZoneRepository: SafeZoneRepository,
    private val dangerZoneRepository: DangerZoneRepository,
    private val eventBus: EventBus
) : ViewModel() {
    private val _uiState = MutableStateFlow(MapsUiState())
    val uiState: StateFlow<MapsUiState> = _uiState.asStateFlow()

    /** Have we already auto-centered on the user's first GPS fix? */
    private var initialCenterApplied = false

    /** Have we already kicked off the one-shot ACLED sync this process? */
    private var acledSyncStarted = false

    init {
        // First-run shard seed using the configured map default. Once NGOs broadcast
        // real capacity over the mesh those rows replace the seed.
        viewModelScope.launch {
            safeZoneRepository.seedDefaultsIfEmpty(
                centerLat = com.elv8.crisisos.core.map.MapConfiguration.DEFAULT_LATITUDE,
                centerLon = com.elv8.crisisos.core.map.MapConfiguration.DEFAULT_LONGITUDE
            )
        }

        // Listen for incoming mesh DANGER_REPORT packets so peer reports also
        // count toward the 1 km² grid aggregation. Idempotent — repo guards
        // against double-subscription.
        dangerZoneRepository.observeIncomingReports()

        // Drop expired crowdsourced reports on every screen open.
        viewModelScope.launch { dangerZoneRepository.purgeStaleReports() }

        // Observe persisted safe zones and recompute distance whenever either set changes.
        viewModelScope.launch {
            safeZoneRepository.observe().collect { zones -> applyZones(zones) }
        }

        // Observe aggregated danger zones (1 km² grid + ACLED) and re-route on change.
        viewModelScope.launch {
            dangerZoneRepository.aggregateForMap().collect { dangers ->
                _uiState.update { state ->
                    val (nearest, rerouted) = pickNearestAvoiding(state.safeZones, state.userLocation, dangers)
                    state.copy(
                        dangerZones = dangers,
                        nearestOpenZone = nearest,
                        routeWasRerouted = rerouted
                    )
                }
            }
        }

        // Surface the 95% capacity broadcast. The auto-dismiss is fired in a
        // child coroutine so the collector never blocks — back-to-back camp
        // updates from multiple NGOs each get their own banner + timer.
        viewModelScope.launch {
            eventBus.events.collect { event ->
                if (event is AppEvent.CapacityEvent.CampNearCapacity) {
                    val hint = CapacityHint(
                        zoneId = event.zoneId,
                        zoneName = event.zoneName,
                        ratio = event.occupancyRatio,
                        timestamp = System.currentTimeMillis()
                    )
                    _uiState.update { it.copy(capacityHint = hint) }
                    launch {
                        delay(CAPACITY_HINT_TTL_MS)
                        _uiState.update { st ->
                            // Only clear if this exact hint is still showing — a newer
                            // banner from another camp must not be wiped by an old timer.
                            if (st.capacityHint?.timestamp == hint.timestamp) {
                                st.copy(capacityHint = null)
                            } else st
                        }
                    }
                }
            }
        }

        locationRepository.startTracking()
        Log.d("CrisisOS_Map", "Location tracking started")

        viewModelScope.launch {
            locationRepository.getCurrentLocation().collect { location ->
                location?.let { applyLocation(it, isFallback = false) }
            }
        }

        viewModelScope.launch {
            delay(500)
            if (_uiState.value.userLocation == null) {
                val last = locationRepository.getLastKnownLocation()
                if (last != null) {
                    applyLocation(last, isFallback = true)
                } else {
                    Log.w("CrisisOS_Map", "No last known location available")
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        locationRepository.stopTracking()
        Log.d("CrisisOS_Map", "Location tracking stopped")
    }

    private fun applyZones(zones: List<SafeZone>) {
        _uiState.update { state ->
            val withDistance = zones.map { z ->
                val km = state.userLocation?.let { loc ->
                    haversineKm(loc.latitude, loc.longitude, z.coordinates.first, z.coordinates.second)
                }
                z.copy(
                    distanceKm = km,
                    distance = km?.let(::formatDistance) ?: "—"
                )
            }.sortedBy { it.distanceKm ?: Double.MAX_VALUE }
            val (nearest, rerouted) = pickNearestAvoiding(withDistance, state.userLocation, state.dangerZones)
            state.copy(
                safeZones = withDistance,
                nearestOpenZone = nearest,
                routeWasRerouted = rerouted
            )
        }
    }

    private fun applyLocation(loc: CrisisLocation, isFallback: Boolean) {
        val firstFix = !initialCenterApplied
        if (firstFix) initialCenterApplied = true

        _uiState.update { state ->
            val refreshed = state.safeZones
                .map { z ->
                    val km = haversineKm(loc.latitude, loc.longitude, z.coordinates.first, z.coordinates.second)
                    z.copy(distanceKm = km, distance = formatDistance(km))
                }
                .sortedBy { it.distanceKm ?: Double.MAX_VALUE }

            val (nearest, rerouted) = pickNearestAvoiding(refreshed, loc, state.dangerZones)

            state.copy(
                userLocation = loc,
                safeZones = refreshed,
                nearestOpenZone = nearest,
                routeWasRerouted = rerouted,
                mapCenter = if (firstFix) Pair(loc.latitude, loc.longitude) else state.mapCenter,
                centerRequest = if (firstFix) state.centerRequest + 1 else state.centerRequest,
                centerZoom = if (firstFix) com.elv8.crisisos.core.map.MapConfiguration.LOCATE_ME_ZOOM else state.centerZoom
            )
        }

        // Kick off ACLED sync once per process, after we have a fix so the
        // pull is at least somewhat anchored to the user's region.
        if (firstFix && !acledSyncStarted) {
            acledSyncStarted = true
            viewModelScope.launch {
                val country = guessCountryForLocation(loc)
                val inserted = dangerZoneRepository.syncFromAcled(country = country, lookbackDays = 7)
                Log.d("CrisisOS_Map", "ACLED sync ($country) → $inserted events")
            }
        }
        Log.d(
            "CrisisOS_Map",
            "Location ${if (isFallback) "(last known)" else "(live)"}: " +
                "${loc.latitude}, ${loc.longitude} firstFix=$firstFix"
        )
    }

    /**
     * Pick the closest open safe zone whose direct line from the user does NOT
     * cross any confirmed-RED danger circle. Falls back to the closest open zone
     * (no rerouting marker) if every option crosses a danger zone — the user
     * still needs *some* destination.
     *
     * Returns (zone, didReroute). didReroute is true iff a closer zone was
     * skipped because it would have crossed a red circle.
     */
    private fun pickNearestAvoiding(
        zones: List<SafeZone>,
        userLocation: CrisisLocation?,
        dangers: List<AggregatedDangerZone>
    ): Pair<SafeZone?, Boolean> {
        val loc = userLocation ?: return null to false
        val openSorted = zones
            .filter { it.status() == ZoneStatus.OPEN && it.distanceKm != null }
            .sortedBy { it.distanceKm }

        if (openSorted.isEmpty()) return null to false
        val redZones = dangers.filter { it.isConfirmedRed }
        if (redZones.isEmpty()) return openSorted.first() to false

        var firstCandidate: SafeZone? = null
        for (zone in openSorted) {
            if (firstCandidate == null) firstCandidate = zone
            val crosses = redZones.any { d ->
                segmentCrossesCircle(
                    loc.latitude, loc.longitude,
                    zone.coordinates.first, zone.coordinates.second,
                    d.centerLat, d.centerLon, d.radiusMeters
                )
            }
            if (!crosses) {
                return zone to (zone !== firstCandidate)
            }
        }
        // Every option is blocked — fall back to the closest, signalling no clean reroute exists.
        return firstCandidate to false
    }

    /**
     * Submit a new crowdsourced danger report at the user's current location.
     * Stored locally + broadcast on the mesh — aggregation kicks in automatically
     * (single = orange/unverified, 3+ unique within 2 h = red).
     */
    fun reportDangerHere(severity: ThreatLevel = ThreatLevel.MEDIUM, note: String = "") {
        val loc = _uiState.value.userLocation
        if (loc == null) {
            _uiState.update { it.copy(transientMessage = "No GPS fix yet — can't pin a danger report.") }
            return
        }
        viewModelScope.launch {
            val zone = DangerZone(
                id = UUID.randomUUID().toString(),
                title = "Danger reported nearby",
                description = note.ifBlank { "Anonymous on-the-ground report." },
                threatLevel = severity,
                distance = "0 m",
                reportedBy = "",
                timestamp = System.currentTimeMillis().toString(),
                coordinates = loc.latitude to loc.longitude
            )
            dangerZoneRepository.reportZone(zone)
            _uiState.update {
                it.copy(transientMessage = "Reported. 2 more nearby reports will auto-flag it as a red zone.")
            }
        }
    }

    fun clearTransientMessage() {
        _uiState.update { it.copy(transientMessage = null) }
    }

    fun dismissCapacityHint() {
        _uiState.update { it.copy(capacityHint = null) }
    }

    fun setMapMode(mode: MapMode) {
        _uiState.update { it.copy(mapMode = mode) }
    }

    fun selectZone(zone: SafeZone?) {
        _uiState.update { it.copy(selectedZone = zone) }
    }

    /** Force the map to animate to the user's current location at locate-me zoom. */
    fun centerOnUserLocation() {
        val loc = _uiState.value.userLocation ?: return
        _uiState.update {
            it.copy(
                mapCenter = Pair(loc.latitude, loc.longitude),
                centerZoom = com.elv8.crisisos.core.map.MapConfiguration.LOCATE_ME_ZOOM,
                centerRequest = it.centerRequest + 1
            )
        }
    }

    /** Pan to a specific zone at the locate-me zoom level. */
    fun centerOnZone(zone: SafeZone) {
        _uiState.update {
            it.copy(
                mapCenter = Pair(zone.coordinates.first, zone.coordinates.second),
                centerZoom = com.elv8.crisisos.core.map.MapConfiguration.LOCATE_ME_ZOOM,
                centerRequest = it.centerRequest + 1
            )
        }
    }

    // ---------- math helpers ----------

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    /**
     * Does the great-circle-approximation segment (lat1,lon1)–(lat2,lon2) come
     * within [radiusMeters] of (centerLat, centerLon)?
     *
     * Implementation: planar approximation. Convert the segment endpoints and
     * the danger center into local meters around the segment midpoint using
     * an equirectangular projection — accurate to <0.5% over the few-km spans
     * we route across, and dramatically cheaper than per-point haversine.
     */
    private fun segmentCrossesCircle(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double,
        centerLat: Double, centerLon: Double,
        radiusMeters: Double
    ): Boolean {
        val midLat = (lat1 + lat2) / 2.0
        val mPerDegLat = 111_320.0
        val mPerDegLon = 111_320.0 * cos(Math.toRadians(midLat)).coerceAtLeast(0.01)

        val px1 = lon1 * mPerDegLon
        val py1 = lat1 * mPerDegLat
        val px2 = lon2 * mPerDegLon
        val py2 = lat2 * mPerDegLat
        val cx  = centerLon * mPerDegLon
        val cy  = centerLat * mPerDegLat

        val dx = px2 - px1
        val dy = py2 - py1
        val l2 = dx * dx + dy * dy
        if (l2 == 0.0) {
            val ddx = cx - px1
            val ddy = cy - py1
            return sqrt(ddx * ddx + ddy * ddy) < radiusMeters
        }
        val t = ((cx - px1) * dx + (cy - py1) * dy) / l2
        val tc = t.coerceIn(0.0, 1.0)
        val closestX = px1 + tc * dx
        val closestY = py1 + tc * dy
        val ddx2 = cx - closestX
        val ddy2 = cy - closestY
        return sqrt(ddx2 * ddx2 + ddy2 * ddy2) < radiusMeters
    }

    private fun guessCountryForLocation(loc: CrisisLocation): String {
        // Very rough first-pass — refine with a reverse-geocoder when offline GIS data ships.
        val lat = loc.latitude
        val lon = loc.longitude
        return when {
            lat in 6.0..36.0 && lon in 68.0..98.0 -> "India"
            lat in 30.0..70.0 && lon in 22.0..40.0 -> "Ukraine"
            lat in 29.0..38.0 && lon in 34.0..43.0 -> "Israel"
            lat in 12.0..32.0 && lon in 32.0..49.0 -> "Yemen"
            else -> "India"
        }
    }

    private fun formatDistance(km: Double): String = when {
        km < 1.0  -> "${(km * 1000).toInt()} m away"
        km < 10.0 -> "%.1f km away".format(km)
        else      -> "${km.toInt()} km away"
    }

    companion object {
        private const val CAPACITY_HINT_TTL_MS = 30_000L
    }
}
