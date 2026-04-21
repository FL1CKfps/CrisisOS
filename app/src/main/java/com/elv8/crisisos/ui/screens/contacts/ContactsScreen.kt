package com.elv8.crisisos.ui.screens.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.elv8.crisisos.domain.model.contact.Contact
import com.elv8.crisisos.domain.model.contact.Group
import com.elv8.crisisos.domain.model.contact.TrustLevel
import com.elv8.crisisos.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ContactsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            CrisisTopBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            if (uiState.activeSection != ContactSection.GROUPS) {
                FloatingActionButton(
                    onClick = { viewModel.openAddDialog() },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = "Add Contact")
                }
            } else {
                FloatingActionButton(
                    onClick = { /* TODO Handle create group */ },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ) {
                    Icon(Icons.Filled.GroupAdd, contentDescription = "Create Group")
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            TabRow(
                selectedTabIndex = uiState.activeSection.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                ContactSection.values().forEach { section ->
                    val isSelected = uiState.activeSection == section
                    Tab(
                        selected = isSelected,
                        onClick = { viewModel.setSection(section) },
                        text = { Text(section.name) },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            when (uiState.activeSection) {
                ContactSection.ALL -> AllContactsTab(uiState, viewModel)
                ContactSection.FAMILY -> FamilyContactsTab(uiState, viewModel)
                ContactSection.GROUPS -> GroupsTab(uiState, viewModel)
            }
        }
    }

    if (uiState.isAddDialogOpen) {
        AddContactDialog(
            crsId = uiState.addByCrsIdInput,
            alias = uiState.addByAliasInput,
            errorMessage = uiState.errorMessage,
            isSaving = uiState.isAddingContact,
            onCrsIdChange = viewModel::updateAddCrsId,
            onAliasChange = viewModel::updateAddAlias,
            onDismiss = viewModel::closeAddDialog,
            onConfirm = viewModel::addContactManually
        )
    }
}

@Composable
fun AllContactsTab(uiState: ContactsUiState, viewModel: ContactsViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = viewModel::updateSearch,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            placeholder = { Text("Search by Alias or CRS-ID") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = CircleShape
        )

        if (uiState.filteredContacts.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Filled.Contacts,
                    title = "No Contacts Found",
                    subtitle = "Add peers manually or over mesh to build your network."
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(uiState.filteredContacts) { contact ->
                    ContactCard(contact = contact, viewModel = viewModel, compact = false)
                }
            }
        }
    }
}

@Composable
fun FamilyContactsTab(uiState: ContactsUiState, viewModel: ContactsViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        CrisisCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(Color(uiState.familyGroup?.avatarColor ?: android.graphics.Color.GRAY)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("F", color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        uiState.familyGroup?.name ?: "Family", 
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "${uiState.familyGroup?.memberCount ?: 0} members", 
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { viewModel.openAddDialog() }) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = "Add Member", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (uiState.familyContacts.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Filled.FamilyRestroom,
                    title = "Family Group is Empty",
                    subtitle = "Add trusted contacts to your Family group"
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(uiState.familyContacts) { contact ->
                    ContactCard(contact = contact, viewModel = viewModel, compact = true)
                }
            }
        }
    }
}

@Composable
fun GroupsTab(uiState: ContactsUiState, viewModel: ContactsViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (uiState.groups.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Filled.Groups,
                    title = "No Custom Groups",
                    subtitle = "Create groups to easily broadcast messages and alerts."
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(uiState.groups) { group ->
                    CrisisCard(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(56.dp).clip(CircleShape).background(Color(group.avatarColor)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(group.name.take(1).uppercase(), color = Color.White, style = MaterialTheme.typography.titleLarge)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(group.name, style = MaterialTheme.typography.titleMedium)
                                Text("${group.memberCount} members", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContactCard(contact: Contact, viewModel: ContactsViewModel, compact: Boolean) {
    var expanded by remember { mutableStateOf(false) }

    CrisisCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(if (compact) 12.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
              com.elv8.crisisos.ui.components.CrsAvatar(
                  crsId = contact.crsId,
                  alias = contact.alias,
                  avatarColor = contact.avatarColor,
                  size = 40.dp
              )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.alias,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = contact.crsId,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            
            val trustBadgeStatus = when (contact.trustLevel) {
                TrustLevel.FAMILY -> BadgeStatus.OK
                TrustLevel.TRUSTED -> BadgeStatus.ACTIVE
                TrustLevel.BASIC -> BadgeStatus.OFFLINE
            }
            
            StatusBadge(text = contact.trustLevel.name, status = trustBadgeStatus)
            
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Options")
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    if (contact.trustLevel != TrustLevel.FAMILY) {
                        DropdownMenuItem(
                            text = { Text("Add to Family") },
                            onClick = { 
                                viewModel.addToFamilyGroup(contact.crsId)
                                expanded = false 
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Set as Trusted") },
                            onClick = { 
                                viewModel.setTrustLevel(contact.crsId, TrustLevel.TRUSTED)
                                expanded = false 
                            }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Set as Trusted") },
                            onClick = { 
                                viewModel.setTrustLevel(contact.crsId, TrustLevel.TRUSTED)
                                expanded = false 
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Set as Basic") },
                            onClick = { 
                                viewModel.setTrustLevel(contact.crsId, TrustLevel.BASIC)
                                expanded = false 
                            }
                        )
                    }
                    Divider()
                    DropdownMenuItem(
                        text = { Text("Remove") },
                        onClick = { 
                            viewModel.removeContact(contact.crsId)
                            expanded = false 
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Block", color = MaterialTheme.colorScheme.error) },
                        onClick = { 
                            viewModel.blockContact(contact.crsId)
                            expanded = false 
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AddContactDialog(
    crsId: String,
    alias: String,
    errorMessage: String?,
    isSaving: Boolean,
    onCrsIdChange: (String) -> Unit,
    onAliasChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Contact") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InputField(
                    value = crsId,
                    onValueChange = onCrsIdChange,
                    label = "CRS-ID (e.g. CRS-XXXX-XXXX)",
                    isError = errorMessage != null && errorMessage.contains("CRS-ID")
                )
                InputField(
                    value = alias,
                    onValueChange = onAliasChange,
                    label = "Alias (Display Name)",
                    isError = errorMessage != null && errorMessage.contains("Alias")
                )
                if (errorMessage != null) {
                    Text(text = errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = !isSaving) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    Text("Add")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Cancel")
            }
        }
    )
}
