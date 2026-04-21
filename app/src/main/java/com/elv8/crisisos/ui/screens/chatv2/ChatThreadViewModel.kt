package com.elv8.crisisos.ui.screens.chatv2

import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elv8.crisisos.domain.model.chat.ChatThread
import com.elv8.crisisos.domain.model.chat.Message
import com.elv8.crisisos.domain.repository.IdentityRepository
import com.elv8.crisisos.domain.repository.ThreadChatRepository
import com.elv8.crisisos.domain.repository.MediaRepository
import com.elv8.crisisos.data.media.MediaPickerHelper
import com.elv8.crisisos.data.media.VoiceRecorder
import com.elv8.crisisos.data.media.RecordingState
import com.elv8.crisisos.domain.model.media.MediaPickResult
import com.elv8.crisisos.domain.model.media.PickError
import com.elv8.crisisos.domain.model.media.MediaAttachment
import com.elv8.crisisos.domain.repository.SendMessageResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import javax.inject.Inject
import java.util.UUID
import com.elv8.crisisos.core.notification.NotificationHandler

data class ChatThreadUiState(
    val thread: ChatThread? = null,
    val messages: List<Message> = emptyList(),
    val inputText: String = "",
    val isTyping: Boolean = false,
    val replyingTo: Message? = null,
    val localCrsId: String = "",
    val isSending: Boolean = false,
    val pendingAttachment: MediaAttachment? = null,
    val isPickingMedia: Boolean = false,
    val isRecording: Boolean = false,
    val recordingDurationMs: Long = 0L,
    val recordingAmplitude: Float = 0f,
    val isSendingMedia: Boolean = false,
    val mediaErrorMessage: String? = null,
    val showAttachmentPreview: Boolean = false,
    val playingAudioId: String? = null
)

