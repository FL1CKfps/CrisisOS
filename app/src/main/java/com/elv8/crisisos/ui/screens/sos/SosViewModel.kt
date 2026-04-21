package com.elv8.crisisos.ui.screens.sos

import com.elv8.crisisos.domain.repository.LocationRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.elv8.crisisos.core.event.EventBus
import com.elv8.crisisos.core.event.AppEvent
import com.elv8.crisisos.data.mesh.MeshMessenger
import com.elv8.crisisos.data.dto.PacketFactory
import com.elv8.crisisos.data.dto.payloads.SosPayload
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SosType(val title: String, val quickPhrase: String) {
    MEDICAL("Medical", "Need medical help immediately"),
    TRAPPED("Trapped", "Trapped under debris/unable to move"),
    MISSING("Missing", "Looking for missing person"),
    ARMED_THREAT("Armed Threat", "Armed threat in the vicinity"),
    FIRE("Fire", "Out of control fire, send help"),
    GENERAL("General SOS", "General emergency, need assistance")
}

data class IncomingAlert(
    val senderId: String,
    val senderAlias: String,
    val sosType: String,
    val message: String
)

data class SosUiState(
    val isBroadcasting: Boolean = false,
    val messageText: String = "",
    val sosType: SosType? = null,
    val broadcastCount: Int = 0,
    val confirmStep: Int = 0, // 0: initial, 1: tap once (show "Hold to confirm"), 2: broadcast
    val broadcastPacketId: String? = null,
    val incomingAlerts: List<IncomingAlert> = emptyList()
)

@HiltViewModel
class SosViewModel @Inject constructor(
    private val messenger: MeshMessenger,
    private val eventBus: EventBus,
    private val locationRepository: LocationRepository,
    private val notificationBus: com.elv8.crisisos.core.notification.NotificationEventBus,
    private val notificationHandler: com.elv8.crisisos.core.notification.NotificationHandler,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _uiState = MutableStateFlow(SosUiState())
    val uiState: StateFlow<SosUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            eventBus.events
                .filterIsInstance<AppEvent.SosEvent.SosReceivedFromPeer>()
                .collect { event ->
                    _uiState.update { state ->
                        state.copy(
                            incomingAlerts = state.incomingAlerts + IncomingAlert(
                                senderId = event.senderId,
                                senderAlias = event.senderAlias,
                                sosType = event.sosType,
                                message = event.message
                            )
                        )
                    }
                }
        }

        viewModelScope.launch {
            eventBus.events
                .filterIsInstance<AppEvent.MeshEvent.MessageSent>()
                .collect { event ->
                    val currentState = _uiState.value
                    if (currentState.isBroadcasting && event.packetId == currentState.broadcastPacketId) {
                        _uiState.update { it.copy(broadcastCount = it.broadcastCount + 1) }
                    }
                }
        }

        viewModelScope.launch {
            uiState.collect { state ->
                if (state.isBroadcasting) {
                    notificationHandler.suppressGroup("group_sos")
                } else {
                    notificationHandler.unsuppressGroup("group_sos")
                }
            }
        }
    }

    fun selectSosType(type: SosType) {
        _uiState.update {
            it.copy(
                sosType = type,
                messageText = type.quickPhrase,
                confirmStep = 0
            )
        }
    }

    fun updateMessage(message: String) {
        _uiState.update { it.copy(messageText = message) }
    }

    fun confirmStep(step: Int) {
        _uiState.update { it.copy(confirmStep = step) }
        if (step == 2) {
            startBroadcast()
        }
    }

    private fun startBroadcast() {
        viewModelScope.launch {
            val loc = locationRepository.getLastKnownLocation()
            val locationHint = loc?.toHumanReadable()

            val currentState = _uiState.value
            val payload = SosPayload(
                sosType = currentState.sosType?.name ?: SosType.GENERAL.name,
                message = currentState.messageText,
                locationHint = locationHint
            )

            val packet = PacketFactory.buildSosPacket(
                senderId = getDeviceId(context),
                senderAlias = getAlias(context),
                payload = payload,
                locationHint = locationHint
            )

            _uiState.update {
                it.copy(
                    isBroadcasting = true,
                    confirmStep = 2,
                    broadcastCount = 0,
                    broadcastPacketId = packet.packetId
                )
            }

            viewModelScope.launch {
                notificationBus.emitSos(
                    com.elv8.crisisos.core.notification.event.NotificationEvent.Sos.OwnAlertBroadcasting(
                        alertId = packet.packetId,
                        sosType = currentState.sosType?.name ?: SosType.GENERAL.name,
                        peersReached = 0
                    )
                )
            }

        }
    }

    fun cancelBroadcast() {
        val currentBroadcastPacketId = uiState.value.broadcastPacketId
        
        val packet = PacketFactory.buildSosCancelPacket(
            senderId = getDeviceId(context),
            senderAlias = getAlias(context)
        )

        viewModelScope.launch {
            messenger.send(packet)
            notificationBus.emitSos(
                com.elv8.crisisos.core.notification.event.NotificationEvent.Sos.OwnAlertStopped(
                    alertId = currentBroadcastPacketId ?: "unknown"
                )
            )
        }

        _uiState.update {
            it.copy(
                isBroadcasting = false,
                confirmStep = 0,
                broadcastCount = 0,
                broadcastPacketId = null
            )
        }
    }

    private fun getDeviceId(ctx: Context): String {
        return Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: java.util.UUID.randomUUID().toString()
    }

    private fun getAlias(ctx: Context): String {
        val sharedPrefs = ctx.getSharedPreferences("crisisos_prefs", Context.MODE_PRIVATE)
        return sharedPrefs.getString("user_alias", "Survivor_${Build.MODEL}") ?: "Survivor_${Build.MODEL}"
    }
}
