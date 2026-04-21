package com.elv8.crisisos.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import com.elv8.crisisos.core.debug.MeshLogger
import android.util.Size
import android.webkit.MimeTypeMap
import com.elv8.crisisos.domain.model.media.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaFileManager @Inject constructor(
    @ApplicationContext val context: Context
) {

    companion object {
        private const val MEDIA_DIR_IMAGES = "crisisos/images"
        private const val MEDIA_DIR_VIDEOS = "crisisos/videos"
        private const val MEDIA_DIR_AUDIO = "crisisos/audio"
        private const val MEDIA_DIR_THUMBNAILS = "crisisos/thumbnails"
        const val MAX_IMAGE_DIMENSION = 1280
        const val MAX_IMAGE_SIZE_BYTES = 512_000L
        const val MAX_VIDEO_SIZE_BYTES = 10_000_000L
    }

    fun getOrCreateMediaDir(type: MediaType): File {
        val subDir = when (type) {
            MediaType.IMAGE -> MEDIA_DIR_IMAGES
            MediaType.VIDEO -> MEDIA_DIR_VIDEOS
            MediaType.AUDIO -> MEDIA_DIR_AUDIO
        }
        val dir = File(context.filesDir, subDir)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun generateFileName(type: MediaType, extension: String): String {
        val timestamp = System.currentTimeMillis()
        val prefix = when (type) {
            MediaType.IMAGE -> "img"
            MediaType.VIDEO -> "vid"
            MediaType.AUDIO -> "aud"
        }
        return "${prefix}_${timestamp}.$extension"
    }

    fun copyUriToInternalStorage(sourceUri: Uri, type: MediaType): File? {
        return try {
            val extension = getExtensionFromUri(context, sourceUri) ?: when (type) {
                MediaType.IMAGE -> "jpg"
                MediaType.VIDEO -> "mp4"
                MediaType.AUDIO -> "m4a"
            }
            val destFile = File(getOrCreateMediaDir(type), generateFileName(type, extension))
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            destFile
        } catch (e: Exception) {
            Log.e("CrisisOS_Media", "copyUriToInternalStorage failed: ${e.message}")
            null
        }
    }

    fun compressImage(sourceFile: File): File? {
        return try {
            if (sourceFile.length() <= MAX_IMAGE_SIZE_BYTES) return sourceFile
            val outputFile = File(getOrCreateMediaDir(MediaType.IMAGE), "c_${sourceFile.name}")
            val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath) ?: return null
            val scale = Math.min(1f, MAX_IMAGE_DIMENSION.toFloat() / Math.max(bitmap.width, bitmap.height))
            val scaledWidth = (bitmap.width * scale).toInt()
            val scaledHeight = (bitmap.height * scale).toInt()
            val scaled = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
            bitmap.recycle()
            outputFile.outputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
            }
            scaled.recycle()
            Log.d("CrisisOS_Media", "Image compressed: ${sourceFile.length()} → ${outputFile.length()} bytes")
            outputFile
        } catch (e: Exception) {
            Log.e("CrisisOS_Media", "Image compression failed: ${e.message}")
            null
        }
    }

    fun generateVideoThumbnail(videoFile: File): File? {
        return try {
            val thumbDir = File(context.filesDir, MEDIA_DIR_THUMBNAILS).also { it.mkdirs() }
            val thumbFile = File(thumbDir, "thumb_${videoFile.nameWithoutExtension}.jpg")
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ThumbnailUtils.createVideoThumbnail(videoFile, Size(480, 270), null)
            } else {
                @Suppress("DEPRECATION")
                ThumbnailUtils.createVideoThumbnail(videoFile.absolutePath, MediaStore.Video.Thumbnails.MINI_KIND)
            }
            bitmap?.let {
                thumbFile.outputStream().use { out -> it.compress(Bitmap.CompressFormat.JPEG, 80, out) }
                it.recycle()
                thumbFile
            }
        } catch (e: Exception) {
            Log.e("CrisisOS_Media", "Thumbnail generation failed: ${e.message}")
            null
        }
    }

    fun getFileSizeBytes(file: File): Long = file.length()

    fun getFileSizeBytes(uri: Uri): Long {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst() && sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L
        } ?: 0L
    }

    fun getMediaDuration(file: File): Long? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            retriever.release()
            duration
        } catch (e: Exception) { null }
    }

    fun getMimeType(file: File): String {
        return MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase())
            ?: "application/octet-stream"
    }

    private fun getExtensionFromUri(context: Context, uri: Uri): String? {
        return context.contentResolver.getType(uri)?.let {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(it)
        }
    }

    fun deleteMediaFile(localUri: String?): Boolean {
        if (localUri == null) return false
        return try {
            File(URI.create(localUri)).delete().also {
                Log.d("CrisisOS_Media", "File deleted: $localUri success=$it")
            }
        } catch (e: Exception) {
            Log.w("CrisisOS_Media", "File delete failed: ${e.message}")
            false
        }
    }
}
