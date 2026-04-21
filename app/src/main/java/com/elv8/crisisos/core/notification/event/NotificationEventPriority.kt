package com.elv8.crisisos.core.notification.event

enum class NotificationEventPriority(val level: Int) {
    LOW(0),
    DEFAULT(1),
    HIGH(2),
    CRITICAL(3)
}
