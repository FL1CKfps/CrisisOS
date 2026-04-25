package com.elv8.crisisos.ui.screens.deadman

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        TimerSection(uiState = uiState)

        Spacer(modifier = Modifier.height(32.dp))

        if (uiState.isActive) {
            Button(
                onClick = { viewModel.checkIn() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("CHECK IN NOW", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        AnimatedVisibility(
            visible = !uiState.isActive,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            SettingsSection(
                uiState = uiState,
                onIntervalSelected = viewModel::setInterval,
                onMessageChange = viewModel::updateAlertMessage,
                onAddContact = { viewModel.addContact("New Contact") },
                onRemoveContact = viewModel::removeContact
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        ActivateToggle(uiState = uiState, onToggle = { 
            if (uiState.isActive) viewModel.deactivate() else viewModel.activate() 
        })
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun TimerSection(uiState: DeadManUiState) {
    val totalSeconds = uiState.intervalMinutes * 60f
    val currentSeconds = uiState.timeRemainingSeconds.toFloat()
    
    val progress = if (totalSeconds > 0) currentSeconds / totalSeconds else 0f
    
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(1000),
        label = "progressAnim"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(240.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = primaryColor,
                startAngle = -90f,
                sweepAngle = animatedProgress * 360f,
                useCenter = false,
                style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
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
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "LAST CHECK-IN: ${uiState.lastCheckIn}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SettingsSection(
    uiState: DeadManUiState,
    onIntervalSelected: (Int) -> Unit,
    onMessageChange: (String) -> Unit,
    onAddContact: () -> Unit,
    onRemoveContact: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Timer Interval", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        val intervals = listOf(15 to "15m", 30 to "30m", 60 to "1h", 120 to "2h", 240 to "4h")
        
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(intervals) { (mins, label) ->
                val selected = uiState.intervalMinutes == mins
                FilterChip(
                    selected = selected,
                    onClick = { onIntervalSelected(mins) },
                    label = { Text(label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Auto-SOS Message", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = uiState.alertMessage,
            onValueChange = onMessageChange,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Escalation Contacts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = onAddContact) {
                Icon(Icons.Default.Add, contentDescription = "Add Contact")
            }
        }
        
        uiState.escalationContacts.forEach { contact ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(contact, style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(onClick = { onRemoveContact(contact) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun ActivateToggle(uiState: DeadManUiState, onToggle: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val buttonColor by animateColorAsState(
            targetValue = if (uiState.isActive) Color(0xFFFF9800) else MaterialTheme.colorScheme.surfaceVariant,
            label = "btnBgColor"
        )
        val textColor by animateColorAsState(
            targetValue = if (uiState.isActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            label = "btnTextColor"
        )

        Button(
            onClick = onToggle,
            colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = CircleShape
        ) {
            Text(
                text = if (uiState.isActive) "DEACTIVATE" else "ACTIVATE SWITCH",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }

        if (uiState.isActive) {
            Spacer(modifier = Modifier.height(12.dp))
            val hours = uiState.timeRemainingSeconds / 3600
            val minutes = (uiState.timeRemainingSeconds % 3600) / 60
            val timeLabel = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
            Text(
                text = "Auto-SOS in $timeLabel if no check-in",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
