package com.elv8.crisisos.domain.repository

import android.net.Uri
import com.elv8.crisisos.domain.model.media.MediaItem
import com.elv8.crisisos.domain.model.media.MediaStatus
import kotlinx.coroutines.flow.Flow
import java.io.File

interface MediaRepository {
    suspend fun prepareImageMessage(
        threadId: String,
        senderCrsId: String,
        sourceUri: Uri,
        messageId: String
    ): MediaItem?

    suspend fun prepareVideoMessage(
        threadId: String,
        senderCrsId: String,
        sourceUri: Uri,
        messageId: String
    ): MediaItem?

    suspend fun prepareAudioMessage(
        threadId: String,
        senderCrsId: String,
        audioFile: File,
        messageId: String
    ): MediaItem?

    fun getMediaForThread(threadId: String): Flow<List<MediaItem>>
    fun getSharedMediaForThread(threadId: String): Flow<List<MediaItem>>
    fun getSharedMediaCount(threadId: String): Flow<Int>
    suspend fun getMediaItem(mediaId: String): MediaItem?
    suspend fun updateMediaStatus(mediaId: String, status: MediaStatus)
    suspend fun receiveIncomingMedia(mediaItem: MediaItem): Boolean
    suspend fun deleteMedia(mediaId: String)
    suspend fun purgeExpiredMedia()
}