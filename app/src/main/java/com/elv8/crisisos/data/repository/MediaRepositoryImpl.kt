package com.elv8.crisisos.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.elv8.crisisos.core.debug.MeshLogger
import com.elv8.crisisos.data.local.dao.MediaDao
import com.elv8.crisisos.data.local.entity.toDomain
import com.elv8.crisisos.data.local.entity.toEntity
import com.elv8.crisisos.data.media.MediaFileManager
import com.elv8.crisisos.domain.model.media.MediaItem
import com.elv8.crisisos.domain.model.media.MediaStatus
import com.elv8.crisisos.domain.model.media.MediaType
import com.elv8.crisisos.domain.repository.MediaRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepositoryImpl @Inject constructor(
    private val mediaDao: MediaDao,
    private val fileManager: MediaFileManager,
    @ApplicationContext private val context: Context
) : MediaRepository {

    override suspend fun prepareImageMessage(
        threadId: String,
        senderCrsId: String,
        sourceUri: Uri,
        messageId: String
    ): MediaItem? {
        val copiedFile = fileManager.copyUriToInternalStorage(sourceUri, MediaType.IMAGE) ?: return null
        val originalSize = copiedFile.length()
        
        val finalFile = if (originalSize > MediaFileManager.MAX_IMAGE_SIZE_BYTES) {
            val compressed = fileManager.compressImage(copiedFile)
            if (compressed != null && compressed != copiedFile) {
                copiedFile.delete()
                compressed
            } else {
                copiedFile
            }
        } else {
            copiedFile
        }

        val item = MediaItem(
            mediaId = UUID.randomUUID().toString(),
            threadId = threadId,
            senderCrsId = senderCrsId,
            receiverCrsId = null,
            type = MediaType.IMAGE,
            localUri = "file://${finalFile.absolutePath}",
            remoteUri = null,
            fileName = finalFile.name,
            mimeType = fileManager.getMimeType(finalFile),
            fileSizeBytes = originalSize,
            compressedSizeBytes = finalFile.length(),
            durationMs = null,
            thumbnailUri = null,
            timestamp = System.currentTimeMillis(),
            status = MediaStatus.READY,
            isOwn = true,
            messageId = messageId
        )
        
        mediaDao.insert(item.toEntity())
        return item
    }

    override suspend fun prepareVideoMessage(
        threadId: String,
        senderCrsId: String,
        sourceUri: Uri,
        messageId: String
    ): MediaItem? {
        val copiedFile = fileManager.copyUriToInternalStorage(sourceUri, MediaType.VIDEO) ?: return null
        val originalSize = copiedFile.length()
        
        if (originalSize > MediaFileManager.MAX_VIDEO_SIZE_BYTES) {
            Log.w("CrisisOS_Media", "Video file too large: $originalSize bytes")
            copiedFile.delete()
            return null
        }
        
        val thumbnailFile = fileManager.generateVideoThumbnail(copiedFile)
        
        val item = MediaItem(
            mediaId = UUID.randomUUID().toString(),
            threadId = threadId,
            senderCrsId = senderCrsId,
            receiverCrsId = null,
            type = MediaType.VIDEO,
            localUri = "file://${copiedFile.absolutePath}",
            remoteUri = null,
            fileName = copiedFile.name,
            mimeType = fileManager.getMimeType(copiedFile),
            fileSizeBytes = originalSize,
            compressedSizeBytes = null,
            durationMs = fileManager.getMediaDuration(copiedFile),
            thumbnailUri = thumbnailFile?.let { "file://${it.absolutePath}" },
            timestamp = System.currentTimeMillis(),
            status = MediaStatus.READY,
            isOwn = true,
            messageId = messageId
        )
        
        mediaDao.insert(item.toEntity())
        return item
    }

    override suspend fun prepareAudioMessage(
        threadId: String,
        senderCrsId: String,
        audioFile: File,
        messageId: String
    ): MediaItem? {
        val destFile = File(fileManager.getOrCreateMediaDir(MediaType.AUDIO), fileManager.generateFileName(MediaType.AUDIO, audioFile.extension))
        audioFile.copyTo(destFile, overwrite = true)
        
        val item = MediaItem(
            mediaId = UUID.randomUUID().toString(),
            threadId = threadId,
            senderCrsId = senderCrsId,
            receiverCrsId = null,
            type = MediaType.AUDIO,
            localUri = "file://${destFile.absolutePath}",
            remoteUri = null,
            fileName = destFile.name,
            mimeType = fileManager.getMimeType(destFile),
            fileSizeBytes = destFile.length(),
            compressedSizeBytes = null,
            durationMs = fileManager.getMediaDuration(destFile),
            thumbnailUri = null,
            timestamp = System.currentTimeMillis(),
            status = MediaStatus.READY,
            isOwn = true,
            messageId = messageId
        )
        
        mediaDao.insert(item.toEntity())
        return item
    }

    override fun getMediaForThread(threadId: String): Flow<List<MediaItem>> {
        return mediaDao.getMediaForThread(threadId).map { list -> list.map { it.toDomain() } }
    }

    override fun getSharedMediaForThread(threadId: String): Flow<List<MediaItem>> {
        return mediaDao.getSharedMediaForThread(threadId).map { list -> list.map { it.toDomain() } }
    }

    override fun getSharedMediaCount(threadId: String): Flow<Int> {
        return mediaDao.getSharedMediaCount(threadId)
    }

    override suspend fun getMediaItem(mediaId: String): MediaItem? {
        return mediaDao.getById(mediaId)?.toDomain()
    }

    override suspend fun updateMediaStatus(mediaId: String, status: MediaStatus) {
        mediaDao.updateStatus(mediaId, status.name)
    }

    override suspend fun receiveIncomingMedia(mediaItem: MediaItem): Boolean {
        mediaDao.insert(mediaItem.copy(status = MediaStatus.RECEIVED).toEntity())
        return true
    }

    override suspend fun deleteMedia(mediaId: String) {
        val media = mediaDao.getById(mediaId)
        if (media != null) {
            fileManager.deleteMediaFile(media.localUri)
            media.thumbnailUri?.let { fileManager.deleteMediaFile(it) }
            mediaDao.delete(mediaId)
        }
    }

    override suspend fun purgeExpiredMedia() {
        val cutoff = System.currentTimeMillis() - 7 * 86_400_000L
        mediaDao.deleteExpired(cutoff)
    }
}