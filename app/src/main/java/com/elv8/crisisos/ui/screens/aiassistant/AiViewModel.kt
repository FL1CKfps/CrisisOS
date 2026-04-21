package com.elv8.crisisos.ui.screens.aiassistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elv8.crisisos.domain.model.AiMessage
import com.elv8.crisisos.domain.model.AiRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AiUiState(
    val messages: List<AiMessage> = emptyList(),
    val inputText: String = "",
    val isProcessing: Boolean = false,
    val isOffline: Boolean = true
)

@HiltViewModel
class AiViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(
        AiUiState(
            messages = listOf(
                AiMessage(
                    role = AiRole.SYSTEM,
                    content = "Running on-device. No data leaves your phone."
                )
            )
        )
    )
    val uiState: StateFlow<AiUiState> = _uiState.asStateFlow()

    private val fakeResponses = listOf(
        "Apply direct pressure to the wound with a clean cloth. If bleeding is severe and from a limb, consider a tourniquet above the wound.",
        "Boil water for at least 1 minute. If boiling is not possible, use chemical purification tablets or a 0.1 micron water filter.",
        "Move to higher ground immediately. Avoid walking or driving through flood waters. Follow the designated evacuation routes broadcasted on the mesh network.",
        "Use the SOS broadcast feature in this app to alert nearby mesh nodes. If you have cellular signal, dial 911 or your local emergency number.",
        "To signal for rescue, make yourself visible. Use a mirror or reflective object to flash sunlight, create three signal fires in a triangle, or use the app's flashing screen SOS mode."
    )

    fun updateInput(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage(forcedText: String? = null) {
        val messageContent = forcedText ?: _uiState.value.inputText
        if (messageContent.isBlank() || _uiState.value.isProcessing) return

        val userMessage = AiMessage(role = AiRole.USER, content = messageContent.trim())
        
        _uiState.update { state ->
            state.copy(
                messages = state.messages + userMessage,
                inputText = "",
                isProcessing = true
            )
        }

        viewModelScope.launch {
            // "Thinking" delay
            delay(1500)

            val responseText = fakeResponses.random()
            val words = responseText.split(" ")
            
            val assistantMessage = AiMessage(
                role = AiRole.ASSISTANT,
                content = "",
                isStreaming = true
            )

            // Add empty streaming message
            _uiState.update { state ->
                state.copy(messages = state.messages + assistantMessage)
            }

            // Fake token streaming
            var streamContent = ""
            for (word in words) {
                delay((50..120).random().toLong()) // Randomize for realism
                streamContent += "$word "
                
                _uiState.update { state ->
                    val updatedMessages = state.messages.map {
                        if (it.id == assistantMessage.id) {
                            it.copy(content = streamContent)
                        } else {
                            it
                        }
                    }
                    state.copy(messages = updatedMessages)
                }
            }

            // Finalize streaming
            _uiState.update { state ->
                val finalMessages = state.messages.map {
                    if (it.id == assistantMessage.id) {
                        it.copy(content = streamContent.trim(), isStreaming = false)
                    } else {
                        it
                    }
                }
                state.copy(messages = finalMessages, isProcessing = false)
            }
        }
    }
}