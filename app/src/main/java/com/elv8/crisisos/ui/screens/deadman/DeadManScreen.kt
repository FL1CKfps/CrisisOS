package com.elv8.crisisos.ui.screens.deadman

// DEAD MAN SWITCH SCREEN — Feature 5 (CrisisOS_Context.md)
//
// This screen is the user-facing surface for everything Feature 5 promises:
//   • Configurable interval (canonical 6h/12h/24h/48h + short demo intervals)
//   • Pre-composed message
//   • Recipients designated by CRS ID OR phone number (or both)
//   • Live armed/disarmed status with countdown ring
//   • Pre-deadline silent reminder note (30 min before)
//   • Transparency panel listing exactly what gets transmitted
//   • Multi-channel delivery badges (Mesh / Push / SMS / Email)
//   • Auto-initiated Missing Person search note (ecosystem trigger → Feature 6)
//   • Resilience guarantees (phone dies / no internet / no recipient connectivity)

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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elv8.crisisos.ui.components.LocalTopBarState

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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            TimerSection(uiState = uiState)

            Spacer(modifier = Modifier.height(16.dp))

            StatusPill(isActive = uiState.isActive)

            Spacer(modifier = Modifier.height(20.dp))

            AnimatedVisibility(visible = uiState.isActive) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    CheckInButton(onClick = { viewModel.checkIn() })
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "You'll get a silent reminder 30 minutes before the deadline.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    ArmedSummaryCard(uiState = uiState)
                    Spacer(modifier = Modifier.height(12.dp))
                    DeliveryChannelsRow()
                    Spacer(modifier = Modifier.height(12.dp))
                    EcosystemTriggerCard()
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

            AnimatedVisibility(visible = uiState.errorMessage != null) {
                uiState.errorMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(12.dp))
                    ErrorBanner(message = msg, onDismiss = { viewModel.clearError() })
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // Sticky bottom CTA — always visible regardless of scroll position.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 20.dp, vertical = 14.dp)
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
            onAdd = { crsId, phone, label ->
                viewModel.addEscalationContact(crsId, phone, label)
            },
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
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 18.dp.toPx(), cap = StrokeCap.Round)
            )
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
                fontSize = 46.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (uiState.isActive) "TIME REMAINING" else "READY",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
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
// CHECK-IN + ARMED VIEW
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
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "I'M OKAY — CHECK IN",
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
            Text(
                text = "WHAT RECIPIENTS WILL RECEIVE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            SummaryRow(
                icon = Icons.Default.Person,
                label = "Your CRS ID",
                value = "Identifies you to family / NGOs"
            )
            Spacer(modifier = Modifier.height(8.dp))
            SummaryRow(
                icon = Icons.Default.LocationOn,
                label = "Last known GPS",
                value = "Plus accuracy and timestamp"
            )
            Spacer(modifier = Modifier.height(8.dp))
            SummaryRow(
                icon = Icons.Default.Shield,
                label = "Last camp checked into",
                value = "If known"
            )
            Spacer(modifier = Modifier.height(8.dp))
            SummaryRow(
                icon = Icons.AutoMirrored.Filled.Message,
                label = "Your pre-written note",
                value = uiState.alertMessage,
                multiLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            SummaryRow(
                icon = Icons.Default.Schedule,
                label = "Trigger interval",
                value = "${formatInterval(uiState.intervalMinutes)} (${uiState.escalationContacts.size} contact${if (uiState.escalationContacts.size == 1) "" else "s"})"
            )
        }
    }
}

@Composable
private fun SummaryRow(
    icon: ImageVector,
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
                maxLines = if (multiLine) 4 else 1
            )
        }
    }
}

