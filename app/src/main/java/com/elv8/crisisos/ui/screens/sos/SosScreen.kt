package com.elv8.crisisos.ui.screens.sos

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FireExtinguisher
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elv8.crisisos.ui.components.CrisisCard
import com.elv8.crisisos.ui.components.LocalTopBarState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val SosBg = Color(0xFF12060A)
private val SosRed = Color(0xFFE53935)
private val SosRedDeep = Color(0xFFB71C1C)
private val SosAmber = Color(0xFFFFB300)

@Composable
fun SosScreen(
    onNavigateBack: () -> Unit,
    viewModel: SosViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val topBarState = LocalTopBarState.current

    LaunchedEffect(Unit) { topBarState.isVisible = false }
    DisposableEffect(Unit) { onDispose { topBarState.isVisible = true } }

    // Tick driver for countdowns; only ticks while broadcasting or in cooldown.
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val needsTick = uiState.isBroadcasting || uiState.cooldownEndsAt != null
    LaunchedEffect(needsTick) {
        while (needsTick) {
            nowMs = System.currentTimeMillis()
            delay(500)
        }
    }

    var showConfirm by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SosBg)
            .systemBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            SosHeader(
                isBroadcasting = uiState.isBroadcasting,
                onBack = {
                    if (uiState.isBroadcasting) {
                        // While broadcasting, the back arrow stops the broadcast.
                        viewModel.cancelBroadcast()
                    } else {
                        onNavigateBack()
                    }
                }
            )

            Spacer(Modifier.height(8.dp))

            AnimatedContent(
                targetState = uiState.isBroadcasting,
                label = "sos_state",
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) }
            ) { broadcasting ->
                if (broadcasting) {
                    BroadcastingPanel(
                        uiState = uiState,
                        nowMs = nowMs,
                        onStop = viewModel::cancelBroadcast
                    )
                } else {
                    IdlePanel(
                        uiState = uiState,
                        nowMs = nowMs,
                        onSelectType = viewModel::selectSosType,
                        onMessageChange = viewModel::updateMessage,
                        onArmConfirm = {
                            viewModel.refreshLocationBeforeConfirm()
                            showConfirm = true
                        }
                    )
                }
            }
        }
    }

    if (showConfirm) {
        ConfirmSosDialog(
            uiState = uiState,
            onConfirm = {
                showConfirm = false
                viewModel.startBroadcast()
            },
            onDismiss = { showConfirm = false }
        )
    }
}

