package com.elv8.crisisos.data.media

import android.content.Context
import android.net.Uri
import android.util.Log
import com.elv8.crisisos.core.debug.MeshLogger
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.elv8.crisisos.domain.model.media.MediaPickResult
import com.elv8.crisisos.domain.model.media.PickError
import com.elv8.crisisos.domain.repository.MediaRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaPickerHelper @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val fileManager: MediaFileManager,
    @ApplicationContext private val context: Context
) {

    companion object {
        const val MAX_IMAGE_SIZE_MB = 20L
        const val MAX_VIDEO_SIZE_MB = 50L
        const val MAX_VIDEO_DURATION_SECONDS = 120
    }

    suspend fun processPickedImage(
        uri: Uri,
        threadId: String,
        senderCrsId: String,
        messageId: String
    ): MediaPickResult {
        return withContext(Dispatchers.IO) {
            try {
                val sizeBytes = fileManager.getFileSizeBytes(uri)
                if (sizeBytes > MAX_IMAGE_SIZE_MB * 1_000_000) {
                    Log.w("CrisisOS_Picker", "Image too large: ${sizeBytes / 1_000_000}MB")
                    return@withContext MediaPickResult.Error(PickError.FILE_TOO_LARGE)
                }
                val mimeType = context.contentResolver.getType(uri) ?: ""
                if (!mimeType.startsWith("image/")) {
                    return@withContext MediaPickResult.Error(PickError.UNSUPPORTED_TYPE)
                }
                val item = mediaRepository.prepareImageMessage(threadId, senderCrsId, uri, messageId)
                    ?: return@withContext MediaPickResult.Error(PickError.COMPRESSION_FAILED)
                MediaPickResult.Success(item)
            } catch (e: Exception) {
                Log.e("CrisisOS_Picker", "processPickedImage failed: ${e.message}")
                MediaPickResult.Error(PickError.STORAGE_ERROR)
            }
        }
    }

    suspend fun processPickedVideo(
        uri: Uri,
        threadId: String,
        senderCrsId: String,
        messageId: String
    ): MediaPickResult {
        return withContext(Dispatchers.IO) {
            try {
                val sizeBytes = fileManager.getFileSizeBytes(uri)
                if (sizeBytes > MAX_VIDEO_SIZE_MB * 1_000_000) {
                    Log.w("CrisisOS_Picker", "Video too large: ${sizeBytes / 1_000_000}MB")
                    return@withContext MediaPickResult.Error(PickError.FILE_TOO_LARGE)
                }
                val mimeType = context.contentResolver.getType(uri) ?: ""
                if (!mimeType.startsWith("video/")) {
                    return@withContext MediaPickResult.Error(PickError.UNSUPPORTED_TYPE)
                }
                val item = mediaRepository.prepareVideoMessage(threadId, senderCrsId, uri, messageId)
                    ?: return@withContext MediaPickResult.Error(PickError.COMPRESSION_FAILED)
                MediaPickResult.Success(item)
            } catch (e: Exception) {
                Log.e("CrisisOS_Picker", "processPickedVideo failed: ${e.message}")
                MediaPickResult.Error(PickError.STORAGE_ERROR)
            }
        }
    }

    fun createImagePickerLauncher(
        activityResultRegistry: ActivityResultRegistry,
        onResult: (Uri?) -> Unit
    ): ActivityResultLauncher<PickVisualMediaRequest> {
        return activityResultRegistry.register(
            "image_picker",
            ActivityResultContracts.PickVisualMedia()
        ) { uri -> onResult(uri) }
    }

    fun createVideoPickerLauncher(
        activityResultRegistry: ActivityResultRegistry,
        onResult: (Uri?) -> Unit
    ): ActivityResultLauncher<PickVisualMediaRequest> {
        return activityResultRegistry.register(
            "video_picker",
            ActivityResultContracts.PickVisualMedia()
        ) { uri -> onResult(uri) }
    }

    fun buildImagePickRequest(): PickVisualMediaRequest {
        return PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    }

    fun buildVideoPickRequest(): PickVisualMediaRequest {
        return PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
    }
}