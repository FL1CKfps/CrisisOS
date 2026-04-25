package com.elv8.crisisos.ui.screens.missingperson

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elv8.crisisos.ui.components.CrisisCard
import com.elv8.crisisos.ui.components.EmptyState
import com.elv8.crisisos.ui.components.LocalTopBarState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissingPersonScreen(
    onNavigateBack: () -> Unit,
    viewModel: MissingPersonViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val topBarState = LocalTopBarState.current

    LaunchedEffect(Unit) {
        topBarState.update(
            title = {
                Column {
                    Text("FIND PERSON", fontWeight = FontWeight.Bold)
                    Text(
                        "CRS-ID lookup + dependent watches",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = uiState.tab.ordinal) {
            Tab(
                selected = uiState.tab == LookupTab.SEARCH,
                onClick = { viewModel.selectTab(LookupTab.SEARCH) },
                text = { Text("Search") },
                icon = { Icon(Icons.Filled.Search, contentDescription = null) }
            )
            Tab(
                selected = uiState.tab == LookupTab.WATCHES,
                onClick = { viewModel.selectTab(LookupTab.WATCHES) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Watches")
                        if (uiState.watches.isNotEmpty()) {
                            Spacer(Modifier.width(6.dp))
                            Badge { Text("${uiState.watches.size}") }
                        }
                    }
                },
                icon = { Icon(Icons.Filled.Visibility, contentDescription = null) }
            )
        }

        AnimatedVisibility(visible = uiState.infoBanner != null) {
            uiState.infoBanner?.let { banner ->
                InfoBanner(text = banner, onDismiss = viewModel::dismissBanner)
            }
        }

        when (uiState.tab) {
            LookupTab.SEARCH -> SearchTab(
                uiState = uiState,
                onQueryChange = viewModel::updateCrsIdQuery,
                onSubmit = viewModel::submitSearch,
                onClear = viewModel::clearSearch,
                onWatch = viewModel::watchResult
            )
            LookupTab.WATCHES -> WatchesTab(
                uiState = uiState,
                onRemove = viewModel::removeWatch,
                onMarkReunited = viewModel::markReunited,
                onJumpToSearch = { viewModel.selectTab(LookupTab.SEARCH) }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Search tab
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SearchTab(
    uiState: MissingPersonUiState,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    onWatch: (LookupResult) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = uiState.crsIdQuery,
                onValueChange = onQueryChange,
                label = { Text("CRS ID (e.g. AKDU-15030720)") },
                placeholder = { Text("ABCD-XXXXXXXX") },
                singleLine = true,
                isError = uiState.crsIdError != null,
                supportingText = {
                    Text(
                        uiState.crsIdError ?: "Enter the exact CRS ID shared with you. Mesh searches up to 4 hops.",
                        color = if (uiState.crsIdError != null)
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Search
                ),
                trailingIcon = {
                    if (uiState.crsIdQuery.isNotBlank()) {
                        IconButton(onClick = onClear) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.Button(
                    onClick = onSubmit,
                    enabled = !uiState.isSearching,
                    modifier = Modifier.weight(1f)
                ) {
                    if (uiState.isSearching) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Searching mesh…")
                    } else {
                        Icon(Icons.Filled.Search, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Search")
                    }
                }
            }
        }

        HorizontalDivider()

        if (!uiState.hasSearched) {
            EmptyState(
                icon = Icons.Filled.PersonSearch,
                title = "Find someone by their CRS ID",
                subtitle = "Type a CRS ID above and we'll ask the local mesh whether anyone has seen them recently. Hits and dependents both appear under Watches."
            )
        } else if (uiState.searchResults.isEmpty() && uiState.isSearching) {
            EmptyState(
                icon = Icons.Filled.Search,
                title = "Querying the mesh…",
                subtitle = "We're broadcasting your search to nearby peers. Replies usually arrive within 4 seconds."
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(uiState.searchResults, key = { it.crsId + it.source.name }) { result ->
                    ResultCard(result = result, onWatch = { onWatch(result) })
                }
            }
        }
    }
}

@Composable
private fun ResultCard(result: LookupResult, onWatch: () -> Unit) {
    val isMiss = result.source == ResultSource.NOT_FOUND
    CrisisCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (isMiss)
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
        else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isMiss) Icons.Filled.Warning else Icons.Filled.Person,
                    contentDescription = null,
                    tint = if (isMiss) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = result.crsId,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (!result.displayName.isNullOrBlank()) {
                        Text(
                            text = result.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                SourceBadge(result.source)
            }

            if (!isMiss) {
                Spacer(Modifier.height(8.dp))
                MetaRow(label = "Last seen", value = result.lastLocation)
                MetaRow(label = "When", value = result.lastSeenAgo)
                if (result.hopsAway >= 0) {
                    MetaRow(label = "Distance", value = "${result.hopsAway} mesh hop${if (result.hopsAway == 1) "" else "s"} away")
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    "No replies on the mesh yet. Add to your watch list and we'll notify you the moment someone surfaces them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                if (result.isWatched) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Done, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("On your watch list", style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    TextButton(onClick = onWatch) {
                        Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add to watch list")
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = label,
            modifier = Modifier.width(80.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SourceBadge(source: ResultSource) {
    val (label, color) = when (source) {
        ResultSource.LOCAL_CACHE -> "Cached" to MaterialTheme.colorScheme.tertiary
        ResultSource.MESH_RESPONSE -> "Mesh" to MaterialTheme.colorScheme.primary
        ResultSource.NOT_FOUND -> "Not found" to MaterialTheme.colorScheme.error
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.18f)
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Watches tab
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WatchesTab(
    uiState: MissingPersonUiState,
    onRemove: (String) -> Unit,
    onMarkReunited: (String) -> Unit,
    onJumpToSearch: () -> Unit
) {
    if (uiState.watches.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.Visibility,
            title = "No-one on your watch list yet",
            subtitle = "Add dependents (children, elders, group members) here. We'll auto-add anyone you broadcast SOS for, and any child alerts received on the mesh.",
            actionLabel = "Search a CRS ID",
            onAction = onJumpToSearch
        )
        return
    }

    val grouped = uiState.watches.groupBy { it.source }
    val orderedGroups = listOf(
        WatchSource.DEPENDENT to "Dependents",
        WatchSource.SOS_AUTO to "Active SOS",
        WatchSource.MANUAL to "Manual watches"
    )

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        orderedGroups.forEach { (source, label) ->
            val entries = grouped[source].orEmpty()
            if (entries.isNotEmpty()) {
                item(key = "header_${source.name}") {
                    SectionHeader(label = label, count = entries.size, icon = iconForSource(source))
                }
                items(entries, key = { "watch_${it.crsId}" }) { entry ->
                    WatchCard(
                        entry = entry,
                        onRemove = { onRemove(entry.crsId) },
                        onMarkReunited = { onMarkReunited(entry.crsId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(label: String, count: Int, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "($count)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WatchCard(entry: WatchEntry, onRemove: () -> Unit, onMarkReunited: () -> Unit) {
    CrisisCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = iconForSource(entry.source),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = entry.displayName ?: entry.crsId,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = entry.crsId,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusBadge(entry.status)
            }

            entry.lastLocation?.let { loc ->
                Spacer(Modifier.height(8.dp))
                MetaRow(label = "Last seen", value = loc)
            }
            MetaRow(label = "Updated", value = entry.lastUpdate)

            entry.note?.let { note ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                if (entry.status != WatchStatus.REUNITED) {
                    TextButton(onClick = onMarkReunited) {
                        Icon(Icons.Filled.Done, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Mark reunited")
                    }
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onRemove) {
                    Icon(Icons.Filled.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Remove")
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: WatchStatus) {
    val (label, color) = when (status) {
        WatchStatus.SEARCHING -> "Searching" to MaterialTheme.colorScheme.tertiary
        WatchStatus.LOCATED -> "Located" to Color(0xFF2E7D32)
        WatchStatus.REUNITED -> "Reunited" to MaterialTheme.colorScheme.primary
    }
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.18f)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

private fun iconForSource(source: WatchSource): ImageVector = when (source) {
    WatchSource.DEPENDENT -> Icons.Filled.ChildCare
    WatchSource.SOS_AUTO -> Icons.Filled.NotificationsActive
    WatchSource.MANUAL -> Icons.Filled.Visibility
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun InfoBanner(text: String, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Icon(
                Icons.Filled.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
