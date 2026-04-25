package com.elv8.crisisos.ui.screens.home

import android.content.Context
import android.os.Build
import android.util.Log
import com.elv8.crisisos.core.notification.NotificationSettings
import com.elv8.crisisos.core.notification.NotificationManagerWrapper
import com.elv8.crisisos.core.notification.NotificationEventBus
import com.elv8.crisisos.core.notification.event.NotificationEvent
import java.util.UUID
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elv8.crisisos.service.MeshForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class MeshStatus {
    CONNECTED, SCANNING, OFFLINE
}

data class HomeUiState(
    val meshStatus: MeshStatus = MeshStatus.SCANNING,
    val peersNearby: Int = 0,
    val lastSyncTime: String = "Scanning...",
    val activeSosAlerts: Int = 2,
    val batteryOptimized: Boolean = true,
    val needsNotificationPermission: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recoveryManager: com.elv8.crisisos.core.recovery.MeshRecoveryManager,
    private val identityRepository: com.elv8.crisisos.domain.repository.IdentityRepository,
    private val notificationSettings: NotificationSettings,
    private val notifWrapper: NotificationManagerWrapper,
    private val notificationEventBus: NotificationEventBus,
    private val meshManager: com.elv8.crisisos.core.network.mesh.IMeshConnectionManager,
    private val peerRepository: com.elv8.crisisos.domain.repository.PeerRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = notifWrapper.hasNotificationPermission()
            _uiState.update { it.copy(needsNotificationPermission = !hasPermission) }
        }

        viewModelScope.launch {
            meshManager.isDiscovering.collect { discovering ->
                _uiState.update { 
                    it.copy(meshStatus = if (discovering) MeshStatus.SCANNING else if (meshManager.connectedPeers.value.isNotEmpty()) MeshStatus.CONNECTED else MeshStatus.OFFLINE)
                }
            }
        }

        viewModelScope.launch {
            meshManager.connectedPeers.collect { peers ->
                _uiState.update { 
                    it.copy(
                        peersNearby = peers.size,
                        meshStatus = if (peers.isNotEmpty()) MeshStatus.CONNECTED else if (meshManager.isDiscovering.value) MeshStatus.SCANNING else MeshStatus.OFFLINE,
                        lastSyncTime = if (peers.isNotEmpty()) "Just now" else it.lastSyncTime
                    )
                }
            }
        }
    }

    fun startMeshService() {
        MeshForegroundService.start(context, "UNKNOWN_CRS", "HomeUser")
    }

    fun stopMeshService() {
        MeshForegroundService.stop(context)
    }

    val recoveryState = recoveryManager.recoveryState

    fun restartMesh() {
        recoveryManager.triggerManualRecovery()
    }

    fun onNotificationPermissionGranted() {
        _uiState.update { it.copy(needsNotificationPermission = false) }
        Log.i("CrisisOS_Home", "POST_NOTIFICATIONS granted")
    }

    fun onNotificationPermissionDenied() {
        Log.w("CrisisOS_Home", "POST_NOTIFICATIONS denied by user")
        _uiState.update { it.copy(needsNotificationPermission = false) }
    }

    fun triggerMockNotifications() {
        viewModelScope.launch {
            val rId = java.util.UUID.randomUUID().toString()
            
            // 1. Mock Chat Message
            notificationEventBus.emit(
                NotificationEvent.Chat.MessageReceived(
                    threadId = "thread_$rId",
                    fromCrsId = "crs_$rId",
                    fromAlias = "Test User $rId",
                    avatarColor = 0xFF5555,
                    messagePreview = "This is a mock mesh message to test notifications",
                    messageId = "msg_$rId",
                    timestamp = System.currentTimeMillis(),
                    isGroupChat = false,
                    groupName = null
                )
            )

            delay(1500)

            // 2. Mock Connection Request
            notificationEventBus.emit(
                NotificationEvent.Request.ConnectionRequestReceived(
                    requestId = "req_$rId",
                    fromCrsId = "crs2_$rId",
                    fromAlias = "Stranger $rId",
                    fromAvatarColor = 0x55FF55,
                    introMessage = "Hi, I am nearby and need connection."
                )
            )

            delay(1500)

            // 3. Mock SOS
            notificationEventBus.emit(
                NotificationEvent.Sos.IncomingAlert(
                    alertId = "sos_$rId",
                    fromCrsId = "sos_crs_$rId",
                    fromAlias = "Victim $rId",
                    sosType = "MEDICAL",
                    message = "Need immediate assistance",
                    locationHint = "Main Street corner",
                    hopsAway = 2
                )
            )
            
            delay(1500)
            
            // 4. Mock Supply AcK
            notificationEventBus.emit(
                NotificationEvent.Supply.RequestAcknowledged(
                    requestId = "sup_$rId",
                    supplyType = "FOOD",
                    ngoAlias = "Local Rescue NGO",
                    estimatedEta = "30 mins"
                )
            )
        }
    }
}
