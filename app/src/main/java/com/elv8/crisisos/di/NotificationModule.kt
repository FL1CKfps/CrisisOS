package com.elv8.crisisos.di

import com.elv8.crisisos.core.notification.NotificationHandler
import com.elv8.crisisos.core.notification.NotificationEventBus
import com.elv8.crisisos.core.notification.NotificationEventLogger
import com.elv8.crisisos.core.notification.NotificationManagerWrapper
import com.elv8.crisisos.core.notification.NotificationBuilder
import com.elv8.crisisos.core.notification.ActiveScreenTracker
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

import com.elv8.crisisos.core.notification.NotificationSettings
import com.elv8.crisisos.data.local.dao.NotificationLogDao

@Module
@InstallIn(SingletonComponent::class)
object NotificationModule {

    @Provides
    @Singleton
    fun provideNotificationSettings(
        @ApplicationContext context: Context
    ): NotificationSettings = NotificationSettings(context)

    @Provides
    @Singleton
    fun provideNotificationManagerWrapper(
        @ApplicationContext context: Context
    ): NotificationManagerWrapper = NotificationManagerWrapper(context)

    @Provides
    @Singleton
    fun provideNotificationBuilder(
        @ApplicationContext context: Context,
        wrapper: NotificationManagerWrapper
    ): NotificationBuilder = NotificationBuilder(context, wrapper)

    @Provides
    @Singleton
    fun provideActiveScreenTracker(): ActiveScreenTracker = ActiveScreenTracker()

    @Provides
    @Singleton
    fun provideNotificationHandler(
        bus: NotificationEventBus,
        builder: NotificationBuilder,
        wrapper: NotificationManagerWrapper,
        activeScreenTracker: ActiveScreenTracker,
        notificationSettings: NotificationSettings,
        notificationLogDao: NotificationLogDao,
        scope: CoroutineScope
    ): NotificationHandler = NotificationHandler(
        bus, builder, wrapper, activeScreenTracker, notificationSettings, notificationLogDao, scope
    )

    @Provides
    @Singleton
    fun provideNotificationEventBus(): NotificationEventBus = NotificationEventBus()

    // applicationScope from AppModule
    
    @Provides
    @Singleton
    fun provideNotificationEventLogger(
        bus: NotificationEventBus,
        scope: CoroutineScope
    ): NotificationEventLogger = NotificationEventLogger(bus, scope).also { /* eager init */ }
}
