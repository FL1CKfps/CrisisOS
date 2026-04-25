package com.elv8.crisisos.data.dto.payloads

import kotlinx.serialization.Serializable

/**
 * Payload broadcast when a Dead Man's Switch deadline is reached without a
 * check-in (Feature 5). Per CrisisOS spec the trigger message must contain
 * "CRS ID, last known GPS, last camp checked into, pre-written note,
 * timestamp" so receiving family devices can act immediately.
 *
 * @param alertMessage         Pre-composed note the user wrote during setup.
 * @param registeredContacts   CRS IDs (or display labels) of the people the
 *                             user designated as escalation recipients.
 * @param senderCrsId          The user's pseudonymous CRS ID. Same identifier
 *                             used everywhere else on the mesh — never the
 *                             ANDROID_ID (which is a cross-app device leak).
 * @param senderAlias          Human-readable alias for display in the recipient
 *                             notification.
 * @param triggeredAt          Wall-clock timestamp (millis since epoch) at
 *                             which the worker fired the trigger.
 * @param latitude             Last-known latitude (best-effort, may be null
 *                             if no GPS fix is available offline).
 * @param longitude            Last-known longitude.
 * @param accuracyMeters       Reported accuracy of the last fix in metres.
 * @param locationTimestamp    Wall-clock timestamp of the last fix — useful
 *                             so recipients can judge how stale "last known"
 *                             actually is.
 */
@Serializable
data class DeadManPayload(
    val alertMessage: String,
    val registeredContacts: List<String>,
    val senderCrsId: String,
    val senderAlias: String,
    val triggeredAt: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val locationTimestamp: Long? = null
)
