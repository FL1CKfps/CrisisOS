package com.elv8.crisisos.ui.screens.maps

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import com.elv8.crisisos.domain.location.CrisisLocation
import com.elv8.crisisos.domain.model.SafeZone
import com.elv8.crisisos.domain.model.SafeZoneType
import com.elv8.crisisos.domain.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class MapMode { MAP, LIST }

data class MapsUiState(
    val safeZones: List<SafeZone> = emptyList(),
    val selectedZone: SafeZone? = null,
    val mapMode: MapMode = MapMode.MAP,
    val isOffline: Boolean = true,
    val userLocation: CrisisLocation? = null,
    val mapCenter: Pair<Double, Double>? = null   // (lat, lon) to animate to
)

@HiltViewModel
class MapsViewModel @Inject constructor(
    private val locationRepository: LocationRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(MapsUiState())
    val uiState: StateFlow<MapsUiState> = _uiState.asStateFlow()

    init {
        loadSampleZones()

        // Start GPS tracking — registers the FusedLocation callback
        locationRepository.startTracking()
        Log.d("CrisisOS_Map", "Location tracking started")

        // Collect live location updates
        viewModelScope.launch {
            locationRepository.getCurrentLocation().collect { location ->
                location?.let {
                    _uiState.update { state ->
                        state.copy(
                            userLocation = location,
                            mapCenter = Pair(location.latitude, location.longitude)
                        )
                    }
                    Log.d("CrisisOS_Map",
                        "Location update: lat=${location.latitude} lon=${location.longitude}")
                }
            }
        }

        // Fallback: try to get last known location immediately
        viewModelScope.launch {
            delay(500) // Give tracking a moment to start
            if (_uiState.value.userLocation == null) {
                val last = locationRepository.getLastKnownLocation()
                if (last != null) {
                    _uiState.update { state ->
                        state.copy(
                            userLocation = last,
                            mapCenter = Pair(last.latitude, last.longitude)
                        )
                    }
                    Log.d("CrisisOS_Map",
                        "Fallback last known: lat=${last.latitude} lon=${last.longitude}")
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

    private fun loadSampleZones() {
        val samples = listOf(
            SafeZone(
                id = UUID.randomUUID().toString(),
                name = "Central Stadium Camp",
                type = SafeZoneType.CAMP,
                distance = "1.2 km away",
                capacity = 2500,
                currentOccupancy = 2100,
                isOperational = true,
                coordinates = Pair(48.8566, 2.3522),
                lastVerified = "2 hours ago",
                operatedBy = "UNHCR"
            ),
            SafeZone(
                id = UUID.randomUUID().toString(),
                name = "City West Hospital",
                type = SafeZoneType.HOSPITAL,
                distance = "3.5 km away",
                capacity = 500,
                currentOccupancy = 480,
                isOperational = true,
                coordinates = Pair(48.8580, 2.3480),
                lastVerified = "15 mins ago",
                operatedBy = "MSF"
            ),
            SafeZone(
                id = UUID.randomUUID().toString(),
                name = "Plaza Water Dispenser",
                type = SafeZoneType.WATER_POINT,
                distance = "0.4 km away",
                capacity = null,
                currentOccupancy = null,
                isOperational = true,
                coordinates = Pair(48.8550, 2.3500),
                lastVerified = "1 hour ago",
                operatedBy = "Local Relief Org"
            ),
            SafeZone(
                id = UUID.randomUUID().toString(),
                name = "North Sector Distribution",
                type = SafeZoneType.FOOD_DISTRIBUTION,
                distance = "2.8 km away",
                capacity = 1000,
                currentOccupancy = 1000,
                isOperational = false, // Full or non-operational
                coordinates = Pair(48.8600, 2.3400),
                lastVerified = "5 hours ago",
                operatedBy = "World Central Kitchen"
            ),
            SafeZone(
                id = UUID.randomUUID().toString(),
                name = "Embassy Extraction Zone",
                type = SafeZoneType.EVACUATION_POINT,
                distance = "5.0 km away",
                capacity = 5000,
                currentOccupancy = 1200,
                isOperational = true,
                coordinates = Pair(48.8650, 2.3300),
                lastVerified = "10 mins ago",
                operatedBy = "Joint Task Force"
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

    fun centerOnUserLocation() {
        val loc = _uiState.value.userLocation ?: return
        _uiState.update { it.copy(mapCenter = Pair(loc.latitude, loc.longitude)) }
    }
}