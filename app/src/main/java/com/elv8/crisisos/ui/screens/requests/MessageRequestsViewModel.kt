package com.elv8.crisisos.ui.screens.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elv8.crisisos.domain.model.chat.MessageRequest
import com.elv8.crisisos.domain.model.connection.ConnectionRequest
import com.elv8.crisisos.domain.repository.ConnectionRequestRepository
import com.elv8.crisisos.domain.repository.MessageRequestRepository
import com.elv8.crisisos.domain.repository.AcceptResult as ConnectionAcceptResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.util.Log
import com.elv8.crisisos.core.notification.NotificationHandler

enum class RequestsTab { MESSAGES, CONNECTIONS }

sealed class RequestSuccessEvent {
    data class MessageAccepted(val threadId: String) : RequestSuccessEvent()
    object MessageRejected : RequestSuccessEvent()
    data class ConnectionAccepted(val threadId: String) : RequestSuccessEvent()
    object ConnectionRejected : RequestSuccessEvent()
}

data class MessageRequestsUiState(
    val messageRequests: List<MessageRequest> = emptyList(),
    val connectionRequests: List<ConnectionRequest> = emptyList(),
    val activeTab: RequestsTab = RequestsTab.MESSAGES,
    val processingId: String? = null,
    val successEvent: RequestSuccessEvent? = null
)

@HiltViewModel
class MessageRequestsViewModel @Inject constructor(
    private val messageRequestRepository: MessageRequestRepository,
    private val connectionRequestRepository: ConnectionRequestRepository,
    private val notificationHandler: NotificationHandler
) : ViewModel() {

    private val _uiState = MutableStateFlow(MessageRequestsUiState())
    val uiState: StateFlow<MessageRequestsUiState> = _uiState.asStateFlow()

    init {
        notificationHandler.suppressGroup("group_requests")
        Log.d("CrisisOS_Requests", "Request notifications suppressed — user on requests screen")
        
        viewModelScope.launch {
            combine(
                messageRequestRepository.getPendingRequests(),
                connectionRequestRepository.getIncomingRequests()
            ) { messages, connections ->
                Pair(messages, connections)
            }.collect { (messages, connections) ->
                _uiState.update { 
                    it.copy(
                        messageRequests = messages,
                        connectionRequests = connections
                    ) 
                }
            }
        }
    }

    fun setTab(tab: RequestsTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun acceptMessageRequest(requestId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(processingId = requestId) }
            val result = messageRequestRepository.acceptRequest(requestId)
            when (result) {
                is MessageRequestRepository.AcceptResult.Success -> {
                    _uiState.update { 
                        it.copy(processingId = null, successEvent = RequestSuccessEvent.MessageAccepted(result.threadId)) 
                    }
                }
                is MessageRequestRepository.AcceptResult.Error -> {
                    _uiState.update { it.copy(processingId = null) }
                }
            }
        }
    }

    fun rejectMessageRequest(requestId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(processingId = requestId) }
            messageRequestRepository.rejectRequest(requestId)
            _uiState.update { 
                it.copy(processingId = null, successEvent = RequestSuccessEvent.MessageRejected) 
            }
        }
    }

    fun acceptConnectionRequest(requestId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(processingId = requestId) }
            val result = connectionRequestRepository.acceptRequest(requestId)
            when (result) {
                is ConnectionAcceptResult.Success -> {
                    _uiState.update { 
                        it.copy(processingId = null, successEvent = RequestSuccessEvent.ConnectionAccepted(result.threadId)) 
                    }
                }
                is ConnectionAcceptResult.Error -> {
                    _uiState.update { it.copy(processingId = null) }
                }
            }
        }
    }

    fun rejectConnectionRequest(requestId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(processingId = requestId) }
            connectionRequestRepository.rejectRequest(requestId)
            _uiState.update { 
                it.copy(processingId = null, successEvent = RequestSuccessEvent.ConnectionRejected) 
            }
        }
    }

    fun clearSuccessEvent() {
        _uiState.update { it.copy(successEvent = null) }
    }

    override fun onCleared() {
        super.onCleared()
        notificationHandler.unsuppressGroup("group_requests")
        Log.d("CrisisOS_Requests", "Request notifications unsuppressed")
    }
}
