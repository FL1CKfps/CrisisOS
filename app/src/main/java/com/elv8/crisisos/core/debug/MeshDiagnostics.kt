package com.elv8.crisisos.core.debug

import com.elv8.crisisos.core.permissions.MeshPermissionManager
import com.elv8.crisisos.data.local.dao.PeerDao
import com.elv8.crisisos.data.mesh.MeshConnectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class DiagnosticsSnapshot(
    val timestamp: String,
    val isAdvertising: Boolean,
    val isDiscovering: Boolean,
    val connectedPeerCount: Int,
    val nearbyPeerCount: Int,
    val missingPermissions: List<String>,
    val discoveryRestartCount: Int,
    val serviceRunning: Boolean,
    val localAlias: String,
    val localCrsId: String,
    val connectedPeers: List<String>
)

@Singleton
class MeshDiagnostics @Inject constructor(
    private val connectionManager: MeshConnectionManager,
    private val permissionManager: MeshPermissionManager,
    private val peerDao: PeerDao,
    private val scope: CoroutineScope
) {
    private val _snapshot = MutableStateFlow<DiagnosticsSnapshot?>(null)
    val snapshot: StateFlow<DiagnosticsSnapshot?> = _snapshot.asStateFlow()

    fun startMonitoring() {
        scope.launch {
            while (isActive) {
                delay(3_000)
                val nearbyCount = peerDao.getNearbyCountOnce()
                val currentSnapshot = DiagnosticsSnapshot(
                    timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
                    isAdvertising = connectionManager.isAdvertising.value,
                    isDiscovering = connectionManager.isDiscovering.value,
                    connectedPeerCount = connectionManager.connectedPeers.value.size,
                    nearbyPeerCount = nearbyCount,
                    missingPermissions = permissionManager.getMissingPermissions(),
                    discoveryRestartCount = connectionManager.getDiscoveryRestartCount(),
                    serviceRunning = true,
                    localAlias = connectionManager.getLocalAlias(),
                    localCrsId = connectionManager.getLocalCrsId(),
                    connectedPeers = connectionManager.connectedPeers.value.values.map { 
                        "${it.alias} (${it.endpointId.take(8)})" 
                    }
                )
                _snapshot.value = currentSnapshot
            }
        }
    }
}
