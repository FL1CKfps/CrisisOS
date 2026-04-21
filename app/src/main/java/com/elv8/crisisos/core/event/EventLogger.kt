package com.elv8.crisisos.core.event

import android.util.Log
import com.elv8.crisisos.core.debug.MeshLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventLogger @Inject constructor(
    private val bus: EventBus,
    private val scope: CoroutineScope
) {
    private val buffer = ArrayDeque<AppEvent>(100)

    init {
        scope.launch {
            bus.events.collect { event ->
                Log.d("CrisisOS_EventBus", event.toString())
                synchronized(buffer) {
                    if (buffer.size >= 100) {
                        buffer.removeFirst()
                    }
                    buffer.addLast(event)
                }
            }
        }
    }

    fun getRecentEvents(): List<AppEvent> {
        return synchronized(buffer) {
            buffer.toList()
        }
    }
}