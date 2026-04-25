package com.elv8.crisisos.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elv8.crisisos.domain.model.chat.ChatThread
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
    private val threadChatRepository: ThreadChatRepository,
    private val notificationHandler: NotificationHandler
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")

    val uiState: StateFlow<ChatListUiState> = combine(
        threadChatRepository.getAllThreads(),
        _searchQuery
    ) { threads, query ->
        val filtered = threads.filter { thread ->
            if (query.isNotBlank() && !thread.displayName.contains(query, ignoreCase = true)) return@filter false
            if (!com.elv8.crisisos.core.debug.MeshDebugConfig.ENABLE_MOCK_PEER_INJECTION && thread.isMock) return@filter false
            true
        }
        ChatListUiState(
            threads = threads,
            pendingRequestCount = 0,
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
            delay(500)
            notificationHandler.clearChatGroupSummary()
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
