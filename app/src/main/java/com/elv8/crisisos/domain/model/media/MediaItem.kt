package com.elv8.crisisos.domain.model.media

data class MediaItem(
    val mediaId: String,
    val threadId: String,
    val senderCrsId: String,
    val receiverCrsId: String?,
    val type: MediaType,
    val localUri: String?,
    val remoteUri: String?,
    val fileName: String,
    val mimeType: String,
    val fileSizeBytes: Long,
    val compressedSizeBytes: Long?,
    val durationMs: Long?,
    val thumbnailUri: String?,
    val timestamp: Long,
    val status: MediaStatus,
    val isOwn: Boolean,
    val messageId: String?,
    val chunkCount: Int = 0,
    val chunksReceived: Int = 0,
    val transferId: String? = null
)
