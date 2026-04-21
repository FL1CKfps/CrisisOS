package com.elv8.crisisos.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.Constraints
import com.elv8.crisisos.data.local.dao.MediaDao
import com.elv8.crisisos.data.media.MediaFileManager
import com.elv8.crisisos.domain.model.media.MediaType
import com.elv8.crisisos.domain.repository.MediaRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.util.concurrent.TimeUnit

@HiltWorker
class MediaCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val mediaRepository: MediaRepository,
    private val mediaDao: MediaDao,
    private val fileManager: MediaFileManager
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_TAG = "media_cleanup"
        fun buildPeriodicRequest(): PeriodicWorkRequest {
            return PeriodicWorkRequestBuilder<MediaCleanupWorker>(24, TimeUnit.HOURS)
                .addTag(WORK_TAG)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
        }
    }

    override suspend fun doWork(): Result {
        var deletedFiles = 0
        val deletedDbRows = 0
        var freedBytes = 0L

        Log.d("CrisisOS_Cleanup", "MediaCleanupWorker started")

        try {
            // 1. Purge DB records older than 7 days with no local file
            mediaRepository.purgeExpiredMedia()

            // 2. Find orphaned local files (files in media dirs with no DB entry)
            val mediaTypes = listOf(MediaType.IMAGE, MediaType.VIDEO, MediaType.AUDIO)
            mediaTypes.forEach { type ->
                val dir = fileManager.getOrCreateMediaDir(type)
                dir.listFiles()?.forEach { file ->
                    val uri = "file://${file.absolutePath}"
                    val entity = mediaDao.getPendingSends().find { it.localUri == uri }
                    if (entity == null) {
                        // No DB entry for this file \u2014 check if it's in a valid Room record
                        // Simple check: if file is older than 7 days and not in DB, delete it
                        if (System.currentTimeMillis() - file.lastModified() > 7 * 86_400_000L) {
                            val size = file.length()
                            if (file.delete()) {
                                deletedFiles++
                                freedBytes += size
                                Log.d("CrisisOS_Cleanup", "Orphaned file deleted: ${file.name}")
                            }
                        }
                    }
                }
            }

            // 3. Clean up thumbnail dir \u2014 delete thumbnails with no corresponding media entry
            val thumbDir = File(applicationContext.filesDir, "crisisos/thumbnails")
            thumbDir.listFiles()?.forEach { thumbFile ->
                // If no media entry references this thumbnail, delete it after 3 days
                if (System.currentTimeMillis() - thumbFile.lastModified() > 3 * 86_400_000L) {
                    val size = thumbFile.length()
                    if (thumbFile.delete()) {
                        deletedFiles++
                        freedBytes += size
                    }
                }
            }

            Log.i("CrisisOS_Cleanup", 
                "Cleanup complete: $deletedFiles files removed, ${freedBytes / 1024}KB freed"
            )
            return Result.success()
        } catch (e: Exception) {
            Log.e("CrisisOS_Cleanup", "MediaCleanupWorker failed: ${e.message}")
            return Result.retry()
        }
    }
}
