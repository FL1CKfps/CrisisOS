package com.elv8.crisisos.core.notification

import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import com.elv8.crisisos.core.debug.MeshLogger
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.elv8.crisisos.core.notification.event.NotificationEventPriority
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationManagerWrapper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager: NotificationManagerCompat =
        NotificationManagerCompat.from(context)

    // --- Channel registration ---
    fun createAllChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channels = listOf(
            NotificationChannelCompat.Builder(
                NotificationChannels.CHAT_MESSAGES,
                NotificationManagerCompat.IMPORTANCE_DEFAULT
            )
                .setName("Messages")
                .setDescription("Incoming mesh chat messages")
                .setVibrationEnabled(true)
                .setLightsEnabled(true)
                .build(),

            NotificationChannelCompat.Builder(
                NotificationChannels.REQUESTS,
                NotificationManagerCompat.IMPORTANCE_DEFAULT
            )
                .setName("Requests")
                .setDescription("Connection and message requests")
                .setVibrationEnabled(true)
                .build(),

            NotificationChannelCompat.Builder(
                NotificationChannels.ALERTS,
                NotificationManagerCompat.IMPORTANCE_HIGH
            )
                .setName("Emergency Alerts")
                .setDescription("SOS alerts and critical notifications")
                .setVibrationEnabled(true)
                .setVibrationPattern(longArrayOf(0, 300, 100, 300))
                .setLightsEnabled(true)
                .setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .build(),

            NotificationChannelCompat.Builder(
                NotificationChannels.SYSTEM,
                NotificationManagerCompat.IMPORTANCE_LOW
            )
                .setName("System")
                .setDescription("Mesh network status and system events")
                .setVibrationEnabled(false)
                .setSound(null, null)
                .build(),

            NotificationChannelCompat.Builder(
                NotificationChannels.MESH_SERVICE,
                NotificationManagerCompat.IMPORTANCE_LOW
            )
                .setName("Mesh Service")
                .setDescription("Ongoing mesh network connection")
                .setVibrationEnabled(false)
                .setSound(null, null)
                .build()
        )

        notificationManager.createNotificationChannelsCompat(channels)
        Log.i("CrisisOS_Notif", "All notification channels created")
    }

    // --- Notification ID management ---
    private val notificationIdMap: MutableMap<String, Int> = ConcurrentHashMap()
    private val nextId = AtomicInteger(2000)  // start at 2000, below 1001 = service notification

    private fun getOrCreateId(key: String): Int =
        notificationIdMap.getOrPut(key) { nextId.getAndIncrement() }

    private fun getSummaryId(groupKey: String): Int =
        getOrCreateId("summary_$groupKey")

    // --- Permission check ---
    fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    // --- Core show function ---
    fun show(notificationId: Int, notification: Notification, channelId: String) {
        if (!hasNotificationPermission()) {
            Log.w("CrisisOS_Notif", "show() skipped — POST_NOTIFICATIONS not granted")
            return
        }
        try {
            notificationManager.notify(notificationId, notification)
            Log.d("CrisisOS_Notif", "Notification shown — id=$notificationId channel=$channelId")
        } catch (e: SecurityException) {
            Log.e("CrisisOS_Notif", "SecurityException showing notification: ${e.message}")
        }
    }

    fun cancel(notificationId: Int) {
        notificationManager.cancel(notificationId)
        Log.d("CrisisOS_Notif", "Notification cancelled — id=$notificationId")
    }

    fun cancelGroup(groupKey: String) {
        notificationIdMap.entries
            .filter { it.key.startsWith(groupKey) || it.key == "summary_$groupKey" }
            .forEach { (_, id) -> notificationManager.cancel(id) }
        Log.d("CrisisOS_Notif", "Notification group cancelled — group=$groupKey")
    }

    fun cancelAll() {
        notificationManager.cancelAll()
        notificationIdMap.clear()
        Log.d("CrisisOS_Notif", "All notifications cancelled")
    }

    // --- Builder helpers ---
    fun buildBaseBuilder(channelId: String, priority: NotificationEventPriority): NotificationCompat.Builder {
        val androidPriority = when (priority) {
            NotificationEventPriority.CRITICAL -> NotificationCompat.PRIORITY_MAX
            NotificationEventPriority.HIGH -> NotificationCompat.PRIORITY_HIGH
            NotificationEventPriority.DEFAULT -> NotificationCompat.PRIORITY_DEFAULT
            NotificationEventPriority.LOW -> NotificationCompat.PRIORITY_LOW
        }
        return NotificationCompat.Builder(context, channelId)
            .setPriority(androidPriority)
            .setSmallIcon(android.R.drawable.ic_dialog_info)  // replace with app icon in follow-up
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
    }

    fun getOrCreateNotificationId(key: String): Int = getOrCreateId(key)
    fun getOrCreateSummaryId(groupKey: String): Int = getSummaryId(groupKey)
}
