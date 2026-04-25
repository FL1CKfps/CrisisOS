package com.elv8.crisisos.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.elv8.crisisos.core.event.AppEvent
import com.elv8.crisisos.core.event.EventBus
import com.elv8.crisisos.core.notification.NotificationEventBus
import com.elv8.crisisos.core.notification.event.NotificationEvent
import com.elv8.crisisos.data.dto.PacketFactory
import com.elv8.crisisos.data.dto.payloads.DeadManPayload
import com.elv8.crisisos.data.remote.mesh.MeshMessenger
import com.elv8.crisisos.domain.repository.IdentityRepository
import com.elv8.crisisos.domain.repository.LocationRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Fires once when the user has not checked in by their Dead Man Switch
 * deadline (Feature 5). Replaced the prior PeriodicWorkRequest design — that
 * one fired every interval regardless of check-ins, which is the opposite of
 * what the spec asks for.
 *
 * On execution the worker:
 *   1. Resolves the local identity (CRS ID + alias) from Room — never
 *      `Settings.Secure.ANDROID_ID` (cross-app device leak).
 *   2. Pulls the last known GPS fix (best-effort, may be null offline).
 *   3. Builds a DEAD_MAN_TRIGGER packet with the proper `DeadManPayload`
 *      (the previous worker mistakenly built a SOS packet — peers running
 *      `MeshMessenger.handleIncomingPacket` for `DEAD_MAN_TRIGGER` could not
 *      decode it).
 *   4. Broadcasts via the mesh.
 *   5. Emits a high-priority local notification so the user (whose phone
 *      may have died and come back, etc.) sees that the switch fired.
 */
@HiltWorker
class DeadManWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val messenger: MeshMessenger,
    private val eventBus: EventBus,
    private val identityRepository: IdentityRepository,
    private val locationRepository: LocationRepository,
    private val notificationEventBus: NotificationEventBus
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_TAG = "dead_man_switch"
        const val WORK_NAME = "dead_man_switch_unique"
        const val KEY_MESSAGE = "alert_message"
        const val KEY_CONTACTS = "contacts_json"

        /**
         * Build a one-shot delayed worker that will fire at the deadline.
         *
         * @param delayMinutes        How long from now until the worker runs.
         * @param message             The user's pre-composed alert text.
         * @param contactDescriptors  Display labels and/or CRS IDs of the
         *                            escalation recipients. Serialized as a
         *                            JSON array so the worker can pass them
         *                            into the payload without losing them.
         */
        fun buildRequest(
            delayMinutes: Int,
            message: String,
            contactDescriptors: List<String>
        ): OneTimeWorkRequest {
            val contactsJson = JSONArray().apply {
                contactDescriptors.forEach { put(it) }
            }.toString()
            return OneTimeWorkRequestBuilder<DeadManWorker>()
                .setInitialDelay(delayMinutes.toLong(), TimeUnit.MINUTES)
                .setInputData(workDataOf(
                    KEY_MESSAGE to message,
                    KEY_CONTACTS to contactsJson
                ))
                .addTag(WORK_TAG)
                .build()
        }
    }

    override suspend fun doWork(): Result {
        val message = inputData.getString(KEY_MESSAGE)
            ?: "Check-in missed — automatic SOS"
        val contactsJson = inputData.getString(KEY_CONTACTS) ?: "[]"
        val contacts = decodeContacts(contactsJson)

        val identity = identityRepository.getIdentity().first()
        // Fail closed: an unbootstrapped identity would either leak a stable
        // collision-prone fallback ID across devices, or send an unauthenticated
        // alert that peers can't attribute. Better to abort and let the user
        // notice a stuck timer than to fire a malformed broadcast.
        if (identity == null || identity.crsId.isBlank()) {
            return Result.failure()
        }
        val senderCrsId = identity.crsId
        val senderAlias = identity.alias.ifBlank { "Survivor" }

        val location = runCatching { locationRepository.getLastKnownLocation() }
            .getOrNull()

        val now = System.currentTimeMillis()

        val payload = DeadManPayload(
            alertMessage = message,
            registeredContacts = contacts,
            senderCrsId = senderCrsId,
            senderAlias = senderAlias,
            triggeredAt = now,
            latitude = location?.latitude,
            longitude = location?.longitude,
            accuracyMeters = location?.accuracy,
            locationTimestamp = location?.timestamp
        )

        val packet = PacketFactory.buildDeadManPacket(
            senderId = senderCrsId,
            senderAlias = senderAlias,
            payload = payload
        )
        messenger.send(packet)

        // Local notification so the user (and anyone with their phone) sees
        // that the dead-man fire happened. Reuses the existing CRITICAL SOS
        // channel — pre-existing infrastructure handles channel creation,
        // grouping, and DND-bypass for SOS-priority events.
        val locationHint = formatLocationHint(payload)
        notificationEventBus.emit(
            NotificationEvent.Sos.IncomingAlert(
                alertId = "dead_man_${UUID.randomUUID()}",
                fromCrsId = senderCrsId,
                fromAlias = senderAlias,
                sosType = "DEAD_MAN_TRIGGERED",
                message = "Dead Man Switch fired — $message",
                locationHint = locationHint,
                hopsAway = 0,
                latitude = location?.latitude,
                longitude = location?.longitude
            )
        )

        eventBus.tryEmit(AppEvent.DeadManEvent.AlertTriggered(message))
        return Result.success()
    }

    private fun decodeContacts(json: String): List<String> = try {
        val arr = JSONArray(json)
        List(arr.length()) { i -> arr.optString(i) }
            .filter { it.isNotBlank() }
    } catch (_: Exception) {
        emptyList()
    }

    private fun formatLocationHint(payload: DeadManPayload): String? {
        val lat = payload.latitude ?: return null
        val lon = payload.longitude ?: return null
        return "%.5f, %.5f".format(lat, lon)
    }
}
