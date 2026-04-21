package com.elv8.crisisos.core.notification.event

sealed class NotificationEvent {

    // ── CHAT ──────────────────────────────────────────────────────────
    sealed class Chat : NotificationEvent() {
        data class MessageReceived(
            val threadId: String,
            val fromCrsId: String,
            val fromAlias: String,
            val avatarColor: Int,
            val messagePreview: String,     // max 80 chars
            val messageId: String,
            val timestamp: Long,
            val isGroupChat: Boolean,
            val groupName: String?          // null for direct chats
        ) : Chat()

        data class TypingStarted(
            val threadId: String,
            val fromAlias: String
        ) : Chat()
    }

    // ── REQUESTS ──────────────────────────────────────────────────────
    sealed class Request : NotificationEvent() {
        data class ConnectionRequestReceived(
            val requestId: String,
            val fromCrsId: String,
            val fromAlias: String,
            val fromAvatarColor: Int,
            val introMessage: String        // optional intro, may be empty
        ) : Request()

        data class ConnectionRequestAccepted(
            val requestId: String,
            val byAlias: String,
            val byCrsId: String,
            val newThreadId: String
        ) : Request()

        data class ConnectionRequestRejected(
            val requestId: String,
            val byAlias: String
        ) : Request()

        data class MessageRequestReceived(
            val requestId: String,
            val fromCrsId: String,
            val fromAlias: String,
            val fromAvatarColor: Int,
            val previewText: String
        ) : Request()
    }

    // ── SOS ───────────────────────────────────────────────────────────
    sealed class Sos : NotificationEvent() {
        data class IncomingAlert(
            val alertId: String,           // packet ID
            val fromCrsId: String,
            val fromAlias: String,
            val sosType: String,           // MEDICAL, TRAPPED, etc.
            val message: String,
            val locationHint: String?,
            val hopsAway: Int              // mesh hops from source
        ) : Sos()

        data class OwnAlertBroadcasting(
            val alertId: String,
            val sosType: String,
            val peersReached: Int
        ) : Sos()

        data class OwnAlertStopped(
            val alertId: String
        ) : Sos()
    }

    // ── SUPPLY ────────────────────────────────────────────────────────
    sealed class Supply : NotificationEvent() {
        data class RequestQueued(
            val requestId: String,
            val supplyType: String
        ) : Supply()

        data class RequestAcknowledged(
            val requestId: String,
            val supplyType: String,
            val ngoAlias: String,
            val estimatedEta: String
        ) : Supply()

        data class RequestFulfilled(
            val requestId: String,
            val supplyType: String,
            val meetingPoint: String
        ) : Supply()
    }

    // ── MISSING PERSON ────────────────────────────────────────────────
    sealed class MissingPerson : NotificationEvent() {
        data class PersonLocated(
            val crsId: String,
            val name: String,
            val lastLocation: String,
            val hopsAway: Int
        ) : MissingPerson()

        data class SearchResponseReceived(
            val queryCrsId: String,
            val name: String,
            val locationFound: String
        ) : MissingPerson()
    }

    // ── SYSTEM ────────────────────────────────────────────────────────
    sealed class System : NotificationEvent() {
        data class MeshConnected(
            val peerAlias: String,
            val peerCount: Int
        ) : System()

        data class MeshDisconnected(
            val lastPeerAlias: String
        ) : System()

        data class PeerNearby(
            val peerAlias: String,
            val peerCrsId: String
        ) : System()

        data class PermissionsMissing(
            val permissions: List<String>
        ) : System()

        object ServiceStarted : System()
        object ServiceStopped : System()
    }
}
