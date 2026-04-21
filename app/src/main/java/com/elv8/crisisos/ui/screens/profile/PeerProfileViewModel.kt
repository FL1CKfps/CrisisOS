package com.elv8.crisisos.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elv8.crisisos.domain.model.media.MediaItem
import com.elv8.crisisos.domain.model.profile.PeerProfile
import com.elv8.crisisos.domain.repository.ContactRepository
import com.elv8.crisisos.domain.repository.IdentityRepository
import com.elv8.crisisos.domain.repository.MediaRepository
import com.elv8.crisisos.domain.repository.PeerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PeerProfileUiState(
    val profile: PeerProfile? = null,
    val sharedMedia: List<MediaItem> = emptyList(),
    val sharedMediaCount: Int = 0,
    val isLoading: Boolean = true,
    val threadId: String? = null,
    val isFromChatContext: Boolean = false,
    val selectedMediaItem: MediaItem? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class PeerProfileViewModel @Inject constructor(
    private val contactRepository: ContactRepository,
    private val peerRepository: PeerRepository,
    private val mediaRepository: MediaRepository,
    private val identityRepository: IdentityRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PeerProfileUiState())
    val uiState: StateFlow<PeerProfileUiState> = _uiState.asStateFlow()

    fun loadProfile(crsId: String, threadId: String?, isFromChat: Boolean) {
        viewModelScope.launch {
            _uiState.update { 
                it.copy(isLoading = true, threadId = threadId, isFromChatContext = isFromChat) 
            }

            // Check if this is local user's own profile
            val localIdentity = identityRepository.getIdentity().first()
            val isSelf = localIdentity?.crsId == crsId

            val profile: PeerProfile? = if (isSelf) {
                localIdentity?.let {
                    PeerProfile(
                        crsId = it.crsId,
                        alias = it.alias,
                        avatarColor = it.avatarColor,
                        trustLevel = null,
                        isContact = false,
                        isBlocked = false,
                        addedAt = null,
                        isSelf = true
                    )
                }
            } else {
                val contact = contactRepository.getContact(crsId)
                val peer = peerRepository.getPeer(crsId)
                val source = contact ?: peer
                if (source == null) {
                    null
                } else {
                    PeerProfile(
                        crsId = crsId,
                        alias = contact?.alias ?: peer?.alias ?: crsId,
                        avatarColor = contact?.avatarColor ?: peer?.avatarColor ?: 0,
                        trustLevel = contact?.trustLevel,
                        isContact = contact != null,
                        isBlocked = contact?.isBlocked ?: false,
                        addedAt = contact?.addedAt,
                        isSelf = false
                    )
                }
            }

            _uiState.update { it.copy(profile = profile, isLoading = false) }

            // Load shared media only in chat context
            if (threadId != null && isFromChat) {
                loadSharedMedia(threadId)
            }
        }
    }

    private fun loadSharedMedia(threadId: String) {
        viewModelScope.launch {
            mediaRepository.getSharedMediaForThread(threadId).collect { items ->
                _uiState.update { it.copy(sharedMedia = items) }
            }
        }
        viewModelScope.launch {
            mediaRepository.getSharedMediaCount(threadId).collect { count ->
                _uiState.update { it.copy(sharedMediaCount = count) }
            }
        }
    }

    fun selectMedia(item: MediaItem) {
        _uiState.update { it.copy(selectedMediaItem = item) }
    }

    fun clearSelectedMedia() {
        _uiState.update { it.copy(selectedMediaItem = null) }
    }

    fun loadSingleMedia(mediaId: String) {
        viewModelScope.launch {
            val item = mediaRepository.getMediaItem(mediaId)
            _uiState.update { it.copy(selectedMediaItem = item) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
