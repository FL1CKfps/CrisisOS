package com.elv8.crisisos.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elv8.crisisos.ui.components.CrisisCard
import com.elv8.crisisos.ui.components.LocalTopBarState
import com.elv8.crisisos.ui.navigation.Screen

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val topBarState = LocalTopBarState.current

    LaunchedEffect(Unit) {
        topBarState.reset()
    }

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 0.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { 50 }
            ) {
                MeshStatusCard(uiState = uiState, onAction = { onNavigate(Screen.Sos.route) })
            }
        }
        item {
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(300, 100)) + slideInVertically(tween(300, 100)) { 50 }
            ) {
                StatsRow(uiState = uiState)
            }
        }
        item {
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(300, 200)) + slideInVertically(tween(300, 200)) { 50 }
            ) {
                Column {
                    Text(
                        text = "Quick Actions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                    )
                    QuickActionsGrid(onNavigate = onNavigate)
                }
            }
        }
        item {
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(300, 300)) + slideInVertically(tween(300, 300)) { 50 }
            ) {
                Column {
                    Text(
                        text = "System Status",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                    )
                    SystemStatusList(uiState = uiState)
                }
            }
        }
    }
}

@Composable
fun MeshStatusCard(uiState: HomeUiState, onAction: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val (statusColor, statusText) = when (uiState.meshStatus) {
        MeshStatus.CONNECTED -> Color(0xFF4CAF50) to "MESH ACTIVE • ${uiState.peersNearby} PEERS"
        MeshStatus.SCANNING -> Color(0xFFFFC107) to "SCANNING FOR PEERS..."
        MeshStatus.OFFLINE -> Color(0xFFF44336) to "OFFLINE"
    }

    CrisisCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .scale(scale)
                        .background(statusColor.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.size(24.dp).background(statusColor, CircleShape))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(text = statusText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text = "Last sync: ${uiState.lastSyncTime}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            
            if (uiState.activeSosAlerts > 0) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${uiState.activeSosAlerts} ACTIVE SOS ALERTS", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun StatsRow(uiState: HomeUiState) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard(modifier = Modifier.weight(1f), title = "SOS Alerts", value = uiState.activeSosAlerts.toString(), icon = Icons.Filled.Warning, accentColor = Color(0xFFF44336))
        StatCard(modifier = Modifier.weight(1f), title = "Peers", value = uiState.peersNearby.toString(), icon = Icons.Filled.CellTower, accentColor = Color(0xFF4CAF50))
        StatCard(modifier = Modifier.weight(1f), title = "Battery", value = if (uiState.batteryOptimized) "Opt" else "Max", icon = Icons.Filled.BatterySaver, accentColor = Color(0xFFFFC107))
    }
}

@Composable
fun StatCard(modifier: Modifier = Modifier, title: String, value: String, icon: ImageVector, accentColor: Color) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.Start) {
            Icon(imageVector = icon, contentDescription = title, tint = accentColor, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(text = title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun QuickActionsGrid(onNavigate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionCard(modifier = Modifier.weight(1f), title = "SOS", icon = Icons.Filled.Warning, isSos = true, onClick = { onNavigate(Screen.Sos.route) })
            ActionCard(modifier = Modifier.weight(1f), title = "Chat", icon = Icons.Filled.Chat, onClick = { onNavigate(Screen.ChatHub.route) })
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionCard(modifier = Modifier.weight(1f), title = "Find Person", icon = Icons.Filled.PersonSearch, onClick = { onNavigate(Screen.MissingPerson.route) })
            ActionCard(modifier = Modifier.weight(1f), title = "AI Assistant", icon = Icons.Filled.Psychology, onClick = { onNavigate(Screen.AiAssistant.route) })
        }
    }
}

@Composable
fun ActionCard(modifier: Modifier = Modifier, title: String, icon: ImageVector, isSos: Boolean = false, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "sosPulseBorder")
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(800, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "borderAlpha"
    )

    val cardColor = if (isSos) Color(0xFFD32F2F) else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isSos) Color.White else MaterialTheme.colorScheme.onSurface
    val baseModifier = if (isSos) modifier.border(2.dp, Color(0xFFFF5252).copy(alpha = borderAlpha), RoundedCornerShape(16.dp)) else modifier

    Card(onClick = onClick, modifier = baseModifier.height(100.dp), colors = CardDefaults.cardColors(containerColor = cardColor), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(imageVector = icon, contentDescription = title, tint = contentColor, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = title, style = MaterialTheme.typography.labelLarge, color = contentColor, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SystemStatusList(uiState: HomeUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusItem(title = "Bluetooth Mesh", value = if (uiState.meshStatus != MeshStatus.OFFLINE) "Operational" else "Disabled", icon = Icons.Default.CellTower, color = if (uiState.meshStatus != MeshStatus.OFFLINE) Color(0xFF4CAF50) else Color.Gray)
        StatusItem(title = "Notification Service", value = if (uiState.needsNotificationPermission) "Action Required" else "Active", icon = Icons.Default.Settings, color = if (uiState.needsNotificationPermission) Color(0xFFF44336) else Color(0xFF4CAF50))
    }
}

@Composable
fun StatusItem(title: String, value: String, icon: ImageVector, color: Color) {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(text = value, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.Bold)
        }
    }
}
