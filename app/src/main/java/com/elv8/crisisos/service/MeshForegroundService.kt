package com.elv8.crisisos.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import com.google.android.gms.nearby.Nearby
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.elv8.crisisos.data.mesh.MeshConnectionManager
import com.elv8.crisisos.data.mesh.MeshHealthMonitor
import com.elv8.crisisos.core.permissions.MeshPermissionManager
import com.elv8.crisisos.data.mesh.MeshMessenger
import com.elv8.crisisos.core.notification.NotificationHandler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterIsInstance
import javax.inject.Inject

@AndroidEntryPoint
class MeshForegroundService : Service() {

    @Inject
    lateinit var connectionManager: MeshConnectionManager

    @Inject
    lateinit var meshHealthMonitor: MeshHealthMonitor

    @Inject
    lateinit var permissionManager: MeshPermissionManager

    @Inject
    lateinit var messenger: MeshMessenger

    @Inject
    lateinit var notificationHandler: NotificationHandler

    @Inject
    lateinit var connectionRequestRepository: com.elv8.crisisos.domain.repository.ConnectionRequestRepository

    @Inject
    lateinit var identityRepository: com.elv8.crisisos.domain.repository.IdentityRepository

    @Inject
    lateinit var eventBus: com.elv8.crisisos.core.event.EventBus

