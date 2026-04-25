package com.elv8.crisisos.ui.screens.sos

import com.elv8.crisisos.domain.repository.LocationRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.elv8.crisisos.core.event.EventBus
import com.elv8.crisisos.core.event.AppEvent
import com.elv8.crisisos.data.remote.mesh.MeshMessenger
import com.elv8.crisisos.data.dto.PacketFactory
import com.elv8.crisisos.data.dto.payloads.SosPayload
import com.elv8.crisisos.domain.location.CrisisLocation
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

/** Snapshot of the location attached to an outgoing SOS broadcast. */
data class SosLocationSnapshot(
    val latitude: Double?,
    val longitude: Double?,
    val gridLabel: String,
    val capturedAt: Long?,
    /** True when GPS was unavailable and we fell back to last-known/no location. */
    val approximate: Boolean
) {
    companion object {
        val Unknown = SosLocationSnapshot(
            latitude = null,
            longitude = null,
            gridLabel = "Location unavailable",
            capturedAt = null,
            approximate = true
        )
    }
}

data class SosUiState(
    val isBroadcasting: Boolean = false,
    val messageText: String = "",
    val sosType: SosType? = null,
    val broadcastCount: Int = 0,
    val broadcastPacketId: String? = null,
    val broadcastStartedAt: Long? = null,
    /** Epoch ms of the next scheduled re-broadcast, drives the UI countdown. */
    val nextRebroadcastAt: Long? = null,
    /** Epoch ms when the post-cancel cooldown ends; while non-null, button is disabled. */
    val cooldownEndsAt: Long? = null,
    val location: SosLocationSnapshot = SosLocationSnapshot.Unknown,
    val myCrsId: String = "",
    val myAlias: String = "",
    val incomingAlerts: List<IncomingAlert> = emptyList()
)

