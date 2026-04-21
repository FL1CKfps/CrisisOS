package com.elv8.crisisos.ui.screens.dangerzone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elv8.crisisos.domain.model.DangerZone
import com.elv8.crisisos.domain.model.ThreatLevel
import com.elv8.crisisos.domain.repository.DangerZoneRepository
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
import java.util.Locale

data class DangerZoneUiState(
    val zones: List<DangerZone> = emptyList(),
    val isLoading: Boolean = false,
    val selectedFilter: ThreatLevel? = null,
    val userLocation: String = "Unknown Location"
)

@HiltViewModel
class DangerZoneViewModel @Inject constructor(
    private val dangerZoneRepository: DangerZoneRepository,
    private val locationRepository: LocationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DangerZoneUiState())
    val uiState: StateFlow<DangerZoneUiState> = _uiState.asStateFlow()

    private var allZones = emptyList<DangerZone>()

    init {
        dangerZoneRepository.observeIncomingReports()
        viewModelScope.launch {
            dangerZoneRepository.purgeStaleReports()
        }
        
        viewModelScope.launch {
            dangerZoneRepository.getDangerZones().collect { zones ->
                allZones = zones
                filterByLevel(_uiState.value.selectedFilter)
            }
        }
        
        viewModelScope.launch {
            locationRepository.getCurrentLocation().collect { loc ->
                if (loc != null) {
                    val lat = String.format(Locale.getDefault(), "%.4f", loc.latitude)
                    val lng = String.format(Locale.getDefault(), "%.4f", loc.longitude)
                    _uiState.update { it.copy(userLocation = "Lat: $lat, Lng: $lng") }
                }
            }
        }
    }

    fun filterByLevel(level: ThreatLevel?) {
        _uiState.update { state -> 
            state.copy(
                selectedFilter = level,
                zones = if (level == null) allZones else allZones.filter { it.threatLevel == level }
            )
        }
    }

    fun refreshZones() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            dangerZoneRepository.purgeStaleReports()
            delay(500)
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun reportNewZone(title: String, description: String, level: ThreatLevel, reportedBy: String) {
        viewModelScope.launch {
            val loc = locationRepository.getLastKnownLocation()
            val coords = if (loc != null) {
                Pair(loc.latitude, loc.longitude)
            } else {
                Pair(0.0, 0.0)
            }
            
            val newZone = DangerZone(
                id = UUID.randomUUID().toString(),
                title = title,
                description = description,
                threatLevel = level,
                distance = "0 km away",
                reportedBy = reportedBy,
                timestamp = System.currentTimeMillis().toString(),
                coordinates = coords
            )
            
            dangerZoneRepository.reportZone(newZone)
        }
    }
}

