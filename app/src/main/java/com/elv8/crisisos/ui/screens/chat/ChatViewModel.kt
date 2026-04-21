package com.elv8.crisisos.ui.screens.chat

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elv8.crisisos.data.mesh.MeshConnectionManager
import com.elv8.crisisos.domain.model.ChatMessage
import com.elv8.crisisos.domain.model.MessageStatus
import com.elv8.crisisos.domain.model.MessageType
import com.elv8.crisisos.domain.repository.MeshRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val peersOnline: Int = 0,
    val isConnected: Boolean = false
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: MeshRepository,
    private val connectionManager: MeshConnectionManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            chatRepository.getMessages().collect { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }

        viewModelScope.launch {
            chatRepository.observeIncomingMessages().collect { /* Room insert triggers getMessages() flow */ }
        }

        viewModelScope.launch {
            connectionManager.peerCount.collect { count ->
                _uiState.update { it.copy(peersOnline = count, isConnected = count > 0) }
            }
        }
    }

    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun clearInput() {
        _uiState.update { it.copy(inputText = "") }
    }

    fun sendMessage() {
        val currentText = _uiState.value.inputText
        if (currentText.isBlank()) return

        val newMessageId = UUID.randomUUID().toString()
        val sharedPrefs = context.getSharedPreferences("crisisos_prefs", Context.MODE_PRIVATE)
        val alias = sharedPrefs.getString("user_alias", "Survivor_${Build.MODEL}") ?: "Survivor_${Build.MODEL}"
        val senderId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: UUID.randomUUID().toString()

        val newMessage = ChatMessage(
            id = newMessageId,
            senderId = senderId,
            senderAlias = alias,
            content = currentText,
            timestamp = System.currentTimeMillis(),
            deliveryStatus = MessageStatus.SENDING,
            hopsCount = 0,
            isOwn = true,
            messageType = MessageType.TEXT
        )

        _uiState.update { it.copy(inputText = "") }

        viewModelScope.launch {
            chatRepository.sendMessage(newMessage)
        }
    }
}
