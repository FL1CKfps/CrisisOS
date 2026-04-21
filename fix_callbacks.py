import sys

with open("app/src/main/java/com/elv8/crisisos/data/mesh/MeshConnectionManager.kt", "r", encoding="utf-8") as f:
    orig = f.read()
text = orig

def find_block(text, search_str):
    idx = text.find(search_str)
    if idx == -1: return -1, -1
    
    brace_start = text.find("{", idx)
    if brace_start == -1: return -1, -1
    
    depth = 0
    in_str = False
    for i in range(brace_start, len(text)):
        c = text[i]
        if c == '"' and text[i-1] != "\\":
            in_str = not in_str
        elif not in_str:
            if c == "{": depth += 1
            elif c == "}":
                depth -= 1
                if depth == 0:
                    return idx, i + 1
    return -1, -1

cb1_start, cb1_end = find_block(text, "private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {")
if cb1_start != -1:
    text = text[:cb1_start] + """private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            val alias = info.endpointName.split("|").getOrElse(0) { info.endpointName }
            val crsId = info.endpointName.split("|").getOrElse(1) { "UNKNOWN-${endpointId.take(6)}" }
            
            Log.i("CrisisOS_Mesh",
                "onConnectionInitiated — endpointId=$endpointId " +
                "alias=$alias crsId=$crsId " +
                "isIncomingConnection=${info.isIncomingConnection}"
            )
            
            // Store alias for this endpoint regardless of direction
            pendingAliases[endpointId] = alias
            pendingCrsIds[endpointId] = crsId
            
            // ALWAYS auto-accept — no user interaction required during testing
            Log.d("CrisisOS_Mesh", "Auto-accepting connection from $alias ($endpointId)")
            connectionsClient.acceptConnection(endpointId, payloadCallback)
                .addOnSuccessListener {
                    Log.d("CrisisOS_Mesh", "acceptConnection() submitted for $endpointId")
                }
                .addOnFailureListener { e ->
                    Log.e("CrisisOS_Mesh", "acceptConnection() FAILED for $endpointId — ${e.message}")
                }
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            val alias = pendingAliases[endpointId] ?: endpointId
            val crsId = pendingCrsIds[endpointId] ?: "UNKNOWN-${endpointId.take(6)}"
            
            Log.i("CrisisOS_Mesh",
                "onConnectionResult — endpointId=$endpointId alias=$alias crsId=$crsId " +
                "statusCode=${resolution.status.statusCode} " +
                "isSuccess=${resolution.status.isSuccess}"
            )
            
            if (resolution.status.isSuccess) {
                val peer = ConnectedPeer(
                    endpointId = endpointId,
                    alias = alias,
                    connectedAt = System.currentTimeMillis(),
                    lastSeenAt = System.currentTimeMillis(),
                    isAuthenticated = false
                )
                _connectedPeers.value = _connectedPeers.value + (endpointId to peer)
                
                Log.i("CrisisOS_Mesh", 
                    "CONNECTION ESTABLISHED — $alias ($crsId) endpointId=$endpointId " +
                    "totalPeers=${_connectedPeers.value.size}"
                )
                
                scope.launch(Dispatchers.IO) {
                    // Update Room with CONNECTED status
                    val existing = peerDao.getByCrsId(crsId)
                    if (existing != null) {
                        peerDao.updateStatus(crsId, "AVAILABLE", System.currentTimeMillis())
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
                        Log.d("CrisisOS_Mesh", "New peer inserted at connection (was advertiser) — crsId=$crsId")
                    }
                    
                    eventBus.emit(AppEvent.MeshEvent.PeerConnected(endpointId, alias))
                    eventBus.emit(AppEvent.MeshEvent.MeshStatusChanged(true, _connectedPeers.value.size))
                }
                
                // Reset watchdog since we found a peer
                startDiscoveryTimeoutWatchdog()
                
            } else {
                Log.e("CrisisOS_Mesh", 
                    "CONNECTION FAILED — $alias endpointId=$endpointId " +
                    "statusCode=${resolution.status.statusCode} " +
                    "statusMessage=${resolution.status.statusMessage}"
                )
                
                // If connection rejected due to duplicate, that is OK — already connected
                if (resolution.status.statusCode == 8001) {
                    Log.d("CrisisOS_Mesh", "Status 8001 = already connected to $alias, ignoring")
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
            
            Log.w("CrisisOS_Mesh", "onDisconnected — endpointId=$endpointId alias=$alias crsId=$crsId")
            
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
    }""" + text[cb1_end:]

cb2_start, cb2_end = find_block(text, "private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {")
if cb2_start != -1:
    text = text[:cb2_start] + """private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            lastEndpointFoundAt = System.currentTimeMillis()
            
            Log.i("CrisisOS_Mesh", 
                "onEndpointFound — endpointId=$endpointId " +
                "endpointName=${info.endpointName} " +
                "serviceId=${info.serviceId}"
            )
            
            // Parse alias from endpoint name (format: "alias|crsId" or just "alias")
            val parts = info.endpointName.split("|")
            val alias = parts.getOrElse(0) { info.endpointName }
            val crsId = parts.getOrElse(1) { "UNKNOWN-${endpointId.take(6)}" }
            
            pendingAliases[endpointId] = alias
            pendingCrsIds[endpointId] = crsId
            
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
                peerDao.insert(entity)
                Log.d("CrisisOS_Mesh", "Peer inserted to Room — crsId=$crsId alias=$alias")
                eventBus.emit(AppEvent.MeshEvent.PeerDiscovered(crsId, alias))
            }
            
            // Request connection — check capacity first
            if (_connectedPeers.value.size >= MeshConfig.MAX_CONNECTIONS) {
                Log.w("CrisisOS_Mesh", "Max connections reached (${MeshConfig.MAX_CONNECTIONS}), not requesting")
                return
            }
            
            Log.d("CrisisOS_Mesh", "Requesting connection to endpointId=$endpointId alias=$alias")
            connectionsClient.requestConnection(
                "$localAlias|$localCrsId",    // send both alias AND crsId in the name field
                endpointId,
                connectionLifecycleCallback
            ).addOnSuccessListener {
                Log.d("CrisisOS_Mesh", "requestConnection() sent successfully to $endpointId")
            }.addOnFailureListener { e ->
                Log.e("CrisisOS_Mesh", "requestConnection() FAILED to $endpointId — ${e.message}")
            }
        }

        override fun onEndpointLost(endpointId: String) {
            val alias = pendingAliases[endpointId] ?: "unknown"
            val crsId = pendingCrsIds[endpointId] ?: "unknown"
            Log.w("CrisisOS_Mesh", "onEndpointLost — endpointId=$endpointId alias=$alias crsId=$crsId")
            
            pendingAliases.remove(endpointId)
            // Do NOT remove pendingCrsIds — may reconnect
            
            scope.launch(Dispatchers.IO) {
                if (crsId != "unknown") {
                    peerDao.updateStatus(crsId, "OFFLINE", System.currentTimeMillis())
                    Log.d("CrisisOS_Mesh", "Peer marked OFFLINE in Room — crsId=$crsId")
                }
                eventBus.emit(AppEvent.MeshEvent.PeerLost(endpointId))
            }
        }
    }""" + text[cb2_end:]

if text != orig:
    with open("app/src/main/java/com/elv8/crisisos/data/mesh/MeshConnectionManager.kt", "w", encoding="utf-8") as f:
        f.write(text)
    print("Success")
else:
    print("Found nothing")
