package com.elv8.crisisos.ui.screens.maps

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elv8.crisisos.domain.location.CrisisLocation
import com.elv8.crisisos.domain.model.SafeZone
import com.elv8.crisisos.domain.model.SafeZoneType
import com.elv8.crisisos.domain.model.ZoneStatus
import com.elv8.crisisos.domain.model.status
import com.elv8.crisisos.domain.repository.LocationRepository
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
    private val locationRepository: LocationRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(MapsUiState())
    val uiState: StateFlow<MapsUiState> = _uiState.asStateFlow()

    /** Have we already auto-centered on the user's first GPS fix? */
    private var initialCenterApplied = false

    init {
        loadSampleZones()

        // Start GPS tracking — registers the FusedLocation callback
        locationRepository.startTracking()
        Log.d("CrisisOS_Map", "Location tracking started")

        // Collect live location updates
        viewModelScope.launch {
            locationRepository.getCurrentLocation().collect { location ->
                location?.let { applyLocation(it, isFallback = false) }
            }
        }

        // Fallback: try to get last known location immediately
        viewModelScope.launch {
            delay(500) // Give tracking a moment to start
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

    private fun applyLocation(loc: CrisisLocation, isFallback: Boolean) {
        val firstFix = !initialCenterApplied
        if (firstFix) initialCenterApplied = true

        _uiState.update { state ->
            // Recompute distance against the new location, then sort by it.
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
                // Only auto-pan the map on the very first fix; after that the user is in control.
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

    private fun loadSampleZones() {
        // Sample zones placed in a tight ring around the default map center (New Delhi)
        // so they're visible at the default zoom level and reachable by foot.
        val cLat = com.elv8.crisisos.core.map.MapConfiguration.DEFAULT_LATITUDE
        val cLon = com.elv8.crisisos.core.map.MapConfiguration.DEFAULT_LONGITUDE

        val samples = listOf(
            SafeZone(
                id = UUID.randomUUID().toString(),
                name = "Central Stadium Camp",
                type = SafeZoneType.CAMP,
                distance = "—",
                capacity = 2500,
                currentOccupancy = 2100,            // ~84% → near full (orange)
                isOperational = true,
                coordinates = Pair(cLat + 0.0090, cLon + 0.0010),  // ~1.0 km N
                lastVerified = "2 hours ago",
                operatedBy = "UNHCR"
            ),
            SafeZone(
                id = UUID.randomUUID().toString(),
                name = "City West Hospital",
                type = SafeZoneType.HOSPITAL,
                distance = "—",
                capacity = 500,
                currentOccupancy = 480,             // 96% → full (red)
                isOperational = true,
                coordinates = Pair(cLat + 0.0020, cLon - 0.0340),  // ~3.3 km W
                lastVerified = "15 mins ago",
                operatedBy = "MSF"
            ),
            SafeZone(
                id = UUID.randomUUID().toString(),
                name = "Plaza Water Dispenser",
                type = SafeZoneType.WATER_POINT,
                distance = "—",
                capacity = null,
                currentOccupancy = null,
                isOperational = true,
                coordinates = Pair(cLat - 0.0036, cLon + 0.0008),  // ~0.4 km S
                lastVerified = "1 hour ago",
                operatedBy = "Local Relief Org"
            ),
            SafeZone(
                id = UUID.randomUUID().toString(),
                name = "North Sector Distribution",
                type = SafeZoneType.FOOD_DISTRIBUTION,
                distance = "—",
                capacity = 1000,
                currentOccupancy = 1000,
                isOperational = false,              // closed → red
                coordinates = Pair(cLat + 0.0250, cLon - 0.0010),  // ~2.8 km N
                lastVerified = "5 hours ago",
                operatedBy = "World Central Kitchen"
            ),
            SafeZone(
                id = UUID.randomUUID().toString(),
                name = "Embassy Extraction Zone",
                type = SafeZoneType.EVACUATION_POINT,
                distance = "—",
                capacity = 5000,
                currentOccupancy = 1200,            // 24% → open (green)
                isOperational = true,
                coordinates = Pair(cLat + 0.0040, cLon + 0.0570),  // ~5.6 km E
                lastVerified = "10 mins ago",
                operatedBy = "Joint Task Force"
            ),
            SafeZone(
                id = UUID.randomUUID().toString(),
                name = "Old Quarter Safe House",
                type = SafeZoneType.SAFE_HOUSE,
                distance = "—",
                capacity = 40,
                currentOccupancy = 12,              // 30% → open (green)
                isOperational = true,
                coordinates = Pair(cLat - 0.0080, cLon - 0.0050),  // ~1 km SW
                lastVerified = "30 mins ago",
                operatedBy = "Civilian Network"
            )
        )
        _uiState.update { it.copy(safeZones = samples) }
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

    // --- distance helpers ---

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
