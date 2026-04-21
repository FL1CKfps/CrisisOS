package com.elv8.crisisos.ui.screens.missingperson

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elv8.crisisos.core.event.AppEvent
import com.elv8.crisisos.core.event.EventBus
import com.elv8.crisisos.domain.repository.MissingPersonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

enum class SearchMode { SEARCH, REGISTER }
enum class SearchType { BY_NAME, BY_CRS_ID }

data class PersonResult(
    val crsId: String,
    val name: String,
    val lastLocation: String,
    val lastSeen: String,
    val hopsAway: Int
)

data class RegisteredPerson(
    val crsId: String,
    val name: String,
    val age: String,
    val photoDescription: String,
    val lastKnownLocation: String,
    val registeredAt: String
)

data class MissingPersonUiState(
    val mode: SearchMode = SearchMode.SEARCH,
    val searchQuery: String = "",
    val searchType: SearchType = SearchType.BY_NAME,
    val generatedCrsId: String = "",
    val searchResults: List<PersonResult> = emptyList(),
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val lastSeen: String = "",
    val registeredPersons: List<RegisteredPerson> = emptyList(),

    val registerName: String = "",
    val registerAge: String = "",
    val registerDescription: String = "",
    val registerLocation: String = ""
)

@HiltViewModel
class MissingPersonViewModel @Inject constructor(
    private val missingPersonRepository: MissingPersonRepository,
    private val eventBus: EventBus
) : ViewModel() {

    private val _uiState = MutableStateFlow(MissingPersonUiState())
    val uiState: StateFlow<MissingPersonUiState> = _uiState.asStateFlow()

    init {
        generateCrsId()

        // Load registered persons from Room
        viewModelScope.launch {
            missingPersonRepository.getRegisteredPersons().collect { persons ->
                _uiState.update { it.copy(registeredPersons = persons) }
            }
        }

        // Start observing incoming mesh data
        viewModelScope.launch {
            missingPersonRepository.observeIncomingPersonData().collect { }
        }

        // Observe ResponseReceived events
        viewModelScope.launch {
            eventBus.events
                .filterIsInstance<AppEvent.MissingPersonEvent.ResponseReceived>()
                .collect { event ->
                    val result = PersonResult(
                        crsId = event.crsId,
                        name = "Located via mesh",
                        lastLocation = event.lastLocation,
                        lastSeen = "Just now",
                        hopsAway = event.hopsAway
                    )
                    _uiState.update { 
                        // To avoid duplicates, check if crsId exists etc., but for now just appending as instructed
                        it.copy(searchResults = it.searchResults + result) 
                    }
                }
        }
    }

    fun switchMode(mode: SearchMode) {
        _uiState.update { it.copy(mode = mode) }
    }

    fun switchSearchType(type: SearchType) {
        _uiState.update { it.copy(searchType = type) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun updateRegisterForm(name: String, age: String, desc: String, loc: String) {
        _uiState.update {
            it.copy(registerName = name, registerAge = age, registerDescription = desc, registerLocation = loc)
        }
    }

    fun searchByName() {
        _uiState.update { it.copy(searchType = SearchType.BY_NAME) }
        search()
    }

    fun searchByCrsId() {
        _uiState.update { it.copy(searchType = SearchType.BY_CRS_ID) }
        search()
    }

    fun search() {
        val state = _uiState.value
        if (state.searchQuery.isBlank()) return

        _uiState.update { it.copy(isSearching = true, hasSearched = true, searchResults = emptyList()) }

        viewModelScope.launch {
            missingPersonRepository.searchPersons(state.searchQuery).collect { persons ->
                val results = persons.map { p ->
                    PersonResult(
                        crsId = p.crsId,
                        name = p.name,
                        lastLocation = p.lastKnownLocation,
                        lastSeen = p.registeredAt,
                        hopsAway = 0 // local
                    )
                }
                _uiState.update { it.copy(isSearching = false, searchResults = results) }
            }
        }
    }

    fun registerPerson() {
        val state = _uiState.value
        if (state.registerName.isBlank()) return

        val newPerson = RegisteredPerson(
            crsId = state.generatedCrsId,
            name = state.registerName,
            age = state.registerAge,
            photoDescription = state.registerDescription,
            lastKnownLocation = state.registerLocation,
            registeredAt = "Just now"
        )

        viewModelScope.launch {
            missingPersonRepository.registerPerson(newPerson)
        }

        _uiState.update {
            it.copy(
                registerName = "",
                registerAge = "",
                registerDescription = "",
                registerLocation = ""
            )
        }
        generateCrsId()
    }

    fun generateCrsId() {
        val id = buildString {
            append((1..8).map { "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".random() }.joinToString(""))
        }
        _uiState.update { it.copy(generatedCrsId = id) }
    }
}
