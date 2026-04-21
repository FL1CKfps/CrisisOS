package com.elv8.crisisos.core.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.elv8.crisisos.MainActivity
import com.elv8.crisisos.core.notification.event.NotificationEvent
import com.elv8.crisisos.core.notification.event.NotificationEventPriority
import com.elv8.crisisos.core.notification.event.channelId
import com.elv8.crisisos.core.notification.event.groupKey
import com.elv8.crisisos.core.notification.event.priority
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationBuilder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wrapper: NotificationManagerWrapper
) {

    // --- Helper: create a deep-link PendingIntent ---
    private fun mainActivityIntent(extras: Bundle? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            extras?.let { putExtras(it) }
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun mainActivityIntentWithDestination(destination: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", destination)
        }
        return PendingIntent.getActivity(
            context, destination.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // --- CHAT MESSAGE notification ---
    fun buildChatMessage(event: NotificationEvent.Chat.MessageReceived): Notification {
        val channelId = event.channelId()
        val priority = event.priority()
        val title = if (event.isGroupChat) "${event.groupName} • ${event.fromAlias}" else event.fromAlias
        val contentText = event.messagePreview.take(80)

        val tapIntent = mainActivityIntentWithDestination("chat_thread/${event.threadId}")

        return wrapper.buildBaseBuilder(channelId, priority)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(
                NotificationCompat.MessagingStyle(
                    Person.Builder().setName(event.fromAlias).setKey(event.fromCrsId).build()
                ).addMessage(
                    NotificationCompat.MessagingStyle.Message(
                        contentText,
                        event.timestamp,
                        Person.Builder().setName(event.fromAlias).setKey(event.fromCrsId).build()
                    )
                ).setGroupConversation(event.isGroupChat)
                    .let { style ->
                        if (event.isGroupChat && event.groupName != null)
                            style.setConversationTitle(event.groupName)
                        else style
                    }
            )
            .setContentIntent(tapIntent)
            .setGroup(event.groupKey())
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setShortcutId(event.threadId)
            .build()
    }

    // --- CONNECTION REQUEST notification ---
    fun buildConnectionRequest(event: NotificationEvent.Request.ConnectionRequestReceived): Notification {
        val tapIntent = mainActivityIntentWithDestination("message_requests")

        val acceptIntent = buildBroadcastIntent(
            action = NotificationActions.ACTION_ACCEPT_CONNECTION,
            extras = Bundle().apply { putString("request_id", event.requestId) }
        )
        val rejectIntent = buildBroadcastIntent(
            action = NotificationActions.ACTION_REJECT_CONNECTION,
            extras = Bundle().apply { putString("request_id", event.requestId) }
        )

        val bodyText = if (event.introMessage.isNotBlank())
            "\"${event.introMessage.take(60)}\""
        else
            "Wants to connect with you on CrisisOS"

        return wrapper.buildBaseBuilder(event.channelId(), event.priority())
            .setContentTitle("Connection request from ${event.fromAlias}")
            .setContentText(bodyText)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "${event.fromAlias} (${event.fromCrsId})\n$bodyText"
                )
            )
            .setContentIntent(tapIntent)
            .setGroup(event.groupKey())
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .addAction(
                android.R.drawable.ic_menu_add,
                "Accept",
                acceptIntent
            )
            .addAction(
                android.R.drawable.ic_menu_delete,
                "Decline",
                rejectIntent
            )
            .build()
    }

    // --- CONNECTION ACCEPTED notification ---
    fun buildConnectionAccepted(event: NotificationEvent.Request.ConnectionRequestAccepted): Notification {
        val tapIntent = mainActivityIntentWithDestination("chat_thread/${event.newThreadId}")
        return wrapper.buildBaseBuilder(event.channelId(), event.priority())
            .setContentTitle("${event.byAlias} accepted your request")
            .setContentText("You can now message each other. Tap to open chat.")
            .setContentIntent(tapIntent)
            .setGroup(event.groupKey())
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .build()
    }

    // --- CONNECTION REJECTED notification ---
    fun buildConnectionRejected(event: NotificationEvent.Request.ConnectionRequestRejected): Notification {
        val tapIntent = mainActivityIntentWithDestination("peer_discovery")
        return wrapper.buildBaseBuilder(event.channelId(), event.priority())
            .setContentTitle("Request declined")
            .setContentText("${event.byAlias} declined your connection request.")
            .setContentIntent(tapIntent)
            .setGroup(event.groupKey())
            .build()
    }

    // --- MESSAGE REQUEST notification ---
    fun buildMessageRequest(event: NotificationEvent.Request.MessageRequestReceived): Notification {
        val tapIntent = mainActivityIntentWithDestination("message_requests")

        val acceptIntent = buildBroadcastIntent(
            action = NotificationActions.ACTION_ACCEPT_MESSAGE_REQUEST,
            extras = Bundle().apply { putString("request_id", event.requestId) }
        )
        val rejectIntent = buildBroadcastIntent(
            action = NotificationActions.ACTION_REJECT_MESSAGE_REQUEST,
            extras = Bundle().apply { putString("request_id", event.requestId) }
        )

        return wrapper.buildBaseBuilder(event.channelId(), event.priority())
            .setContentTitle("Message request from ${event.fromAlias}")
            .setContentText("\"${event.previewText.take(60)}\"")
            .setContentIntent(tapIntent)
            .setGroup(event.groupKey())
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .addAction(android.R.drawable.ic_menu_add, "Accept", acceptIntent)
            .addAction(android.R.drawable.ic_menu_delete, "Decline", rejectIntent)
            .build()
    }

    // --- SOS ALERT notification ---
    fun buildSosAlert(event: NotificationEvent.Sos.IncomingAlert): Notification {
        val tapIntent = mainActivityIntentWithDestination("sos")
        val locationLine = if (event.locationHint != null) "\nLocation: ${event.locationHint}" else ""
        val bodyText = "${event.sosType.replace('_', ' ')} — ${event.message}$locationLine\nVia ${event.hopsAway} mesh hop(s)"

        return wrapper.buildBaseBuilder(event.channelId(), event.priority())
            .setContentTitle("SOS ALERT — ${event.fromAlias}")
            .setContentText("${event.sosType} emergency nearby")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setContentIntent(tapIntent)
            .setGroup(event.groupKey())
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(false)
            .setFullScreenIntent(tapIntent, true)  // heads-up notification
            .build()
    }

    // --- SUPPLY UPDATE notifications ---
    fun buildSupplyAcknowledged(event: NotificationEvent.Supply.RequestAcknowledged): Notification {
        val tapIntent = mainActivityIntentWithDestination("supply")
        return wrapper.buildBaseBuilder(event.channelId(), event.priority())
            .setContentTitle("Supply request acknowledged")
            .setContentText("${event.ngoAlias} will deliver ${event.supplyType.lowercase()}. ETA: ${event.estimatedEta}")
            .setContentIntent(tapIntent)
            .setGroup(event.groupKey())
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
    }

    fun buildSupplyFulfilled(event: NotificationEvent.Supply.RequestFulfilled): Notification {
        val tapIntent = mainActivityIntentWithDestination("supply")
        return wrapper.buildBaseBuilder(event.channelId(), event.priority())
            .setContentTitle("Supply ready for pickup")
            .setContentText("${event.supplyType} ready at ${event.meetingPoint}")
            .setContentIntent(tapIntent)
            .setGroup(event.groupKey())
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
    }

    // --- MISSING PERSON LOCATED notification ---
    fun buildPersonLocated(event: NotificationEvent.MissingPerson.PersonLocated): Notification {
        val tapIntent = mainActivityIntentWithDestination("missing_person")
        return wrapper.buildBaseBuilder(event.channelId(), event.priority())
            .setContentTitle("Person located — ${event.name}")
            .setContentText("Last seen: ${event.lastLocation} (${event.hopsAway} hop(s) away)")
            .setContentIntent(tapIntent)
            .setGroup(event.groupKey())
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
    }

    // --- SYSTEM notifications ---
    fun buildPeerNearby(event: NotificationEvent.System.PeerNearby): Notification {
        val tapIntent = mainActivityIntentWithDestination("peer_discovery")
        return wrapper.buildBaseBuilder(event.channelId(), event.priority())
            .setContentTitle("Peer nearby")
            .setContentText("${event.peerAlias} is in range")
            .setContentIntent(tapIntent)
            .setGroup(event.groupKey())
            .build()
    }

    // --- Group summary notification ---
    fun buildGroupSummary(groupKey: String, channelId: String, count: Int, title: String): Notification {
        return wrapper.buildBaseBuilder(channelId, NotificationEventPriority.LOW)
            .setContentTitle(title)
            .setContentText("$count new notifications")
            .setGroup(groupKey)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()
    }

    // --- Broadcast intent for action buttons ---
    private fun buildBroadcastIntent(action: String, extras: Bundle): PendingIntent {
        val intent = Intent(action).apply {
            `package` = context.packageName
            putExtras(extras)
        }
        return PendingIntent.getBroadcast(
            context,
            action.hashCode() + extras.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
