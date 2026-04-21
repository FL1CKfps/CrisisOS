package com.elv8.crisisos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.elv8.crisisos.domain.model.media.MediaItem
import com.elv8.crisisos.domain.model.media.MediaStatus
import com.elv8.crisisos.domain.model.media.MediaType

@Entity(
    tableName = "media_items",
    indices = [
        androidx.room.Index(value = ["threadId"]),
        androidx.room.Index(value = ["timestamp"])
    ]
)
data class MediaEntity(
    @PrimaryKey
    val mediaId: String,
    val threadId: String,
    val senderCrsId: String,
    val receiverCrsId: String?,
    val type: String,
    val localUri: String?,
    val remoteUri: String?,
    val fileName: String,
    val mimeType: String,
    val fileSizeBytes: Long,
    val compressedSizeBytes: Long?,
    val durationMs: Long?,
    val thumbnailUri: String?,
    val timestamp: Long,
    val status: String,
    val isOwn: Boolean,
    val messageId: String?,
    val chunkCount: Int,
    val chunksReceived: Int,
    val transferId: String?
)

fun MediaEntity.toDomain(): MediaItem = MediaItem(
    mediaId = mediaId,
    threadId = threadId,
    senderCrsId = senderCrsId,
    receiverCrsId = receiverCrsId,
    type = MediaType.valueOf(type),
    localUri = localUri,
    remoteUri = remoteUri,
    fileName = fileName,
    mimeType = mimeType,
    fileSizeBytes = fileSizeBytes,
    compressedSizeBytes = compressedSizeBytes,
    durationMs = durationMs,
    thumbnailUri = thumbnailUri,
    timestamp = timestamp,
    status = MediaStatus.valueOf(status),
    isOwn = isOwn,
    messageId = messageId,
    chunkCount = chunkCount,
    chunksReceived = chunksReceived,
    transferId = transferId
)

fun MediaItem.toEntity(): MediaEntity = MediaEntity(
    mediaId = mediaId,
    threadId = threadId,
    senderCrsId = senderCrsId,
    receiverCrsId = receiverCrsId,
    type = type.name,
    localUri = localUri,
    remoteUri = remoteUri,
    fileName = fileName,
    mimeType = mimeType,
    fileSizeBytes = fileSizeBytes,
    compressedSizeBytes = compressedSizeBytes,
    durationMs = durationMs,
    thumbnailUri = thumbnailUri,
    timestamp = timestamp,
    status = status.name,
    isOwn = isOwn,
    messageId = messageId,
    chunkCount = chunkCount,
    chunksReceived = chunksReceived,
    transferId = transferId
)
