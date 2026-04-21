with open("app/src/main/java/com/elv8/crisisos/data/mesh/MeshMessenger.kt", "r", encoding="utf-8") as f:
    text = f.read()

old_ping = '''            MeshPacketType.SYSTEM_PING -> {
                val ackPacket = MeshPacket(
                    packetId = java.util.UUID.randomUUID().toString(),
                    type = MeshPacketType.SYSTEM_ACK,
                    senderId = localDeviceId,
                    senderAlias = localDeviceId,
                    payload = "",
                    timestamp = System.currentTimeMillis(),
                    priority = com.elv8.crisisos.data.dto.PacketPriority.NORMAL,
                    targetId = packet.senderId
                )
                send(ackPacket)
            }'''

new_ping = '''            MeshPacketType.SYSTEM_PING -> {
                Log.d("CrisisOS_Mesh", "PING received from ${packet.senderAlias} — sending ACK")
                
                val localAlias = try { connectionManager.localAlias } catch (e: Exception) { localDeviceId }
                val ack = com.elv8.crisisos.data.dto.PacketFactory.buildAckPacket(localDeviceId, localAlias, packet.packetId)  
                sendToEndpoint(incomingEndpointId, ack)
            }
            MeshPacketType.SYSTEM_ACK -> {
                Log.d("CrisisOS_Mesh", "ACK received from ${packet.senderAlias} — peer is alive")
                // find the endpointId by matching senderAlias in pendingAliases
                var matchingEndpointId = incomingEndpointId
                for ((epId, alias) in connectionManager.pendingAliases) {
                    if (alias == packet.senderAlias) {
                        matchingEndpointId = epId
                        break
                    }
                }
                connectionManager.updateLastSeen(matchingEndpointId)
            }'''

text = text.replace(old_ping, new_ping)

with open("app/src/main/java/com/elv8/crisisos/data/mesh/MeshMessenger.kt", "w", encoding="utf-8") as f:
    f.write(text)

print("Updated MeshMessenger")
