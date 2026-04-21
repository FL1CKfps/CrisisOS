package com.elv8.crisisos.ui.screens.checkpoint

import com.elv8.crisisos.ui.components.EmptyState
import androidx.compose.material.icons.filled.Security

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elv8.crisisos.domain.model.Checkpoint
import com.elv8.crisisos.ui.components.CrisisCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckpointScreen(
    onNavigateBack: () -> Unit,
    viewModel: CheckpointViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Checkpoint Intelligence", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshCheckpoints() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Data")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            SectionHeader(title = "CHECKPOINTS NEARBY", count = uiState.checkpoints.size)

            Spacer(modifier = Modifier.height(12.dp))

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.checkpoints.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Security,
                    title = "Clear Route",
                    subtitle = "No active checkpoints reported nearby.",
                    actionLabel = "Refresh Maps",
                    onAction = { viewModel.refreshCheckpoints() }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(uiState.checkpoints, key = { it.id }) { checkpoint ->
                        CheckpointCard(checkpoint = checkpoint) {
                            viewModel.selectCheckpoint(it)
                        }
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (uiState.selectedCheckpoint != null) {
        CheckpointDetailSheet(
            checkpoint = uiState.selectedCheckpoint!!,
            onDismiss = { viewModel.selectCheckpoint(null) },
            onSubmitUpdate = { cpId, isOpen, rating, notes ->
                viewModel.submitUpdate(cpId, isOpen, rating, notes)
            }
        )
    }
}

@Composable
fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.weight(1f))
        Text("$count active", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun CheckpointCard(checkpoint: Checkpoint, onClick: (Checkpoint) -> Unit) {
    val statusColor = if (checkpoint.isOpen) Color(0xFF4CAF50) else Color(0xFFF44336)
    val statusText = if (checkpoint.isOpen) "OPEN" else "CLOSED"

    CrisisCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(checkpoint) }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = checkpoint.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(text = checkpoint.location, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                    modifier = Modifier
                        .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                // Controller Tag
                Text(
                    text = checkpoint.controlledBy.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
                )

                // Icons
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (checkpoint.allowsCivilians) {
                        Icon(Icons.Default.Accessibility, contentDescription = "Civilians Allowed", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    } else {
                        Icon(Icons.Default.Block, contentDescription = "Restricted", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    }
                    if (checkpoint.requiresDocuments) {
                        Icon(Icons.Default.Description, contentDescription = "Docs Required", modifier = Modifier.size(18.dp), tint = Color(0xFFFF9800))
                    }
                }

                // Safety Rating
                SafetyStarRating(rating = checkpoint.safetyRating, iconSize = 16)
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Last report: ${checkpoint.lastReport}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${checkpoint.reportCount} reports", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckpointDetailSheet(
    checkpoint: Checkpoint,
    onDismiss: () -> Unit,
    onSubmitUpdate: (String, Boolean, Int, String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isCurrentlyOpen by remember { mutableStateOf(checkpoint.isOpen) }
    var currentRating by remember { mutableStateOf(checkpoint.safetyRating) }
    var userNotes by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(checkpoint.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(checkpoint.location, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                
                CrisisCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("CURRENT INTELLIGENCE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(checkpoint.notes, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text("REPORT UPDATE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Is this checkpoint open?", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Row(
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(if (isCurrentlyOpen) Color(0xFF4CAF50) else Color.Transparent)
                                .clickable { isCurrentlyOpen = true }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("YES", color = if (isCurrentlyOpen) Color.White else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(if (!isCurrentlyOpen) Color(0xFFF44336) else Color.Transparent)
                                .clickable { isCurrentlyOpen = false }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("NO", color = if (!isCurrentlyOpen) Color.White else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Safety Rating", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    InteractiveSafetyStarRating(rating = currentRating, onRatingChanged = { currentRating = it })
                }
            }

            item {
                OutlinedTextField(
                    value = userNotes,
                    onValueChange = { userNotes = it },
                    label = { Text("Observation Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            item {
                Button(
                    onClick = { onSubmitUpdate(checkpoint.id, isCurrentlyOpen, currentRating, userNotes) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("SUBMIT INTEL TO MESH", fontWeight = FontWeight.Bold)
                }
            }

            // Fake Report History block
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text("RECENT REPORTS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ReportHistoryItem(time = checkpoint.lastReport, content = checkpoint.notes)
                    ReportHistoryItem(time = "3 hrs ago", content = "Heavy document checking began.")
                    ReportHistoryItem(time = "5 hrs ago", content = "Safe to cross, distributed water initially.")
                }
            }
        }
    }
}

@Composable
fun ReportHistoryItem(time: String, content: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(content, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun SafetyStarRating(rating: Int, iconSize: Int = 20) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 1..5) {
            val filled = i <= rating
            Icon(
                imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = "$i star",
                tint = if (filled) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(iconSize.dp)
            )
        }
    }
}

@Composable
fun InteractiveSafetyStarRating(rating: Int, onRatingChanged: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in 1..5) {
            val filled = i <= rating
            val starTint by animateColorAsState(
                targetValue = if (filled) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                label = "starColorAnim"
            )
            
            Icon(
                imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = "Set $i star rating",
                tint = starTint,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { onRatingChanged(i) }
                    .padding(4.dp)
            )
        }
    }
}
