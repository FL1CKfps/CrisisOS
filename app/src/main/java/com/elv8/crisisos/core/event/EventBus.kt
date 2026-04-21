package com.elv8.crisisos.core.event

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

@Singleton
class EventBus @Inject constructor() {

    private val _events = MutableSharedFlow<AppEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val events: SharedFlow<AppEvent> = _events.asSharedFlow()

    suspend fun emit(event: AppEvent) {
        _events.emit(event)
    }

    fun tryEmit(event: AppEvent): Boolean {
        return _events.tryEmit(event)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : AppEvent> observe(
        scope: CoroutineScope,
        type: KClass<T>,
        onEvent: suspend (T) -> Unit
    ) {
        scope.launch {
            _events.collect { event ->
                if (type.isInstance(event)) {
                    onEvent(event as T)
                }
            }
        }
    }
}