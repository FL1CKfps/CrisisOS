package com.elv8.crisisos.data.dto

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json

val MeshJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

object PacketParser {
    fun parse(json: String): MeshPacket? {
        return try {
            MeshJson.decodeFromString(json)
        } catch (e: Exception) {
            null
        }
    }

    fun <T> decodePayload(packet: MeshPacket, deserializer: DeserializationStrategy<T>): T? {
        return try {
            MeshJson.decodeFromString(deserializer, packet.payload)
        } catch (e: Exception) {
            null
        }
    }
}
