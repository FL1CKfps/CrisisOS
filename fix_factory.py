with open("app/src/main/java/com/elv8/crisisos/data/dto/PacketFactory.kt", "r", encoding="utf-8") as f:
    content = f.read()

new_method = """    fun buildAckPacket(senderId: String, senderAlias: String, targetPacketId: String): MeshPacket {
        return createWrappedPacket(
            type = MeshPacketType.SYSTEM_ACK,
            senderId = senderId,
            senderAlias = senderAlias,
            payloadString = "{\"target\":\"$targetPacketId\"}",
            priority = PacketPriority.LOW
        )
    }

}"""

content = content.replace("}", "", 1)
content = content[:content.rfind("}")] + new_method
with open("app/src/main/java/com/elv8/crisisos/data/dto/PacketFactory.kt", "w", encoding="utf-8") as f:
    f.write(content)
print("Updated factory")
