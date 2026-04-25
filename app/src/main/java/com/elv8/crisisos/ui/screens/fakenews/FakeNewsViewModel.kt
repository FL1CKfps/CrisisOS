package com.elv8.crisisos.ui.screens.fakenews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elv8.crisisos.core.heuristics.FakeNewsAnalyzer
import com.elv8.crisisos.data.local.entity.FakeNewsCheckEntity
import com.elv8.crisisos.data.repository.CrisisIntelRepository
import com.elv8.crisisos.domain.model.Verdict
import com.elv8.crisisos.domain.model.VerificationResult
import com.elv8.crisisos.domain.repository.FakeNewsCheckRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

data class FakeNewsUiState(
    val claimInput: String = "",
    val isAnalyzing: Boolean = false,
    val result: VerificationResult? = null,
    val recentChecks: List<VerificationResult> = emptyList()
)

@HiltViewModel
class FakeNewsViewModel @Inject constructor(
    private val analyzer: FakeNewsAnalyzer,
    private val repository: FakeNewsCheckRepository,
    private val intel: CrisisIntelRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FakeNewsUiState())
    val uiState: StateFlow<FakeNewsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeRecent().collect { checks ->
                _uiState.update { state ->
                    state.copy(recentChecks = checks.map { it.toDomain() })
                }
            }
        }
    }

    fun updateInput(text: String) {
        _uiState.update { it.copy(claimInput = text, result = null) }
    }

    fun analyzeClaim() {
        val claim = _uiState.value.claimInput.trim()
        if (claim.isBlank() || _uiState.value.isAnalyzing) return

        _uiState.update { it.copy(isAnalyzing = true, result = null) }

        viewModelScope.launch {
            // Pure-CPU analyzer; bounce off Default to keep the main thread free.
            val baseResult = withContext(Dispatchers.Default) {
                analyzer.analyze(claim, currentTimeLabel())
            }

            // Best-effort GDELT cross-reference (online only). Failures are
            // swallowed: the offline verdict is still authoritative per spec.
            val articles = intel.crossReferenceClaim(claim).getOrNull().orEmpty()
            val mergedSources = if (articles.isNotEmpty()) {
                baseResult.sources + articles.take(3).map { "GDELT: ${it.domain}" }
            } else baseResult.sources
            val result = baseResult.copy(sources = mergedSources)

            // Persist into Room — recentChecks list will refresh through the observe() flow.
            repository.record(result.toEntity())

            _uiState.update { state ->
                state.copy(
                    isAnalyzing = false,
                    result = result,
                    claimInput = "" // mirror previous behavior — clear after analysis
                )
            }
        }
    }

    fun selectRecentCheck(result: VerificationResult) {
        _uiState.update { it.copy(result = result, claimInput = result.claimText) }
    }

    private fun currentTimeLabel(): String =
        SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date())

    private fun VerificationResult.toEntity(): FakeNewsCheckEntity = FakeNewsCheckEntity(
        id = UUID.randomUUID().toString(),
        claimText = claimText,
        verdict = verdict.name,
        confidenceScore = confidenceScore,
        reasoning = reasoning,
        sources = sources.joinToString("|"),
        checkedAt = System.currentTimeMillis()
    )

    private fun FakeNewsCheckEntity.toDomain(): VerificationResult = VerificationResult(
        id = id,
        claimText = claimText,
        verdict = runCatching { Verdict.valueOf(verdict) }.getOrDefault(Verdict.UNVERIFIED),
        confidenceScore = confidenceScore,
        sources = sources.split("|").filter { it.isNotBlank() },
        reasoning = reasoning,
        checkedAt = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(checkedAt))
    )
}
