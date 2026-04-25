package com.elv8.crisisos.ui.screens.deconfliction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elv8.crisisos.domain.model.DeconflictionReport
import com.elv8.crisisos.domain.model.ProtectionStatus
import com.elv8.crisisos.domain.model.ReportType
import com.elv8.crisisos.domain.repository.DeconflictionRepository
import com.elv8.crisisos.domain.repository.IdentityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

data class DeconflictionUiState(
    val reports: List<DeconflictionReport> = emptyList(),
    val currentStep: Int = 1,
    val draftType: ReportType? = null,
    val draftFacilityName: String = "",
    val draftCoordinates: String = "",
    val draftStatus: ProtectionStatus = ProtectionStatus.PROTECTED,
    val isGenerating: Boolean = false,
    val generatedHash: String? = null
)

@HiltViewModel
class DeconflictionViewModel @Inject constructor(
    private val repository: DeconflictionRepository,
    private val identityRepository: IdentityRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeconflictionUiState())
    val uiState: StateFlow<DeconflictionUiState> = _uiState.asStateFlow()

    init {
        repository.observeIncoming()
        viewModelScope.launch {
            repository.observe().collect { reports ->
                _uiState.update { it.copy(reports = reports) }
            }
        }
    }

    fun updateDraftType(type: ReportType) {
        _uiState.update { it.copy(draftType = type) }
    }

    fun updateFacilityName(name: String) {
        _uiState.update { it.copy(draftFacilityName = name) }
    }

    fun updateCoordinates(coords: String) {
        _uiState.update { it.copy(draftCoordinates = coords) }
    }

    fun useCurrentLocation() {
        // The location repository is wired into the Maps screen; here we accept whatever
        // coordinate string the user pastes (NGOs typically copy-paste from a satellite tool).
        _uiState.update { it.copy(draftCoordinates = it.draftCoordinates) }
    }

    fun updateProtectionStatus(status: ProtectionStatus) {
        _uiState.update { it.copy(draftStatus = status) }
    }

    fun nextStep() {
        if (_uiState.value.currentStep < 3) {
            _uiState.update { it.copy(currentStep = it.currentStep + 1) }
        }
    }

    fun previousStep() {
        if (_uiState.value.currentStep > 1) {
            _uiState.update { it.copy(currentStep = it.currentStep - 1) }
        }
    }

    fun generateReport() {
        val state = _uiState.value
        if (state.draftType == null || state.draftFacilityName.isBlank() || state.draftCoordinates.isBlank()) return

        _uiState.update { it.copy(isGenerating = true) }

        viewModelScope.launch {
            val rawData = "${state.draftType.name}-${state.draftFacilityName}-${state.draftCoordinates}-${UUID.randomUUID()}"
            val hash = generateHash(rawData)
            val shortenedId = hash.take(16)

            val newReport = DeconflictionReport(
                id = shortenedId,
                reportType = state.draftType,
                facilityName = state.draftFacilityName,
                coordinates = state.draftCoordinates,
                protectionStatus = state.draftStatus,
                genevaArticle = state.draftType.article,
                submittedAt = getCurrentTime(),
                broadcastHash = hash
            )
            val identity = identityRepository.getIdentity().first()
            repository.submit(newReport, submittedBy = identity?.crsId ?: "local_device")

            _uiState.update {
                it.copy(
                    isGenerating = false,
                    generatedHash = shortenedId
                )
            }
        }
    }

    fun resetDraft() {
        _uiState.update {
            it.copy(
                currentStep = 1,
                draftType = null,
                draftFacilityName = "",
                draftCoordinates = "",
                draftStatus = ProtectionStatus.PROTECTED,
                generatedHash = null
            )
        }
    }

    private fun generateHash(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun getCurrentTime(): String {
        val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
        return sdf.format(Date())
    }
}
