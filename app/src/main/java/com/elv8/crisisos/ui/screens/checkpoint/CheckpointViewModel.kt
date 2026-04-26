package com.elv8.crisisos.ui.screens.checkpoint

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elv8.crisisos.domain.model.Checkpoint
import com.elv8.crisisos.domain.model.DocumentsRequired
import com.elv8.crisisos.domain.model.ThreatLevel
import com.elv8.crisisos.domain.model.WaitTime
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

/**
 * UI state for Feature 7 (Checkpoint Threat Intelligence).
 *
 * `selectedCheckpoint` is non-null when the bottom sheet is open in
 * "update existing" mode. `composeNewReport == true` opens the same
 * sheet in "new report" mode where the user types in a checkpoint
 * name + grid label themselves.
 */
data class CheckpointUiState(
    val checkpoints: List<Checkpoint> = emptyList(),
    val isLoading: Boolean = false,
    val selectedCheckpoint: Checkpoint? = null,
    val composeNewReport: Boolean = false
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
        // Per spec: 2-hour TTL — sweep on entry so first render is clean.
        viewModelScope.launch { repository.purgeStaleReports() }
    }

    fun selectCheckpoint(checkpoint: Checkpoint?) {
        _uiState.update { it.copy(selectedCheckpoint = checkpoint, composeNewReport = false) }
    }

    fun startNewReport() {
        _uiState.update { it.copy(composeNewReport = true, selectedCheckpoint = null) }
    }

    fun cancelComposer() {
        _uiState.update { it.copy(composeNewReport = false, selectedCheckpoint = null) }
    }

    /**
     * Submits a Feature-7 anonymous report. Maps the threat level to
     * the legacy `isOpen`/`safetyRating` fields for back-compat with
     * older peers (HOSTILE/BLOCKED → closed; SAFE → 5★; UNKNOWN → 3★).
     *
     * `checkpointId == null` means "new report" — a fresh row with a
     * generated UUID is created.
     */
    fun submitReport(
        checkpointId: String?,
        name: String,
        gridLabel: String,
        threatLevel: ThreatLevel,
        docs: DocumentsRequired,
        wait: WaitTime,
        anonymousNote: String
    ) {
        viewModelScope.launch {
            val cleanedNote = anonymousNote.trim()
            val effectiveOpen = threatLevel != ThreatLevel.HOSTILE && wait != WaitTime.BLOCKED
            val effectiveSafety = when (threatLevel) {
                ThreatLevel.SAFE -> 5
                ThreatLevel.UNKNOWN -> 3
                ThreatLevel.HOSTILE -> 1
            }

            val target: Checkpoint = if (checkpointId != null) {
                _uiState.value.checkpoints.find { it.id == checkpointId } ?: return@launch
            } else {
                Checkpoint(
                    id = UUID.randomUUID().toString(),
                    name = name.ifBlank { "Unnamed checkpoint" },
                    location = gridLabel.ifBlank { "Unknown grid" },
                    controlledBy = "Unknown",
                    safetyRating = effectiveSafety,
                    isOpen = effectiveOpen,
                    lastReport = "Just now",
                    reportCount = 0,
                    allowsCivilians = threatLevel != ThreatLevel.HOSTILE,
                    requiresDocuments = docs != DocumentsRequired.NONE,
                    notes = cleanedNote
                )
            }

            val updated = target.copy(
                threatLevel = threatLevel,
                docsRequired = docs,
                waitTime = wait,
                isOpen = effectiveOpen,
                safetyRating = effectiveSafety,
                requiresDocuments = docs != DocumentsRequired.NONE,
                allowsCivilians = threatLevel != ThreatLevel.HOSTILE,
                notes = cleanedNote.ifBlank { target.notes },
                lastUpdatedAt = System.currentTimeMillis()
            )
            repository.submitUpdate(updated)
            _uiState.update { it.copy(selectedCheckpoint = null, composeNewReport = false) }
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
