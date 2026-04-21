package com.elv8.crisisos.data.dto.payloads

import kotlinx.serialization.Serializable

@Serializable
data class MediaAnnouncePayload(
    val mediaId: String,
    val messageId: String,
    val mediaType: String,
    val mimeType: String,
    val fileSizeBytes: Long,
    val compressedSizeBytes: Long?,
    val thumbnailBase64: String?,
    val durationMs: Long?,
    val chunkCount: Int,
    val fileName: String,
    val filePayloadId: Long = 0L,  // Nearby Connections payloadId of the file being sent
    val threadId: String = ""
)