@Composable
private fun SosHeader(isBroadcasting: Boolean, onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = if (isBroadcasting) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = if (isBroadcasting) "Stop & exit" else "Back",
                tint = Color.White
            )
        }
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = if (isBroadcasting) "BROADCASTING SOS" else "EMERGENCY SOS",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = if (isBroadcasting) "Repeating every 10 min" else "Hold the red button to send",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Idle panel — pre-broadcast configuration + hold-to-broadcast button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun IdlePanel(
    uiState: SosUiState,
    nowMs: Long,
    onSelectType: (SosType) -> Unit,
    onMessageChange: (String) -> Unit,
    onArmConfirm: () -> Unit
) {
    val cooldownRemainingMs = uiState.cooldownEndsAt?.let { (it - nowMs).coerceAtLeast(0L) } ?: 0L
    val inCooldown = cooldownRemainingMs > 0L

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { IdentityRow(crsId = uiState.myCrsId, alias = uiState.myAlias) }
        item { LocationRow(snapshot = uiState.location) }
        item { TypeChipRow(selected = uiState.sosType, onSelect = onSelectType) }
        item {
            OutlinedTextField(
                value = uiState.messageText,
                onValueChange = onMessageChange,
                label = { Text("Add details (optional)", color = Color.White.copy(alpha = 0.7f)) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = SosAmber,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                    cursorColor = SosAmber
                ),
                maxLines = 3
            )
        }

        if (inCooldown) {
            item { CooldownBanner(remainingMs = cooldownRemainingMs) }
        }

        item {
            HoldToBroadcastButton(
                enabled = !inCooldown && uiState.sosType != null,
                onComplete = onArmConfirm
            )
        }

        item {
            Text(
                text = if (uiState.sosType == null) {
                    "Select an emergency type above to enable the broadcast button."
                } else {
                    "Press and hold for 2 seconds. You'll get one final confirm dialog."
                },
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
private fun IdentityRow(crsId: String, alias: String) {
    SosCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(14.dp)) {
            Icon(Icons.Filled.Bolt, contentDescription = null, tint = SosAmber, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Broadcasting as", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                Text(
                    text = if (crsId.isNotBlank()) crsId else "(loading identity…)",
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (alias.isNotBlank()) {
                    Text(alias, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun LocationRow(snapshot: SosLocationSnapshot) {
    val hasFix = snapshot.latitude != null
    SosCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(14.dp)) {
            Icon(
                imageVector = if (hasFix && !snapshot.approximate) Icons.Filled.GpsFixed else Icons.Filled.GpsOff,
                contentDescription = null,
                tint = if (snapshot.approximate) SosAmber else Color(0xFF66BB6A),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Location attached", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                Text(
                    text = snapshot.gridLabel,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (snapshot.approximate) {
                    Text(
                        text = if (hasFix) "Last-known fix — flagged approximate" else "GPS unavailable — last-known will be sent",
                        color = SosAmber,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun TypeChipRow(selected: SosType?, onSelect: (SosType) -> Unit) {
    Column {
        Text(
            "EMERGENCY TYPE",
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SosType.entries.forEach { type ->
                val isSelected = type == selected
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(type) },
                    label = { Text(type.title) },
                    leadingIcon = {
                        Icon(
                            imageVector = iconForType(type),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.White.copy(alpha = 0.06f),
                        labelColor = Color.White,
                        iconColor = Color.White,
                        selectedContainerColor = SosAmber.copy(alpha = 0.18f),
                        selectedLabelColor = SosAmber,
                        selectedLeadingIconColor = SosAmber
                    )
                )
            }
        }
    }
}

@Composable
private fun CooldownBanner(remainingMs: Long) {
    Surface(
        color = SosAmber.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(14.dp)) {
            Icon(Icons.Filled.Restore, contentDescription = null, tint = SosAmber, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Cooldown active",
                    color = SosAmber,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    "Next SOS available in ${formatMmSs(remainingMs)} — prevents accidental spam.",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun HoldToBroadcastButton(enabled: Boolean, onComplete: () -> Unit) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var progress by remember { mutableFloatStateOf(0f) }
    var holdJob by remember { mutableStateOf<Job?>(null) }

    val ringColor = if (enabled) SosRed else Color(0xFF6E2A2A)
    val labelColor = if (enabled) Color.White else Color.White.copy(alpha = 0.4f)

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Box(
            modifier = Modifier
                .size(220.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onPress = {
                            val started = System.currentTimeMillis()
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            holdJob?.cancel()
                            holdJob = scope.launch {
                                while (isActive) {
                                    val elapsed = (System.currentTimeMillis() - started).toFloat()
                                    progress = (elapsed / 2000f).coerceAtMost(1f)
                                    if (progress >= 1f) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onComplete()
                                        progress = 0f
                                        break
                                    }
                                    delay(16)
                                }
                            }
                            tryAwaitRelease()
                            holdJob?.cancel()
                            holdJob = null
                            progress = 0f
                        }
                    )
                }
                .drawBehind {
                    val strokeW = 14.dp.toPx()
                    val radius = (size.minDimension - strokeW) / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)
                    // Track
                    drawCircle(
                        color = Color.White.copy(alpha = 0.08f),
                        radius = radius,
                        center = center,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                    // Progress arc (clockwise from top)
                    if (progress > 0f) {
                        drawArc(
                            color = ringColor,
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            style = Stroke(width = strokeW, cap = StrokeCap.Round),
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(SosRed, SosRedDeep)
                        )
                    )
                    .border(2.dp, Color.White.copy(alpha = if (enabled) 0.5f else 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = "Hold to broadcast SOS",
                        tint = labelColor,
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        text = if (progress > 0f) "${(progress * 100).toInt()}%" else "HOLD",
                        color = labelColor,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "to broadcast",
                        color = labelColor.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Confirmation dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ConfirmSosDialog(
    uiState: SosUiState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val type = uiState.sosType ?: SosType.GENERAL
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = SosRed) },
        title = { Text("Send emergency alert?", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                ConfirmRow(label = "From", value = uiState.myCrsId.ifBlank { "(unknown)" }, mono = true)
                ConfirmRow(label = "Type", value = type.title)
                ConfirmRow(
                    label = "Location",
                    value = uiState.location.gridLabel +
                        if (uiState.location.approximate) "  (approximate)" else "",
                    mono = true
                )
                ConfirmRow(
                    label = "Message",
                    value = uiState.messageText.ifBlank { type.quickPhrase }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Will broadcast on the mesh now and re-broadcast every 10 minutes until you cancel.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = SosRed)
            ) { Text("SEND SOS", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ConfirmRow(label: String, value: String, mono: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            modifier = Modifier.width(80.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Broadcasting panel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BroadcastingPanel(uiState: SosUiState, nowMs: Long, onStop: () -> Unit) {
    val type = uiState.sosType ?: SosType.GENERAL
    val transition = rememberInfiniteTransition(label = "sosBroadcasting")
    val pulse by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val nextRemainingMs = uiState.nextRebroadcastAt?.let { (it - nowMs).coerceAtLeast(0L) } ?: 0L
    val elapsedMs = uiState.broadcastStartedAt?.let { nowMs - it } ?: 0L

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(CircleShape)
                        .background(SosRed.copy(alpha = pulse * 0.35f))
                        .border(3.dp, SosRed, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(72.dp)
                    )
                }
            }
        }

        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${uiState.broadcastCount}",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 56.sp
                )
                Text(
                    text = if (uiState.broadcastCount == 1) "PEER REACHED" else "PEERS REACHED",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        item {
            SosCard {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(iconForType(type), contentDescription = null, tint = SosAmber, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(type.title, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    if (uiState.messageText.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            uiState.messageText,
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        item { LocationRow(snapshot = uiState.location) }

        item {
            SosCard {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(14.dp)) {
                    Icon(Icons.Filled.Restore, contentDescription = null, tint = SosAmber, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Auto-repeat", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                        Text(
                            "Next broadcast in ${formatMmSs(nextRemainingMs)}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Active for ${formatMmSs(elapsedMs)} — keeps repeating until you stop.",
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }

        item {
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("MARK SAFE & STOP", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "A 10-minute cooldown starts after you stop.",
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SosCard(content: @Composable () -> Unit) {
    CrisisCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = Color.White.copy(alpha = 0.06f)
    ) { content() }
}

private fun iconForType(type: SosType): ImageVector = when (type) {
    SosType.MEDICAL -> Icons.Filled.LocalHospital
    SosType.TRAPPED -> Icons.Filled.Report
    SosType.MISSING -> Icons.Filled.PersonSearch
    SosType.ARMED_THREAT -> Icons.Filled.Warning
    SosType.FIRE -> Icons.Filled.FireExtinguisher
    SosType.GENERAL -> Icons.Filled.Warning
}

private fun formatMmSs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
