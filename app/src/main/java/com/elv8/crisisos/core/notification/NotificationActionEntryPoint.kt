package com.elv8.crisisos.core.notification

import com.elv8.crisisos.domain.repository.ConnectionRequestRepository
import com.elv8.crisisos.domain.repository.MessageRequestRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NotificationActionEntryPoint {
    fun connectionRequestRepository(): ConnectionRequestRepository
    fun messageRequestRepository(): MessageRequestRepository
    fun notificationManagerWrapper(): NotificationManagerWrapper
    fun applicationScope(): CoroutineScope
}