package com.elv8.crisisos

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import com.elv8.crisisos.core.firebase.CrisisOSFirebase
import com.elv8.crisisos.core.notification.NotificationManagerWrapper
import com.elv8.crisisos.work.OutboxRetryWorker
import com.elv8.crisisos.work.MediaCleanupWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CrisisOSApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var notificationManagerWrapper: NotificationManagerWrapper

    @Inject
    lateinit var firebase: CrisisOSFirebase

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Firebase auto-inits via FirebaseInitProvider; this is a defensive call.
        firebase.ensureInitialized(this)
        firebase.logEvent("app_open")

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            OutboxRetryWorker.WORK_TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            OutboxRetryWorker.buildPeriodicRequest()
        )
        
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            MediaCleanupWorker.WORK_TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            MediaCleanupWorker.buildPeriodicRequest()
        )
        android.util.Log.i("CrisisOS_App", "MediaCleanupWorker scheduled")
        
        notificationManagerWrapper.createAllChannels()
        android.util.Log.i("CrisisOS_App", "Notification channels registered")

        // OSMDroid global configuration — must run before any MapView is created
        val osmConfig = org.osmdroid.config.Configuration.getInstance()
        osmConfig.userAgentValue = com.elv8.crisisos.core.map.MapConfiguration.OSM_USER_AGENT
        val tileCache = java.io.File(filesDir, com.elv8.crisisos.core.map.MapConfiguration.TILE_CACHE_FOLDER)
        if (!tileCache.exists()) tileCache.mkdirs()
        osmConfig.osmdroidTileCache = tileCache
        osmConfig.tileFileSystemCacheMaxBytes = com.elv8.crisisos.core.map.MapConfiguration.TILE_CACHE_MAX_BYTES
        osmConfig.load(this, androidx.preference.PreferenceManager.getDefaultSharedPreferences(this))
        android.util.Log.i("CrisisOS_Map", "OSMDroid initialized — cache: ${tileCache.absolutePath}")
    }
}
