package com.elv8.crisisos.core.notification

import android.util.Log
import com.elv8.crisisos.core.debug.MeshLogger
import com.elv8.crisisos.core.notification.event.NotificationEvent
import com.elv8.crisisos.core.notification.event.channelId
import com.elv8.crisisos.core.notification.event.groupKey
import com.elv8.crisisos.core.notification.event.priority
import kotlinx.coroutines.CoroutineScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationEventLogger @Inject constructor(
    bus: NotificationEventBus,
    scope: CoroutineScope
) {

    private val eventLog: ArrayDeque<Pair<Long, NotificationEvent>> = ArrayDeque(50)

    init {
        bus.observeAll(scope) { event ->
            synchronized(eventLog) {
                if (eventLog.size >= 50) eventLog.removeFirst()
                eventLog.addLast(Pair(System.currentTimeMillis(), event))
            }
            Log.d(
                "CrisisOS_NotifBus",
                "[${formatTime(System.currentTimeMillis())}] ${event::class.simpleName} " +
                        "ch=${event.channelId()} priority=${event.priority().name} group=${event.groupKey()}"
            )
        }
    }

    fun getRecentEvents(): List<Pair<Long, NotificationEvent>> =
        synchronized(eventLog) { eventLog.toList() }

    private fun formatTime(ts: Long): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ts))
}
