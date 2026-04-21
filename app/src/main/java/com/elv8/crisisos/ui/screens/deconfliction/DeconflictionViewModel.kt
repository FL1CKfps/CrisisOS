package com.elv8.crisisos.ui.screens.deconfliction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elv8.crisisos.domain.model.DeconflictionReport
import com.elv8.crisisos.domain.model.ProtectionStatus
import com.elv8.crisisos.domain.model.ReportType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
class DeconflictionViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(DeconflictionUiState())
    val uiState: StateFlow<DeconflictionUiState> = _uiState.asStateFlow()

    init {
        loadSampleReports()
    }

    private fun loadSampleReports() {
        val sample1 = DeconflictionReport(
            id = generateHash("sample1").take(16),
            reportType = ReportType.MEDICAL_FACILITY,
            facilityName = "City Central Hospital",
            coordinates = "48.8566° N, 2.3522° E",
            protectionStatus = ProtectionStatus.PROTECTED,
            genevaArticle = ReportType.MEDICAL_FACILITY.article,
            submittedAt = getCurrentTime(),
            broadcastHash = generateHash("sample1_b")
        )
        
        val sample2 = DeconflictionReport(
            id = generateHash("sample2").take(16),
            reportType = ReportType.WATER_SOURCE,
            facilityName = "North Reservoir Station",
            coordinates = "48.8601° N, 2.3382° E",
            protectionStatus = ProtectionStatus.AT_RISK,
            genevaArticle = ReportType.WATER_SOURCE.article,
            submittedAt = getCurrentTime(),
            broadcastHash = generateHash("sample2_b")
        )

        _uiState.update { it.copy(reports = listOf(sample1, sample2)) }
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
        _uiState.update { it.copy(draftCoordinates = "48.8584° N, 2.3488° E (GPS)") }
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
            delay(2000)

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

            _uiState.update { 
                it.copy(
                    isGenerating = false,
                    generatedHash = shortenedId,
                    reports = listOf(newReport) + it.reports
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