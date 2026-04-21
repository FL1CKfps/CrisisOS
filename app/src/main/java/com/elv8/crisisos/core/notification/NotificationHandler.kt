package com.elv8.crisisos.core.notification

import android.util.Log
import com.elv8.crisisos.core.debug.MeshLogger
import com.elv8.crisisos.core.notification.event.NotificationEvent
import com.elv8.crisisos.core.notification.event.channelId
import com.elv8.crisisos.core.notification.event.groupKey
import kotlinx.coroutines.CoroutineScope
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import com.elv8.crisisos.data.local.dao.NotificationLogDao
import com.elv8.crisisos.data.local.entity.NotificationLogEntity

@Singleton
class NotificationHandler @Inject constructor(
    private val bus: NotificationEventBus,
    private val builder: NotificationBuilder,
    private val wrapper: NotificationManagerWrapper,
    private val activeScreenTracker: ActiveScreenTracker,
    private val notificationSettings: NotificationSettings,
    private val notificationLogDao: NotificationLogDao,
    private val scope: CoroutineScope
) {

    // --- Suppression state (in-memory) ---
    private val suppressedThreadIds: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    // When user is actively in a chat thread, add its ID here.
    // Remove when user leaves the screen.

    private val suppressedGroups: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    // e.g. if user is on the requests screen, suppress request notifications

    fun clearNotificationsForThread(threadId: String) {
        wrapper.cancelGroup("group_chat_$threadId")
        resetGroup("group_chat_$threadId")
        Log.d("CrisisOS_NotifHandler", "Notifications cleared for thread: $threadId")
    }

    fun clearChatGroupSummary() {
        wrapper.cancelGroup("group_chat")
    }

    // --- Deduplication state ---
    private val recentEventHashes: ArrayDeque<Int> = ArrayDeque(100)
    private fun isDuplicate(event: NotificationEvent): Boolean {
        val hash = when (event) {
            is NotificationEvent.Chat.MessageReceived -> "${event.messageId}".hashCode()
            is NotificationEvent.Request.ConnectionRequestReceived -> "${event.requestId}".hashCode()
            is NotificationEvent.Request.MessageRequestReceived -> "${event.requestId}".hashCode()
            is NotificationEvent.Sos.IncomingAlert -> "${event.alertId}".hashCode()
            is NotificationEvent.Supply.RequestAcknowledged -> "${event.requestId}_ack".hashCode()
            is NotificationEvent.Supply.RequestFulfilled -> "${event.requestId}_done".hashCode()
            is NotificationEvent.MissingPerson.PersonLocated -> "${event.crsId}_located".hashCode()
            else -> event.hashCode()
        }
        synchronized(recentEventHashes) {
            if (recentEventHashes.contains(hash)) {
                Log.d("CrisisOS_NotifHandler", "Duplicate event suppressed: ${event::class.simpleName} hash=$hash")
                return true
            }
            if (recentEventHashes.size >= 100) recentEventHashes.removeFirst()
            recentEventHashes.addLast(hash)
            return false
        }
    }

    // --- Group active count (for summary notifications) ---
    private val groupActiveCounts: MutableMap<String, AtomicInteger> = ConcurrentHashMap()
    private fun incrementGroup(groupKey: String): Int =
        groupActiveCounts.getOrPut(groupKey) { AtomicInteger(0) }.incrementAndGet()
    private fun resetGroup(groupKey: String) {
        groupActiveCounts[groupKey]?.set(0)
    }

    // --- Settings State ---
    private var isDndEnabled = false
    private var chatEnabled = true
    private var sosEnabled = true
    private var requestEnabled = true
    private var systemEnabled = false

    // --- Suppression control (called from ViewModels) ---
    fun suppressThread(threadId: String) {
        suppressedThreadIds.add(threadId)
        wrapper.cancelGroup("group_chat_$threadId")
        Log.d("CrisisOS_NotifHandler", "Thread suppressed: $threadId")
    }
    
    fun unsuppressThread(threadId: String) {
        suppressedThreadIds.remove(threadId)
        Log.d("CrisisOS_NotifHandler", "Thread unsuppressed: $threadId")
    }
    
    fun suppressGroup(groupKey: String) { suppressedGroups.add(groupKey) }
    
    fun unsuppressGroup(groupKey: String) { suppressedGroups.remove(groupKey) }

    // --- Start processing ---
    fun startProcessing() {
        Log.i("CrisisOS_NotifHandler", "NotificationHandler started — listening for events")
        
        scope.launch { notificationSettings.isDndEnabled.collect { isDndEnabled = it } }
        scope.launch { notificationSettings.isChatEnabled.collect { chatEnabled = it } }
        scope.launch { notificationSettings.isSosEnabled.collect { sosEnabled = it } }
        scope.launch { notificationSettings.isRequestEnabled.collect { requestEnabled = it } }
        scope.launch { notificationSettings.isSystemEnabled.collect { systemEnabled = it } }

        bus.observeAll(scope) { event ->
            processEvent(event)
        }
    }

    private fun logSuppressed(event: NotificationEvent, reason: String) {
        Log.d("CrisisOS_NotifHandler", "Suppressed (reason=$reason): ${event::class.simpleName}")
        scope.launch(Dispatchers.IO) {
            notificationLogDao.insert(NotificationLogEntity(
                eventType = event::class.simpleName ?: "Unknown",
                groupKey = event.groupKey(),
                channelId = event.channelId(),
                title = "suppressed",
                body = reason,
                timestamp = System.currentTimeMillis(),
                wasShown = false,
                wasDismissed = false,
                associatedId = null
            ))
        }
    }

    private fun logShown(event: NotificationEvent, title: String = "shown", body: String = "notification displayed") {
        scope.launch(Dispatchers.IO) {
            notificationLogDao.insert(NotificationLogEntity(
                eventType = event::class.simpleName ?: "Unknown",
                groupKey = event.groupKey(),
                channelId = event.channelId(),
                title = title,
                body = body,
                timestamp = System.currentTimeMillis(),
                wasShown = true,
                wasDismissed = false,
                associatedId = extractAssociatedId(event)
            ))
        }
    }

    private fun extractAssociatedId(event: NotificationEvent): String? = when (event) {
        is NotificationEvent.Chat.MessageReceived -> event.threadId
        is NotificationEvent.Request.ConnectionRequestReceived -> event.requestId
        is NotificationEvent.Request.MessageRequestReceived -> event.requestId
        is NotificationEvent.Sos.IncomingAlert -> event.alertId
        is NotificationEvent.Supply.RequestAcknowledged -> event.requestId
        else -> null
    }

    // --- Core routing function ---
    private fun processEvent(event: NotificationEvent) {
        val active = activeScreenTracker.activeScreen.value
        Log.v("CrisisOS_NotifHandler", "Active screen during event: $active")
        
        Log.d("CrisisOS_NotifHandler", "Processing: ${event::class.simpleName}")

        if (isDuplicate(event)) return

        // DND check — SOS always bypasses DND
        val isSos = event is NotificationEvent.Sos
        if (isDndEnabled && !isSos) {
            logSuppressed(event, reason = "DND")
            return
        }

        // Per-category check
        val categoryEnabled = when (event) {
            is NotificationEvent.Chat -> chatEnabled
            is NotificationEvent.Sos -> sosEnabled
            is NotificationEvent.Request -> requestEnabled
            is NotificationEvent.System -> systemEnabled
            is NotificationEvent.Supply -> requestEnabled
            is NotificationEvent.MissingPerson -> sosEnabled // high priority, same bucket as SOS
            else -> true
        }
        if (!categoryEnabled) {
            logSuppressed(event, reason = "category_disabled")
            return
        }

        val groupKey = event.groupKey()
        if (suppressedGroups.contains(groupKey)) {
            Log.d("CrisisOS_NotifHandler", "Group suppressed, skipping: $groupKey")
            logSuppressed(event, reason = "screen_active")
            return
        }

        when (event) {

            is NotificationEvent.Chat.MessageReceived -> handleChatMessage(event)
            is NotificationEvent.Chat.TypingStarted -> { /* no notification for typing */ }

            is NotificationEvent.Request.ConnectionRequestReceived -> handleConnectionRequest(event)
            is NotificationEvent.Request.ConnectionRequestAccepted -> handleConnectionAccepted(event)
            is NotificationEvent.Request.ConnectionRequestRejected -> handleConnectionRejected(event)
            is NotificationEvent.Request.MessageRequestReceived -> handleMessageRequest(event)

            is NotificationEvent.Sos.IncomingAlert -> handleSosAlert(event)
            is NotificationEvent.Sos.OwnAlertBroadcasting -> { /* service notification handles this */ }
            is NotificationEvent.Sos.OwnAlertStopped -> wrapper.cancelGroup("group_sos")

            is NotificationEvent.Supply.RequestAcknowledged -> handleSupplyAck(event)
            is NotificationEvent.Supply.RequestFulfilled -> handleSupplyFulfilled(event)
            is NotificationEvent.Supply.RequestQueued -> { /* no notification — UI feedback is enough */ }

            is NotificationEvent.MissingPerson.PersonLocated -> handlePersonLocated(event)
            is NotificationEvent.MissingPerson.SearchResponseReceived -> { /* handled by PersonLocated */ }

            is NotificationEvent.System.PeerNearby -> handlePeerNearby(event)
            is NotificationEvent.System.MeshConnected -> { /* silent — mesh notification handles this */ }
            is NotificationEvent.System.PermissionsMissing -> handlePermissionsMissing(event)
            else -> Log.d("CrisisOS_NotifHandler", "No handler for: ${event::class.simpleName}")
        }
    }

    // --- Individual handlers ---

    private fun handleChatMessage(event: NotificationEvent.Chat.MessageReceived) {
        if (suppressedThreadIds.contains(event.threadId)) {
            Log.d("CrisisOS_NotifHandler", "Chat suppressed for thread: ${event.threadId}")
            return
        }
        logShown(event)
        val notifId = wrapper.getOrCreateNotificationId("chat_${event.threadId}")
        val notification = builder.buildChatMessage(event)
        wrapper.show(notifId, notification, event.channelId())

        val count = incrementGroup(event.groupKey())
        if (count > 1) {
            val summaryId = wrapper.getOrCreateSummaryId(event.groupKey())
            val summary = builder.buildGroupSummary(
                event.groupKey(), event.channelId(), count, "CrisisOS Messages"
            )
            wrapper.show(summaryId, summary, event.channelId())
        }
    }

    private fun handleConnectionRequest(event: NotificationEvent.Request.ConnectionRequestReceived) {
        logShown(event)
        val notifId = wrapper.getOrCreateNotificationId("conn_req_${event.requestId}")
        wrapper.show(notifId, builder.buildConnectionRequest(event), event.channelId())

        val count = incrementGroup(event.groupKey())
        if (count > 1) showRequestSummary(count)
    }

    private fun handleConnectionAccepted(event: NotificationEvent.Request.ConnectionRequestAccepted) {
        logShown(event)
        val notifId = wrapper.getOrCreateNotificationId("conn_accept_${event.requestId}")
        wrapper.show(notifId, builder.buildConnectionAccepted(event), event.channelId())
    }

    private fun handleConnectionRejected(event: NotificationEvent.Request.ConnectionRequestRejected) {
        logShown(event)
        val notifId = wrapper.getOrCreateNotificationId("conn_reject_${event.requestId}")
        wrapper.show(notifId, builder.buildConnectionRejected(event), event.channelId())
    }

    private fun handleMessageRequest(event: NotificationEvent.Request.MessageRequestReceived) {
        logShown(event)
        val notifId = wrapper.getOrCreateNotificationId("msg_req_${event.requestId}")
        wrapper.show(notifId, builder.buildMessageRequest(event), event.channelId())

        val count = incrementGroup(event.groupKey())
        if (count > 1) showRequestSummary(count)
    }

    private fun handleSosAlert(event: NotificationEvent.Sos.IncomingAlert) {
        logShown(event)
        val notifId = wrapper.getOrCreateNotificationId("sos_${event.alertId}")
        wrapper.show(notifId, builder.buildSosAlert(event), event.channelId())
        // SOS never grouped — each alert is individual
    }

    private fun handleSupplyAck(event: NotificationEvent.Supply.RequestAcknowledged) {
        logShown(event)
        val notifId = wrapper.getOrCreateNotificationId("supply_ack_${event.requestId}")
        wrapper.show(notifId, builder.buildSupplyAcknowledged(event), event.channelId())
    }

    private fun handleSupplyFulfilled(event: NotificationEvent.Supply.RequestFulfilled) {
        logShown(event)
        val notifId = wrapper.getOrCreateNotificationId("supply_done_${event.requestId}")
        wrapper.show(notifId, builder.buildSupplyFulfilled(event), event.channelId())
    }

    private fun handlePersonLocated(event: NotificationEvent.MissingPerson.PersonLocated) {
        logShown(event)
        val notifId = wrapper.getOrCreateNotificationId("person_${event.crsId}")
        wrapper.show(notifId, builder.buildPersonLocated(event), event.channelId())
    }

    private fun handlePeerNearby(event: NotificationEvent.System.PeerNearby) {
        // Rate-limit: only one "peer nearby" notification per peer per session
        val key = "peer_nearby_${event.peerCrsId}"
        if (wrapper.getOrCreateNotificationId(key) > 0) return  // already shown this peer
        logShown(event)
        val notifId = wrapper.getOrCreateNotificationId(key)
        wrapper.show(notifId, builder.buildPeerNearby(event), event.channelId())
    }

    private fun handlePermissionsMissing(event: NotificationEvent.System.PermissionsMissing) {
        Log.w("CrisisOS_NotifHandler", "Permissions missing: ${event.permissions}")
        // Permission request is handled by HomeViewModel, not a notification
    }

    private fun showRequestSummary(count: Int) {
        val summaryId = wrapper.getOrCreateSummaryId("group_requests")
        val summary = builder.buildGroupSummary("group_requests", NotificationChannels.REQUESTS, count, "CrisisOS Requests")
        wrapper.show(summaryId, summary, NotificationChannels.REQUESTS)
    }
}
