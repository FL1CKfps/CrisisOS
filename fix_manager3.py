import re

with open('app/src/main/java/com/elv8/crisisos/data/mesh/MeshConnectionManager.kt', 'r', encoding='utf-8') as f:
    text = f.read()

# Add sendToEndpoint
send_to_endpoint_code = '''
    private fun sendToEndpoint(endpointId: String, packet: com.elv8.crisisos.data.dto.MeshPacket) {
        val json = com.elv8.crisisos.data.dto.MeshJson.encodeToString(com.elv8.crisisos.data.dto.MeshPacket.serializer(), packet)
        connectionsClient.sendPayload(endpointId, Payload.fromBytes(json.toByteArray(Charsets.UTF_8)))
    }

    private fun startPeerHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            Log.d("CrisisOS_Mesh", "Peer heartbeat started — interval=10s")
            while (isActive) {
                delay(10_000)
                
                val connected = _connectedPeers.value
                if (connected.isEmpty()) continue
                
                Log.d("CrisisOS_Mesh", "Heartbeat tick — ${connected.size} peers connected")
                
                val now = System.currentTimeMillis()
                
                connected.forEach { (endpointId, peer) ->
                    val timeSinceLastSeen = now - peer.lastSeenAt
                    
                    // If peer hasn't been seen in 45 seconds, assume disconnected
                    if (timeSinceLastSeen > 45_000) {
                        Log.w("CrisisOS_Mesh", 
                            "Peer ${peer.alias} not seen for ${timeSinceLastSeen}ms — marking OFFLINE"
                        )
                        onDisconnectedByHeartbeat(endpointId)
                        return@forEach
                    }
                    
                    // Send a SYSTEM_PING to confirm the connection is alive
                    val pingPacket = com.elv8.crisisos.data.dto.PacketFactory.buildPingPacket(localCrsId, localAlias)
                    sendToEndpoint(endpointId, pingPacket)
                    
                    Log.v("CrisisOS_Mesh", "Heartbeat ping sent to ${peer.alias} ($endpointId)")
                }
            }
        }
    }

    private fun onDisconnectedByHeartbeat(endpointId: String) {
        val alias = pendingAliases[endpointId] ?: "unknown"
        val crsId = pendingCrsIds[endpointId] ?: "unknown"
        Log.w("CrisisOS_Mesh", "Heartbeat disconnect — $alias ($crsId)")
        
        _connectedPeers.value = _connectedPeers.value - endpointId
        try { connectionsClient.disconnectFromEndpoint(endpointId) } catch (e: Exception) { }
        
        scope.launch(Dispatchers.IO) {
            if (crsId != "unknown") peerDao.updateStatus(crsId, "OFFLINE", System.currentTimeMillis())
            eventBus.emit(AppEvent.MeshEvent.PeerDisconnected(endpointId))
        }
    }
'''

start_mesh_idx = text.find('    fun startMesh(')
text = text[:start_mesh_idx] + send_to_endpoint_code + "\n" + text[start_mesh_idx:]

# Step 4
text = text.replace('        startDiscovery()\n        debugLogJob?.cancel()', '        startDiscovery()\n        startPeerHeartbeat()\n        debugLogJob?.cancel()')

# Step 5
text = text.replace('        discoveryWatchdogJob?.cancel()\n        debugLogJob?.cancel()', '        heartbeatJob?.cancel()\n        discoveryWatchdogJob?.cancel()\n        debugLogJob?.cancel()')

with open('app/src/main/java/com/elv8/crisisos/data/mesh/MeshConnectionManager.kt', 'w', encoding='utf-8') as f:
    f.write(text)
print('Applied steps 3, 4, 5')
