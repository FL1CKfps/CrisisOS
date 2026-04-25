package com.elv8.crisisos.ui.screens.aiassistant

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elv8.crisisos.core.ai.AiContextGatherer
import com.elv8.crisisos.core.ai.GemmaInference
import com.elv8.crisisos.core.ai.ResponseCompletion
import com.elv8.crisisos.domain.model.AiAction
import com.elv8.crisisos.domain.model.AiMessage
import com.elv8.crisisos.domain.model.AiRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AiUiState(
    val messages: List<AiMessage> = emptyList(),
    val inputText: String = "",
    val attachedImageUri: Uri? = null,
    val isProcessing: Boolean = false,
    val isThinking: Boolean = false,
    val isOffline: Boolean = true,
    val isModelLoaded: Boolean = false,
    val isModelLoading: Boolean = false,
    val downloadProgress: Float? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class AiViewModel @Inject constructor(
    private val gemma: GemmaInference,
    private val contextGatherer: AiContextGatherer
) : ViewModel() {
    private var activeAssistantMessageId: String? = null
    private var activeAssistantAccumulatedText: String = ""
    private var responseJob: Job? = null

    private val _uiState = MutableStateFlow(
        AiUiState(
            messages = listOf(
                AiMessage(
                    role = AiRole.SYSTEM,
                    content = "CrisisOS AI is powered by Gemma 4 E2B. All inference happens locally on your device."
                )
            )
        )
    )
    val uiState: StateFlow<AiUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(isModelLoading = true, errorMessage = null) }
            gemma.loadModel()
            val loaded = gemma.isModelLoaded()
            _uiState.update { 
                it.copy(
                    isModelLoaded = loaded, 
                    isModelLoading = false,
                    errorMessage = if (!loaded && gemma.isModelDownloaded()) "Failed to initialize engine. The model shard may be incompatible or corrupted." else null
                )
            }
        }

        viewModelScope.launch {
            gemma.downloadProgress.collect { progress ->
                _uiState.update { it.copy(downloadProgress = progress) }
            }
        }

        // Handle partial result streaming from Gemma
        viewModelScope.launch {
            gemma.partialResults.collect { partial ->
                if (partial.isEmpty()) return@collect

                _uiState.update { it.copy(isThinking = false) }

                if (activeAssistantMessageId == null) {
                    val newMsg = AiMessage(
                        role = AiRole.ASSISTANT,
                        content = partial,
                        isStreaming = true
                    )
                    activeAssistantMessageId = newMsg.id
                    activeAssistantAccumulatedText = partial
                    _uiState.update { state -> state.copy(messages = state.messages + newMsg) }
                } else {
                    activeAssistantAccumulatedText += partial
                    val targetId = activeAssistantMessageId
                    _uiState.update { state ->
                        val updated = state.messages.map {
                            if (it.id == targetId) it.copy(content = activeAssistantAccumulatedText) else it
                        }
                        state.copy(messages = updated)
                    }
                }
            }
        }

        viewModelScope.launch {
            gemma.responseCompleted.collect { completion ->
                val targetId = activeAssistantMessageId
                _uiState.update { state ->
                    var updatedMessages = state.messages
                    if (targetId != null) {
                        updatedMessages = updatedMessages.map {
                            if (it.id == targetId) {
                                val finalContent = activeAssistantAccumulatedText.trim()
                                it.copy(
                                    content = finalContent,
                                    isStreaming = false,
                                    actions = parseActions(finalContent)
                                )
                            } else it
                        }
                    } else if (completion == ResponseCompletion.FAILED) {
                        updatedMessages = updatedMessages + AiMessage(
                            role = AiRole.ASSISTANT,
                            content = "I could not generate a local response. Please try again."
                        )
                    }

                    state.copy(
                        messages = updatedMessages,
                        isProcessing = false,
                        isThinking = false,
                        isModelLoaded = gemma.isModelLoaded()
                    )
                }

                activeAssistantMessageId = null
                activeAssistantAccumulatedText = ""
            }
        }
    }

    private fun parseActions(text: String): List<AiAction> {
        val actions = mutableListOf<AiAction>()
        val regex = Regex("\\[ACTION:([A-Z_]+)(?:\\?([^]]+))?]")
        regex.findAll(text).forEach { match ->
            val feature = match.groupValues[1]
            val paramsString = match.groupValues.getOrNull(2)
            val params = mutableMapOf<String, String>()
            
            paramsString?.split("&")?.forEach { pair ->
                val parts = pair.split("=")
                if (parts.size == 2) {
                    params[parts[0]] = parts[1]
                }
            }
            
            actions.add(AiAction(feature, params))
        }
        return actions
    }

    fun updateInput(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun attachImage(uri: Uri?) {
        _uiState.update { it.copy(attachedImageUri = uri) }
    }

    fun clearAttachedImage() {
        _uiState.update { it.copy(attachedImageUri = null) }
    }

    fun sendMessage(forcedText: String? = null) {
        val currentState = _uiState.value
        val messageContent = (forcedText ?: currentState.inputText).trim()
        val attachedImage = currentState.attachedImageUri

        if (currentState.isProcessing) return
        if (messageContent.isBlank() && attachedImage == null) return

        if (!currentState.isModelLoaded) {
            _uiState.update { state ->
                state.copy(
                    messages = state.messages + AiMessage(
                        role = AiRole.SYSTEM,
                        content = "Model is not ready. Tap download to install Gemma 4 E2B first."
                    )
                )
            }
            return
        }

        val userMessageContent = buildString {
            if (messageContent.isNotBlank()) {
                append(messageContent)
            }
            if (attachedImage != null) {
                if (isNotEmpty() && !endsWith("\n")) append("\n\n")
                append("[Image attached]")
            }
        }.ifBlank { "[Image attached]" }

        val userMessage = AiMessage(role = AiRole.USER, content = userMessageContent)

        _uiState.update { state ->
            state.copy(
                messages = state.messages + userMessage,
                inputText = "",
                attachedImageUri = null,
                isProcessing = true,
                isThinking = true
            )
        }

        activeAssistantMessageId = null
        activeAssistantAccumulatedText = ""
        responseJob?.cancel()

        responseJob = viewModelScope.launch {
            val userContext = contextGatherer.gather()
            gemma.generateResponse(messageContent, attachedImage, userContext)
        }
    }

    fun stopResponse() {
        if (!_uiState.value.isProcessing) return

        responseJob?.cancel()
        gemma.stopGeneration()

        val targetId = activeAssistantMessageId
        _uiState.update { state ->
            val updatedMessages = if (targetId != null) {
                state.messages.map {
                    if (it.id == targetId) it.copy(isStreaming = false) else it
                }
            } else {
                state.messages
            }

            state.copy(messages = updatedMessages, isProcessing = false)
        }

        activeAssistantMessageId = null
        activeAssistantAccumulatedText = ""
    }

    fun downloadModel() {
        if (_uiState.value.isModelLoading || _uiState.value.downloadProgress != null) return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isModelLoading = true, errorMessage = null) }
            try {
                gemma.downloadModel()
            } catch (_: Exception) {
                // Error handled in repository
            } finally {
                val loaded = gemma.isModelLoaded()
                _uiState.update { 
                    it.copy(
                        isModelLoaded = loaded, 
                        isModelLoading = false,
                        errorMessage = if (!loaded && gemma.isModelDownloaded()) "Engine initialization failed. Your phone's RAM might be too low or the file is corrupt." else null
                    ) 
                }
            }
        }
    }

    fun deleteModel() {
        viewModelScope.launch {
            gemma.close()
            val deleted = gemma.deleteModelFile()
            _uiState.update { 
                it.copy(
                    isModelLoaded = false, 
                    errorMessage = if (deleted) "Model shard deleted. You can now retry the download." else "Failed to delete model shard."
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        responseJob?.cancel()
        gemma.close()
    }
}
