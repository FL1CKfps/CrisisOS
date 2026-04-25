package com.elv8.crisisos.ui.screens.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elv8.crisisos.data.local.entity.CommunityPostEntity
import com.elv8.crisisos.domain.repository.CommunityBoardRepository
import com.elv8.crisisos.domain.repository.IdentityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CommunityCategory(val label: String) {
    GENERAL("General"),
    HELP("Need Help"),
    OFFER("Offering"),
    REUNITE("Reuniting"),
    UPDATE("Status Update")
}

data class CommunityBoardUiState(
    val posts: List<CommunityPostEntity> = emptyList(),
    val draft: String = "",
    val draftCategory: CommunityCategory = CommunityCategory.GENERAL,
    val isPosting: Boolean = false,
    val canPin: Boolean = false,
    val pinDraft: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class CommunityBoardViewModel @Inject constructor(
    private val repository: CommunityBoardRepository,
    private val identityRepository: IdentityRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CommunityBoardUiState())
    val uiState: StateFlow<CommunityBoardUiState> = _uiState.asStateFlow()

    init {
        repository.observeIncoming()

        viewModelScope.launch {
            repository.observe().collect { rows ->
                _uiState.update { state ->
                    val ordered = rows.sortedWith(
                        compareByDescending<CommunityPostEntity> { it.pinned }
                            .thenByDescending { it.createdAt }
                    )
                    state.copy(posts = ordered)
                }
            }
        }

        viewModelScope.launch {
            val identity = identityRepository.getIdentity().first()
            val alias = identity?.alias.orEmpty().uppercase()
            // Same NGO heuristic as CrisisNews until a dedicated NGO flag exists.
            val canPin = alias.startsWith("NGO_") || alias.endsWith("_OFFICIAL")
            _uiState.update { it.copy(canPin = canPin) }
        }
    }

    fun updateDraft(text: String) {
        _uiState.update { it.copy(draft = text, error = null) }
    }

    fun selectCategory(c: CommunityCategory) {
        _uiState.update { it.copy(draftCategory = c) }
    }

    fun togglePin(value: Boolean) {
        if (!_uiState.value.canPin) return
        _uiState.update { it.copy(pinDraft = value) }
    }

    fun submit() {
        val state = _uiState.value
        val body = state.draft.trim()
        if (body.isBlank()) {
            _uiState.update { it.copy(error = "Write something to share.") }
            return
        }
        _uiState.update { it.copy(isPosting = true, error = null) }
        viewModelScope.launch {
            try {
                repository.post(
                    body = body,
                    category = state.draftCategory.name,
                    pinned = state.canPin && state.pinDraft
                )
                _uiState.update {
                    it.copy(
                        isPosting = false,
                        draft = "",
                        draftCategory = CommunityCategory.GENERAL,
                        pinDraft = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isPosting = false, error = e.message ?: "Failed to post.") }
            }
        }
    }

    fun togglePinned(post: CommunityPostEntity) {
        if (!_uiState.value.canPin) return
        viewModelScope.launch {
            try {
                repository.setPinned(post.id, !post.pinned)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Pin not allowed.") }
            }
        }
    }
}
