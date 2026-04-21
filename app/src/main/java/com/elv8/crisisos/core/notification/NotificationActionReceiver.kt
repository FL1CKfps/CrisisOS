package com.elv8.crisisos.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.elv8.crisisos.core.debug.MeshLogger
import com.elv8.crisisos.domain.repository.ConnectionRequestRepository
import com.elv8.crisisos.domain.repository.MessageRequestRepository
import com.elv8.crisisos.domain.repository.AcceptResult
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("CrisisOS_NotifAction", "Action received: ${intent.action}")

        val action = intent.action ?: return
        val pendingResult = goAsync() // keep BroadcastReceiver alive for async work

        // Get Hilt-injected dependencies via EntryPointAccessors
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            NotificationActionEntryPoint::class.java
        )
        val connectionRequestRepository = entryPoint.connectionRequestRepository()
        val messageRequestRepository = entryPoint.messageRequestRepository()
        val notificationManager = entryPoint.notificationManagerWrapper()
        val scope = entryPoint.applicationScope()

        scope.launch {
            try {
                when (action) {
                    NotificationActions.ACTION_ACCEPT_CONNECTION -> {
                        val requestId = intent.getStringExtra("request_id") ?: return@launch
                        Log.i("CrisisOS_NotifAction", "Accepting connection request: $requestId")
                        val result = connectionRequestRepository.acceptRequest(requestId)
                        when (result) {
                            is AcceptResult.Success -> {
                                Log.i("CrisisOS_NotifAction", "Connection accepted — threadId=${result.threadId}")
                                notificationManager.cancel(
                                    notificationManager.getOrCreateNotificationId("conn_req_$requestId")
                                )
                            }
                            is AcceptResult.Error ->
                                Log.e("CrisisOS_NotifAction", "Accept failed: ${result.message}")
                        }
                    }

                    NotificationActions.ACTION_REJECT_CONNECTION -> {
                        val requestId = intent.getStringExtra("request_id") ?: return@launch
                        Log.i("CrisisOS_NotifAction", "Rejecting connection request: $requestId")
                        connectionRequestRepository.rejectRequest(requestId)
                        notificationManager.cancel(
                            notificationManager.getOrCreateNotificationId("conn_req_$requestId")
                        )
                    }

                    NotificationActions.ACTION_ACCEPT_MESSAGE_REQUEST -> {
                        val requestId = intent.getStringExtra("request_id") ?: return@launch
                        Log.i("CrisisOS_NotifAction", "Accepting message request: $requestId")
                        val result = messageRequestRepository.acceptRequest(requestId)
                        when (result) {
                            is MessageRequestRepository.AcceptResult.Success -> {
                                Log.i("CrisisOS_NotifAction", "Message request accepted — threadId=${result.threadId}")
                                notificationManager.cancel(
                                    notificationManager.getOrCreateNotificationId("msg_req_$requestId")
                                )
                            }
                            is MessageRequestRepository.AcceptResult.Error ->
                                Log.e("CrisisOS_NotifAction", "Message accept failed")
                        }
                    }

                    NotificationActions.ACTION_REJECT_MESSAGE_REQUEST -> {
                        val requestId = intent.getStringExtra("request_id") ?: return@launch
                        Log.i("CrisisOS_NotifAction", "Rejecting message request: $requestId")
                        messageRequestRepository.rejectRequest(requestId)
                        notificationManager.cancel(
                            notificationManager.getOrCreateNotificationId("msg_req_$requestId")
                        )
                    }

                    else -> Log.w("CrisisOS_NotifAction", "Unknown action: $action")
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}