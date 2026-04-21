package com.elv8.crisisos.ui.screens.home

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import com.elv8.crisisos.ui.navigation.Screen

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var isVisible by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { isVisible = true }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            androidx.compose.animation.AnimatedVisibility(
                visible = isVisible,
                enter = androidx.compose.animation.fadeIn(animationSpec = tween(300, delayMillis = 0)) + 
                        androidx.compose.animation.slideInVertically(animationSpec = tween(300, delayMillis = 0)) { 50 }
            ) {
                MeshStatusCard(uiState = uiState)
            }
        }
        item {
            androidx.compose.animation.AnimatedVisibility(
                visible = isVisible,
                enter = androidx.compose.animation.fadeIn(animationSpec = tween(300, delayMillis = 100)) + 
                        androidx.compose.animation.slideInVertically(animationSpec = tween(300, delayMillis = 100)) { 50 }
            ) {
                StatsRow(uiState = uiState)
            }
        }
        item {
            androidx.compose.animation.AnimatedVisibility(
                visible = isVisible,
                enter = androidx.compose.animation.fadeIn(animationSpec = tween(300, delayMillis = 200)) + 
                        androidx.compose.animation.slideInVertically(animationSpec = tween(300, delayMillis = 200)) { 50 }
            ) {
                Column {
                    Text(
                        text = "Quick Actions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                    QuickActionsGrid(onNavigate = onNavigate)
                }
            }
        }
        item {
            androidx.compose.animation.AnimatedVisibility(
                visible = isVisible,
                enter = androidx.compose.animation.fadeIn(animationSpec = tween(300, delayMillis = 300)) + 
                        androidx.compose.animation.slideInVertically(animationSpec = tween(300, delayMillis = 300)) { 50 }
            ) {
                Column {
                    Text(
                        text = "Recent Activity",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                    RecentActivityList()
                    
                    // Added for mock testing
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
                    androidx.compose.material3.Button(
                        onClick = { viewModel.triggerMockNotifications() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        androidx.compose.material3.Text("Trigger Mock Notifications")
                    }
                }
            }
        }
    }
}

@Composable
fun MeshStatusCard(uiState: HomeUiState) {
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
        MeshStatus.CONNECTED -> Color(0xFF4CAF50) to "MESH ACTIVE � ${uiState.peersNearby} PEERS"
        MeshStatus.SCANNING -> Color(0xFFFFC107) to "SCANNING FOR PEERS..."
        MeshStatus.OFFLINE -> Color(0xFFF44336) to "OFFLINE"
    }

    CrisisCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .scale(scale)
                    .background(statusColor.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(statusColor, CircleShape)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Last sync: ${uiState.lastSyncTime}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun StatsRow(uiState: HomeUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            title = "SOS Alerts",
            value = uiState.activeSosAlerts.toString(),
            icon = Icons.Filled.Warning,
            accentColor = Color(0xFFF44336)
        )
        StatCard(
            modifier = Modifier.weight(1f),
            title = "Peers",
            value = uiState.peersNearby.toString(),
            icon = Icons.Filled.CellTower,
            accentColor = Color(0xFF4CAF50)
        )
        StatCard(
            modifier = Modifier.weight(1f),
            title = "Battery",
            value = if (uiState.batteryOptimized) "Opt" else "Max",
            icon = Icons.Filled.BatterySaver,
            accentColor = Color(0xFFFFC107)
        )
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: ImageVector,
    accentColor: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = accentColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun QuickActionsGrid(onNavigate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionCard(
                modifier = Modifier.weight(1f),
                title = "SOS",
                icon = Icons.Filled.Warning,
                isSos = true,
                onClick = { onNavigate(Screen.Sos.route) }
            )
            ActionCard(
                modifier = Modifier.weight(1f),
                title = "Chat",
                icon = Icons.Filled.Chat,
                onClick = { onNavigate(Screen.ChatHub.route) }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionCard(
                modifier = Modifier.weight(1f),
                title = "Find Person",
                icon = Icons.Filled.PersonSearch,
                onClick = { onNavigate(Screen.MissingPerson.route) }
            )
            ActionCard(
                modifier = Modifier.weight(1f),
                title = "AI Assistant",
                icon = Icons.Filled.Psychology,
                onClick = { onNavigate(Screen.AiAssistant.route) }
            )
        }
    }
}

@Composable
fun ActionCard(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    isSos: Boolean = false,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sosPulseBorder")
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "borderAlpha"
    )

    val cardColor = if (isSos) Color(0xFFD32F2F) else MaterialTheme.colorScheme.secondaryContainer
    val contentColor = if (isSos) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
    
    val baseModifier = if (isSos) {
        modifier.border(2.dp, Color(0xFFFF5252).copy(alpha = borderAlpha), RoundedCornerShape(12.dp))
    } else {
        modifier
    }

    Card(
        onClick = onClick,
        modifier = baseModifier.height(80.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = contentColor,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun RecentActivityList() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ActivityItem(title = "Peer 'Alpha-7' joined the mesh", time = "2 mins ago")
        ActivityItem(title = "SOS Alert resolved in sector 4", time = "15 mins ago")
        ActivityItem(title = "New map package downloaded", time = "1 hour ago")
    }
}

@Composable
fun ActivityItem(title: String, time: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = time,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}






