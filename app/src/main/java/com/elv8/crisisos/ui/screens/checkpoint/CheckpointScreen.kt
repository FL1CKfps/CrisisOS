package com.elv8.crisisos.ui.screens.checkpoint

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elv8.crisisos.domain.model.Checkpoint
import com.elv8.crisisos.domain.model.DocumentsRequired
import com.elv8.crisisos.domain.model.ThreatLevel
import com.elv8.crisisos.domain.model.VerificationStatus
import com.elv8.crisisos.domain.model.WaitTime
import com.elv8.crisisos.ui.components.CrisisCard
import com.elv8.crisisos.ui.components.EmptyState
import com.elv8.crisisos.ui.components.LocalTopBarState
import kotlinx.coroutines.delay

/* ------------------------------------------------------------------ */
/*  Entry point                                                       */
/* ------------------------------------------------------------------ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckpointScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAiAssistant: () -> Unit = {},
    onNavigateToMaps: () -> Unit = {},
    viewModel: CheckpointViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val topBarState = LocalTopBarState.current

    LaunchedEffect(Unit) {
        topBarState.update(
            title = { Text("CHECKPOINT INTEL", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = { viewModel.refreshCheckpoints() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh & purge stale")
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.startNewReport() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("REPORT", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PrivacyBanner()

            ConfidentialityRow(reportCount = uiState.checkpoints.size)

            when {
                uiState.isLoading -> {
                    Spacer(Modifier.height(24.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            "Purging reports older than 2 h…",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                uiState.checkpoints.isEmpty() -> EmptyState(
                    icon = Icons.Default.Security,
                    title = "Clear Route",
                    subtitle = "No active checkpoint reports nearby. Tap REPORT if you've encountered one.",
                    actionLabel = "Refresh",
                    onAction = { viewModel.refreshCheckpoints() }
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.checkpoints, key = { it.id }) { cp ->
                        CheckpointCard(
                            checkpoint = cp,
                            onTap = { viewModel.selectCheckpoint(cp) },
                            onNegotiationScript = onNavigateToAiAssistant,
                            onAlternateRoute = onNavigateToMaps
                        )
                    }
                    item {
                        Spacer(Modifier.height(8.dp))
                        AggregationExplainerCard()
                    }
                }
            }
        }
    }

    // ---- Report sheet (update existing OR new report) ----
    val targetCheckpoint = uiState.selectedCheckpoint
    if (targetCheckpoint != null || uiState.composeNewReport) {
        ReportSheet(
            existing = targetCheckpoint,
            isNewReport = uiState.composeNewReport,
            onDismiss = { viewModel.cancelComposer() },
            onSubmit = { name, grid, threat, docs, wait, note ->
                viewModel.submitReport(
                    checkpointId = targetCheckpoint?.id,
                    name = name,
                    gridLabel = grid,
                    threatLevel = threat,
                    docs = docs,
                    wait = wait,
                    anonymousNote = note
                )
            }
        )
    }
}

/* ------------------------------------------------------------------ */
/*  Privacy / methodology banners                                     */
/* ------------------------------------------------------------------ */

