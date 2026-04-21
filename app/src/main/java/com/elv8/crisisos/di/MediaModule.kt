package com.elv8.crisisos.di

import android.content.Context
import com.elv8.crisisos.data.local.dao.MediaDao
import com.elv8.crisisos.data.media.MediaFileManager
import com.elv8.crisisos.data.repository.MediaRepositoryImpl
import com.elv8.crisisos.domain.repository.MediaRepository
import com.elv8.crisisos.data.media.VoiceRecorder
import com.elv8.crisisos.data.media.MediaPickerHelper
import com.elv8.crisisos.core.permissions.MediaPermissionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MediaModule {

    @Provides
    @Singleton
    fun provideMediaFileManager(@ApplicationContext context: Context): MediaFileManager {
        return MediaFileManager(context)
    }

    @Provides
    @Singleton
    fun provideMediaRepository(
        mediaDao: MediaDao,
        fileManager: MediaFileManager,
        @ApplicationContext context: Context
    ): MediaRepository {
        return MediaRepositoryImpl(mediaDao, fileManager, context)
    }

    @Provides
    @Singleton
    fun provideVoiceRecorder(
        @ApplicationContext context: Context,
        fileManager: MediaFileManager
    ): VoiceRecorder {
        return VoiceRecorder(context, fileManager)
    }

    @Provides
    @Singleton
    fun provideMediaPickerHelper(
        mediaRepository: MediaRepository,
        fileManager: MediaFileManager,
        @ApplicationContext context: Context
    ): MediaPickerHelper {
        return MediaPickerHelper(mediaRepository, fileManager, context)
    }

    @Provides
    @Singleton
    fun provideMediaPermissionManager(
        @ApplicationContext context: Context
    ): MediaPermissionManager {
        return MediaPermissionManager(context)
    }
}
