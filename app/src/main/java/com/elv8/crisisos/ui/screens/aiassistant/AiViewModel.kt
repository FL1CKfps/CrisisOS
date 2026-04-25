package com.elv8.crisisos.ui.screens.aiassistant

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.elv8.crisisos.core.ai.AiContextGatherer
import com.elv8.crisisos.core.ai.GemmaInference
import com.elv8.crisisos.core.ai.InferenceMetrics
import com.elv8.crisisos.core.ai.ResponseCompletion
import kotlinx.coroutines.flow.collect
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
import java.util.Locale
import javax.inject.Inject

enum class PromptCategory(val label: String) {
    GENERAL("General"),
    MEDICAL("Medical"),
    CHECKPOINT("Checkpoint"),
    LEGAL("Legal"),
    SURVIVAL("Survival"),
    SIGNAL("Signal & Rescue")
}

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
    val errorMessage: String? = null,
    val speakingMessageId: String? = null,
    val isListening: Boolean = false,
    val activeCategory: PromptCategory = PromptCategory.GENERAL,
    val lastMetrics: InferenceMetrics? = null
)

@HiltViewModel
class AiViewModel @Inject constructor(
    private val app: Application,
    private val gemma: GemmaInference,
    private val contextGatherer: AiContextGatherer
) : AndroidViewModel(app) {
    private var activeAssistantMessageId: String? = null
    private var activeAssistantAccumulatedText: String = ""
    private var responseJob: Job? = null
    private var lastUserPrompt: String? = null
    private var lastUserImage: Uri? = null

    private val _uiState = MutableStateFlow(
        AiUiState(
            messages = listOf(welcomeMessage())
        )
    )
    val uiState: StateFlow<AiUiState> = _uiState.asStateFlow()

    // ===== Text to speech (hands-free critical for checkpoint negotiation) =====
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        initTts()

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

        // Single completion path — metrics arrive WITH completion so they always
        // attach to the correct message even under load.
        viewModelScope.launch {
            gemma.responseCompleted.collect { completion ->
                val targetId = activeAssistantMessageId
                val metrics = (completion as? ResponseCompletion.Success)?.metrics

                _uiState.update { state ->
                    var updatedMessages = state.messages
                    if (targetId != null) {
                        updatedMessages = updatedMessages.map {
                            if (it.id == targetId) {
                                val finalContent = activeAssistantAccumulatedText.trim()
                                it.copy(
                                    content = finalContent,
                                    isStreaming = false,
                                    actions = parseActions(finalContent),
                                    firstTokenLatencyMs = metrics?.firstTokenLatencyMs,
                                    tokensPerSecond = metrics?.tokensPerSecond
                                )
                            } else it
                        }
                    } else if (completion is ResponseCompletion.Failed) {
                        updatedMessages = updatedMessages + AiMessage(
                            role = AiRole.ASSISTANT,
                            content = "I could not generate a local response. Please try again."
                        )
                    }

                    state.copy(
                        messages = updatedMessages,
                        isProcessing = false,
                        isThinking = false,
                        isModelLoaded = gemma.isModelLoaded(),
                        lastMetrics = metrics ?: state.lastMetrics
                    )
                }

                activeAssistantMessageId = null
                activeAssistantAccumulatedText = ""
            }
        }
    }

    private fun initTts() {
        tts = TextToSpeech(app.applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                runCatching { tts?.language = Locale.getDefault() }
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _uiState.update { it.copy(speakingMessageId = utteranceId) }
                    }

                    override fun onDone(utteranceId: String?) {
                        _uiState.update {
                            if (it.speakingMessageId == utteranceId) it.copy(speakingMessageId = null) else it
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _uiState.update {
                            if (it.speakingMessageId == utteranceId) it.copy(speakingMessageId = null) else it
                        }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        _uiState.update {
                            if (it.speakingMessageId == utteranceId) it.copy(speakingMessageId = null) else it
                        }
                    }
                })
            } else {
                Log.w("CrisisOS_AI_TTS", "TextToSpeech failed to initialize: $status")
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

    fun selectCategory(category: PromptCategory) {
        _uiState.update { it.copy(activeCategory = category) }
    }

    fun setListening(listening: Boolean) {
        _uiState.update { it.copy(isListening = listening) }
    }

    fun appendVoiceTranscript(transcript: String) {
        if (transcript.isBlank()) return
        _uiState.update { state ->
            val joiner = if (state.inputText.isBlank() || state.inputText.endsWith(" ")) "" else " "
            state.copy(
                inputText = state.inputText + joiner + transcript,
                isListening = false
            )
        }
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

        lastUserPrompt = messageContent
        lastUserImage = attachedImage

        runGeneration(messageContent, attachedImage)
    }

    private fun runGeneration(messageContent: String, attachedImage: Uri?) {
        activeAssistantMessageId = null
        activeAssistantAccumulatedText = ""
        responseJob?.cancel()

        responseJob = viewModelScope.launch {
            val userContext = contextGatherer.gather()
            gemma.generateResponse(messageContent, attachedImage, userContext)
        }
    }

    /**
     * Re-runs the last user prompt. Removes the most recent assistant turn first
     * so the new answer replaces the old one.
     */
    fun regenerateLastResponse() {
        val state = _uiState.value
        if (state.isProcessing) return
        val prompt = lastUserPrompt ?: return
        if (!state.isModelLoaded) return

        // Drop the trailing ASSISTANT message (if present) so the new one takes its place.
        val trimmed = if (state.messages.lastOrNull()?.role == AiRole.ASSISTANT) {
            state.messages.dropLast(1)
        } else state.messages

        _uiState.update {
            it.copy(
                messages = trimmed,
                isProcessing = true,
                isThinking = true
            )
        }

        runGeneration(prompt, lastUserImage)
    }

    /**
     * Reset the chat surface and the underlying Gemma conversation memory.
     * The engine stays warm so the next message is still fast.
     *
     * Waits for any in-flight generation to fully cancel BEFORE recreating the
     * native Conversation — this avoids racing LiteRT-LM cancellation with
     * conversation.close() / createConversation().
     */
    fun clearConversation() {
        val wasProcessing = _uiState.value.isProcessing
        if (wasProcessing) stopResponse()
        stopSpeaking()

        viewModelScope.launch {
            // Ensure the previous response coroutine has actually finished before we
            // tear down the native conversation object.
            runCatching { responseJob?.join() }
            val ok = gemma.resetConversation()
            lastUserPrompt = null
            lastUserImage = null
            _uiState.update {
                it.copy(
                    messages = listOf(welcomeMessage(reset = true, success = ok)),
                    inputText = "",
                    attachedImageUri = null,
                    isProcessing = false,
                    isThinking = false,
                    lastMetrics = null
                )
            }
        }
    }

    /** Copy a message to the system clipboard. */
    fun copyMessage(message: AiMessage) {
        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("CrisisOS AI", message.content))
    }

    /**
     * Read an assistant message aloud (hands-free for checkpoint negotiation, while driving, etc.).
     * Strips markdown and action tags so it sounds natural.
     */
    fun speakMessage(message: AiMessage) {
        val engine = tts ?: return
        if (!ttsReady) return

        val current = _uiState.value.speakingMessageId
        if (current == message.id) {
            stopSpeaking()
            return
        }

        // Always stop any ongoing speech first
        engine.stop()

        val cleaned = stripForSpeech(message.content)
        if (cleaned.isBlank()) return

        engine.speak(cleaned, TextToSpeech.QUEUE_FLUSH, null, message.id)
    }

    fun stopSpeaking() {
        tts?.stop()
        _uiState.update { it.copy(speakingMessageId = null) }
    }

    private fun stripForSpeech(raw: String): String {
        return raw
            .replace(Regex("\\[ACTION:[^]]+]"), "")
            .replace(Regex("```[\\s\\S]*?```"), " code block ")
            .replace(Regex("`([^`]*)`"), "$1")
            .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
            .replace(Regex("\\*([^*]+)\\*"), "$1")
            .replace(Regex("^#+\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("^[-•]\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("\\s+"), " ")
            .trim()
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

            state.copy(messages = updatedMessages, isProcessing = false, isThinking = false)
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

    private fun welcomeMessage(reset: Boolean = false, success: Boolean = true): AiMessage {
        val content = when {
            reset && success -> "New conversation started. The AI's memory has been cleared."
            reset && !success -> "Conversation cleared. (Engine memory could not be fully reset.)"
            else -> "CrisisOS AI is powered by Gemma 4 E2B. All inference happens locally on your device."
        }
        return AiMessage(role = AiRole.SYSTEM, content = content)
    }

    override fun onCleared() {
        super.onCleared()
        responseJob?.cancel()
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        gemma.close()
    }
}