@Composable
private fun PrivacyBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .background(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Shield,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "ANONYMOUS BY DESIGN",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "No CRS ID attached. Reports are aggregated to a 1 km² grid and auto-expire after 2 hours.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ConfidentialityRow(reportCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "ACTIVE REPORTS",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.weight(1f))
        Text(
            "$reportCount within 2 h window",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/* ------------------------------------------------------------------ */
/*  Checkpoint card                                                   */
/* ------------------------------------------------------------------ */

@Composable
private fun CheckpointCard(
    checkpoint: Checkpoint,
    onTap: () -> Unit,
    onNegotiationScript: () -> Unit,
    onAlternateRoute: () -> Unit
) {
    val verification = remember(checkpoint) {
        when {
            checkpoint.verifiedByNgo -> VerificationStatus.NGO_VERIFIED
            checkpoint.reportCount >= 2 -> VerificationStatus.CONFIRMED
            else -> VerificationStatus.UNVERIFIED
        }
    }
    val threatColors = threatColors(checkpoint.threatLevel)
    val freshness = freshnessLabel(checkpoint.lastUpdatedAt)

    CrisisCard(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = threatColors.accent.copy(alpha = 0.45f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onTap() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top row — name, location, threat pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = checkpoint.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Grid · ${checkpoint.location}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(12.dp))
                ThreatPill(checkpoint.threatLevel)
            }

            Spacer(Modifier.height(12.dp))

            // Verification + freshness
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VerificationBadge(verification, checkpoint.reportCount)
                Spacer(Modifier.weight(1f))
                FreshnessChip(freshness)
            }

            Spacer(Modifier.height(12.dp))

            // Wait time + docs row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetaPill(
                    icon = Icons.Default.AccessTime,
                    label = waitLabel(checkpoint.waitTime),
                    tint = if (checkpoint.waitTime == WaitTime.BLOCKED) WarningRed else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                MetaPill(
                    icon = Icons.Default.Description,
                    label = docsLabel(checkpoint.docsRequired),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }

            // Anonymous note preview
            if (checkpoint.notes.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Anonymous note: ${checkpoint.notes}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Routing & negotiation CTAs.
            //
            // Anti-misuse (Feature 7 spec): high-impact actions are gated
            // behind CONFIRMED status (>=2 reports) or NGO override, so
            // that a single anonymous report cannot trigger reroute or
            // negotiation flows on its own.
            val isDangerous = checkpoint.threatLevel == ThreatLevel.HOSTILE ||
                checkpoint.waitTime == WaitTime.BLOCKED
            val isCorroborated = verification != VerificationStatus.UNVERIFIED
            AnimatedVisibility(visible = isDangerous && isCorroborated) {
                Column {
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onAlternateRoute,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Reroute", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = onNegotiationScript,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Script", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            // Show a corroboration-needed hint instead, so users aren't
            // pushed to take action on a single unverified hostile report.
            AnimatedVisibility(visible = isDangerous && !isCorroborated) {
                Column {
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.HelpOutline,
                            contentDescription = null,
                            tint = UnverifiedAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Reroute & negotiation hints unlock once a second report corroborates this.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Pills, badges, chips                                              */
/* ------------------------------------------------------------------ */

@Composable
private fun ThreatPill(level: ThreatLevel) {
    val c = threatColors(level)
    Row(
        modifier = Modifier
            .background(c.accent.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(c.icon, contentDescription = null, tint = c.accent, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = c.label,
            color = c.accent,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun VerificationBadge(status: VerificationStatus, reportCount: Int) {
    val (icon, label, tint) = when (status) {
        VerificationStatus.NGO_VERIFIED -> Triple(
            Icons.Default.Verified,
            "NGO VERIFIED SAFE",
            VerifiedBlue
        )
        VerificationStatus.CONFIRMED -> Triple(
            Icons.Default.CheckCircle,
            "CONFIRMED · $reportCount reports",
            ConfirmedGreen
        )
        VerificationStatus.UNVERIFIED -> Triple(
            Icons.Default.HelpOutline,
            "UNVERIFIED · 1 report",
            UnverifiedAmber
        )
    }
    Row(
        modifier = Modifier
            .background(tint.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FreshnessChip(label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            Icons.Default.AccessTime,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(12.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MetaPill(
    icon: ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/* ------------------------------------------------------------------ */
/*  Aggregation explainer (full Feature-7 spec dump)                  */
/* ------------------------------------------------------------------ */

@Composable
private fun AggregationExplainerCard() {
    CrisisCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.GppGood,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "HOW THIS FEED WORKS",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(10.dp))

            val rules = listOf(
                "Reports are aggregated to a 1 km² grid — never your exact coordinates.",
                "Status shown is the majority vote across reports in the last 2 hours.",
                "Reports auto-expire after 2 hours and are purged from the mesh.",
                "1 report = UNVERIFIED · ≥ 2 reports = CONFIRMED.",
                "An NGO sender flagging a checkpoint SAFE marks it NGO VERIFIED SAFE.",
                "Maps auto-suggests an alternate route when you approach a HOSTILE checkpoint.",
                "If reaching one is unavoidable, the AI assistant generates a negotiation script.",
                "Anti-misuse: aggregation + anonymity prevent any single report from triggering action."
            )
            rules.forEach { rule ->
                Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                    Text(
                        "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        rule,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Report sheet — 7-step Feature-7 flow                              */
/* ------------------------------------------------------------------ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportSheet(
    existing: Checkpoint?,
    isNewReport: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (
        name: String,
        grid: String,
        threat: ThreatLevel,
        docs: DocumentsRequired,
        wait: WaitTime,
        note: String
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var nameField by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var gridField by rememberSaveable { mutableStateOf(existing?.location ?: "") }
    var threatLevel by rememberSaveable { mutableStateOf(existing?.threatLevel ?: ThreatLevel.UNKNOWN) }
    var docs by rememberSaveable { mutableStateOf(existing?.docsRequired ?: DocumentsRequired.NONE) }
    var wait by rememberSaveable { mutableStateOf(existing?.waitTime ?: WaitTime.UNDER_15M) }
    var note by rememberSaveable { mutableStateOf("") }

    val canSubmit = (nameField.isNotBlank() && gridField.isNotBlank()) || existing != null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Text(
                if (isNewReport) "NEW CHECKPOINT REPORT" else "UPDATE REPORT",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (isNewReport) "Step-by-step report flow" else (existing?.name ?: ""),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            if (!isNewReport && existing != null) {
                Text(
                    "Grid · ${existing.location}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))
            AnonymityReminder()

            // Optional name + grid (new-report mode only)
            if (isNewReport) {
                Spacer(Modifier.height(20.dp))
                StepHeader(1, "Identify the checkpoint")
                OutlinedTextField(
                    value = nameField,
                    onValueChange = { nameField = it },
                    label = { Text("Checkpoint name or landmark") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = gridField,
                    onValueChange = { gridField = it },
                    label = { Text("Grid label (1 km² area)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Step 1/2 — Threat status
            Spacer(Modifier.height(20.dp))
            StepHeader(if (isNewReport) 2 else 1, "Threat status")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThreatLevel.entries.forEach { lvl ->
                    val sel = lvl == threatLevel
                    val c = threatColors(lvl)
                    FilterChip(
                        selected = sel,
                        onClick = { threatLevel = lvl },
                        label = { Text(c.label, fontWeight = FontWeight.Bold) },
                        leadingIcon = { Icon(c.icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = c.accent.copy(alpha = 0.22f),
                            selectedLabelColor = c.accent,
                            selectedLeadingIconColor = c.accent
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Step 2/3 — Documents
            Spacer(Modifier.height(20.dp))
            StepHeader(if (isNewReport) 3 else 2, "Documents required")
            ChipGrid(
                items = DocumentsRequired.entries.toList(),
                isSelected = { it == docs },
                onSelect = { docs = it },
                label = { docsLabel(it) }
            )

            // Step 3/4 — Wait time
            Spacer(Modifier.height(20.dp))
            StepHeader(if (isNewReport) 4 else 3, "Wait time")
            ChipGrid(
                items = WaitTime.entries.toList(),
                isSelected = { it == wait },
                onSelect = { wait = it },
                label = { waitLabel(it) }
            )

            // Step 4/5 — Anonymous note
            Spacer(Modifier.height(20.dp))
            StepHeader(if (isNewReport) 5 else 4, "Anonymous note (optional)")
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = { Text("e.g. \"3 armed personnel checking IDs\"") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                shape = RoundedCornerShape(12.dp),
                supportingText = {
                    Text(
                        "Your name and CRS ID are NEVER attached to this note.",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )

            // Submit
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    onSubmit(nameField, gridField, threatLevel, docs, wait, note)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp),
                enabled = canSubmit
            ) {
                Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("BROADCAST ANONYMOUSLY", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Once you submit, the report is added to a 1 km² grid bucket. " +
                    "If at least one other person has reported the same area in the last 2 hours, " +
                    "the status flips to CONFIRMED.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StepHeader(number: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$number",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun AnonymityReminder() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(VerifiedBlue.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = VerifiedBlue, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            "This report is anonymous. No CRS ID is attached. " +
                "Reports auto-expire after 2 h.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun <T> ChipGrid(
    items: List<T>,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    label: (T) -> String
) {
    // 2-column wrap so all four enum values are visible without scrolling
    val rows = items.chunked(2)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { item ->
                    FilterChip(
                        selected = isSelected(item),
                        onClick = { onSelect(item) },
                        label = { Text(label(item), fontWeight = FontWeight.Medium) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // Pad odd-length last row so chips don't grow to full-width
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Helpers                                                           */
/* ------------------------------------------------------------------ */

private data class ThreatPalette(
    val accent: Color,
    val label: String,
    val icon: ImageVector
)

@Composable
private fun threatColors(level: ThreatLevel): ThreatPalette = when (level) {
    ThreatLevel.SAFE -> ThreatPalette(SafeGreen, "SAFE", Icons.Default.CheckCircle)
    ThreatLevel.HOSTILE -> ThreatPalette(WarningRed, "HOSTILE", Icons.Default.Warning)
    ThreatLevel.UNKNOWN -> ThreatPalette(UnverifiedAmber, "UNKNOWN", Icons.Default.ReportProblem)
}

private fun docsLabel(d: DocumentsRequired) = when (d) {
    DocumentsRequired.NONE -> "No docs"
    DocumentsRequired.ID -> "ID required"
    DocumentsRequired.PASSPORT -> "Passport required"
    DocumentsRequired.MULTIPLE -> "Multiple docs"
}

private fun waitLabel(w: WaitTime) = when (w) {
    WaitTime.UNDER_15M -> "< 15 min"
    WaitTime.FIFTEEN_TO_60M -> "15 – 60 min"
    WaitTime.OVER_60M -> "1 hr +"
    WaitTime.BLOCKED -> "BLOCKED"
}

/**
 * Live-updating freshness label; reflects the 2 h TTL of Feature 7.
 * Recomputes once per minute so the list visibly counts down toward
 * "Expired".
 */
@Composable
private fun freshnessLabel(lastUpdatedAt: Long): String {
    val ttlMs = 2L * 60L * 60L * 1000L
    val labelState = produceState(initialValue = computeFreshness(lastUpdatedAt, ttlMs), lastUpdatedAt) {
        while (true) {
            value = computeFreshness(lastUpdatedAt, ttlMs)
            delay(60_000L)
        }
    }
    return labelState.value
}

private fun computeFreshness(lastUpdatedAt: Long, ttlMs: Long): String {
    val remaining = (lastUpdatedAt + ttlMs) - System.currentTimeMillis()
    if (remaining <= 0) return "Expired"
    val mins = remaining / 60_000L
    return when {
        mins < 1 -> "Expires in <1 m"
        mins < 60 -> "Expires in ${mins} m"
        else -> {
            val h = mins / 60
            val m = mins % 60
            "Expires in ${h}h ${m}m"
        }
    }
}

/* Palette constants — kept hex-literal so legibility is identical in
 * light & dark mode (Compose theme would otherwise shift the meaning
 * of "danger" between modes). */
private val SafeGreen = Color(0xFF2E7D32)
private val WarningRed = Color(0xFFD32F2F)
private val UnverifiedAmber = Color(0xFFE6A100)
private val ConfirmedGreen = Color(0xFF388E3C)
private val VerifiedBlue = Color(0xFF1565C0)
