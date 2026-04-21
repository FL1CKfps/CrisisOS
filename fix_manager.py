import sys
import re

with open('app/src/main/java/com/elv8/crisisos/data/mesh/MeshConnectionManager.kt', 'r', encoding='utf-8') as f:
    text = f.read()

# We know payloadCallback ends securely with // no-op for now\n        }\n    }
start_pattern = r'// no-op for now\s*\}\s*\}'
end_pattern = r'^\s*fun startMesh\(alias: String\) \{'

m_start = re.search(start_pattern, text)
m_end = re.search(end_pattern, text, re.MULTILINE)

if not m_start or not m_end:
    print('Failed to find markers')
    sys.exit(1)

prefix = text[:m_start.end()]
suffix = text[m_end.start():]

new_content = prefix + '\n\n' + '''    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            val alias = info.endpointName.split("|").getOrElse(0) { info.endpointName }
            val crsId = info.endpointName.split("|").getOrElse(1) { "UNKNOWN-" }
            
            Log.i("CrisisOS_Mesh",
                "onConnectionInitiated — endpointId= " +
                "alias= crsId= " +
                "isIncomingConnection="
            )
            
            // Store alias for this endpoint regardless of direction
            pendingAliases[endpointId] = alias
            pendingCrsIds[endpointId] = crsId
            
            // ALWAYS auto-accept — no user interaction required during testing
            Log.d("CrisisOS_Mesh", "Auto-accepting connection from  ()")
            connectionsClient.acceptConnection(endpointId, payloadCallback)
                .addOnSuccessListener {
                    Log.d("CrisisOS_Mesh", "acceptConnection() submitted for ")
                }
                .addOnFailureListener { e ->
                    Log.e("CrisisOS_Mesh", "acceptConnection() FAILED for  — ")
                }
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            val alias = pendingAliases[endpointId] ?: endpointId
            val crsId = pendingCrsIds[endpointId] ?: "UNKNOWN-"
            
            Log.i("CrisisOS_Mesh",
                "onConnectionResult — endpointId= alias= crsId= " +
                "statusCode= " +
                "isSuccess="
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
                    "CONNECTION ESTABLISHED —  () endpointId= " +
                    "totalPeers="
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
                        Log.d("CrisisOS_Mesh", "New peer inserted at connection (was advertiser) — crsId=")
                    }
                    
                    eventBus.emit(AppEvent.MeshEvent.PeerConnected(endpointId, alias))
                    eventBus.emit(AppEvent.MeshEvent.MeshStatusChanged(true, _connectedPeers.value.size))
                }
                
                // Reset watchdog since we found a peer
                startDiscoveryTimeoutWatchdog()
                
            } else {
                Log.e("CrisisOS_Mesh", 
                    "CONNECTION FAILED —  endpointId= " +
                    "statusCode= " +
                    "statusMessage="
                )
                
                // If connection rejected due to duplicate, that is OK — already connected
                if (resolution.status.statusCode == 8001) {
                    Log.d("CrisisOS_Mesh", "Status 8001 = already connected to , ignoring")
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
            
            Log.w("CrisisOS_Mesh", "onDisconnected — endpointId= alias= crsId=")
            
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
            
            Log.i("CrisisOS_Mesh", 
                "onEndpointFound — endpointId= " +
                "endpointName= " +
                "serviceId="
            )
            
            // Parse alias from endpoint name (format: "alias|crsId" or just "alias")
            val parts = info.endpointName.split("|")
            val alias = parts.getOrElse(0) { info.endpointName }
            val crsId = parts.getOrElse(1) { "UNKNOWN-" }
            
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
                Log.d("CrisisOS_Mesh", "Peer inserted to Room — crsId= alias=")
                eventBus.emit(AppEvent.MeshEvent.PeerDiscovered(crsId, alias))
            }
            
            // Request connection — check capacity first
            if (_connectedPeers.value.size >= MeshConfig.MAX_CONNECTIONS) {
                Log.w("CrisisOS_Mesh", "Max connections reached (), not requesting")
                return
            }
            
            Log.d("CrisisOS_Mesh", "Requesting connection to endpointId= alias=")
            connectionsClient.requestConnection(
                "|",    // send both alias AND crsId in the name field
                endpointId,
                connectionLifecycleCallback
            ).addOnSuccessListener {
                Log.d("CrisisOS_Mesh", "requestConnection() sent successfully to ")
            }.addOnFailureListener { e ->
                Log.e("CrisisOS_Mesh", "requestConnection() FAILED to  — ")
            }
        }

        override fun onEndpointLost(endpointId: String) {
            val alias = pendingAliases[endpointId] ?: "unknown"
            val crsId = pendingCrsIds[endpointId] ?: "unknown"
            Log.w("CrisisOS_Mesh", "onEndpointLost — endpointId= alias= crsId=")
            
            pendingAliases.remove(endpointId)
            // Do NOT remove pendingCrsIds — may reconnect
            
            scope.launch(Dispatchers.IO) {
                if (crsId != "unknown") {
                    peerDao.updateStatus(crsId, "OFFLINE", System.currentTimeMillis())
                    Log.d("CrisisOS_Mesh", "Peer marked OFFLINE in Room — crsId=")
                }
                eventBus.emit(AppEvent.MeshEvent.PeerLost(endpointId))
            }
        }
    }

''' + '\n    ' + suffix.lstrip()

with open('app/src/main/java/com/elv8/crisisos/data/mesh/MeshConnectionManager.kt', 'w', encoding='utf-8') as f:
    f.write(new_content)

print('Successfully re-stitched the callback layers!')
