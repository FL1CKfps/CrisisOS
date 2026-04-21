package com.elv8.crisisos.core.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.elv8.crisisos.core.debug.MeshLogger
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeshPermissionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun getRequiredPermissions(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.BLUETOOTH)
            add(Manifest.permission.BLUETOOTH_ADMIN)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    fun getMissingPermissions(): List<String> = getRequiredPermissions().filter {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }

    fun areAllPermissionsGranted(): Boolean = getMissingPermissions().isEmpty()

    fun logPermissionState() {
        val missing = getMissingPermissions()
        if (missing.isEmpty()) {
            Log.i("CrisisOS_Perms", "All mesh permissions GRANTED")
        } else {
            Log.e("CrisisOS_Perms", "MISSING PERMISSIONS: ")
        }
    }
}
