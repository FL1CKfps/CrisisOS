package com.elv8.crisisos

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import com.elv8.crisisos.core.firebase.CrisisOSFirebase
import com.elv8.crisisos.core.notification.NotificationManagerWrapper
import com.elv8.crisisos.domain.repository.CommunityBoardRepository
import com.elv8.crisisos.domain.repository.DeconflictionRepository
import com.elv8.crisisos.domain.repository.NewsRepository
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

    // Mesh-broadcast feed repositories — collectors must run at app scope so
    // packets received before any consuming screen opens still get persisted
    // (EventBus is a non-replay SharedFlow). Each repo's observeIncoming() is
    // idempotent (AtomicBoolean guard) so subsequent VM-side calls are no-ops.
    @Inject
    lateinit var newsRepository: NewsRepository

    @Inject
    lateinit var communityBoardRepository: CommunityBoardRepository

    @Inject
    lateinit var deconflictionRepository: DeconflictionRepository

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

        // Bootstrap mesh-broadcast feed collectors at app scope. Without this,
        // CRISIS_NEWS / COMMUNITY_POST / DECONFLICTION_REPORT packets that
        // arrive while the user is on Home or any unrelated screen would be
        // silently dropped (EventBus is non-replay).
        newsRepository.observeIncoming()
        communityBoardRepository.observeIncoming()
        deconflictionRepository.observeIncoming()
        android.util.Log.i("CrisisOS_App", "Mesh feed collectors bootstrapped (news/community/deconfliction)")

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
