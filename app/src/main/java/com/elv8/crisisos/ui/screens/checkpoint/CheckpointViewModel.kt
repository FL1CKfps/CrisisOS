package com.elv8.crisisos.ui.screens.checkpoint

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elv8.crisisos.domain.model.Checkpoint
import com.elv8.crisisos.domain.repository.CheckpointRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class CheckpointUiState(
    val checkpoints: List<Checkpoint> = emptyList(),
    val isLoading: Boolean = false,
    val selectedCheckpoint: Checkpoint? = null
)

@HiltViewModel
class CheckpointViewModel @Inject constructor(
    private val repository: CheckpointRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CheckpointUiState())
    val uiState: StateFlow<CheckpointUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeIncomingUpdates().collect {}
        }
        viewModelScope.launch {
            repository.getCheckpoints().collect { checkpoints ->
                _uiState.update { it.copy(checkpoints = checkpoints) }
            }
        }
    }

    fun selectCheckpoint(checkpoint: Checkpoint?) {
        _uiState.update { it.copy(selectedCheckpoint = checkpoint) }
    }

    fun submitUpdate(checkpointId: String, isOpenStatus: Boolean, safety: Int, newNotes: String) {
        viewModelScope.launch {
            val target = _uiState.value.checkpoints.find { it.id == checkpointId } ?: return@launch
            val updated = target.copy(
                isOpen = isOpenStatus,
                safetyRating = safety,
                notes = newNotes.ifBlank { target.notes }
            )
            repository.submitUpdate(updated)
            _uiState.update { it.copy(selectedCheckpoint = null) }
        }
    }

    fun refreshCheckpoints() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.purgeStaleReports()
            delay(500)
            _uiState.update { it.copy(isLoading = false) }
        }
    }
}
