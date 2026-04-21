with open("app/src/main/java/com/elv8/crisisos/data/dto/PacketFactory.kt", "r", encoding="utf-8") as f:
    text = f.read()

text = text.replace("    fun buildAckPacket(senderId: String, senderAlias: String, targetPacketId: String): MeshPacket {\n        return createWrappedPacket(\n            type = MeshPacketType.SYSTEM_ACK,\n            senderId = senderId,\n            senderAlias = senderAlias,\n            payloadString = \"{\\\"target\\\":\\\"$targetPacketId\\\"}\",\n            priority = PacketPriority.LOW\n        )\n    }\n\n}", "")
if 'fun buildAckPacket' not in text:
    last_brace = text.rfind('}')
    text = text[:last_brace] + '''
    fun buildAckPacket(senderId: String, senderAlias: String, targetPacketId: String): MeshPacket {
        return createWrappedPacket(
            type = MeshPacketType.SYSTEM_ACK,
            senderId = senderId,
            senderAlias = senderAlias,
            payloadString = "{}",
            priority = PacketPriority.LOW
        )
    }
}'''

with open("app/src/main/java/com/elv8/crisisos/data/dto/PacketFactory.kt", "w", encoding="utf-8") as f:
    f.write(text)

print("Fixed factory")
