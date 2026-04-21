package com.elv8.crisisos.core.debug

import android.util.Log
import com.elv8.crisisos.core.debug.MeshLogger
import com.elv8.crisisos.data.local.dao.PeerDao
import com.elv8.crisisos.data.local.entity.PeerEntity
import com.elv8.crisisos.domain.model.identity.CrsIdGenerator
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MockPeerInjector @Inject constructor(
    private val peerDao: PeerDao,
    private val scope: CoroutineScope
) {
    private val mockPeers = listOf(
        Triple("CRS-DBG1-TEST", "Ayaan Khan [MOCK]", -52),
        Triple("CRS-DBG2-TEST", "Priya Mehta [MOCK]", -67),
        Triple("CRS-DBG3-TEST", "Jonas Weber [MOCK]", -78)
    )

    suspend fun injectMockPeers() {
        if (!MeshDebugConfig.ENABLE_MOCK_PEER_INJECTION) return
        
        Log.d("CrisisOS_Debug", "Injecting ${mockPeers.size} mock peers into Room")
        
        mockPeers.forEachIndexed { index, (crsId, alias, signal) ->
            withContext(Dispatchers.IO) {
                delay(500L * index)  // stagger injection
                val entity = PeerEntity(
                    crsId = crsId,
                    alias = alias,
                    deviceId = "mock_device_$index",
                    discoveredAt = System.currentTimeMillis(),
                    lastSeenAt = System.currentTimeMillis(),
                    signalStrength = signal,
                    distanceMeters = ((-signal - 35) / 55f * 95f + 5f),
                    status = "AVAILABLE",
                    isNearby = true,
                    avatarColor = CrsIdGenerator.generateAvatarColor(crsId),
                    publicKey = null
                )
                peerDao.insert(entity)
                Log.d("CrisisOS_Debug", "Mock peer injected — $alias ($crsId)")
            }
        }
    }

    fun startMockSignalFluctuation() {
        if (!MeshDebugConfig.ENABLE_MOCK_PEER_INJECTION) return
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(6_000)
                mockPeers.forEach { (crsId, alias, baseSignal) ->
                    val fluctuated = (baseSignal + (-4..4).random()).coerceIn(-90, -35)
                    val distance = ((-fluctuated - 35) / 55f * 95f + 5f)
                    peerDao.updateSignal(crsId, fluctuated, distance)
                    peerDao.touchLastSeen(crsId, System.currentTimeMillis())
                    Log.v("CrisisOS_Debug", "Mock signal update — $alias: ${fluctuated}dBm")
                }
            }
        }
    }

    suspend fun removeMockPeers() {
        mockPeers.forEach { (crsId, _, _) ->
            withContext(Dispatchers.IO) {
                peerDao.delete(crsId)
            }
            Log.d("CrisisOS_Debug", "Mock peer removed — $crsId")
        }
    }
}
