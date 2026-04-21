package com.elv8.crisisos.data.mesh

/*
  CRISISOS MEDIA MESH TRANSFER — FUTURE IMPLEMENTATION PLAN
  ===========================================================
  
  CURRENT STATE (v1):
  - MediaAnnouncePacket sends metadata + base64 thumbnail
  - Full binary file is stored locally ONLY
  - Recipient sees thumbnail but cannot view full media
  
  FUTURE IMPLEMENTATION (v2):
  
  1. CHUNK SIZE STRATEGY:
     Nearby Connections max payload: 32KB per BYTES payload
     Chunk size: 28KB (leaving 4KB header room)
     Max file: 10MB image → ~357 chunks
     Max audio: 5min @ 128kbps → ~4.6MB → ~164 chunks
  
  2. TRANSFER FLOW:
     Sender:  MediaAnnouncePacket (metadata) →
              MediaChunkPacket × N (binary chunks) →
     Receiver: MediaChunkAckPacket per chunk →
              After all chunks received: assemble file →
              Update MediaEntity.status = RECEIVED, set localUri
  
  3. PACKET TYPES (already in MeshPacketType):
     MEDIA_ANNOUNCE  — already implemented
     MEDIA_CHUNK     — reserved
     MEDIA_CHUNK_ACK — reserved
  
  4. CHUNK PACKET STRUCTURE:
     MediaChunkPayload {
       mediaId: String,
       chunkIndex: Int,
       totalChunks: Int,
       chunkData: String,   // Base64 encoded chunk bytes
       checksum: String     // MD5 of chunk for integrity
     }
  
  5. RESUME STRATEGY:
     MediaEntity.chunksReceived tracks progress
     On reconnect: sender resends from chunksReceived index
     Receiver stores partial file until complete
  
  6. STORAGE STRATEGY:
     In-progress chunks stored as temp files:
       crisisos/transfers/{mediaId}.tmp
     On completion: rename to final location
     On failure/expiry: delete temp file
  
  7. BANDWIDTH MANAGEMENT:
     Only transfer if peer is CONNECTED (not just discovered)
     Throttle: max 1 concurrent transfer per peer
     Priority: user-initiated > background
*/

object MediaTransferStub {
    const val CHUNK_SIZE_BYTES = 28_672  // 28KB
    const val MAX_CONCURRENT_TRANSFERS = 1
    const val TRANSFER_TIMEOUT_MS = 30 * 60_000L  // 30 minutes per file

    fun calculateChunkCount(fileSizeBytes: Long): Int =
        ((fileSizeBytes + CHUNK_SIZE_BYTES - 1) / CHUNK_SIZE_BYTES).toInt()

    fun getTransferEstimateSeconds(fileSizeBytes: Long, throughputBytesPerSec: Int = 50_000): Int =
        (fileSizeBytes / throughputBytesPerSec).toInt()
}
