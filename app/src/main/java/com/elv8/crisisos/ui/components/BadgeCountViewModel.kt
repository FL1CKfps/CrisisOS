package com.elv8.crisisos.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elv8.crisisos.domain.repository.ConnectionRequestRepository
import com.elv8.crisisos.domain.repository.MessageRequestRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class BadgeCountViewModel @Inject constructor(
    messageRequestRepository: MessageRequestRepository,
    connectionRequestRepository: ConnectionRequestRepository
) : ViewModel() {

    val totalPendingCount: StateFlow<Int> = combine(
        messageRequestRepository.getPendingRequests(),
        connectionRequestRepository.getIncomingRequests()
    ) { messages, connections ->
        messages.size + connections.size
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )
}