@HiltViewModel
class ChatThreadViewModel @Inject constructor(
    private val threadChatRepository: ThreadChatRepository,
    private val identityRepository: IdentityRepository,
    private val notificationHandler: NotificationHandler,
    private val mediaRepository: MediaRepository,
    private val mediaPickerHelper: MediaPickerHelper,
    private val voiceRecorder: VoiceRecorder,
    private val audioPlayer: com.elv8.crisisos.data.media.AudioPlayer,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val threadId: String = savedStateHandle.get<String>("threadId") ?: ""

    private val _uiState = MutableStateFlow(ChatThreadUiState())
    val uiState: StateFlow<ChatThreadUiState> = _uiState.asStateFlow()

    private var typingJob: Job? = null

    init {
        viewModelScope.launch {
            audioPlayer.playbackState.collect { state ->
                when (state) {
                    is com.elv8.crisisos.data.media.AudioPlaybackState.Playing -> {
                        _uiState.update { it.copy(playingAudioId = state.mediaId) }
                    }
                    is com.elv8.crisisos.data.media.AudioPlaybackState.Paused -> {
                        _uiState.update { it.copy(playingAudioId = null) }
                    }
                    is com.elv8.crisisos.data.media.AudioPlaybackState.Completed -> {
                        _uiState.update { it.copy(playingAudioId = null) }
                    }
                    is com.elv8.crisisos.data.media.AudioPlaybackState.Error -> {
                        _uiState.update { it.copy(playingAudioId = null, mediaErrorMessage = state.message) }
                    }
                    com.elv8.crisisos.data.media.AudioPlaybackState.Idle -> {
                        _uiState.update { it.copy(playingAudioId = null) }
                    }
                }
            }
        }
        
        if (threadId.isNotBlank()) {
            notificationHandler.suppressThread(threadId)
            Log.d("CrisisOS_ChatThread", "Notification suppression ON for thread: $threadId")
        }
        viewModelScope.launch {
            if (threadId.isNotBlank()) {
                notificationHandler.clearNotificationsForThread(threadId)
            }
            _uiState.update { it.copy(thread = threadChatRepository.getThread(threadId)) }
            threadChatRepository.markThreadRead(threadId)
        }

        viewModelScope.launch {
            threadChatRepository.getMessagesForThread(threadId).collect { msgs ->
                _uiState.update { it.copy(messages = msgs) }
            }
        }

        viewModelScope.launch {
            identityRepository.getIdentity().collect { identity ->
                _uiState.update { it.copy(localCrsId = identity?.crsId ?: "") }
            }
        }

        viewModelScope.launch {
            voiceRecorder.state.collect { recState ->
                when (recState) {
                    is RecordingState.Idle -> {
                        _uiState.update { it.copy(isRecording = false, recordingDurationMs = 0L, recordingAmplitude = 0f) }
                    }
                    is RecordingState.Recording -> {
                        _uiState.update { it.copy(
                            isRecording = true,
                            recordingDurationMs = recState.durationMs,
                            recordingAmplitude = recState.amplitudeLevel
                        )}
                    }
                    is RecordingState.Completed -> {
                        _uiState.update { it.copy(isRecording = false) }
                    }
                    is RecordingState.Error -> {
                        _uiState.update { it.copy(
                            isRecording = false,
                            mediaErrorMessage = recState.message
                        )}
                    }
                }
            }
        }
    }

    fun updateInput(text: String) {
        val wasBlank = _uiState.value.inputText.isBlank()
        _uiState.update { it.copy(inputText = text) }

        if (text.isNotBlank()) {
            typingJob?.cancel()
            typingJob = viewModelScope.launch {
                // In actual deployment, emit typing broadcast here
                delay(10000)
                // Stop broadcast
            }
        } else {
            typingJob?.cancel()
        }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank()) return

        val replyingToId = _uiState.value.replyingTo?.messageId
        _uiState.update { it.copy(inputText = "", replyingTo = null, isSending = true) }

        viewModelScope.launch {
            threadChatRepository.sendMessage(threadId, text, replyingToId)
            _uiState.update { it.copy(isSending = false) }
        }
    }

    fun setReplyingTo(message: Message?) {
        _uiState.update { it.copy(replyingTo = message) }
    }

    fun clearReplyingTo() {
        _uiState.update { it.copy(replyingTo = null) }
    }

    fun onImagePicked(uri: Uri?) {
        if (uri == null) {
            _uiState.update { it.copy(isPickingMedia = false) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isPickingMedia = true, mediaErrorMessage = null) }
            val tId = savedStateHandle.get<String>("threadId") ?: return@launch
            val identity = identityRepository.getIdentity().first() ?: return@launch
            val messageId = UUID.randomUUID().toString()

            val result = mediaPickerHelper.processPickedImage(uri, tId, identity.crsId, messageId)
            when (result) {
                is MediaPickResult.Success -> {
                    _uiState.update { it.copy(isPickingMedia = false, isSendingMedia = true) }
                    val sendResult = threadChatRepository.sendMediaMessage(
                        threadId = tId,
                        mediaItem = result.mediaItem
                    )
                    _uiState.update { it.copy(
                        isSendingMedia = false,
                        mediaErrorMessage = if (sendResult is SendMessageResult.Error) sendResult.reason else null
                    )}
                }
                is MediaPickResult.Error -> {
                    val msg = when (result.reason) {
                        PickError.FILE_TOO_LARGE -> "File too large to send"
                        PickError.UNSUPPORTED_TYPE -> "Unsupported file type"
                        PickError.COMPRESSION_FAILED -> "Could not process image"
                        else -> "Failed to attach media"
                    }
                    _uiState.update { it.copy(isPickingMedia = false, mediaErrorMessage = msg) }
                }
                is MediaPickResult.Cancelled -> {
                    _uiState.update { it.copy(isPickingMedia = false) }
                }
            }
        }
    }

    fun onVideoPicked(uri: Uri?) {
        if (uri == null) {
            _uiState.update { it.copy(isPickingMedia = false) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isPickingMedia = true, mediaErrorMessage = null) }
            val tId = savedStateHandle.get<String>("threadId") ?: return@launch
            val identity = identityRepository.getIdentity().first() ?: return@launch
            val messageId = UUID.randomUUID().toString()

            val result = mediaPickerHelper.processPickedVideo(uri, tId, identity.crsId, messageId)
            when (result) {
                is MediaPickResult.Success -> {
                    _uiState.update { it.copy(isPickingMedia = false, isSendingMedia = true) }
                    val sendResult = threadChatRepository.sendMediaMessage(
                        threadId = tId,
                        mediaItem = result.mediaItem
                    )
                    _uiState.update { it.copy(
                        isSendingMedia = false,
                        mediaErrorMessage = if (sendResult is SendMessageResult.Error) sendResult.reason else null
                    )}
                }
                is MediaPickResult.Error -> {
                    val msg = when (result.reason) {
                        PickError.FILE_TOO_LARGE -> "File too large to send"
                        PickError.UNSUPPORTED_TYPE -> "Unsupported file type"
                        PickError.COMPRESSION_FAILED -> "Could not process video"
                        else -> "Failed to attach media"
                    }
                    _uiState.update { it.copy(isPickingMedia = false, mediaErrorMessage = msg) }
                }
                is MediaPickResult.Cancelled -> {
                    _uiState.update { it.copy(isPickingMedia = false) }
                }
            }
        }
    }

    fun sendPendingMediaMessage() {
        val attachment = _uiState.value.pendingAttachment ?: return
        val tId = savedStateHandle.get<String>("threadId") ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSendingMedia = true, showAttachmentPreview = false) }
            val result = threadChatRepository.sendMediaMessage(
                threadId = tId,
                mediaItem = attachment.mediaItem
            )
            _uiState.update { it.copy(
                isSendingMedia = false,
                pendingAttachment = null,
                mediaErrorMessage = if (result is SendMessageResult.Error) result.reason else null
            )}
        }
    }

    fun discardPendingAttachment() {
        viewModelScope.launch {
            _uiState.value.pendingAttachment?.let { attachment ->
                mediaRepository.deleteMedia(attachment.mediaItem.mediaId)
            }
            _uiState.update { it.copy(pendingAttachment = null, showAttachmentPreview = false) }
        }
    }

    fun startVoiceRecording() {
        val started = voiceRecorder.startRecording()
        if (!started) {
            _uiState.update { it.copy(mediaErrorMessage = "Could not start recording") }
        }
    }

    fun stopVoiceRecording() {
        val file = voiceRecorder.stopRecording() ?: return
        val tId = savedStateHandle.get<String>("threadId") ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSendingMedia = true) }
            val identity = identityRepository.getIdentity().first() ?: return@launch
            val messageId = UUID.randomUUID().toString()
            val mediaItem = mediaRepository.prepareAudioMessage(tId, identity.crsId, file, messageId)
            if (mediaItem != null) {
                threadChatRepository.sendMediaMessage(threadId = tId, mediaItem = mediaItem)
            }
            _uiState.update { it.copy(isSendingMedia = false) }
            voiceRecorder.reset()
        }
    }

    fun cancelVoiceRecording() {
        voiceRecorder.cancelRecording()
    }

    fun toggleAudioPlayback(id: String) {
        viewModelScope.launch {
            val currentState = audioPlayer.playbackState.value
            val isSameMedia = (currentState as? com.elv8.crisisos.data.media.AudioPlaybackState.Playing)?.mediaId == id ||
                              (currentState as? com.elv8.crisisos.data.media.AudioPlaybackState.Paused)?.mediaId == id ||
                              (currentState as? com.elv8.crisisos.data.media.AudioPlaybackState.Completed)?.mediaId == id
            
            if (isSameMedia) {
                when (currentState) {
                    is com.elv8.crisisos.data.media.AudioPlaybackState.Playing -> audioPlayer.pause()
                    is com.elv8.crisisos.data.media.AudioPlaybackState.Paused, is com.elv8.crisisos.data.media.AudioPlaybackState.Completed -> {
                        // We can fetch item to reuse the URI
                        val item = mediaRepository.getMediaItem(id)
                        val uri = item?.localUri ?: item?.remoteUri
                        if (uri != null) {
                            audioPlayer.play(id, uri)
                        } else {
                            _uiState.update { it.copy(mediaErrorMessage = "Audio file not resolved") }
                        }
                    }
                    else -> {}
                }
            } else {
                val item = mediaRepository.getMediaItem(id)
                val uriStr = item?.localUri ?: item?.remoteUri
                if (uriStr != null) {
                    val uri = if (uriStr.startsWith("content://") || uriStr.startsWith("file://")) {
                        android.net.Uri.parse(uriStr)
                    } else {
                        android.net.Uri.fromFile(java.io.File(uriStr))
                    }
                    
                    var isValid = false
                    var length = 0L
                    var path = uriStr

                    try {
                        if (uri.scheme == "content") {
                            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                                if (cursor.moveToFirst() && sizeIndex >= 0) {
                                    length = cursor.getLong(sizeIndex)
                                    isValid = length > 0
                                }
                            }
                            if (!isValid) { // Fallback stream check
                                context.contentResolver.openInputStream(uri)?.use { stream ->
                                    length = stream.available().toLong()
                                    isValid = length > 0
                                }
                            }
                        } else {
                            val file = if (uri.scheme == "file") {
                                java.io.File(uri.path!!)
                            } else {
                                java.io.File(uriStr)
                            }
                            path = file.absolutePath
                            isValid = file.exists() && file.length() > 0
                            length = file.length()
                        }
                    } catch (e: Exception) {
                        Log.e("CrisisOS_Audio", "URI Validation failed: ${e.message}")
                    }

                    Log.d("CrisisOS_Audio", "Preparing play for id=$id path=$path")

                    if (isValid) {
                        Log.d("CrisisOS_Audio", "File is valid. Size is $length bytes")
                        audioPlayer.play(id, uriStr)
                    } else {
                        Log.e("CrisisOS_Audio", "Audio not available or not downloaded. path=$path size=$length")
                        _uiState.update { it.copy(mediaErrorMessage = "Audio not available or not downloaded") }
                    }
                } else {
                    Log.e("CrisisOS_Audio", "No valid URI found for media $id")
                    _uiState.update { it.copy(mediaErrorMessage = "Audio URI not found") }
                }
            }
        }
    }

    fun clearMediaError() {
        _uiState.update { it.copy(mediaErrorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.stop()
        if (voiceRecorder.isRecording) voiceRecorder.cancelRecording()
        
        if (threadId.isNotBlank()) {
            notificationHandler.unsuppressThread(threadId)
            Log.d("CrisisOS_ChatThread", "Notification suppression OFF for thread: $threadId")
        }
    }
}