@HiltViewModel
class SosViewModel @Inject constructor(
    private val messenger: MeshMessenger,
    private val eventBus: EventBus,
    private val locationRepository: LocationRepository,
    private val identityRepository: com.elv8.crisisos.domain.repository.IdentityRepository,
    private val notificationBus: com.elv8.crisisos.core.notification.NotificationEventBus,
    private val notificationHandler: com.elv8.crisisos.core.notification.NotificationHandler,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        const val REPEAT_INTERVAL_MS: Long = 10 * 60 * 1000L
        const val COOLDOWN_MS: Long = 10 * 60 * 1000L
        /** A captured location older than this is treated as "approximate". */
        private const val LOCATION_FRESH_WINDOW_MS: Long = 5 * 60 * 1000L
    }

    private val _uiState = MutableStateFlow(SosUiState())
    val uiState: StateFlow<SosUiState> = _uiState.asStateFlow()

    private var repeatJob: Job? = null
    private var cooldownJob: Job? = null

    init {
        // Pre-load identity for confirmation dialog and packet sending.
        viewModelScope.launch {
            val identity = identityRepository.getIdentity().first()
            val crsId = identity?.crsId ?: getDeviceId(context)
            val alias = identity?.alias ?: getAlias(context)
            _uiState.update { it.copy(myCrsId = crsId, myAlias = alias) }
        }

        // Pre-load latest location so confirm dialog is accurate.
        viewModelScope.launch {
            refreshLocationSnapshot()
        }

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
                messageText = if (it.messageText.isBlank()) type.quickPhrase else it.messageText
            )
        }
    }

    fun updateMessage(message: String) {
        _uiState.update { it.copy(messageText = message) }
    }

    /** Refreshes location snapshot just before the user confirms an SOS. */
    fun refreshLocationBeforeConfirm() {
        viewModelScope.launch { refreshLocationSnapshot() }
    }

    private suspend fun refreshLocationSnapshot() {
        val loc = locationRepository.getLastKnownLocation()
        _uiState.update { it.copy(location = loc.toSnapshot()) }
    }

    private fun CrisisLocation?.toSnapshot(): SosLocationSnapshot {
        if (this == null) return SosLocationSnapshot.Unknown
        val ageMs = System.currentTimeMillis() - this.timestamp
        return SosLocationSnapshot(
            latitude = latitude,
            longitude = longitude,
            gridLabel = formatGrid(latitude, longitude),
            capturedAt = timestamp,
            approximate = ageMs > LOCATION_FRESH_WINDOW_MS
        )
    }

    /**
     * Begin broadcasting. No-ops if a cooldown is currently active or we are already broadcasting.
     */
    fun startBroadcast() {
        val current = _uiState.value
        if (current.isBroadcasting) return
        if (current.cooldownEndsAt != null && current.cooldownEndsAt > System.currentTimeMillis()) return

        viewModelScope.launch {
            // Refresh just-in-time so the broadcast carries the freshest fix.
            refreshLocationSnapshot()
            val state = _uiState.value
            val type = state.sosType ?: SosType.GENERAL

            val packet = sendOnce(state, type)

            _uiState.update {
                it.copy(
                    isBroadcasting = true,
                    broadcastPacketId = packet.packetId,
                    broadcastStartedAt = System.currentTimeMillis(),
                    broadcastCount = 0,
                    nextRebroadcastAt = System.currentTimeMillis() + REPEAT_INTERVAL_MS,
                    cooldownEndsAt = null
                )
            }

            notificationBus.emitSos(
                com.elv8.crisisos.core.notification.event.NotificationEvent.Sos.OwnAlertBroadcasting(
                    alertId = packet.packetId,
                    sosType = type.name,
                    peersReached = 0
                )
            )

            scheduleRepeats(type)
        }
    }

    private suspend fun sendOnce(state: SosUiState, type: SosType): com.elv8.crisisos.data.dto.MeshPacket {
        val payload = SosPayload(
            sosType = type.name,
            message = state.messageText.ifBlank { type.quickPhrase },
            locationHint = state.location.gridLabel.takeUnless { state.location.latitude == null },
            latitude = state.location.latitude,
            longitude = state.location.longitude,
            senderName = state.myAlias
        )
        val packet = PacketFactory.buildSosPacket(
            senderId = state.myCrsId,
            senderAlias = state.myAlias,
            payload = payload,
            locationHint = state.location.gridLabel
        )
        messenger.send(packet)
        return packet
    }

    private fun scheduleRepeats(type: SosType) {
        repeatJob?.cancel()
        repeatJob = viewModelScope.launch {
            while (true) {
                delay(REPEAT_INTERVAL_MS)
                val s = _uiState.value
                if (!s.isBroadcasting) break
                refreshLocationSnapshot()
                val refreshed = _uiState.value
                sendOnce(refreshed, type)
                _uiState.update {
                    it.copy(nextRebroadcastAt = System.currentTimeMillis() + REPEAT_INTERVAL_MS)
                }
            }
        }
    }

    fun cancelBroadcast() {
        val state = _uiState.value
        if (!state.isBroadcasting) return

        repeatJob?.cancel()
        repeatJob = null

        val cancelPacket = PacketFactory.buildSosCancelPacket(
            senderId = state.myCrsId.ifBlank { getDeviceId(context) },
            senderAlias = state.myAlias.ifBlank { getAlias(context) }
        )

        viewModelScope.launch {
            messenger.send(cancelPacket)
            notificationBus.emitSos(
                com.elv8.crisisos.core.notification.event.NotificationEvent.Sos.OwnAlertStopped(
                    alertId = state.broadcastPacketId ?: "unknown"
                )
            )
        }

        val cooldownUntil = System.currentTimeMillis() + COOLDOWN_MS
        _uiState.update {
            it.copy(
                isBroadcasting = false,
                broadcastCount = 0,
                broadcastPacketId = null,
                broadcastStartedAt = null,
                nextRebroadcastAt = null,
                cooldownEndsAt = cooldownUntil
            )
        }

        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            delay(COOLDOWN_MS)
            _uiState.update { it.copy(cooldownEndsAt = null) }
        }
    }

    fun dismissIncomingAlert(senderId: String) {
        _uiState.update { it.copy(incomingAlerts = it.incomingAlerts.filterNot { a -> a.senderId == senderId }) }
    }

    private fun getDeviceId(ctx: Context): String {
        return Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: java.util.UUID.randomUUID().toString()
    }

    private fun getAlias(ctx: Context): String {
        val sharedPrefs = ctx.getSharedPreferences("crisisos_prefs", Context.MODE_PRIVATE)
        return sharedPrefs.getString("user_alias", "Survivor_${Build.MODEL}") ?: "Survivor_${Build.MODEL}"
    }

    override fun onCleared() {
        repeatJob?.cancel()
        cooldownJob?.cancel()
        // Defensive: if the user navigates away while broadcasting, the suppress collector
        // will never re-trigger unsuppress. Make sure incoming SOS notifications can fire again.
        notificationHandler.unsuppressGroup("group_sos")
        super.onCleared()
    }
}

/** Coarse human-readable grid label used by the SOS confirm dialog and broadcast UI. */
private fun formatGrid(lat: Double, lng: Double): String {
    val ns = if (lat >= 0) "N" else "S"
    val ew = if (lng >= 0) "E" else "W"
    return "GRID %.4f%s, %.4f%s".format(kotlin.math.abs(lat), ns, kotlin.math.abs(lng), ew)
}
