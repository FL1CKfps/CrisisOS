package com.elv8.crisisos.ui.screens.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elv8.crisisos.data.local.dao.ConnectionRequestDao
import com.elv8.crisisos.domain.model.identity.UserIdentity
import com.elv8.crisisos.domain.model.peer.Peer
import com.elv8.crisisos.domain.model.peer.PeerStatus
import com.elv8.crisisos.domain.repository.IdentityRepository
import com.elv8.crisisos.domain.repository.PeerRepository
import com.elv8.crisisos.data.local.preferences.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import android.util.Log
import com.elv8.crisisos.core.debug.MeshDebugConfig
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

enum class PeerSortOrder {
    SIGNAL, DISTANCE, NAME, RECENT
}

data class PeerDiscoveryUiState(
    val peers: List<Peer> = emptyList(),
    val filteredPeers: List<Peer> = emptyList(),
    val isDiscovering: Boolean = false,
    val searchQuery: String = "",
    val sortOrder: PeerSortOrder = PeerSortOrder.SIGNAL,
    val filterStatus: PeerStatus? = null,
    val filterRequested: Boolean = false,
    val localIdentity: UserIdentity? = null,
    val requestedCrsIds: Set<String> = emptySet(),
    val errorMessage: String? = null,
    val hasSeenOnboarding: Boolean = false,
    val isHybridMode: Boolean = MeshDebugConfig.HYBRID_MODE,
    val debugPeerCount: Int = 0
)

@HiltViewModel
class PeerDiscoveryViewModel @Inject constructor(
    private val peerRepository: PeerRepository,
    private val connectionRequestDao: ConnectionRequestDao,
    private val identityRepository: IdentityRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PeerDiscoveryUiState())
    val uiState: StateFlow<PeerDiscoveryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            peerRepository.getPeerCount().collect { count ->
                _uiState.update { it.copy(debugPeerCount = count) }
                Log.d("CrisisOS_Discovery", "Total peers in Room (nearby): $count")
            }
        }

        viewModelScope.launch {
            preferencesManager.hasSeenDiscoveryOnboarding.collect { hasSeen ->
                _uiState.update { it.copy(hasSeenOnboarding = hasSeen) }
            }
        }

        viewModelScope.launch {
            peerRepository.getNearbyPeers()
                .distinctUntilChanged()    // only emit when data actually changes
                .collect { peers ->
                    Log.d("CrisisOS_Discovery", "ViewModel received ${peers.size} nearby peers from Room")
                    _uiState.update { it.copy(peers = peers) }
                    applyFilterAndSort()
                }
        }
        
        viewModelScope.launch {
            peerRepository.isDiscovering.collect { discovering ->
                Log.d("CrisisOS_Discovery", "isDiscovering changed: $discovering")
                _uiState.update { it.copy(isDiscovering = discovering) }
            }
        }
        
        // Load already-sent requests
        viewModelScope.launch {
            connectionRequestDao.getOutgoing()
                .distinctUntilChanged()
                .collect { requests ->
                    val requestedIds = requests
                        .filter { it.status in listOf("PENDING", "ACCEPTED") }
                        .map { it.toCrsId }
                        .toSet()
                    _uiState.update { it.copy(requestedCrsIds = requestedIds) }
                }
        }
        
        // Load local identity
        viewModelScope.launch {
            identityRepository.getIdentity()
                .filterNotNull()
                .collect { identity ->
                    _uiState.update { it.copy(localIdentity = identity) }
                }
        }
        
        // Start discovery automatically when ViewModel is created
        Log.d("CrisisOS_Discovery", "ViewModel init — calling startDiscovery()")
        startDiscovery()
    }

    fun startDiscovery() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDiscovering = true) }
            peerRepository.startDiscovery()
        }
    }

    fun stopDiscovery() {
        peerRepository.stopDiscovery()
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilterAndSort()
    }

    fun setSortOrder(order: PeerSortOrder) {
        _uiState.update { it.copy(sortOrder = order) }
        applyFilterAndSort()
    }

    fun setStatusFilter(status: PeerStatus?) {
        _uiState.update { it.copy(filterStatus = status, filterRequested = false) }
        applyFilterAndSort()
    }

    fun setRequestedFilter() {
        _uiState.update { it.copy(filterStatus = null, filterRequested = true) }
        applyFilterAndSort()
    }

    private fun applyFilterAndSort() {
        val state = _uiState.value
        var result = state.peers

        if (state.searchQuery.isNotBlank()) {
            result = result.filter {
                it.alias.contains(state.searchQuery, ignoreCase = true) ||      
                it.crsId.contains(state.searchQuery, ignoreCase = true)
            }
        }

        if (state.filterStatus != null) {
            result = result.filter { it.status == state.filterStatus }
        }

        if (state.filterRequested) {
            result = result.filter { it.crsId in state.requestedCrsIds }
        }

        result = when (state.sortOrder) {
            PeerSortOrder.SIGNAL -> result.sortedByDescending { it.signalStrength }
            PeerSortOrder.DISTANCE -> result.sortedBy { it.distanceMeters }
            PeerSortOrder.NAME -> result.sortedBy { it.alias }
            PeerSortOrder.RECENT -> result.sortedByDescending { it.lastSeenAt }
        }

        _uiState.update { it.copy(filteredPeers = result) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun hasAlreadySentRequest(crsId: String): Boolean {
        return crsId in _uiState.value.requestedCrsIds
    }

    fun dismissOnboarding() {
        viewModelScope.launch {
            preferencesManager.setHasSeenDiscoveryOnboarding(true)
        }
    }
}
