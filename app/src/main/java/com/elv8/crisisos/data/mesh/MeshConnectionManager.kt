package com.elv8.crisisos.data.mesh

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import com.elv8.crisisos.core.debug.MeshLogger
import com.elv8.crisisos.core.event.AppEvent
import com.elv8.crisisos.core.event.EventBus
import com.elv8.crisisos.data.dto.PacketParser
import com.elv8.crisisos.data.local.dao.PeerDao
import com.elv8.crisisos.data.local.entity.PeerEntity
import com.elv8.crisisos.domain.model.identity.CrsIdGenerator
import com.elv8.crisisos.domain.repository.PeerRepository
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import dagger.Lazy

@Singleton
class MeshConnectionManager @Inject constructor(
    private val context: Context,
    private val eventBus: EventBus,
    private val scope: CoroutineScope,
    private val peerDao: PeerDao,
    private val peerRepositoryLazy: Lazy<PeerRepository>
) {
    private val connectionsClient: ConnectionsClient = Nearby.getConnectionsClient(context)

    private val _connectedPeers = MutableStateFlow<Map<String, ConnectedPeer>>(emptyMap())
    private val _isAdvertising = MutableStateFlow(false)
    private val _isDiscovering = MutableStateFlow(false)

    val connectedPeers: StateFlow<Map<String, ConnectedPeer>> = _connectedPeers.asStateFlow()
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()
    val peerCount = _connectedPeers.map { it.size }

    private lateinit var localAlias: String
    private var localCrsId: String = ""

    fun getDiscoveryRestartCount(): Int = discoveryRestartCount
    fun getLocalAlias(): String = if (::localAlias.isInitialized) localAlias else "UNKNOWN"
    fun getLocalCrsId(): String = localCrsId

    val pendingAliases: MutableMap<String, String> = ConcurrentHashMap()
    private val pendingCrsIds: MutableMap<String, String> = ConcurrentHashMap()

    // File transfer tracking
    private val activeFileTransfers: MutableMap<Long, String> = ConcurrentHashMap()  // payloadId → fileId (outgoing)
    private val pendingFilePayloads: MutableMap<Long, Payload> = ConcurrentHashMap() // payloadId → Payload (incoming)

    data class ActiveEndpoint(
        val endpointId: String,
        val crsId: String,
        val alias: String,
        var signalStrength: Int = -65,       // dBm, Nearby doesn't expose real RSSI yet
        var distanceMeters: Float = 10f,
        var discoveredAt: Long = System.currentTimeMillis(),
        var lastSeenAt: Long = System.currentTimeMillis(),
        var isConnected: Boolean = false
    )

    private val activeEndpoints: MutableMap<String, ActiveEndpoint> = ConcurrentHashMap()
    private var signalRefreshJob: Job? = null

    private var discoveryWatchdogJob: Job? = null
    private var debugLogJob: Job? = null
    private var heartbeatJob: Job? = null
    private var advertisingJob: Job? = null
    private var discoveryJob: Job? = null
    private var lastEndpointFoundAt: Long = 0L
    private val DISCOVERY_WATCHDOG_TIMEOUT_MS = 25_000L
    private val DISCOVERY_RESTART_DELAY_MS = 2_000L
    private var discoveryRestartCount = 0

    fun setLocalCrsId(crsId: String) {
        this.localCrsId = crsId
    }

    fun updateLastSeen(endpointId: String) {
        val crsId = pendingCrsIds[endpointId] ?: return
        val now = System.currentTimeMillis()
        
        // Update in-memory ConnectedPeer
        val current = _connectedPeers.value[endpointId]
        if (current != null) {
            _connectedPeers.value = _connectedPeers.value + (endpointId to current.copy(lastSeenAt = now))
        }
        
        // Update Room async (no suspend needed � fire and forget)
        scope.launch(Dispatchers.IO) {
            peerDao.updateStatus(crsId, "AVAILABLE", now)
                (peerRepositoryLazy.get() as? com.elv8.crisisos.data.repository.PeerRepositoryImpl)?.forceRefreshPeer(crsId)
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            Log.e("MEDIA_DEBUG", "=== onPayloadReceived === endpointId=$endpointId type=${payload.type} id=${payload.id}")
            MeshLogger.connection(
                "onPayloadReceived � endpointId=$endpointId type=${payload.type}"
            )
            
            when (payload.type) {
                Payload.Type.BYTES -> {
                    Log.e("MEDIA_DEBUG", "BYTES BRANCH HIT: payloadId=${payload.id} size=${payload.asBytes()?.size}")
                    val bytes = payload.asBytes()
                    if (bytes == null || bytes.isEmpty()) {
                        MeshLogger.warn("Mesh", "Received empty payload from $endpointId � ignoring")
                        return
                    }
                    
                    val json = try {
                        String(bytes, Charsets.UTF_8)
                    } catch (e: Exception) {
                        MeshLogger.error("Mesh", "Failed to decode payload bytes from $endpointId � ${e.message}")
                        return
                    }
                    
                    MeshLogger.payload(
                        "Payload from $endpointId (${bytes.size} bytes): ${json.take(120)}..."
                    )
                    
                    // Update last seen for this peer
                    updateLastSeen(endpointId)
                    
                    val packet = try {
                        PacketParser.parse(json)
                    } catch (e: Exception) {
                        MeshLogger.error("Mesh", "PacketParser threw for payload from $endpointId � ${e.message}")
                        null
                    }
                    
                    if (packet == null) {
                        MeshLogger.warn("Mesh", "Could not parse packet from $endpointId � json preview: ${json.take(80)}")
                        return
                    }
                    
                    MeshLogger.connection(
                        "Packet parsed � type=${packet.type} packetId=${packet.packetId} " +
                        "from=${packet.senderAlias} ttl=${packet.ttl} hop=${packet.hopCount}"
                    )
                    
                    scope.launch {
                        try {
                            eventBus.emit(AppEvent.MeshEvent.RawPacketReceived(packet, endpointId))
                        } catch (e: Exception) {
                            MeshLogger.error("Mesh", "EventBus emit failed  ${e.message}")
                        }
                    }
                }
                
                Payload.Type.STREAM -> {
                    MeshLogger.connection("Stream payload received from $endpointId — not handled yet")
                }
                
                Payload.Type.FILE -> {
                    Log.e("MEDIA_DEBUG", "FILE BRANCH HIT: payloadId=${payload.id}")
                    Log.d("CrisisOS_Mesh", "File payload received id=${payload.id} from $endpointId")
                    pendingFilePayloads[payload.id] = payload
                }
                
                else -> {
                    MeshLogger.warn("Mesh", "Unknown payload type ${payload.type} from $endpointId")
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            Log.e("MEDIA_DEBUG", "=== onPayloadTransferUpdate === payloadId=${update.payloadId} status=${update.status} transferred=${update.bytesTransferred} total=${update.totalBytes}")
            // Track outgoing file transfers
            val outgoingFileId = activeFileTransfers[update.payloadId]
            if (outgoingFileId != null) {
                when (update.status) {
                    PayloadTransferUpdate.Status.SUCCESS -> {
                        activeFileTransfers.remove(update.payloadId)
                        Log.i("CrisisOS_Mesh", "FileSendCompleted: fileId=$outgoingFileId payloadId=${update.payloadId}")
                        scope.launch { eventBus.emit(AppEvent.MeshEvent.FileSendCompleted(outgoingFileId)) }
                    }
                    PayloadTransferUpdate.Status.FAILURE -> {
                        activeFileTransfers.remove(update.payloadId)
                        Log.e("CrisisOS_Mesh", "FileSendFailed: fileId=$outgoingFileId payloadId=${update.payloadId}")
                        scope.launch { eventBus.emit(AppEvent.MeshEvent.FileSendFailed(outgoingFileId)) }
                    }
                    PayloadTransferUpdate.Status.IN_PROGRESS -> {
                        val pct = if (update.totalBytes > 0) (update.bytesTransferred * 100 / update.totalBytes) else 0
                        MeshLogger.payload("File send progress: fileId=$outgoingFileId ${pct}%")
                    }
                    else -> {}
                }
                return
            }

            // Track incoming file transfers
            if (pendingFilePayloads.containsKey(update.payloadId)) {
                when (update.status) {
                    PayloadTransferUpdate.Status.SUCCESS -> {
                        val filePayload = pendingFilePayloads.remove(update.payloadId)
                        val payloadFile = filePayload?.asFile()
                        if (payloadFile != null) {
                            Log.i("CrisisOS_Mesh", "MediaFileReceived: payloadId=${update.payloadId} from $endpointId")
                            scope.launch {
                                eventBus.emit(AppEvent.MeshEvent.MediaFileReceived(endpointId, update.payloadId, payloadFile))
                            }
                        } else {
                            Log.e("CrisisOS_Mesh", "File payload completed but asFile() returned null: payloadId=${update.payloadId}")
                        }
                    }
                    PayloadTransferUpdate.Status.FAILURE -> {
                        pendingFilePayloads.remove(update.payloadId)
                        MeshLogger.warn("Mesh", "Incoming file transfer FAILED: payloadId=${update.payloadId} from $endpointId")
                    }
                    PayloadTransferUpdate.Status.IN_PROGRESS -> {
                        val pct = if (update.totalBytes > 0) (update.bytesTransferred * 100 / update.totalBytes) else 0
                        MeshLogger.payload("File receive progress: payloadId=${update.payloadId} ${pct}%")
                    }
                    else -> {}
                }
                return
            }

            // Original behavior for non-file payloads
            if (update.status == PayloadTransferUpdate.Status.FAILURE) {
                MeshLogger.warn("Mesh", 
                    "Payload transfer FAILED — endpointId=$endpointId " +
                    "payloadId=${update.payloadId}"
                )
            }
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            val crsId = info.endpointName.split("|").getOrElse(0) { info.endpointName }
            val alias = info.endpointName.split("|").getOrElse(1) { "Unknown" }
            
            Log.i("CrisisOS_Mesh",
                "onConnectionInitiated � endpointId=$endpointId " +
                "alias=$alias crsId=$crsId " +
                "isIncomingConnection=${info.isIncomingConnection}"
            )
            
            // Store alias for this endpoint regardless of direction
            pendingAliases[endpointId] = alias
            pendingCrsIds[endpointId] = crsId
            
            // ALWAYS auto-accept � no user interaction required during testing
            MeshLogger.connection("Auto-accepting connection from $alias ($endpointId)")
            connectionsClient.acceptConnection(endpointId, payloadCallback)
                .addOnSuccessListener {
                    MeshLogger.connection("acceptConnection() submitted for $endpointId")
                }
                .addOnFailureListener { e ->
                    MeshLogger.error("Mesh", "acceptConnection() FAILED for $endpointId � ${e.message}")
                }
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            val alias = pendingAliases[endpointId] ?: endpointId
            val crsId = pendingCrsIds[endpointId] ?: ("UNKNOWN-" + endpointId.take(6))
            
            Log.i("CrisisOS_Mesh",
                "onConnectionResult � endpointId=$endpointId alias=$alias crsId=$crsId " +
                "statusCode=${resolution.status.statusCode} " +
                "isSuccess=${resolution.status.isSuccess}"
            )
            
            if (resolution.status.isSuccess) {
                val peer = ConnectedPeer(
                    endpointId = endpointId,
                    crsId = crsId,
                    alias = alias,
                    connectedAt = System.currentTimeMillis(),
                    lastSeenAt = System.currentTimeMillis(),
                    isAuthenticated = false
                )
                _connectedPeers.value = _connectedPeers.value + (endpointId to peer)

                activeEndpoints[endpointId]?.isConnected = true
                activeEndpoints[endpointId]?.lastSeenAt = System.currentTimeMillis()

                Log.i("CrisisOS_Mesh",
                    "CONNECTION ESTABLISHED � $alias ($crsId) endpointId=$endpointId " +
                    "totalPeers=${_connectedPeers.value.size}"
                )
                
                scope.launch(Dispatchers.IO) {
                    // Update Room with CONNECTED status
                    val existing = peerDao.getByCrsId(crsId)
                    if (existing != null) {
                        peerDao.updateStatus(crsId, "AVAILABLE", System.currentTimeMillis())
                        (peerRepositoryLazy.get() as? com.elv8.crisisos.data.repository.PeerRepositoryImpl)?.forceRefreshPeer(crsId)
                    } else {
                        // Peer connected without prior discovery (we were advertiser, they initiated)
                        peerDao.insert(PeerEntity(
                            crsId = crsId, alias = alias, deviceId = endpointId,
                            discoveredAt = System.currentTimeMillis(),
                            lastSeenAt = System.currentTimeMillis(),
                            signalStrength = -55, distanceMeters = 5f,
                            status = "AVAILABLE", isNearby = true,
                            avatarColor = CrsIdGenerator.generateAvatarColor(crsId),
                            publicKey = null
                        ))
                        MeshLogger.connection("New peer inserted at connection (was advertiser) � crsId=$crsId")
                    }
                    
                    eventBus.emit(AppEvent.MeshEvent.PeerConnected(endpointId, alias))
                    eventBus.emit(AppEvent.MeshEvent.MeshStatusChanged(true, _connectedPeers.value.size))
                }
                
                // Reset watchdog since we found a peer
                startDiscoveryTimeoutWatchdog()
                
            } else {
                MeshLogger.error("Mesh", 
                    "CONNECTION FAILED � $alias endpointId=$endpointId " +
                    "statusCode=${resolution.status.statusCode} " +
                    "statusMessage=${resolution.status.statusMessage}"
                )
                
                // If connection rejected due to duplicate, that is OK � already connected
                if (resolution.status.statusCode == 8001) {
                    MeshLogger.connection("Status 8001 = already connected to $alias, ignoring")
                } else {
                    scope.launch(Dispatchers.IO) {
                        if (crsId != "unknown") peerDao.updateStatus(crsId, "OFFLINE", System.currentTimeMillis())
                    }
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            val alias = pendingAliases[endpointId] ?: "unknown"
            val crsId = pendingCrsIds[endpointId] ?: "unknown"

            MeshLogger.warn("Mesh", "onDisconnected � endpointId=$endpointId alias=$alias crsId=$crsId")

            activeEndpoints.remove(endpointId)
            MeshLogger.connection("ActiveEndpoint removed � total active: ${activeEndpoints.size}")

            _connectedPeers.value = _connectedPeers.value - endpointId

            scope.launch(Dispatchers.IO) {
                if (crsId != "unknown") {
                    peerDao.updateStatus(crsId, "OFFLINE", System.currentTimeMillis())
                }
                eventBus.emit(AppEvent.MeshEvent.PeerDisconnected(endpointId))
                eventBus.emit(AppEvent.MeshEvent.MeshStatusChanged(
                    _connectedPeers.value.isNotEmpty(), _connectedPeers.value.size
                ))
            }
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            lastEndpointFoundAt = System.currentTimeMillis()
            
            MeshLogger.connection(
                "onEndpointFound � endpointId=$endpointId " +
                "endpointName=${info.endpointName} " +
                "serviceId=${info.serviceId}"
            )
            
            // Parse alias from endpoint name (format: "crsId|alias" or just "alias")
            val parts = info.endpointName.split("|")
            val crsId = parts.getOrElse(0) { info.endpointName }
            val alias = parts.getOrElse(1) { "Unknown" }

            pendingAliases[endpointId] = alias
            pendingCrsIds[endpointId] = crsId

            activeEndpoints[endpointId] = ActiveEndpoint(
                endpointId = endpointId,
                crsId = crsId,
                alias = alias
            )
            MeshLogger.connection("ActiveEndpoint added � total active: ${activeEndpoints.size}")

            // Insert into Room IMMEDIATELY so UI shows the peer
            scope.launch(Dispatchers.IO) {
                val existing = peerDao.getByCrsId(crsId)
                val entity = PeerEntity(
                    crsId = crsId,
                    alias = alias,
                    deviceId = endpointId,
                    discoveredAt = System.currentTimeMillis(),
                    lastSeenAt = System.currentTimeMillis(),
                    signalStrength = -65,       // default until we get real signal
                    distanceMeters = 10f,
                    status = "AVAILABLE",
                    isNearby = true,
                    avatarColor = CrsIdGenerator.generateAvatarColor(crsId),
                    publicKey = null
                )
                if (existing == null) {
                    peerDao.insert(entity)
                    MeshLogger.connection("Peer inserted to Room � crsId=$crsId alias=$alias")
                } else {
                    peerDao.updateStatus(crsId, "AVAILABLE", System.currentTimeMillis())
                        (peerRepositoryLazy.get() as? com.elv8.crisisos.data.repository.PeerRepositoryImpl)?.forceRefreshPeer(crsId)
                }
                eventBus.emit(AppEvent.MeshEvent.PeerDiscovered(crsId, alias))
            }
            
            // Request connection � check capacity first
            if (_connectedPeers.value.size >= MeshConfig.MAX_CONNECTIONS) {
                MeshLogger.warn("Mesh", "Max connections reached (${MeshConfig.MAX_CONNECTIONS}), not requesting")
                return
            }
            
            MeshLogger.connection("Requesting connection to endpointId=$endpointId alias=$alias")
            connectionsClient.requestConnection(
                "$localCrsId|$localAlias",    // consistency: CrsId|Alias
                endpointId,
                connectionLifecycleCallback
            ).addOnSuccessListener {
                MeshLogger.connection("requestConnection() sent successfully to $endpointId")
            }.addOnFailureListener { e ->
                MeshLogger.error("Mesh", "requestConnection() FAILED to $endpointId  ${e.message}")
            }
        }

        override fun onEndpointLost(endpointId: String) {
            val alias = pendingAliases[endpointId] ?: "unknown"
            val crsId = pendingCrsIds[endpointId] ?: "unknown"
            MeshLogger.warn("Mesh", "onEndpointLost � endpointId=$endpointId alias=$alias crsId=$crsId")

            activeEndpoints.remove(endpointId)
            MeshLogger.connection("ActiveEndpoint removed � total active: ${activeEndpoints.size}")

            pendingAliases.remove(endpointId)
            // Do NOT remove pendingCrsIds � may reconnect

            scope.launch(Dispatchers.IO) {
                if (crsId != "unknown") {
                    peerDao.updateStatus(crsId, "OFFLINE", System.currentTimeMillis())
                    MeshLogger.connection("Peer marked OFFLINE in Room � crsId=$crsId")
                }
                eventBus.emit(AppEvent.MeshEvent.PeerLost(endpointId))
            }
        }
    }

    fun startMesh(alias: String) {
        localAlias = alias
        startAdvertising()
        startDiscovery()
        startPeerHeartbeat()
        startSignalRefreshLoop()
        debugLogJob?.cancel()
        debugLogJob = scope.launch {
            while(isActive) {
                delay(30_000)
                MeshLogger.connection(getMeshDebugState())
            }
        }
    }

    fun startAdvertising() {
        val options = AdvertisingOptions.Builder()
            .setStrategy(MeshConfig.STRATEGY)
            .build()
        val advertiserName = "$localCrsId|$localAlias"
        connectionsClient.startAdvertising(
            advertiserName, MeshConfig.SERVICE_ID, connectionLifecycleCallback, options
        )
            .addOnSuccessListener {
                MeshLogger.connection("Advertising started successfully")
                _isAdvertising.value = true
            }
            .addOnFailureListener { e ->
                MeshLogger.error("Mesh", "Advertising failed, retrying locally: ${e.message}")
                _isAdvertising.value = false
                restartAdvertising()
            }
    }

    fun startDiscovery() {
        val options = DiscoveryOptions.Builder()
            .setStrategy(MeshConfig.STRATEGY)
            .build()
        connectionsClient.startDiscovery(
            MeshConfig.SERVICE_ID, endpointDiscoveryCallback, options
        )
            .addOnSuccessListener {
                MeshLogger.connection("Discovery started successfully")
                _isDiscovering.value = true
                startDiscoveryTimeoutWatchdog()
            }
            .addOnFailureListener { e ->
                MeshLogger.error("Mesh", "Discovery failed, retrying locally: ${e.message}")
                _isDiscovering.value = false
                restartDiscovery()
            }
    }

    private fun startDiscoveryTimeoutWatchdog() {
        heartbeatJob?.cancel()
        heartbeatJob?.cancel()
        discoveryWatchdogJob?.cancel()
        discoveryWatchdogJob = scope.launch {
            MeshLogger.connection("Discovery watchdog started � timeout=${DISCOVERY_WATCHDOG_TIMEOUT_MS}ms")
            delay(DISCOVERY_WATCHDOG_TIMEOUT_MS)
            val timeSinceLastPeer = System.currentTimeMillis() - lastEndpointFoundAt
            val hasPeers = _connectedPeers.value.isNotEmpty()
            if (!hasPeers && timeSinceLastPeer > DISCOVERY_WATCHDOG_TIMEOUT_MS) {
                discoveryRestartCount++
                MeshLogger.warn("Mesh", 
                    "Discovery watchdog triggered � no peers found in ${DISCOVERY_WATCHDOG_TIMEOUT_MS}ms " +
                    "(restart #$discoveryRestartCount)"
                )
                restartDiscovery()
            } else {
                MeshLogger.connection(
                    "Discovery watchdog OK � peers=${_connectedPeers.value.size} " +
                    "timeSinceLastPeer=${timeSinceLastPeer}ms"
                )
                startDiscoveryTimeoutWatchdog()
            }
        }
    }

    private fun restartDiscovery() {
        scope.launch {
            MeshLogger.connection("restartDiscovery() � stopping discovery first")
            try { connectionsClient.stopDiscovery() } catch (e: Exception) { MeshLogger.warn("Mesh", "stopDiscovery() threw: ${e.message}") }
            _isDiscovering.value = false
            delay(DISCOVERY_RESTART_DELAY_MS)
            MeshLogger.connection("restartDiscovery() � restarting now")
            startDiscovery()
        startPeerHeartbeat()
        startPeerHeartbeat()
        }
    }

    private fun restartAdvertising() {
        scope.launch {
            MeshLogger.connection("restartAdvertising() � stopping first")
            try { connectionsClient.stopAdvertising() } catch (e: Exception) { /* ignore */ }
            _isAdvertising.value = false
            delay(DISCOVERY_RESTART_DELAY_MS)
            MeshLogger.connection("restartAdvertising() � restarting now")
            startAdvertising()
        }
    }

    fun getMeshDebugState(): String = buildString {
        appendLine("=== CrisisOS Mesh Debug ===")
        appendLine("isAdvertising: ${_isAdvertising.value}")
        appendLine("isDiscovering: ${_isDiscovering.value}")
        appendLine("connectedPeers: ${_connectedPeers.value.size}")
        appendLine("localAlias: $localAlias")
        appendLine("lastEndpointFoundAt: ${if (lastEndpointFoundAt == 0L) "never" else ("" + (System.currentTimeMillis() - lastEndpointFoundAt) + "ms ago")}")
        appendLine("discoveryRestartCount: $discoveryRestartCount")
        appendLine("peers:")
        _connectedPeers.value.forEach { (id, peer) ->
            appendLine("  - $id | ${peer.alias} | connected=${peer.connectedAt}")
        }
    }

    fun stopAdvertising() {
        connectionsClient.stopAdvertising()
        _isAdvertising.value = false
    }

    fun stopDiscovery() {
        connectionsClient.stopDiscovery()
        _isDiscovering.value = false
    }

    private fun sendToEndpoint(endpointId: String, packet: com.elv8.crisisos.data.dto.MeshPacket) {
        val json = com.elv8.crisisos.data.dto.MeshJson.encodeToString(com.elv8.crisisos.data.dto.MeshPacket.serializer(), packet)
        connectionsClient.sendPayload(endpointId, Payload.fromBytes(json.toByteArray(Charsets.UTF_8)))
    }

    /**
     * Send a pre-built file payload to a specific endpoint.
     * The caller creates Payload.fromFile() to capture payloadId before building the announce.
     */
    fun sendFilePayload(endpointId: String, filePayload: Payload, fileId: String) {
        Log.e("MEDIA_DEBUG", "=== sendFilePayload CALLED === endpointId=$endpointId fileId=$fileId payloadId=${filePayload.id}")
        activeFileTransfers[filePayload.id] = fileId

        connectionsClient.sendPayload(endpointId, filePayload)
            .addOnSuccessListener {
                Log.e("MEDIA_DEBUG", "connectionsClient.sendPayload SUCCESS for payloadId=${filePayload.id}")
                Log.i("CrisisOS_Mesh", "File payload sent: fileId=$fileId payloadId=${filePayload.id}")
            }
            .addOnFailureListener { e ->
                Log.e("MEDIA_DEBUG", "connectionsClient.sendPayload FAILED: ${e.message} statusCode=${(e as? com.google.android.gms.common.api.ApiException)?.statusCode}")
                activeFileTransfers.remove(filePayload.id)
                Log.e("CrisisOS_Mesh", "File payload send FAILED: fileId=$fileId — ${e.message}")
                scope.launch {
                    eventBus.emit(AppEvent.MeshEvent.MessageFailed(fileId, e.message ?: "File send failed"))
                }
            }
    }

    /**
     * Convenience: open a file, create a Payload, and send it.
     * Returns the payloadId so the caller can include it in the announce packet.
     */
    fun sendFileToEndpoint(endpointId: String, file: File, fileId: String): Long {
        return try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val filePayload = Payload.fromFile(pfd)
            sendFilePayload(endpointId, filePayload, fileId)
            filePayload.id
        } catch (e: Exception) {
            Log.e("CrisisOS_Mesh", "sendFileToEndpoint error: fileId=$fileId — ${e.message}", e)
            scope.launch {
                eventBus.emit(AppEvent.MeshEvent.MessageFailed(fileId, e.message ?: "File open failed"))
            }
            -1L
        }
    }

    private fun startPeerHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            MeshLogger.connection("Peer heartbeat started � interval=10s")
            while (isActive) {
                delay(10_000)
                
                val connected = _connectedPeers.value
                if (connected.isEmpty()) continue
                
                MeshLogger.connection("Heartbeat tick � ${connected.size} peers connected")
                
                val now = System.currentTimeMillis()
                
                connected.forEach { (endpointId, peer) ->
                    val timeSinceLastSeen = now - peer.lastSeenAt
                    
                    if (timeSinceLastSeen > 45_000) {
                        MeshLogger.warn("Mesh", "Peer ${peer.alias} not seen for ${timeSinceLastSeen}ms � marking OFFLINE")
                        onDisconnectedByHeartbeat(endpointId)
                        return@forEach
                    }
                    
                    val pingPacket = com.elv8.crisisos.data.dto.PacketFactory.buildPingPacket(localCrsId, localAlias)
                    sendToEndpoint(endpointId, pingPacket)
                    MeshLogger.payload("Heartbeat ping sent to ${peer.alias} ($endpointId)")
                }
            }
        }
    }

    private fun onDisconnectedByHeartbeat(endpointId: String) {
        val alias = pendingAliases[endpointId] ?: "unknown"
        val crsId = pendingCrsIds[endpointId] ?: "unknown"
        MeshLogger.warn("Mesh", "Heartbeat disconnect � $alias ($crsId)")
        
        _connectedPeers.value = _connectedPeers.value - endpointId
        try { connectionsClient.disconnectFromEndpoint(endpointId) } catch (e: Exception) { }
        
        scope.launch(Dispatchers.IO) {
            if (crsId != "unknown") peerDao.updateStatus(crsId, "OFFLINE", System.currentTimeMillis())
            eventBus.emit(AppEvent.MeshEvent.PeerDisconnected(endpointId))
        }
    }

    private fun startSignalRefreshLoop() {
        signalRefreshJob?.cancel()
        signalRefreshJob = scope.launch {
            MeshLogger.connection("Signal refresh loop started � interval=8s")
            while (isActive) {
                delay(8_000)

                if (activeEndpoints.isEmpty()) continue

                val now = System.currentTimeMillis()
                MeshLogger.payload("Signal refresh � updating ${activeEndpoints.size} endpoints")

                activeEndpoints.values.forEach { endpoint ->
                    // Simulate realistic signal fluctuation until Nearby exposes real RSSI
                    val currentSignal = endpoint.signalStrength
                    val fluctuation = (-5..5).random()
                    val newSignal = (currentSignal + fluctuation).coerceIn(-90, -35)
                    endpoint.signalStrength = newSignal

                    // Distance is loosely derived from signal (not real but realistic)
                    val newDistance = (((-newSignal - 35) / 55f) * 95f + 5f).coerceIn(2f, 100f)
                    endpoint.distanceMeters = newDistance
                    endpoint.lastSeenAt = now

                    // Write to Room
                    scope.launch(Dispatchers.IO) {
                        val existing = peerDao.getByCrsId(endpoint.crsId)
                        if (existing != null) {
                            peerDao.updateSignal(endpoint.crsId, newSignal, newDistance)
                            peerDao.updateStatus(endpoint.crsId, "AVAILABLE", now)
                            (peerRepositoryLazy.get() as? com.elv8.crisisos.data.repository.PeerRepositoryImpl)?.forceRefreshPeer(endpoint.crsId)
                            MeshLogger.payload("Signal refresh for ${endpoint.alias} � signal=${newSignal}dBm distance=${newDistance.toInt()}m")
                        }
                    }
                }
            }
        }
    }

    fun stopAll() {
        MeshLogger.connection("stopAll() called � tearing down mesh")
        
        discoveryWatchdogJob?.cancel()
        heartbeatJob?.cancel()
        signalRefreshJob?.cancel()
        debugLogJob?.cancel()
        advertisingJob?.cancel()
        discoveryJob?.cancel()
        
        try { connectionsClient.stopAdvertising() } catch (e: Exception) { MeshLogger.warn("Mesh", "stopAdvertising error: ${e.message}") }
        try { connectionsClient.stopDiscovery() } catch (e: Exception) { MeshLogger.warn("Mesh", "stopDiscovery error: ${e.message}") }
        
        _connectedPeers.value.keys.forEach { endpointId ->
            try { connectionsClient.disconnectFromEndpoint(endpointId) } catch (e: Exception) { }
        }
        
        _connectedPeers.value = emptyMap()
        _isAdvertising.value = false
        _isDiscovering.value = false
        activeEndpoints.clear()
        pendingAliases.clear()
        discoveryRestartCount = 0
        
        scope.launch(Dispatchers.IO) {
            peerDao.markAllOffline()
            MeshLogger.connection("stopAll() � all peers marked offline in Room")
            eventBus.emit(AppEvent.MeshEvent.MeshStatusChanged(false, 0))
        }
    }

    fun disconnectFromEndpoint(endpointId: String) {
        connectionsClient.disconnectFromEndpoint(endpointId)
        _connectedPeers.value = _connectedPeers.value - endpointId
        scope.launch { eventBus.emit(AppEvent.MeshEvent.PeerDisconnected(endpointId)) }
    }
}