    @Inject
    lateinit var notificationBus: com.elv8.crisisos.core.notification.NotificationEventBus

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob()) 
    private lateinit var wakeLock: PowerManager.WakeLock
    private var isMeshRunning = false

    override fun onCreate() {
        super.onCreate()
        Log.d("CrisisOS_Service", "Service onCreate()")
        
        createNotificationChannel()
        val notification = buildNotification("Initializing mesh...")
        startForeground(NOTIFICATION_ID, notification)
        
        // Acquire partial wakelock so service survives screen off
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "CrisisOS:MeshWakeLock"
        ).apply {
            acquire(60 * 60 * 1000L) // 1 hour max, refreshed in ping loop
        }
        Log.d("CrisisOS_Service", "WakeLock acquired")

        serviceScope.launch {
            eventBus.events.filterIsInstance<com.elv8.crisisos.core.event.AppEvent.MeshEvent.MeshStatusChanged>().collect { event ->
                if (event.isActive && event.peerCount == 0) {
                    notificationBus.tryEmit(com.elv8.crisisos.core.notification.event.NotificationEvent.System.ServiceStarted)
                }
            }
        }

        serviceScope.launch {
            eventBus.events.filterIsInstance<com.elv8.crisisos.core.event.AppEvent.MeshEvent.PeerDiscovered>().collect { event ->
                notificationBus.tryEmit(
                    com.elv8.crisisos.core.notification.event.NotificationEvent.System.PeerNearby(
                        peerAlias = event.peerAlias,
                        peerCrsId = event.peerId
                    )
                )
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Mesh Connectivity",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Mesh session alive"
                setSound(null, null)
                enableVibration(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        Log.d("CrisisOS_Service", "onStartCommand action=$action startId=$startId")
        
        when (action) {
            ACTION_START -> {
                if (!isMeshRunning) {
                    Log.i("CrisisOS_Service", "Service starting mesh")
                    startMesh()
                    isMeshRunning = true
                } else {
                    Log.d("CrisisOS_Service", "Mesh already running � ignoring start")
                }
            }
            ACTION_STOP -> {
                Log.i("CrisisOS_Service", "Service stopping mesh")
                stopMesh()
                isMeshRunning = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
            ACTION_RESTART -> {
                Log.i("CrisisOS_Service", "Service received restart command")
                stopMesh()
                isMeshRunning = false
                serviceScope.launch {
                    delay(2_000)
                    startMesh()
                    isMeshRunning = true
                }
            }
        }
        
        // START_STICKY: system will restart this service if killed
        return START_STICKY
    }

    private fun buildNotification(contentText: String): Notification {
        val restartIntent = PendingIntent.getForegroundService(
            this, 1, restartIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("CrisisOS Mesh")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .addAction(android.R.drawable.ic_media_rew, "Restart", restartIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun startMesh() {
        Log.d("CrisisOS_Service", "startMesh() called � checking permissions")  

        if (!permissionManager.areAllPermissionsGranted()) {
            Log.e("CrisisOS_Service", "startMesh() ABORTED � missing: ${permissionManager.getMissingPermissions()}")
            updateNotification("Permissions missing � open app to grant")       
            return
        }

        serviceScope.launch {
            try {
                val identity = identityRepository.getIdentity().first()
                val deviceId = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID)
                
                val alias = identity?.alias ?: "Survivor_${android.os.Build.MODEL}"
                val crsId = identity?.crsId ?: deviceId

                android.util.Log.i("CrisisOS_Service", "startMesh() - alias=$alias deviceId=$deviceId crsId=$crsId")
                messenger.setLocalDeviceId(crsId)
                
                connectionManager.setLocalCrsId(crsId)
                connectionManager.startMesh(alias)
                
                meshHealthMonitor.runHealthCheck()

                notificationHandler.startProcessing()
                Log.i("CrisisOS_Service", "NotificationHandler started")

                startPingLoop()
                updateNotification("Mesh active - searching for peers")
            } catch (e: Exception) {
                android.util.Log.e("CrisisOS_Service", "Error reading identity", e)
            }
        }
    }

    private fun stopMesh() {
        connectionManager.stopAll()
    }

    private fun startPingLoop() {
        serviceScope.launch {
            while (isActive) {
                delay(50L * 60L * 1000L) // 50 minutes
                Log.d("CrisisOS_Service", "Refreshing Wakelock dynamically")
                try {
                    if (wakeLock.isHeld) {
                        wakeLock.release()
                        wakeLock.acquire(60 * 60 * 1000L)
                    }
                } catch (e: Exception) {
                    Log.w("CrisisOS_Service", "Failed to refresh Wakelock: ${e.message}")
                }
            }
        }
    }

    override fun onDestroy() {
        Log.w("CrisisOS_Service", "Service onDestroy() � mesh will go down")
        isMeshRunning = false
        try {
            if (wakeLock.isHeld) wakeLock.release()
        } catch (e: Exception) {
            Log.w("CrisisOS_Service", "WakeLock release failed: ${e.message}")
        }
        stopMesh()
        serviceScope.cancel()
        super.onDestroy()
        
        // Schedule restart via AlarmManager as fallback
        scheduleServiceRestart()
    }

    private fun scheduleServiceRestart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w("CrisisOS_Service", "Cannot schedule exact alarms, skipping restart")
                return
            }
        }
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = PendingIntent.getForegroundService(
            this,
            0,
            startIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setExact(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + 5_000,  // restart in 5 seconds
            intent
        )
        Log.d("CrisisOS_Service", "Service restart scheduled in 5 seconds via AlarmManager")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "mesh_channel"
        const val ACTION_START = "com.elv8.crisisos.MESH_START"
        const val ACTION_STOP = "com.elv8.crisisos.MESH_STOP"
        const val ACTION_RESTART = "com.elv8.crisisos.MESH_RESTART"
        private const val EXTRA_CRS_ID = "crsId"
        private const val EXTRA_ALIAS = "alias"

        fun startIntent(context: Context): Intent = Intent(context, MeshForegroundService::class.java).apply { action = ACTION_START }
        fun restartIntent(context: Context): Intent = Intent(context, MeshForegroundService::class.java).apply { action = ACTION_RESTART }

        fun start(context: Context, crsId: String, alias: String) {
            val intent = startIntent(context).apply {
                putExtra(EXTRA_CRS_ID, crsId)
                putExtra(EXTRA_ALIAS, alias)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, MeshForegroundService::class.java).apply { action = ACTION_STOP }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}



