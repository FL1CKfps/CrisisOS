package com.elv8.crisisos.core.debug

import android.util.Log
import com.elv8.crisisos.core.debug.MeshLogger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MeshLogger {
    private val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())     
    private fun ts() = sdf.format(Date())

    fun discovery(msg: String) = Log.d("CrisisOS_Discovery", "[${ts()}] $msg")
    fun connection(msg: String) = Log.d("CrisisOS_Connection", "[${ts()}] $msg")
    fun payload(msg: String) = Log.v("CrisisOS_Payload", "[${ts()}] $msg")    
    fun room(msg: String) = Log.d("CrisisOS_Room", "[${ts()}] $msg")
    fun service(msg: String) = Log.d("CrisisOS_Service", "[${ts()}] $msg")
    fun permission(msg: String) = Log.d("CrisisOS_Perms", "[${ts()}] $msg")
    fun heartbeat(msg: String) = Log.v("CrisisOS_Heartbeat", "[${ts()}] $msg")
    fun warn(tag: String, msg: String) = Log.w("CrisisOS_$tag", "[${ts()}] $msg")
    fun error(tag: String, msg: String, e: Throwable? = null) = Log.e("CrisisOS_$tag", "[${ts()}] $msg", e)

    // adb logcat -s CrisisOS_Discovery     — peer discovery events only
    // adb logcat -s CrisisOS_Connection    — handshake events only
    // adb logcat -s CrisisOS_Room          — Room insert/update events
    // adb logcat -s CrisisOS_Service       — service lifecycle
    // adb logcat -s CrisisOS_*             — ALL CrisisOS logs (no system noise)
}
