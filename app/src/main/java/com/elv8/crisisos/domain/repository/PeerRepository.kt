package com.elv8.crisisos.domain.repository

import com.elv8.crisisos.domain.model.peer.Peer
import com.elv8.crisisos.domain.model.peer.PeerStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface PeerRepository {
    val isDiscovering: StateFlow<Boolean>

    fun getNearbyPeers(): Flow<List<Peer>>
    fun getAllKnownPeers(): Flow<List<Peer>>
    fun getPeerCount(): Flow<Int>
    suspend fun getPeer(crsId: String): Peer?
    suspend fun startDiscovery()
    fun stopDiscovery()
    suspend fun updatePeerStatus(crsId: String, status: PeerStatus)
    suspend fun clearOfflinePeers()
}
