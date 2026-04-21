$filePath = "app/src/main/java/com/elv8/crisisos/data/mesh/MeshConnectionManager.kt"
$content = Get-Content -Raw $filePath

$content = $content -replace '(?s)private val connectionLifecycleCallback = object : ConnectionLifecycleCallback\(\) \{.*?\n        override fun onEndpointLost\(endpointId: String\) \{.*?\}', `
@'
private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
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
    }