@Composable
private fun DeliveryChannelsRow() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "DELIVERY CHANNELS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChannelChip(
                    icon = Icons.Default.Bluetooth,
                    label = "Mesh",
                    available = true,
                    modifier = Modifier.weight(1f)
                )
                ChannelChip(
                    icon = Icons.Default.Notifications,
                    label = "Push",
                    available = true,
                    modifier = Modifier.weight(1f)
                )
                ChannelChip(
                    icon = Icons.Default.PhoneAndroid,
                    label = "SMS",
                    available = false,
                    modifier = Modifier.weight(1f)
                )
                ChannelChip(
                    icon = Icons.Default.Send,
                    label = "Email",
                    available = false,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Mesh + push always on. SMS/email queued via NGO anchor when online.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChannelChip(
    icon: ImageVector,
    label: String,
    available: Boolean,
    modifier: Modifier = Modifier
) {
    val bg = if (available) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val fg = if (available) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    }
    Column(
        modifier = modifier
            .background(bg, RoundedCornerShape(10.dp))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = fg)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = fg
        )
        if (!available) {
            Text(
                text = "soon",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = fg.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun EcosystemTriggerCard() {
    // Per CrisisOS_Context.md: "Dead man's switch firing automatically
    // initiates a Missing Person search (Feature 6) for the user's CRS ID."
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.PersonSearch,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Auto Missing-Person search",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "If this fires, the mesh starts searching for your CRS ID automatically.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// SETTINGS (DISARMED VIEW)
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
        Spacer(modifier = Modifier.height(12.dp))
        ResilienceCard()
    }
}

@Composable
private fun SectionCard(
    icon: ImageVector,
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
        border = BorderStroke(
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
        // Ordered: short demo intervals first, then the canonical 6h/12h/24h/48h
        // set called out in CrisisOS_Context.md → Feature 5 Setup.
        val intervals = listOf(
            15 to "15m",
            30 to "30m",
            60 to "1h",
            120 to "2h",
            240 to "4h",
            360 to "6h",
            720 to "12h",
            1440 to "24h",
            2880 to "48h"
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(intervals) { (mins, label) ->
                val selected = uiState.intervalMinutes == mins
                FilterChip(
                    selected = selected,
                    onClick = { onIntervalSelected(mins) },
                    label = {
                        Text(
                            label,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "If you don't tap \"I'm okay\" within this window, your contacts get alerted automatically. A silent reminder fires 30 min before deadline.",
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
            placeholder = {
                Text("e.g. I was heading to Camp B. Contact Mom +91 98765 43210.")
            }
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Sent verbatim to every recipient alongside your CRS ID, GPS, and last camp.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
            FilledTonalButton(
                onClick = onAddContact,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
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
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Recipients are addressed by CRS ID (in-mesh) or phone (SMS fallback).",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
            text = "Add at least one CRS ID or phone number to receive your alert.",
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
                text = contact.label.ifBlank { contact.crsId.ifBlank { contact.phoneNumber.orEmpty() } },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            // Channel badges + secondary identifiers underneath the label.
            val secondary = buildList {
                if (contact.crsId.isNotBlank() && contact.crsId != contact.label) add(contact.crsId)
                if (!contact.phoneNumber.isNullOrBlank()) add("☎ ${contact.phoneNumber}")
            }.joinToString("  •  ")
            if (secondary.isNotBlank()) {
                Text(
                    text = secondary,
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

@Composable
private fun ResilienceCard() {
    // Per CrisisOS_Context.md → Feature 5 "Loopholes handled".
    SectionCard(icon = Icons.Default.Security, title = "Resilience") {
        ResilienceLine(
            icon = Icons.Default.PhoneAndroid,
            text = "Phone dies before deadline → mesh anchor still fires the alert."
        )
        Spacer(modifier = Modifier.height(8.dp))
        ResilienceLine(
            icon = Icons.Default.Bluetooth,
            text = "No internet since last sync → uses last sync timestamp; fires anyway."
        )
        Spacer(modifier = Modifier.height(8.dp))
        ResilienceLine(
            icon = Icons.Default.Send,
            text = "No connectivity to recipients → alert is queued and retried via every available channel."
        )
    }
}

@Composable
private fun ResilienceLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .size(16.dp)
                .padding(top = 2.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ────────────────────────────────────────────────────────────────────────────
// ADD CONTACT DIALOG (CRS ID and / or phone number)
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun AddContactDialog(
    availableFamilyContacts: List<EscalationContact>,
    alreadyAdded: List<EscalationContact>,
    inlineError: String?,
    onAdd: (crsId: String, phone: String, label: String) -> Unit,
    onClearError: () -> Unit,
    onDismiss: () -> Unit
) {
    var crsId by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("") }

    val addedIds = remember(alreadyAdded) { alreadyAdded.map { it.crsId.lowercase() }.toSet() }
    val quickPickable = remember(availableFamilyContacts, addedIds) {
        availableFamilyContacts.filter { it.crsId.lowercase() !in addedIds }
    }

    val canAdd = crsId.isNotBlank() || phone.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add escalation contact", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Designated by CRS ID, phone number, or both. They'll receive your alert if you don't check in.",
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
                    label = { Text("CRS ID") },
                    placeholder = { Text("e.g. CRS-7K3M-9X2P") },
                    singleLine = true,
                    isError = inlineError != null,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Next
                    ),
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = {
                        phone = it
                        if (inlineError != null) onClearError()
                    },
                    label = { Text("Phone number") },
                    placeholder = { Text("e.g. +91 98765 43210") },
                    singleLine = true,
                    isError = inlineError != null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next
                    ),
                    leadingIcon = {
                        Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(Modifier.height(4.dp))
                Text(
                    text = "At least one of CRS ID or phone is required.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(10.dp))

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
                                onClick = { onAdd(c.crsId, c.phoneNumber.orEmpty(), c.label) },
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
                onClick = { onAdd(crsId, phone, label) },
                enabled = canAdd
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
            Spacer(modifier = Modifier.height(8.dp))
            val hours = uiState.timeRemainingSeconds / 3600
            val minutes = (uiState.timeRemainingSeconds % 3600) / 60
            val timeLabel = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Auto-SOS in $timeLabel if no check-in",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium
                )
            }
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
