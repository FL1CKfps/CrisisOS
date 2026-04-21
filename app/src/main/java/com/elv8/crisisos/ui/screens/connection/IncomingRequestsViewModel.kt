package com.elv8.crisisos.ui.screens.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elv8.crisisos.domain.model.connection.ConnectionRequest
import com.elv8.crisisos.domain.repository.AcceptResult
import com.elv8.crisisos.domain.repository.ConnectionRequestRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RequestTab { INCOMING, OUTGOING }

data class IncomingRequestsUiState(
    val incomingRequests: List<ConnectionRequest> = emptyList(),
    val outgoingRequests: List<ConnectionRequest> = emptyList(),
    val activeTab: RequestTab = RequestTab.INCOMING,
    val processingRequestId: String? = null,
    val successMessage: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class IncomingRequestsViewModel @Inject constructor(
    private val connectionRequestRepository: ConnectionRequestRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(IncomingRequestsUiState())
    val uiState: StateFlow<IncomingRequestsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                connectionRequestRepository.getIncomingRequests(),
                connectionRequestRepository.getOutgoingRequests()
            ) { incoming, outgoing ->
                Pair(incoming, outgoing)
            }.collect { (incoming, outgoing) ->
                _uiState.update {
                    it.copy(
                        incomingRequests = incoming,
                        outgoingRequests = outgoing
                    )
                }
            }
        }
    }

    fun setActiveTab(tab: RequestTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun acceptRequest(requestId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(processingRequestId = requestId) }
            val result = connectionRequestRepository.acceptRequest(requestId)
            when (result) {
                is AcceptResult.Success -> {
                    _uiState.update {
                        it.copy(
                            processingRequestId = null,
                            successMessage = "Connection established! You can now chat."
                        )
                    }
                }
                is AcceptResult.Error -> {
                    _uiState.update {
                        it.copy(
                            processingRequestId = null,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun rejectRequest(requestId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(processingRequestId = requestId) }
            connectionRequestRepository.rejectRequest(requestId)
            _uiState.update {
                it.copy(
                    processingRequestId = null,
                    successMessage = "Request declined."
                )
            }
        }
    }

    fun cancelOutgoingRequest(requestId: String) {
        viewModelScope.launch {
            connectionRequestRepository.cancelRequest(requestId)
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(successMessage = null, errorMessage = null) }
    }
}
