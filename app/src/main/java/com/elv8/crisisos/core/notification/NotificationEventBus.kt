package com.elv8.crisisos.core.notification

import android.util.Log
import com.elv8.crisisos.core.debug.MeshLogger
import com.elv8.crisisos.core.notification.event.NotificationEvent
import com.elv8.crisisos.core.notification.event.priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import javax.inject.Singleton

@Singleton
class NotificationEventBus {

    val _events = MutableSharedFlow<NotificationEvent>(
        replay = 0,
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val events: SharedFlow<NotificationEvent> = _events.asSharedFlow()

    suspend fun emit(event: NotificationEvent) {
        _events.emit(event)
        Log.d("CrisisOS_NotifBus", "Event emitted: ${event::class.simpleName} priority=${event.priority().name}")
    }

    fun tryEmit(event: NotificationEvent): Boolean {
        val result = _events.tryEmit(event)
        if (!result) {
            Log.w("CrisisOS_NotifBus", "tryEmit DROPPED: ${event::class.simpleName} — buffer full")
        }
        return result
    }

    inline fun <reified T : NotificationEvent> observe(
        scope: CoroutineScope,
        crossinline onEvent: suspend (T) -> Unit
    ) {
        scope.launch {
            _events
                .filterIsInstance<T>()
                .collect { event -> onEvent(event) }
        }
    }

    fun observeAll(
        scope: CoroutineScope,
        onEvent: suspend (NotificationEvent) -> Unit
    ) {
        scope.launch { _events.collect { onEvent(it) } }
    }

    // Convenience emitters — one per top-level domain
    suspend fun emitChat(event: NotificationEvent.Chat) = emit(event)
    suspend fun emitRequest(event: NotificationEvent.Request) = emit(event)
    suspend fun emitSos(event: NotificationEvent.Sos) = emit(event)
    suspend fun emitSupply(event: NotificationEvent.Supply) = emit(event)
    suspend fun emitMissingPerson(event: NotificationEvent.MissingPerson) = emit(event)
    suspend fun emitSystem(event: NotificationEvent.System) = emit(event)
}
