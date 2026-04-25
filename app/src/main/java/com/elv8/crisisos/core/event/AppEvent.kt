package com.elv8.crisisos.core.event

import com.elv8.crisisos.data.dto.MeshPacket

sealed class AppEvent {

    sealed class MeshEvent : AppEvent() {
        data class PeerDiscovered(val peerId: String, val peerAlias: String) : MeshEvent()
        data class PeerLost(val peerId: String) : MeshEvent()
        data class PeerConnected(val peerId: String, val peerAlias: String) : MeshEvent()
        data class PeerDisconnected(val peerId: String) : MeshEvent()
        data class RawPacketReceived(val packet: MeshPacket, val incomingEndpointId: String) : MeshEvent()
        data class MessageReceived(val packet: MeshPacket, val incomingEndpointId: String) : MeshEvent()
        data class MessageSent(val packetId: String) : MeshEvent()
        data class MessageFailed(val packetId: String, val reason: String) : MeshEvent()
        data class MeshStatusChanged(val isActive: Boolean, val peerCount: Int) : MeshEvent()

        // File transfer events
        data class MediaFileReceived(
            val endpointId: String,
            val payloadId: Long,
            val payloadFile: com.google.android.gms.nearby.connection.Payload.File
        ) : MeshEvent()
        data class FileSendCompleted(val fileId: String) : MeshEvent()
        data class FileSendFailed(val fileId: String) : MeshEvent()
    }

    sealed class SosEvent : AppEvent() {
        data class SosBroadcastStarted(val sosType: String, val message: String) : SosEvent()
        object SosBroadcastStopped : SosEvent()
        data class SosReceivedFromPeer(val senderId: String, val senderAlias: String, val sosType: String, val message: String) : SosEvent()
    }

    sealed class DeadManEvent : AppEvent() {
        object CheckInReceived : DeadManEvent()
        object TimerExpired : DeadManEvent()
        data class AlertTriggered(val message: String) : DeadManEvent()
    }

    sealed class MissingPersonEvent : AppEvent() {
        data class QueryBroadcast(val senderId: String, val queryType: String, val crsId: String, val name: String, val age: Int?, val description: String?, val lastLocation: String?) : MissingPersonEvent()
        data class ResponseReceived(val crsId: String, val lastLocation: String, val hopsAway: Int) : MissingPersonEvent()
    }

    sealed class SupplyEvent : AppEvent() {
        data class RequestBroadcast(val supplyType: String, val location: String) : SupplyEvent()
        data class AckReceived(val requestId: String, val ngoId: String, val eta: String) : SupplyEvent()
    }

    sealed class ChildAlertEvent : AppEvent() {
        data class AlertBroadcast(val childId: String, val name: String, val location: String) : ChildAlertEvent()
    }

    sealed class ConnectionEvent : AppEvent() {
        data class SendOutboundRequest(val requestId: String, val toCrsId: String, val message: String, val fromAvatarColor: Int, val fromAlias: String) : ConnectionEvent()
        data class SendOutboundResponse(val requestId: String, val toCrsId: String, val accepted: Boolean, val fromAlias: String, val fromAvatarColor: Int) : ConnectionEvent()
        data class RequestReceived(val requestId: String, val fromCrsId: String, val fromAlias: String, val fromAvatarColor: Int, val message: String, val timestamp: Long) : ConnectionEvent()
        data class ResponseReceived(val requestId: String, val fromCrsId: String, val fromAlias: String, val fromAvatarColor: Int, val accepted: Boolean) : ConnectionEvent()
    }

    sealed class CapacityEvent : AppEvent() {
        /**
         * Emitted when an NGO updates a camp's occupancy and it crosses the
         * 95% threshold defined in CrisisOS_Context.md (Feature 2 § "Camp
         * capacity"). Nearby camps and the maps screen react to this by
         * surfacing a "redirect incoming" hint.
         */
        data class CampNearCapacity(
            val zoneId: String,
            val zoneName: String,
            val occupancyRatio: Float,
            val latitude: Double,
            val longitude: Double
        ) : CapacityEvent()
    }

    sealed class SystemEvent : AppEvent() {
        object AppForegrounded : SystemEvent()
        object AppBackgrounded : SystemEvent()
        data class LocationUpdated(val lat: Double, val lon: Double) : SystemEvent()
        data class PermissionGranted(val permission: String) : SystemEvent()
        data class PermissionDenied(val permission: String) : SystemEvent()
    }
}
