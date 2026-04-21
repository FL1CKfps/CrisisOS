package com.elv8.crisisos.work

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.elv8.crisisos.core.event.AppEvent
import com.elv8.crisisos.core.event.EventBus
import com.elv8.crisisos.data.dto.PacketFactory
import com.elv8.crisisos.data.dto.payloads.SosPayload
import com.elv8.crisisos.data.mesh.MeshMessenger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class DeadManWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val messenger: MeshMessenger,
    private val eventBus: EventBus
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_TAG = "dead_man_switch"
        const val KEY_MESSAGE = "alert_message"
        const val KEY_CONTACTS = "contacts_json"

        fun buildRequest(intervalMinutes: Int, message: String, contacts: List<String>): PeriodicWorkRequest {
            return PeriodicWorkRequestBuilder<DeadManWorker>(intervalMinutes.toLong(), TimeUnit.MINUTES)
                .setInputData(workDataOf(
                    KEY_MESSAGE to message,
                    KEY_CONTACTS to "[]" // Or serialize contacts if needed
                ))
                .addTag(WORK_TAG)
                .build()
        }
    }

    override suspend fun doWork(): Result {
        val message = inputData.getString(KEY_MESSAGE) ?: "Check-in missed — automatic SOS"
        val contactsJson = inputData.getString(KEY_CONTACTS) ?: "[]"
        
        val packet = PacketFactory.buildSosPacket(
            senderId = getLocalDeviceId(applicationContext),
            senderAlias = getLocalAlias(applicationContext),
            payload = SosPayload(sosType = "DEAD_MAN", message = message, locationHint = null)
        )
        
        messenger.send(packet)
        eventBus.tryEmit(AppEvent.DeadManEvent.AlertTriggered(message))
        
        return Result.success()
    }

    private fun getLocalDeviceId(ctx: Context): String {
        return Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: java.util.UUID.randomUUID().toString()
    }

    private fun getLocalAlias(ctx: Context): String {
        val sharedPrefs = ctx.getSharedPreferences("crisisos_prefs", Context.MODE_PRIVATE)
        return sharedPrefs.getString("user_alias", "Survivor_${Build.MODEL}") ?: "Survivor_${Build.MODEL}"
    }
}