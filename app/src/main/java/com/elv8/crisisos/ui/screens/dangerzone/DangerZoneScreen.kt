package com.elv8.crisisos.ui.screens.dangerzone

import com.elv8.crisisos.ui.components.EmptyState
import androidx.compose.material.icons.filled.WarningAmber

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

@OptIn(ExperimentalMaterial3Api::class)@Composable
fun DangerZoneScreen(
    onNavigateBack: () -> Unit,
    viewModel: DangerZoneViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var sheetVisible by remember { mutableStateOf(false) }
    val topBarState = com.elv8.crisisos.ui.components.LocalTopBarState.current

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
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            HeaderCard(uiState = uiState)

            Spacer(modifier = Modifier.height(16.dp))

            FilterChipsRow(
                selectedFilter = uiState.selectedFilter,
                onFilterSelected = viewModel::filterByLevel
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (uiState.isLoading) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
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
                    items(uiState.zones, key = { it.id }) { zone ->
                        ThreatCard(zone = zone)
                    }
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
fun HeaderCard(uiState: DangerZoneUiState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF8B0000)) // Dark red
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("THREAT INTELLIGENCE", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(uiState.userLocation, style = MaterialTheme.typography.bodyLarge, color = Color.White)
            Text("Last updated: 2 minutes ago", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
            Spacer(modifier = Modifier.height(8.dp))
            Text("Sources: ACLED · Mesh Reports · User Submitted", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFF9800))
        }
    }
}

@Composable
fun FilterChipsRow(selectedFilter: ThreatLevel?, onFilterSelected: (ThreatLevel?) -> Unit) {
    val levels = listOf(null) + ThreatLevel.entries.toTypedArray()

    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(levels) { level ->
            val isSelected = selectedFilter == level
            val label = level?.name ?: "ALL"

            val bgColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                label = "filterBg"
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "filterText"
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(bgColor)
                    .clickable { onFilterSelected(if (isSelected) null else level) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = textColor)
            }
        }
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
                    .height(IntrinsicSize.Min)
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = zone.distance,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = zone.reportedBy,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (expanded) zone.description else zone.description.take(60) + if (zone.description.length > 60) "..." else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Reported: ${zone.timestamp}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { /* Handle report outdated */ }, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(24.dp)) {
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var selectedLevel by remember { mutableStateOf(ThreatLevel.MEDIUM) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text("Report Danger Zone", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Event Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("Location (Approx distance or intersection)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text("Threat severity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ThreatLevel.entries.toTypedArray()) { level ->
                    if (level == ThreatLevel.UNKNOWN) return@items
                    FilterChip(
                        selected = selectedLevel == level,
                        onClick = { selectedLevel = level },
                        label = { Text(level.name) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { onSubmit(title, description, selectedLevel, location) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = title.isNotBlank() && location.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("SUBMIT REPORT", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onError)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}



