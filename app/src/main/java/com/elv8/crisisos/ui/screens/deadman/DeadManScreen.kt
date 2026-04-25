package com.elv8.crisisos.ui.screens.deadman

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elv8.crisisos.ui.components.LocalTopBarState

/**
 * DEAD MAN SWITCH — Feature 5
 *
 * Visual hierarchy (top → bottom):
 *   1. Hero timer ring with status pill (ARMED / DISARMED).
 *   2. Active state: prominent green CHECK IN button + setting summary card.
 *   3. Inactive state: scrollable settings cards (Interval, Message, Contacts).
 *   4. Inline error banner.
 *   5. Sticky-ish ACTIVATE / DEACTIVATE bottom CTA.
 *
 * Contacts are added inline by CRS ID via [AddContactDialog] — no external
 * "Contacts" page is required. Family contacts already known to the app
 * (when present) are exposed as one-tap quick-pick chips inside the dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeadManScreen(
    onNavigateBack: () -> Unit,
    viewModel: DeadManViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val topBarState = LocalTopBarState.current

    LaunchedEffect(Unit) {
        topBarState.update(
            title = { Text("DEAD MAN'S SWITCH", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Scrollable content. Sticky CTA lives outside this column so it
        // doesn't scroll out of reach when contact lists grow tall.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            TimerSection(uiState = uiState)

            Spacer(modifier = Modifier.height(16.dp))

            StatusPill(isActive = uiState.isActive)

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedVisibility(visible = uiState.isActive) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    CheckInButton(onClick = { viewModel.checkIn() })
                    Spacer(modifier = Modifier.height(16.dp))
                    ArmedSummaryCard(uiState = uiState)
                }
            }

            AnimatedVisibility(
                visible = !uiState.isActive,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                SettingsSection(
                    uiState = uiState,
                    onIntervalSelected = viewModel::setInterval,
                    onMessageChange = viewModel::updateAlertMessage,
                    onAddContact = viewModel::openAddContactDialog,
                    onRemoveContact = viewModel::removeContact
                )
            }

            // Inline error surface — replaces the previous silent no-op when the
            // user tapped Activate without contacts or tried to change settings
            // while the switch was already armed.
            AnimatedVisibility(visible = uiState.errorMessage != null) {
                uiState.errorMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(12.dp))
                    ErrorBanner(message = msg, onDismiss = { viewModel.clearError() })
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Sticky bottom CTA — always visible regardless of scroll position.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            ActivateToggle(uiState = uiState, onToggle = {
                if (uiState.isActive) viewModel.deactivate() else viewModel.activate()
            })
        }
    }

    if (uiState.showAddContactDialog) {
        AddContactDialog(
            availableFamilyContacts = uiState.availableFamilyContacts,
            alreadyAdded = uiState.escalationContacts,
            inlineError = uiState.addContactError,
            onAdd = { crsId, label -> viewModel.addContactByCrsId(crsId, label) },
            onClearError = { viewModel.clearAddContactError() },
            onDismiss = { viewModel.dismissAddContactDialog() }
        )
    }
}

// ────────────────────────────────────────────────────────────────────────────
// TIMER + STATUS
// ────────────────────────────────────────────────────────────────────────────

@SuppressLint("DefaultLocale")
@Composable
private fun TimerSection(uiState: DeadManUiState) {
    val totalSeconds = uiState.intervalMinutes * 60f
    val currentSeconds = uiState.timeRemainingSeconds.toFloat()

    val progress = if (totalSeconds > 0) currentSeconds / totalSeconds else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(1000),
        label = "progressAnim"
    )

    // Color shifts toward warning red as the timer drains, but only when armed.
    val ringColor by animateColorAsState(
        targetValue = when {
            !uiState.isActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            progress < 0.15f -> MaterialTheme.colorScheme.error
            progress < 0.4f -> Color(0xFFFF9800)
            else -> MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(600),
        label = "ringColor"
    )
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(260.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Track
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 18.dp.toPx(), cap = StrokeCap.Round)
            )
            // Progress
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = animatedProgress * 360f,
                useCenter = false,
                style = Stroke(width = 18.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val hours = uiState.timeRemainingSeconds / 3600
            val minutes = (uiState.timeRemainingSeconds % 3600) / 60
            val seconds = uiState.timeRemainingSeconds % 60

            val timeString = if (hours > 0) {
                String.format("%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }

            Text(
                text = timeString,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (uiState.isActive) "TIME REMAINING" else "READY",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Last check-in · ${uiState.lastCheckIn}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusPill(isActive: Boolean) {
    val container by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        label = "statusBg"
    )
    val onContainer by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "statusFg"
    )

    Row(
        modifier = Modifier
            .background(container, RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isActive) {
            // Pulsing dot signals "live monitoring".
            val transition = rememberInfiniteTransition(label = "pulse")
            val alpha by transition.animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(900),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulseAlpha"
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = alpha), CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(onContainer.copy(alpha = 0.5f), CircleShape)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (isActive) "ARMED" else "DISARMED",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = onContainer
        )
    }
}

// ────────────────────────────────────────────────────────────────────────────
// CHECK-IN + ARMED SUMMARY
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun CheckInButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(
            text = "✓  CHECK IN NOW",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun ArmedSummaryCard(uiState: DeadManUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SummaryRow(
                icon = Icons.Default.Schedule,
                label = "Interval",
                value = formatInterval(uiState.intervalMinutes)
            )
            Spacer(modifier = Modifier.height(10.dp))
            SummaryRow(
                icon = Icons.Default.Group,
                label = "Will alert",
                value = if (uiState.escalationContacts.isEmpty()) {
                    "—"
                } else {
                    "${uiState.escalationContacts.size} contact${if (uiState.escalationContacts.size == 1) "" else "s"}"
                }
            )
            Spacer(modifier = Modifier.height(10.dp))
            SummaryRow(
                icon = Icons.AutoMirrored.Filled.Message,
                label = "Message",
                value = uiState.alertMessage,
                multiLine = true
            )
        }
    }
}

@Composable
private fun SummaryRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    multiLine: Boolean = false
) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .size(18.dp)
                .padding(top = 2.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = if (multiLine) 3 else 1
            )
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// SETTINGS SECTION (inactive state)
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsSection(
    uiState: DeadManUiState,
    onIntervalSelected: (Int) -> Unit,
    onMessageChange: (String) -> Unit,
    onAddContact: () -> Unit,
    onRemoveContact: (EscalationContact) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        IntervalCard(uiState = uiState, onIntervalSelected = onIntervalSelected)
        Spacer(modifier = Modifier.height(12.dp))
        MessageCard(uiState = uiState, onMessageChange = onMessageChange)
        Spacer(modifier = Modifier.height(12.dp))
        ContactsCard(
            contacts = uiState.escalationContacts,
            onAddContact = onAddContact,
            onRemoveContact = onRemoveContact
        )
    }
}

@Composable
private fun SectionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (trailing != null) trailing()
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun IntervalCard(
    uiState: DeadManUiState,
    onIntervalSelected: (Int) -> Unit
) {
    SectionCard(icon = Icons.Default.Schedule, title = "Timer interval") {
        val intervals = listOf(15 to "15m", 30 to "30m", 60 to "1h", 120 to "2h", 240 to "4h")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(intervals) { (mins, label) ->
                val selected = uiState.intervalMinutes == mins
                FilterChip(
                    selected = selected,
                    onClick = { onIntervalSelected(mins) },
                    label = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "If you don't check in within this window, your contacts get alerted automatically.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MessageCard(
    uiState: DeadManUiState,
    onMessageChange: (String) -> Unit
) {
    SectionCard(icon = Icons.AutoMirrored.Filled.Message, title = "Auto-SOS message") {
        OutlinedTextField(
            value = uiState.alertMessage,
            onValueChange = onMessageChange,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 4,
            shape = RoundedCornerShape(12.dp),
            placeholder = { Text("e.g. I haven't checked in. Send help.") }
        )
    }
}

@Composable
private fun ContactsCard(
    contacts: List<EscalationContact>,
    onAddContact: () -> Unit,
    onRemoveContact: (EscalationContact) -> Unit
) {
    SectionCard(
        icon = Icons.Default.Group,
        title = "Escalation contacts",
        trailing = {
            // Compact "+ Add" pill instead of a bare icon — clearer affordance.
            FilledTonalButton(
                onClick = onAddContact,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 12.dp, vertical = 6.dp
                ),
                shape = RoundedCornerShape(50)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    ) {
        if (contacts.isEmpty()) {
            EmptyContactsState(onAddContact = onAddContact)
        } else {
            contacts.forEachIndexed { index, contact ->
                if (index > 0) Spacer(modifier = Modifier.height(8.dp))
                ContactRow(contact = contact, onRemove = { onRemoveContact(contact) })
            }
        }
    }
}

@Composable
private fun EmptyContactsState(onAddContact: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                RoundedCornerShape(12.dp)
            )
            .padding(vertical = 20.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Group,
            contentDescription = null,
            modifier = Modifier.size(36.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "No contacts yet",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Add at least one CRS ID to receive your alert.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onAddContact) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Add a contact", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ContactRow(contact: EscalationContact, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar circle with initial — gives the list visual texture without
        // relying on real avatars (which we don't carry in escalation state).
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            val initial = contact.label.firstOrNull()?.uppercaseChar()?.toString()
                ?: contact.crsId.firstOrNull()?.uppercaseChar()?.toString()
                ?: "?"
            Text(
                text = initial,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.label.ifBlank { contact.crsId },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            if (contact.label.isNotBlank() && contact.crsId.isNotBlank() && contact.label != contact.crsId) {
                Text(
                    text = contact.crsId,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove ${contact.label}",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// ADD CONTACT DIALOG (inline by CRS ID)
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun AddContactDialog(
    availableFamilyContacts: List<EscalationContact>,
    alreadyAdded: List<EscalationContact>,
    inlineError: String?,
    onAdd: (crsId: String, label: String) -> Unit,
    onClearError: () -> Unit,
    onDismiss: () -> Unit
) {
    var crsId by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("") }

    val addedIds = remember(alreadyAdded) { alreadyAdded.map { it.crsId.lowercase() }.toSet() }
    val quickPickable = remember(availableFamilyContacts, addedIds) {
        availableFamilyContacts.filter { it.crsId.lowercase() !in addedIds }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Add escalation contact", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Enter the CRS ID of the person who should receive your alert if you don't check in.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = crsId,
                    onValueChange = {
                        crsId = it
                        if (inlineError != null) onClearError()
                    },
                    label = { Text("CRS ID *") },
                    placeholder = { Text("e.g. CRS-7K3M-9X2P") },
                    singleLine = true,
                    isError = inlineError != null,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label (optional)") },
                    placeholder = { Text("e.g. Mom, Sister Priya") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (inlineError != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = inlineError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                if (quickPickable.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = "OR PICK FROM YOUR FAMILY CONTACTS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(quickPickable) { c ->
                            AssistChip(
                                onClick = { onAdd(c.crsId, c.label) },
                                label = { Text(c.label.ifBlank { c.crsId }) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(crsId, label) },
                enabled = crsId.isNotBlank()
            ) {
                Text("Add", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ────────────────────────────────────────────────────────────────────────────
// ERROR + ACTIVATE
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Dismiss",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun ActivateToggle(uiState: DeadManUiState, onToggle: () -> Unit) {
    val isActive = uiState.isActive
    val activeRed = MaterialTheme.colorScheme.error
    val activeRedDark = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
    val primary = MaterialTheme.colorScheme.primary
    val primaryDark = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)

    val brush = remember(isActive, activeRed, primary) {
        if (isActive) {
            Brush.horizontalGradient(listOf(activeRed, activeRedDark))
        } else {
            Brush.horizontalGradient(listOf(primary, primaryDark))
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(brush)
                .border(
                    width = 1.dp,
                    color = if (isActive) activeRed else primary,
                    shape = RoundedCornerShape(20.dp)
                )
        ) {
            // Use a Button with transparent background so we keep ripple +
            // accessibility semantics without re-implementing them.
            Button(
                onClick = onToggle,
                modifier = Modifier.fillMaxSize(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(20.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Icon(
                    imageVector = if (isActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isActive) "DEACTIVATE" else "ACTIVATE SWITCH",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        if (isActive) {
            Spacer(modifier = Modifier.height(10.dp))
            val hours = uiState.timeRemainingSeconds / 3600
            val minutes = (uiState.timeRemainingSeconds % 3600) / 60
            val timeLabel = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
            Text(
                text = "Auto-SOS in $timeLabel if no check-in",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// HELPERS
// ────────────────────────────────────────────────────────────────────────────

private fun formatInterval(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60}h"
    else -> "${minutes / 60}h ${minutes % 60}m"
}
