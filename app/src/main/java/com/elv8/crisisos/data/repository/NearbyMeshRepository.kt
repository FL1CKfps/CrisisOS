package com.elv8.crisisos.data.repository

import com.elv8.crisisos.data.local.dao.PeerDao
import com.elv8.crisisos.data.local.entity.PeerEntity
import com.elv8.crisisos.data.mesh.MeshConnectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NearbyMeshRepository @Inject constructor(
    private val connectionManager: MeshConnectionManager,
    private val peerDao: PeerDao
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        scope.launch {
            connectionManager.connectedPeers.collect { peersMap ->
                peersMap.values.forEach { peer ->
                    val entity = PeerEntity(
                        endpointId = peer.endpointId,
                        crsId = peer.endpointId,
                        alias = peer.alias,
                        status = "ACTIVE",
                        lastSeen = peer.lastSeenAt,
                        rssi = peer.signalStrength ?: 0,
                        lastSeenAt = peer.lastSeenAt,
                        signalStrength = peer.signalStrength ?: 0
                    )
                    peerDao.insert(entity)
                }
            }
        }
    }

    fun observeActivePeers(): Flow<List<PeerEntity>> {
        return peerDao.getAllNearby() // Since observeActivePeers doesn't exist in PeerDao anymore, wait let's check
    }

    suspend fun startMesh(crsId: String, alias: String) {
        connectionManager.startMesh(alias)
    }

    fun stopMesh() {
        connectionManager.stopAll()
    }
}
