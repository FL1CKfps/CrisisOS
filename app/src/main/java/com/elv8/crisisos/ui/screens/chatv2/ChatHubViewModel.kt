package com.elv8.crisisos.ui.screens.chatv2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elv8.crisisos.domain.repository.ConnectionRequestRepository
import com.elv8.crisisos.domain.repository.MessageRequestRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ChatHubViewModel @Inject constructor(
    private val messageRequestRepository: MessageRequestRepository,
    private val connectionRequestRepository: ConnectionRequestRepository
) : ViewModel() {

    val activeTab: MutableStateFlow<Int> = MutableStateFlow(0)

    val totalRequestCount: StateFlow<Int> = combine(
        messageRequestRepository.getPendingRequests(),
        connectionRequestRepository.getIncomingRequests()
    ) { messages, connections ->
        messages.size + connections.size
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    fun setTab(index: Int) {
        activeTab.value = index
    }
}
