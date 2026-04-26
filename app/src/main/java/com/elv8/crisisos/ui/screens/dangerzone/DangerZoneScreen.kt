package com.elv8.crisisos.ui.screens.dangerzone

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elv8.crisisos.domain.model.DangerZone
import com.elv8.crisisos.domain.model.ThreatLevel
import com.elv8.crisisos.ui.components.CrisisCard
import com.elv8.crisisos.ui.components.EmptyState
import com.elv8.crisisos.ui.components.LocalTopBarState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DangerZoneScreen(
    onNavigateBack: () -> Unit,
    viewModel: DangerZoneViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var sheetVisible by remember { mutableStateOf(false) }
    val topBarState = LocalTopBarState.current

    LaunchedEffect(Unit) {
        topBarState.update(
            title = { Text("Danger Zones") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            HeaderCard(uiState = uiState)
            Spacer(modifier = Modifier.height(16.dp))
            FilterChipsRow(selectedFilter = uiState.selectedFilter, onFilterSelected = viewModel::filterByLevel)
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (uiState.isLoading) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }
                    }
                } else if (uiState.zones.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Default.WarningAmber,
                            title = "No threats reported",
                            subtitle = "No threats reported in your area  stay alert",
                            modifier = Modifier.fillMaxWidth().padding(top = 32.dp)
                        )
                    }
                } else {
                    items(uiState.zones, key = { it.id }) { zone -> ThreatCard(zone = zone) }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }

        FloatingActionButton(
            onClick = { sheetVisible = true },
            containerColor = Color(0xFFFF9800),
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Report Zone")
        }
    }

    if (sheetVisible) {
        ReportZoneBottomSheet(
            onDismiss = { sheetVisible = false },
            onSubmit = { title, desc, level, loc ->
                viewModel.reportNewZone(title, desc, level, loc)
                sheetVisible = false
            }
        )
    }
}

@Composable
fun ThreatCard(zone: DangerZone) {
    var expanded by remember { mutableStateOf(false) }
    val threatColor = when (zone.threatLevel) {
        ThreatLevel.LOW -> Color(0xFF4CAF50)
        ThreatLevel.MEDIUM -> Color(0xFFFFC107)
        ThreatLevel.HIGH -> Color(0xFFFF5722)
        ThreatLevel.CRITICAL -> Color(0xFFE53935)
        ThreatLevel.UNKNOWN -> Color.Gray
    }
    val threatLabel = if (zone.threatLevel in listOf(ThreatLevel.CRITICAL, ThreatLevel.HIGH)) "AVOID" else "CAUTION"

    CrisisCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .heightIn(min = 56.dp)
                    .background(threatColor)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = zone.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = threatLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = threatColor,
                        modifier = Modifier
                            .background(threatColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                            .border(1.dp, threatColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = zone.distance, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = zone.reportedBy,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (expanded) zone.description else zone.description.take(60) + if (zone.description.length > 60) "..." else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Reported: ${zone.timestamp}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { }, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(24.dp)) {
                                Text("Report Outdated")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportZoneBottomSheet(onDismiss: () -> Unit, onSubmit: (String, String, ThreatLevel, String) -> Unit) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var selectedLevel by remember { mutableStateOf(ThreatLevel.MEDIUM) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text("Report Danger Zone", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Event Title") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Location (Approx distance or intersection)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
    }
}

@Composable
private fun HeaderCard(uiState: DangerZoneUiState) {
    CrisisCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = Color(0xFFFF9800)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Active Threat Zones",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${uiState.zones.size} reported  ${uiState.userLocation}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun FilterChipsRow(
    selectedFilter: ThreatLevel?,
    onFilterSelected: (ThreatLevel?) -> Unit
) {
    val options: List<Pair<String, ThreatLevel?>> = listOf(
        "All" to null,
        "Low" to ThreatLevel.LOW,
        "Medium" to ThreatLevel.MEDIUM,
        "High" to ThreatLevel.HIGH,
        "Critical" to ThreatLevel.CRITICAL
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (label, value) ->
            androidx.compose.material3.FilterChip(
                selected = selectedFilter == value,
                onClick = { onFilterSelected(value) },
                label = { Text(label) }
            )
        }
    }
}
