package com.elv8.crisisos.ui.screens.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elv8.crisisos.domain.model.contact.Contact
import com.elv8.crisisos.domain.model.contact.Group
import com.elv8.crisisos.domain.model.contact.TrustLevel
import com.elv8.crisisos.domain.model.identity.CrsIdGenerator
import com.elv8.crisisos.domain.repository.ContactRepository
import com.elv8.crisisos.domain.repository.GroupRepository
import com.elv8.crisisos.domain.repository.IdentityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ContactSection { ALL, FAMILY, GROUPS }

data class ContactsUiState(
    val contacts: List<Contact> = emptyList(),
    val groups: List<Group> = emptyList(),
    val familyGroup: Group? = null,
    val familyContacts: List<Contact> = emptyList(),
    val activeSection: ContactSection = ContactSection.ALL,
    val searchQuery: String = "",
    val filteredContacts: List<Contact> = emptyList(),
    val isAddDialogOpen: Boolean = false,
    val addByCrsIdInput: String = "",
    val addByAliasInput: String = "",
    val isAddingContact: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val contactRepository: ContactRepository,
    private val groupRepository: GroupRepository,
    private val identityRepository: IdentityRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContactsUiState())
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val familyGroup = groupRepository.getOrCreateFamilyGroup()
            _uiState.update { it.copy(familyGroup = familyGroup) }

            combine(
                contactRepository.getAllContacts(),
                groupRepository.getAllGroups(),
                contactRepository.getFamilyContacts()
            ) { allContacts, allGroups, familyContacts ->
                Triple(allContacts, allGroups, familyContacts)
            }.collect { (allContacts, allGroups, familyContacts) ->
                _uiState.update { state ->
                    val filtered = performSearch(allContacts, state.searchQuery)
                    state.copy(
                        contacts = allContacts,
                        groups = allGroups,
                        familyContacts = familyContacts,
                        filteredContacts = filtered
                    )
                }
            }
        }
    }

    fun setSection(section: ContactSection) {
        _uiState.update { it.copy(activeSection = section) }
    }

    fun updateSearch(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                filteredContacts = performSearch(state.contacts, query)
            )
        }
    }

    private fun performSearch(contacts: List<Contact>, query: String): List<Contact> {
        if (query.isBlank()) return contacts
        val lowerQuery = query.lowercase()
        return contacts.filter {
            it.alias.lowercase().contains(lowerQuery) || it.crsId.lowercase().contains(lowerQuery)
        }
    }

    fun openAddDialog() {
        _uiState.update { it.copy(isAddDialogOpen = true, addByCrsIdInput = "", addByAliasInput = "", errorMessage = null) }
    }

    fun closeAddDialog() {
        _uiState.update { it.copy(isAddDialogOpen = false) }
    }

    fun updateAddCrsId(input: String) {
        val uppercaseInput = input.uppercase()
        _uiState.update { it.copy(addByCrsIdInput = uppercaseInput, errorMessage = null) }
    }

    fun updateAddAlias(input: String) {
        _uiState.update { it.copy(addByAliasInput = input) }
    }

    fun addContactManually() {
        val state = uiState.value
        val crsId = state.addByCrsIdInput.trim()
        val alias = state.addByAliasInput.trim()

        if (!CrsIdGenerator.isValid(crsId)) {
            _uiState.update { it.copy(errorMessage = "Invalid CRS-ID format (e.g. CRS-XXXX-XXXX)") }
            return
        }

        if (alias.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Alias cannot be blank") }
            return
        }
        
        _uiState.update { it.copy(isAddingContact = true) }

        viewModelScope.launch {
            if (contactRepository.isContact(crsId)) {
                _uiState.update { it.copy(isAddingContact = false, errorMessage = "Contact already exists") }
                return@launch
            }

            val color = CrsIdGenerator.generateAvatarColor(crsId)
            contactRepository.addContact(crsId, alias, color)
            
            _uiState.update { 
                it.copy(
                    isAddingContact = false, 
                    isAddDialogOpen = false, 
                    successMessage = "Contact added",
                    addByCrsIdInput = "",
                    addByAliasInput = ""
                ) 
            }
        }
    }

    fun removeContact(crsId: String) {
        viewModelScope.launch {
            contactRepository.removeContact(crsId)
        }
    }

    fun blockContact(crsId: String) {
        viewModelScope.launch {
            contactRepository.blockContact(crsId)
        }
    }

    fun setTrustLevel(crsId: String, level: TrustLevel) {
        viewModelScope.launch {
            contactRepository.setTrustLevel(crsId, level)
        }
    }

    fun addToFamilyGroup(crsId: String) {
        viewModelScope.launch {
            val familyId = _uiState.value.familyGroup?.groupId ?: return@launch
            groupRepository.addMember(familyId, crsId)
            contactRepository.setTrustLevel(crsId, TrustLevel.FAMILY)
            contactRepository.addToGroup(crsId, familyId)
            _uiState.update { it.copy(successMessage = "Added to Family group") }
        }
    }
}
