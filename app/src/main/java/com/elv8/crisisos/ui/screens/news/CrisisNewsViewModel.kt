package com.elv8.crisisos.ui.screens.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elv8.crisisos.data.local.entity.NewsItemEntity
import com.elv8.crisisos.domain.repository.IdentityRepository
import com.elv8.crisisos.domain.repository.NewsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class NewsCategory(val label: String) {
    ALERT("Alert"),
    INFRASTRUCTURE("Infrastructure"),
    AID("Aid"),
    SAFETY("Safety"),
    OTHER("Other")
}

data class CrisisNewsUiState(
    val items: List<NewsItemEntity> = emptyList(),
    val canPublish: Boolean = false,
    val isComposerOpen: Boolean = false,
    val draftHeadline: String = "",
    val draftBody: String = "",
    val draftCategory: NewsCategory = NewsCategory.ALERT,
    val isPublishing: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class CrisisNewsViewModel @Inject constructor(
    private val repository: NewsRepository,
    private val identityRepository: IdentityRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CrisisNewsUiState())
    val uiState: StateFlow<CrisisNewsUiState> = _uiState.asStateFlow()

    init {
        repository.observeIncoming()

        viewModelScope.launch {
            repository.observe().collect { items ->
                _uiState.update { state ->
                    val sorted = items.sortedWith(
                        compareByDescending<NewsItemEntity> { it.isOfficial }
                            .thenByDescending { it.publishedAt }
                    )
                    state.copy(items = sorted)
                }
            }
        }

        // NGO publish gate — until we add a real NGO bit on UserIdentity, the
        // alias convention "NGO_*" or "*_OFFICIAL" doubles as the NGO heuristic.
        // Verified relief organizations bootstrap their handles this way during
        // onboarding (per spec section "NGO directory").
        viewModelScope.launch {
            val identity = identityRepository.getIdentity().first()
            val alias = identity?.alias.orEmpty().uppercase()
            val canPublish = alias.startsWith("NGO_") || alias.endsWith("_OFFICIAL")
            _uiState.update { it.copy(canPublish = canPublish) }
        }
    }

    fun openComposer() {
        if (!_uiState.value.canPublish) return
        _uiState.update { it.copy(isComposerOpen = true, error = null) }
    }

    fun closeComposer() {
        _uiState.update {
            it.copy(
                isComposerOpen = false,
                draftHeadline = "",
                draftBody = "",
                draftCategory = NewsCategory.ALERT,
                error = null
            )
        }
    }

    fun updateHeadline(value: String) {
        _uiState.update { it.copy(draftHeadline = value, error = null) }
    }

    fun updateBody(value: String) {
        _uiState.update { it.copy(draftBody = value, error = null) }
    }

    fun selectCategory(value: NewsCategory) {
        _uiState.update { it.copy(draftCategory = value) }
    }

    fun publish() {
        val state = _uiState.value
        if (!state.canPublish) return
        if (state.draftHeadline.isBlank()) {
            _uiState.update { it.copy(error = "Headline can't be empty.") }
            return
        }
        _uiState.update { it.copy(isPublishing = true, error = null) }
        viewModelScope.launch {
            try {
                repository.publish(
                    headline = state.draftHeadline,
                    body = state.draftBody,
                    category = state.draftCategory.name
                )
                _uiState.update {
                    it.copy(
                        isPublishing = false,
                        isComposerOpen = false,
                        draftHeadline = "",
                        draftBody = "",
                        draftCategory = NewsCategory.ALERT
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isPublishing = false, error = e.message ?: "Failed to publish.")
                }
            }
        }
    }
}
