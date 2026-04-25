package com.elv8.crisisos.ui.screens.maps

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elv8.crisisos.domain.location.CrisisLocation
import com.elv8.crisisos.domain.model.SafeZone
import com.elv8.crisisos.domain.model.ZoneStatus
import com.elv8.crisisos.domain.model.status
import com.elv8.crisisos.domain.repository.LocationRepository
import com.elv8.crisisos.domain.repository.SafeZoneRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class MapMode { MAP, LIST }

data class MapsUiState(
    val safeZones: List<SafeZone> = emptyList(),
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
    /** Nearest open zone — used to render the suggested-route polyline. */
    val nearestOpenZone: SafeZone? = null
)

@HiltViewModel
class MapsViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
    private val safeZoneRepository: SafeZoneRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(MapsUiState())
    val uiState: StateFlow<MapsUiState> = _uiState.asStateFlow()

    /** Have we already auto-centered on the user's first GPS fix? */
    private var initialCenterApplied = false

    init {
        // First-run shard seed using the configured map default. Once NGOs broadcast
        // real capacity over the mesh those rows replace the seed.
        viewModelScope.launch {
            safeZoneRepository.seedDefaultsIfEmpty(
                centerLat = com.elv8.crisisos.core.map.MapConfiguration.DEFAULT_LATITUDE,
                centerLon = com.elv8.crisisos.core.map.MapConfiguration.DEFAULT_LONGITUDE
            )
        }

        // Observe persisted safe zones and recompute distance whenever either set changes.
        viewModelScope.launch {
            safeZoneRepository.observe().collect { zones ->
                applyZones(zones)
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
            val nearest = withDistance
                .filter { it.status() == ZoneStatus.OPEN }
                .minByOrNull { it.distanceKm ?: Double.MAX_VALUE }
            state.copy(safeZones = withDistance, nearestOpenZone = nearest)
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

            val nearest = refreshed
                .filter { it.status() == ZoneStatus.OPEN }
                .minByOrNull { it.distanceKm ?: Double.MAX_VALUE }

            state.copy(
                userLocation = loc,
                safeZones = refreshed,
                nearestOpenZone = nearest,
                mapCenter = if (firstFix) Pair(loc.latitude, loc.longitude) else state.mapCenter,
                centerRequest = if (firstFix) state.centerRequest + 1 else state.centerRequest,
                centerZoom = if (firstFix) com.elv8.crisisos.core.map.MapConfiguration.LOCATE_ME_ZOOM else state.centerZoom
            )
        }
        Log.d(
            "CrisisOS_Map",
            "Location ${if (isFallback) "(last known)" else "(live)"}: " +
                "${loc.latitude}, ${loc.longitude} firstFix=$firstFix"
        )
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

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun formatDistance(km: Double): String = when {
        km < 1.0  -> "${(km * 1000).toInt()} m away"
        km < 10.0 -> "%.1f km away".format(km)
        else      -> "${km.toInt()} km away"
    }
}
