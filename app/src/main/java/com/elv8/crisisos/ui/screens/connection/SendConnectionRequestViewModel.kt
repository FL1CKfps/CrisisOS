package com.elv8.crisisos.ui.screens.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elv8.crisisos.domain.model.peer.Peer
import com.elv8.crisisos.domain.repository.ConnectionRequestRepository
import com.elv8.crisisos.domain.repository.PeerRepository
import com.elv8.crisisos.domain.repository.SendRequestResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SendRequestUiState(
    val peer: Peer? = null,
    val messageText: String = "",
    val isSending: Boolean = false,
    val result: SendRequestResult? = null,
    val charCount: Int = 0
)

@HiltViewModel
class SendConnectionRequestViewModel @Inject constructor(
    private val connectionRequestRepository: ConnectionRequestRepository,
    private val peerRepository: PeerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SendRequestUiState())
    val uiState: StateFlow<SendRequestUiState> = _uiState.asStateFlow()

    fun loadPeer(crsId: String) {
        viewModelScope.launch {
            val peer = peerRepository.getPeer(crsId)
            _uiState.update { it.copy(peer = peer) }
        }
    }

    fun updateMessage(text: String) {
        if (text.length > 120) return
        _uiState.update { it.copy(messageText = text, charCount = text.length) }
    }

    fun sendRequest() {
        val peer = _uiState.value.peer ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }
            val result = connectionRequestRepository.sendRequest(peer, _uiState.value.messageText)
            _uiState.update { it.copy(isSending = false, result = result) }
        }
    }

    fun clearResult() {
        _uiState.update { it.copy(result = null) }
    }
}

