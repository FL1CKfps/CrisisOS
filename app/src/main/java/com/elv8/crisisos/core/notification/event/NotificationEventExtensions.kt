package com.elv8.crisisos.core.notification.event

import com.elv8.crisisos.core.notification.NotificationChannels

fun NotificationEvent.priority(): NotificationEventPriority = when (this) {
    is NotificationEvent.Sos.IncomingAlert -> NotificationEventPriority.CRITICAL
    is NotificationEvent.Sos.OwnAlertBroadcasting -> NotificationEventPriority.HIGH
    is NotificationEvent.Request.ConnectionRequestReceived -> NotificationEventPriority.HIGH
    is NotificationEvent.Request.MessageRequestReceived -> NotificationEventPriority.HIGH
    is NotificationEvent.Request.ConnectionRequestAccepted -> NotificationEventPriority.DEFAULT
    is NotificationEvent.Chat.MessageReceived -> NotificationEventPriority.DEFAULT
    is NotificationEvent.Supply.RequestAcknowledged -> NotificationEventPriority.DEFAULT
    is NotificationEvent.Supply.RequestFulfilled -> NotificationEventPriority.HIGH
    is NotificationEvent.MissingPerson.PersonLocated -> NotificationEventPriority.HIGH
    is NotificationEvent.System.PeerNearby -> NotificationEventPriority.LOW
    is NotificationEvent.System.MeshConnected -> NotificationEventPriority.LOW
    else -> NotificationEventPriority.LOW
}

fun NotificationEvent.channelId(): String = when (this) {
    is NotificationEvent.Chat -> NotificationChannels.CHAT_MESSAGES
    is NotificationEvent.Request -> NotificationChannels.REQUESTS
    is NotificationEvent.Sos -> NotificationChannels.ALERTS
    is NotificationEvent.MissingPerson -> NotificationChannels.ALERTS
    is NotificationEvent.Supply -> NotificationChannels.REQUESTS
    is NotificationEvent.System -> NotificationChannels.SYSTEM
}

fun NotificationEvent.groupKey(): String = when (this) {
    is NotificationEvent.Chat.MessageReceived -> "group_chat_${threadId}"
    is NotificationEvent.Request.ConnectionRequestReceived -> "group_requests"
    is NotificationEvent.Request.MessageRequestReceived -> "group_requests"
    is NotificationEvent.Sos -> "group_sos"
    is NotificationEvent.Supply -> "group_supply"
    is NotificationEvent.System -> "group_system"
    else -> "group_misc"
}
