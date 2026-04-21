package com.elv8.crisisos.data.repository

import android.util.Log
import com.elv8.crisisos.core.debug.MeshLogger
import com.elv8.crisisos.core.event.AppEvent
import com.elv8.crisisos.core.event.EventBus
import com.elv8.crisisos.data.local.dao.PeerDao
import com.elv8.crisisos.data.local.entity.PeerEntity
import com.elv8.crisisos.data.mesh.MeshConnectionManager
import com.elv8.crisisos.domain.model.identity.CrsIdGenerator
import com.elv8.crisisos.domain.model.peer.Peer
import com.elv8.crisisos.domain.model.peer.PeerStatus
import com.elv8.crisisos.domain.repository.IdentityRepository
import com.elv8.crisisos.domain.repository.PeerRepository
import com.elv8.crisisos.core.debug.MeshDebugConfig
import com.elv8.crisisos.core.debug.MockPeerInjector
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PeerRepositoryImpl @Inject constructor(
    private val peerDao: PeerDao,
    private val identityRepository: IdentityRepository,
    private val meshConnectionManager: MeshConnectionManager,
    private val eventBus: EventBus,
    private val mockPeerInjector: MockPeerInjector,
    private val scope: CoroutineScope
) : PeerRepository {

    override val isDiscovering: StateFlow<Boolean> = meshConnectionManager.isDiscovering

    init {
        observeMeshEvents()
    }

    private fun observeMeshEvents() {
        eventBus.observe(scope, AppEvent.MeshEvent.PeerConnected::class) { event ->
            scope.launch(Dispatchers.IO) {
                val peer = PeerEntity(
                    crsId = event.peerId,
                    alias = event.peerAlias,
                    deviceId = event.peerId,
                    discoveredAt = System.currentTimeMillis(),
                    lastSeenAt = System.currentTimeMillis(),
                    signalStrength = -50,
                    distanceMeters = 5.0f,
                    status = PeerStatus.AVAILABLE.name,
                    isNearby = true,
                    avatarColor = CrsIdGenerator.generateAvatarColor(event.peerId),
                    publicKey = null
                )
                peerDao.insert(peer)
            }
        }

        eventBus.observe(scope, AppEvent.MeshEvent.PeerDisconnected::class) { event ->
            scope.launch(Dispatchers.IO) {
                peerDao.updateStatusLegacy(event.peerId, PeerStatus.OFFLINE.name, System.currentTimeMillis())
            }
        }

        eventBus.observe(scope, AppEvent.MeshEvent.PeerLost::class) { event ->  
            scope.launch(Dispatchers.IO) {
                peerDao.updateStatusLegacy(event.peerId, PeerStatus.OFFLINE.name, System.currentTimeMillis())
            }
        }
    }

    private fun PeerEntity.toDomain() = Peer(
        crsId = crsId,
        alias = alias,
        deviceId = deviceId,
        discoveredAt = discoveredAt,
        lastSeenAt = lastSeenAt,
        signalStrength = signalStrength,
        distanceMeters = distanceMeters,
        status = try { PeerStatus.valueOf(status) } catch (e: Exception) { PeerStatus.UNKNOWN },
        isNearby = isNearby,
        avatarColor = avatarColor,
        publicKey = publicKey
    )

    override fun getNearbyPeers(): Flow<List<Peer>> =
        peerDao.getAllNearby()
            .map { entities ->
                entities.map { it.toDomain() }
            }
            .flowOn(Dispatchers.IO)
            .catch { e ->
                Log.e("CrisisOS_PeerRepo", "getNearbyPeers() Flow error: ${e.message}")
                emit(emptyList())
            }

    override fun getAllKnownPeers(): Flow<List<Peer>> =
        peerDao.getAll()
            .map { it.map { entity -> entity.toDomain() } }
            .flowOn(Dispatchers.IO)

    override suspend fun getPeer(crsId: String): Peer? = withContext(Dispatchers.IO) {
        peerDao.getByCrsId(crsId)?.toDomain()
    }

    override suspend fun updatePeerStatus(crsId: String, status: PeerStatus) = withContext<Unit>(Dispatchers.IO) {
        peerDao.updateStatusLegacy(crsId, status.name, System.currentTimeMillis())
    }

    override suspend fun clearOfflinePeers() = withContext<Unit>(Dispatchers.IO) {
        peerDao.markAllOffline()
    }

    override fun getPeerCount(): Flow<Int> = peerDao.getNearbyCount()

    override suspend fun startDiscovery() {
        meshConnectionManager.startDiscovery()
        if (MeshDebugConfig.HYBRID_MODE) {
            scope.launch {
                delay(MeshDebugConfig.MOCK_INJECT_DELAY_MS)
                mockPeerInjector.injectMockPeers()
                mockPeerInjector.startMockSignalFluctuation()
                Log.d("CrisisOS_PeerRepo", "Hybrid mode: mock peers injected + fluctuation started")
            }
        }
    }

    override fun stopDiscovery() {
        meshConnectionManager.stopDiscovery()
        if (MeshDebugConfig.ENABLE_MOCK_PEER_INJECTION) {
            scope.launch { mockPeerInjector.removeMockPeers() }
        }
    }

    suspend fun forceRefreshPeer(crsId: String) {
        val now = System.currentTimeMillis()
        peerDao.touchLastSeen(crsId, now)
        Log.d("CrisisOS_PeerRepo", "Force-refreshed peer $crsId in Room")
    }
}
