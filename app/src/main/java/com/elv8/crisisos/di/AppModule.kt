package com.elv8.crisisos.di

import android.content.Context
import com.elv8.crisisos.core.event.EventBus
import com.elv8.crisisos.core.event.EventLogger
import dagger.Module
import dagger.Provides
import com.elv8.crisisos.core.permissions.MeshPermissionManager
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton
import com.elv8.crisisos.data.mesh.MeshConnectionManager
import com.elv8.crisisos.data.mesh.MeshHealthMonitor
import com.elv8.crisisos.data.mesh.MeshMessenger
import com.elv8.crisisos.data.local.dao.OutboxDao
import com.elv8.crisisos.domain.repository.OutboxRepository
import androidx.work.WorkManager

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideMeshPermissionManager(@ApplicationContext context: Context): MeshPermissionManager {
        return MeshPermissionManager(context)
    }

    @Provides
    @Singleton
    fun provideApplicationContext(@ApplicationContext context: Context): Context {
        return context
    }

    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @Provides
    @Singleton
    fun provideEventBus(): EventBus {
        return EventBus()
    }

    @Provides
    @Singleton
    fun provideMockPeerInjector(
        peerDao: com.elv8.crisisos.data.local.dao.PeerDao,
        scope: CoroutineScope
    ): com.elv8.crisisos.core.debug.MockPeerInjector {
        return com.elv8.crisisos.core.debug.MockPeerInjector(peerDao, scope)
    }

    @Provides
    @Singleton
    fun provideMeshRecoveryManager(
        connectionManager: com.elv8.crisisos.data.mesh.MeshConnectionManager,
        permissionManager: com.elv8.crisisos.core.permissions.MeshPermissionManager,
        eventBus: com.elv8.crisisos.core.event.EventBus,
        scope: kotlinx.coroutines.CoroutineScope,
        @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context
    ): com.elv8.crisisos.core.recovery.MeshRecoveryManager {
        return com.elv8.crisisos.core.recovery.MeshRecoveryManager(connectionManager, permissionManager, eventBus, scope, context)
    }

    @Provides
    @Singleton
    fun provideMeshDiagnostics(
        connectionManager: MeshConnectionManager,
        permissionManager: com.elv8.crisisos.core.permissions.MeshPermissionManager,
        peerDao: com.elv8.crisisos.data.local.dao.PeerDao,
        scope: CoroutineScope
    ): com.elv8.crisisos.core.debug.MeshDiagnostics {
        return com.elv8.crisisos.core.debug.MeshDiagnostics(
            connectionManager, permissionManager, peerDao, scope
        )
    }

    @Provides
    @Singleton
    fun provideMeshConnectionManager(
        @ApplicationContext context: Context,
        eventBus: EventBus,
        scope: CoroutineScope,
        peerDao: com.elv8.crisisos.data.local.dao.PeerDao, peerRepositoryLazy: dagger.Lazy<com.elv8.crisisos.domain.repository.PeerRepository>
    ): MeshConnectionManager {
        return MeshConnectionManager(context, eventBus, scope, peerDao, peerRepositoryLazy)
    }

    @Provides
    @Singleton
    fun provideMeshMessenger(
        @ApplicationContext context: Context,
        connectionManager: MeshConnectionManager,
        outboxRepository: OutboxRepository,
        eventBus: EventBus,
        notificationBus: com.elv8.crisisos.core.notification.NotificationEventBus,
        chatDao: com.elv8.crisisos.data.local.dao.ChatDao,
        chatThreadDao: com.elv8.crisisos.data.local.dao.ChatThreadDao,
        mediaRepository: com.elv8.crisisos.domain.repository.MediaRepository,
        mediaDao: com.elv8.crisisos.data.local.dao.MediaDao,
        fileManager: com.elv8.crisisos.data.media.MediaFileManager,
        scope: CoroutineScope
    ): MeshMessenger {
        return MeshMessenger(
            context = context,
            connectionManager = connectionManager,
            outboxRepository = outboxRepository,
            eventBus = eventBus,
            notificationBus = notificationBus,
            chatDao = chatDao,
            chatThreadDao = chatThreadDao,
            mediaRepository = mediaRepository,
            mediaDao = mediaDao,
            fileManager = fileManager,
            scope = scope
        )
    }

    @Provides
    @Singleton
    fun provideMeshHealthMonitor(
        outboxRepository: OutboxRepository,
        outboxDao: OutboxDao,
        connectionManager: MeshConnectionManager,
        scope: CoroutineScope
    ): MeshHealthMonitor {
        return MeshHealthMonitor(outboxRepository, outboxDao, connectionManager, scope)
    }

    @Provides
    fun provideEventLogger(
        bus: EventBus,
        scope: CoroutineScope
    ): EventLogger {
        return EventLogger(bus, scope)
    }

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }
}

