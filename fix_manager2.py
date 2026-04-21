with open('app/src/main/java/com/elv8/crisisos/data/mesh/MeshConnectionManager.kt', 'r', encoding='utf-8') as f:
    content = f.read()

old_update_last_seen = '''    private fun updateLastSeen(endpointId: String) {
        val peer = _connectedPeers.value[endpointId] ?: return
        _connectedPeers.value = _connectedPeers.value + (endpointId to peer.copy(lastSeenAt = System.currentTimeMillis()))
    }'''

new_update_last_seen = '''    private fun updateLastSeen(endpointId: String) {
        val crsId = pendingCrsIds[endpointId] ?: return
        val now = System.currentTimeMillis()
        
        // Update in-memory ConnectedPeer
        val current = _connectedPeers.value[endpointId]
        if (current != null) {
            _connectedPeers.value = _connectedPeers.value + (endpointId to current.copy(lastSeenAt = now))
        }
        
        // Update Room async (no suspend needed — fire and forget)
        scope.launch(Dispatchers.IO) {
            peerDao.updateStatus(crsId, "AVAILABLE", now)
        }
    }'''

content = content.replace(old_update_last_seen, new_update_last_seen)

old_payload_callback = '''    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val bytes = payload.asBytes() ?: return
                val json = String(bytes, Charsets.UTF_8)
                val packet = PacketParser.parse(json) ?: return
                updateLastSeen(endpointId)
                scope.launch { eventBus.emit(AppEvent.MeshEvent.MessageReceived(packet, endpointId)) }
            }
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // no-op for now
        }
    }'''

new_payload_callback = '''    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            Log.d("CrisisOS_Mesh", 
                "onPayloadReceived — endpointId= type="
            )
            
            when (payload.type) {
                Payload.Type.BYTES -> {
                    val bytes = payload.asBytes()
                    if (bytes == null || bytes.isEmpty()) {
                        Log.w("CrisisOS_Mesh", "Received empty payload from  — ignoring")
                        return
                    }
                    
                    val json = try {
                        String(bytes, Charsets.UTF_8)
                    } catch (e: Exception) {
                        Log.e("CrisisOS_Mesh", "Failed to decode payload bytes from  — ")
                        return
                    }
                    
                    Log.v("CrisisOS_Mesh", 
                        "Payload from  ( bytes): ..."
                    )
                    
                    // Update last seen for this peer
                    updateLastSeen(endpointId)
                    
                    val packet = try {
                        PacketParser.parse(json)
                    } catch (e: Exception) {
                        Log.e("CrisisOS_Mesh", "PacketParser threw for payload from  — ")
                        null
                    }
                    
                    if (packet == null) {
                        Log.w("CrisisOS_Mesh", "Could not parse packet from  — json preview: ")
                        return
                    }
                    
                    Log.d("CrisisOS_Mesh", 
                        "Packet parsed — type= packetId= " +
                        "from= ttl= hop="
                    )
                    
                    scope.launch {
                        try {
                            eventBus.emit(AppEvent.MeshEvent.MessageReceived(packet, endpointId))
                        } catch (e: Exception) {
                            Log.e("CrisisOS_Mesh", "EventBus emit failed — ")
                        }
                    }
                }
                
                Payload.Type.STREAM -> {
                    Log.d("CrisisOS_Mesh", "Stream payload received from  — not handled yet")
                }
                
                Payload.Type.FILE -> {
                    Log.d("CrisisOS_Mesh", "File payload received from  — not handled yet")
                }
                
                else -> {
                    Log.w("CrisisOS_Mesh", "Unknown payload type  from ")
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Only log failures — success is too noisy
            if (update.status == PayloadTransferUpdate.Status.FAILURE) {
                Log.w("CrisisOS_Mesh", 
                    "Payload transfer FAILED — endpointId= " +
                    "payloadId="
                )
            }
        }
    }'''

content = content.replace(old_payload_callback, new_payload_callback)

with open('app/src/main/java/com/elv8/crisisos/data/mesh/MeshConnectionManager.kt', 'w', encoding='utf-8') as f:
    f.write(content)
print('Success 1, 2')
