package com.elv8.crisisos.ui.screens.chatv2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elv8.crisisos.domain.model.chat.ChatThread
import com.elv8.crisisos.domain.repository.ConnectionRequestRepository
import com.elv8.crisisos.domain.repository.ThreadChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.elv8.crisisos.core.notification.NotificationHandler
import kotlinx.coroutines.delay

data class ChatListUiState(
    val threads: List<ChatThread> = emptyList(),
    val pendingRequestCount: Int = 0,
    val searchQuery: String = "",
    val filteredThreads: List<ChatThread> = emptyList()
)

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val messageRequestRepository: com.elv8.crisisos.domain.repository.MessageRequestRepository,
    private val threadChatRepository: ThreadChatRepository,
    private val connectionRequestRepository: ConnectionRequestRepository,
    private val notificationHandler: NotificationHandler
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")

    val uiState: StateFlow<ChatListUiState> = combine(
        threadChatRepository.getAllThreads(),
        connectionRequestRepository.getIncomingRequests(),
        messageRequestRepository.getPendingRequests(),
        _searchQuery
    ) { threads, requests, messages, query ->
        val filtered = threads.filter { thread ->
            if (query.isNotBlank() && !thread.displayName.contains(query, ignoreCase = true)) return@filter false
            if (!com.elv8.crisisos.core.debug.MeshDebugConfig.ENABLE_MOCK_PEER_INJECTION && thread.isMock) return@filter false
            true
        }
        ChatListUiState(
            threads = threads,
            pendingRequestCount = requests.size + messages.size,
            searchQuery = query,
            filteredThreads = filtered
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ChatListUiState()
    )

    init {
        viewModelScope.launch {
            // Small delay to let the screen render first
            delay(500)
            notificationHandler.clearChatGroupSummary()
            // Note: individual thread notifications are NOT cancelled — only the summary
            android.util.Log.d("CrisisOS_ChatList", "Chat group summary cleared on list open")
        }
    }

    fun markThreadsAsRead(threadIds: List<String>) {
        threadIds.forEach { threadId ->
            notificationHandler.clearNotificationsForThread(threadId)
        }
    }

    fun updateSearch(query: String) {
        _searchQuery.value = query
    }

    fun pinThread(threadId: String, pinned: Boolean) {
        viewModelScope.launch {
            threadChatRepository.pinThread(threadId, pinned)
        }
    }

    fun deleteThread(threadId: String) {
        viewModelScope.launch {
            threadChatRepository.deleteThread(threadId)
        }
    }
}
