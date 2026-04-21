package com.elv8.crisisos.core.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.elv8.crisisos.domain.model.media.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaPermissionManager @Inject constructor(
    @ApplicationContext val context: Context
) {
    fun getRequiredPermissionsForImagePick(): List<String> {
        return buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    fun getRequiredPermissionsForVideoPick(): List<String> {
        return buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_VIDEO)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    fun getRequiredPermissionsForAudioRecord(): List<String> {
        return listOf(Manifest.permission.RECORD_AUDIO)
    }

    fun getMissingMediaPermissions(type: MediaType): List<String> {
        val required = when (type) {
            MediaType.IMAGE -> getRequiredPermissionsForImagePick()
            MediaType.VIDEO -> getRequiredPermissionsForVideoPick()
            MediaType.AUDIO -> getRequiredPermissionsForAudioRecord()
        }
        return required.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
    }

    fun areMediaPermissionsGranted(type: MediaType): Boolean {
        return getMissingMediaPermissions(type).isEmpty()
    }

    fun logMediaPermissionState() {
        MediaType.values().forEach { type ->
            val missing = getMissingMediaPermissions(type)
            if (missing.isEmpty()) {
                Log.i("CrisisOS_MediaPerms", "${type.name}: all permissions granted")
            } else {
                Log.w("CrisisOS_MediaPerms", "${type.name}: MISSING ${missing.joinToString()}")
            }
        }
    }
}
