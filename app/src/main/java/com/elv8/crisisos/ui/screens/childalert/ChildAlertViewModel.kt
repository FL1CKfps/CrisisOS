package com.elv8.crisisos.ui.screens.childalert

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elv8.crisisos.domain.model.ChildRecord
import com.elv8.crisisos.domain.model.ChildStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChildFormState(
    val childName: String = "",
    val approximateAge: Int = 5,
    val physicalDescription: String = "",
    val lastKnownLocation: String = "",
    val registeredBy: String = ""
)

data class ChildAlertUiState(
    val registeredChildren: List<ChildRecord> = emptyList(),
    val registrationForm: ChildFormState = ChildFormState(),
    val isRegistering: Boolean = false,
    val newlyRegisteredChild: ChildRecord? = null
)

@HiltViewModel
class ChildAlertViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(ChildAlertUiState())
    val uiState: StateFlow<ChildAlertUiState> = _uiState.asStateFlow()

    init {
        loadSampleRecords()
    }

    private fun loadSampleRecords() {
        val sample1 = ChildRecord(
            crsChildId = "CH-7291-KXZB",
            childName = "Leo (Unknown Last Name)",
            approximateAge = 4,
            physicalDescription = "Wearing a blue jacket and red sneakers. Has a small scar on left cheek.",
            lastKnownLocation = "Central Market Evacuation Point",
            registeredBy = "Sarah M. (Aid Worker)",
            registeredAt = System.currentTimeMillis() - 86400000, // 1 day ago
            status = ChildStatus.SEARCHING,
            locatedAt = null,
            broadcastCount = 312
        )
        
        val sample2 = ChildRecord(
            crsChildId = "CH-1452-MNQP",
            childName = "Maya Collins",
            approximateAge = 8,
            physicalDescription = "Brown hair in braids, wearing a yellow dress.",
            lastKnownLocation = "Sector 4 Relief Camp",
            registeredBy = "Mom (Jane Collins)",
            registeredAt = System.currentTimeMillis() - 172800000, // 2 days ago
            status = ChildStatus.LOCATED,
            locatedAt = "Safe Zone Beta Clinic",
            broadcastCount = 1845
        )

        _uiState.update { it.copy(registeredChildren = listOf(sample1, sample2)) }
    }

    fun updateForm(update: (ChildFormState) -> ChildFormState) {
        _uiState.update { it.copy(registrationForm = update(it.registrationForm)) }
    }

    fun incrementAge() {
        val form = _uiState.value.registrationForm
        if (form.approximateAge < 18) {
            updateForm { it.copy(approximateAge = it.approximateAge + 1) }
        }
    }

    fun decrementAge() {
        val form = _uiState.value.registrationForm
        if (form.approximateAge > 0) {
            updateForm { it.copy(approximateAge = it.approximateAge - 1) }
        }
    }

    fun registerChild() {
        val form = _uiState.value.registrationForm
        if (form.childName.isBlank() || form.physicalDescription.isBlank() || 
            form.lastKnownLocation.isBlank() || form.registeredBy.isBlank()) {
            return
        }

        _uiState.update { it.copy(isRegistering = true) }

        viewModelScope.launch {
            // Simulate network/mesh hashing delay
            delay(1500)

            val generatedId = "CH-${(1000..9999).random()}-${randomAlphaStr(4)}"

            val newRecord = ChildRecord(
                crsChildId = generatedId,
                childName = form.childName.trim(),
                approximateAge = form.approximateAge,
                physicalDescription = form.physicalDescription.trim(),
                lastKnownLocation = form.lastKnownLocation.trim(),
                registeredBy = form.registeredBy.trim(),
                registeredAt = System.currentTimeMillis(),
                status = ChildStatus.SEARCHING,
                locatedAt = null,
                broadcastCount = 1 // initial broadcast
            )

            _uiState.update { state ->
                state.copy(
                    isRegistering = false,
                    registeredChildren = listOf(newRecord) + state.registeredChildren,
                    registrationForm = ChildFormState(), // reset form
                    newlyRegisteredChild = newRecord
                )
            }
        }
    }

    fun dismissConfirmation() {
        _uiState.update { it.copy(newlyRegisteredChild = null) }
    }

    fun markLocated(childId: String, location: String) {
        _uiState.update { state ->
            val updatedRecords = state.registeredChildren.map {
                if (it.crsChildId == childId && it.status == ChildStatus.SEARCHING) {
                    it.copy(status = ChildStatus.LOCATED, locatedAt = location, broadcastCount = it.broadcastCount + 10)
                } else {
                    it
                }
            }
            state.copy(registeredChildren = updatedRecords)
        }
    }

    private fun randomAlphaStr(length: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        return (1..length).map { chars.random() }.joinToString("")
    }
}