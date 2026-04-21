package com.elv8.crisisos.ui.screens.fakenews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elv8.crisisos.domain.model.Verdict
import com.elv8.crisisos.domain.model.VerificationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class FakeNewsUiState(
    val claimInput: String = "",
    val isAnalyzing: Boolean = false,
    val result: VerificationResult? = null,
    val recentChecks: List<VerificationResult> = emptyList()
)

@HiltViewModel
class FakeNewsViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(FakeNewsUiState())
    val uiState: StateFlow<FakeNewsUiState> = _uiState.asStateFlow()

    init {
        loadRecentChecks()
    }

    private fun loadRecentChecks() {
        val dummyChecks = listOf(
            VerificationResult(
                claimText = "The main bridge leading out of the city is destroyed by bombing.",
                verdict = Verdict.LIKELY_FALSE,
                confidenceScore = 0.85f,
                sources = listOf("reuters.com", "localgov.gov", "GDELT Network"),
                reasoning = "Satellite imagery from 2 hours ago and 4 active ground reports confirm the bridge remains intact and passable, though congested.",
                checkedAt = getCurrentTime()
            ),
            VerificationResult(
                claimText = "A secondary distribution center has been established at the central high school.",
                verdict = Verdict.VERIFIED,
                confidenceScore = 0.94f,
                sources = listOf("redcross.org", "unhcr.org", "Official Alert"),
                reasoning = "Official NGOs have corroborated this setup via their secured mesh nodes. Supplies are actively being distributed.",
                checkedAt = getCurrentTime()
            ),
            VerificationResult(
                claimText = "Rations are now requiring payment in hard currency starting tonight.",
                verdict = Verdict.MISLEADING,
                confidenceScore = 0.76f,
                sources = listOf("Snopes Offline", "GDELT Fact Check"),
                reasoning = "Official aid distributions remain completely free. However, illegal black markets operating nearby are demanding currency. Do not confuse the two.",
                checkedAt = getCurrentTime()
            )
        )
        _uiState.update { it.copy(recentChecks = dummyChecks) }
    }

    fun updateInput(text: String) {
        _uiState.update { it.copy(claimInput = text, result = null) }
    }

    fun analyzeClaim() {
        val claim = _uiState.value.claimInput.trim()
        if (claim.isBlank() || _uiState.value.isAnalyzing) return

        _uiState.update { it.copy(isAnalyzing = true, result = null) }

        viewModelScope.launch {
            // Simulates GDELT lookup
            delay(2000)

            val verdicts = Verdict.values()
            val randomVerdict = verdicts.random()
            
            // Generate a random confidence score leaning towards the verdict's extremity
            val confidence = when(randomVerdict) {
                Verdict.VERIFIED -> (80..99).random() / 100f
                Verdict.LIKELY_FALSE -> (75..95).random() / 100f
                Verdict.UNVERIFIED -> (20..50).random() / 100f
                Verdict.MISLEADING -> (60..85).random() / 100f
                Verdict.SATIRE -> (85..99).random() / 100f
            }

            val reasoning = generateFakeReasoning(randomVerdict)
            val sources = generateFakeSources(randomVerdict)

            val newResult = VerificationResult(
                claimText = claim,
                verdict = randomVerdict,
                confidenceScore = confidence,
                sources = sources,
                reasoning = reasoning,
                checkedAt = getCurrentTime()
            )

            _uiState.update { state ->
                val updatedRecent = listOf(newResult) + state.recentChecks
                state.copy(
                    isAnalyzing = false,
                    result = newResult,
                    recentChecks = updatedRecent,
                    claimInput = "" // clear input after analysis
                )
            }
        }
    }
    
    fun selectRecentCheck(result: VerificationResult) {
        _uiState.update { it.copy(result = result, claimInput = result.claimText) }
    }

    private fun getCurrentTime(): String {
        val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun generateFakeReasoning(verdict: Verdict): String {
        return when (verdict) {
            Verdict.VERIFIED -> "Multiple trusted ground sources and official international NGO databases corroborate this claim. It aligns with recent verified events."
            Verdict.LIKELY_FALSE -> "This claim directly contradicts verified telemetry and ground truth established by established humanitarian organizations in the area."
            Verdict.UNVERIFIED -> "There is not enough secure mesh chatter or official validation available to confirm or deny this claim at this time. Proceed with caution."
            Verdict.MISLEADING -> "While containing a grain of truth, the surrounding context has been heavily manipulated. Verified sources report a different overall narrative."
            Verdict.SATIRE -> "This claim originates from known parody or satirical broadcasting nodes. It is not intended to be taken as factual ground truth."
        }
    }

    private fun generateFakeSources(verdict: Verdict): List<String> {
        val allSources = listOf("GDELT", "Mesh Node Alpha", "RedCross Local", "UNHCR Broadcast", "Local Gov Feed", "Snopes Offline", "Reuters Archive")
        return allSources.shuffled().take((1..3).random())
    }
}