package com.elv8.crisisos.ui.screens.discovery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elv8.crisisos.domain.model.peer.Peer
import com.elv8.crisisos.domain.model.peer.PeerStatus
import com.elv8.crisisos.ui.components.CrisisCard
import com.elv8.crisisos.ui.components.InputField
import com.elv8.crisisos.ui.components.StatusBadge
import com.elv8.crisisos.ui.components.BadgeStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeerDiscoveryScreen(
    onNavigateToConnectionRequest: (peerCrsId: String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: PeerDiscoveryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var sheetTemporarilyDismissed by remember { mutableStateOf(false) }

    if (!uiState.hasSeenOnboarding && !sheetTemporarilyDismissed) {
        DiscoveryOnboardingSheet(
            onDismiss = {
                sheetTemporarilyDismissed = true
                viewModel.dismissOnboarding()
            }
        )
    }

    LaunchedEffect(Unit) {
        viewModel.startDiscovery()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopDiscovery()
        }
    }

    Scaffold(
        topBar = { }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Item 1 � Local identity header card
            item {
                uiState.localIdentity?.let { identity ->
                    CrisisCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            com.elv8.crisisos.ui.components.CrsAvatar(
                                crsId = identity.crsId,
                                alias = identity.alias,
                                avatarColor = identity.avatarColor,
                                size = 40.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = identity.alias,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = identity.crsId,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            StatusBadge(text = "YOU", status = BadgeStatus.ACTIVE)
                        }
                    }
                }
            }

            // Item 2 � Scanning header row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "NEARBY DEVICES",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = androidx.compose.ui.unit.TextUnit(1.5f, androidx.compose.ui.unit.TextUnitType.Sp)),
                        color = Color(0xFFFF9800),
                        fontFamily = FontFamily.Monospace
                    )
                    
                    if (uiState.isDiscovering) {
                        AnimatedScanningDots()
                    } else {
                        Text(
                            text = "OFFLINE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Item 3 � Search + Sort row
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    InputField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        label = "Search by name or CRS-ID...",
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PeerSortOrder.values().forEach { order ->
                            val isSelected = uiState.sortOrder == order
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setSortOrder(order) },
                                label = { Text(order.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFFF9800).copy(alpha = 0.2f),
                                    selectedLabelColor = Color(0xFFFF9800)
                                )
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = uiState.filterStatus == null && !uiState.filterRequested,
                            onClick = { viewModel.setStatusFilter(null) },
                            label = { Text("All") }
                        )
                        FilterChip(
                            selected = uiState.filterStatus == PeerStatus.AVAILABLE,
                            onClick = { viewModel.setStatusFilter(PeerStatus.AVAILABLE) },
                            label = { Text("Available") }
                        )
                        FilterChip(
                            selected = uiState.filterStatus == PeerStatus.BUSY,
                            onClick = { viewModel.setStatusFilter(PeerStatus.BUSY) },
                            label = { Text("Busy") }
                        )
                        FilterChip(
                            selected = uiState.filterRequested,
                            onClick = { viewModel.setRequestedFilter() },
                            label = { Text("Requested") }
                        )
                    }
                }
            }

            // Item 4..N � PeerListItems or Empty States
            if (uiState.filteredPeers.isEmpty()) {
                item {
                    if (uiState.isDiscovering) {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                ScanningRing()
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Scanning for nearby devices...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.WifiFind, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("No devices found", style = MaterialTheme.typography.titleMedium)
                                Text("Move closer to other CrisisOS users", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            } else {
                itemsIndexed(uiState.filteredPeers, key = { _, peer -> peer.crsId }) { index, peer ->
                    var isVisible by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                    LaunchedEffect(peer.crsId) { isVisible = true }
                    
                    AnimatedVisibility(
                        visible = isVisible,
                        enter = fadeIn(animationSpec = tween(300, delayMillis = index * 60)) + 
                                slideInVertically(animationSpec = tween(300, delayMillis = index * 60)) { 50 }
                    ) {
                        PeerListItem(
                            peer = peer,
                            hasExistingRequest = viewModel.hasAlreadySentRequest(peer.crsId),
                            onConnect = { onNavigateToConnectionRequest(peer.crsId) }
                        )
                    }
                }
            }

            if (com.elv8.crisisos.BuildConfig.DEBUG) {
                item {
                    val diagnosticsViewModel: DiagnosticsViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                    val snapshot by diagnosticsViewModel.snapshot.collectAsStateWithLifecycle()
                    DiagnosticsPanel(snapshot = snapshot)
                }
            }
        }
    }
}

@Composable
private fun PeerListItem(
    peer: Peer,
    hasExistingRequest: Boolean,
    onConnect: () -> Unit
) {
    CrisisCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !hasExistingRequest, onClick = onConnect)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
              com.elv8.crisisos.ui.components.CrsAvatar(
                  crsId = peer.crsId,
                  alias = peer.alias,
                  avatarColor = peer.avatarColor,
                  size = 40.dp
              )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = peer.alias,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                when (peer.status) {
                                    PeerStatus.AVAILABLE -> Color(0xFF4CAF50)
                                    PeerStatus.BUSY -> Color(0xFFFFC107)
                                    else -> Color.Gray
                                }
                            )
                    )
                }
                Text(
                    text = peer.crsId,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SignalBars(signalStrength = peer.signalStrength)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "m away",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            if (hasExistingRequest) {
                StatusBadge(text = "Requested", status = BadgeStatus.ACTIVE)
            } else {
                IconButton(onClick = onConnect) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = "Connect",
                        tint = Color(0xFFFF9800)
                    )
                }
            }
        }
    }
}

@Composable
private fun SignalBars(signalStrength: Int) {
    val barColor = if (signalStrength > -90) Color(0xFFFF9800) else Color.Gray
    
    Canvas(modifier = Modifier.size(16.dp).padding(top = 4.dp)) {
        val gap = 2.dp.toPx()
        val width = 2.dp.toPx()
        
        // Bar heights
        val h1 = 4.dp.toPx()
        val h2 = 6.dp.toPx()
        val h3 = 9.dp.toPx()
        val h4 = 12.dp.toPx()
        
        val colors = listOf(
            if (signalStrength > -90) barColor else Color.Gray,
            if (signalStrength > -75) barColor else Color.Gray,
            if (signalStrength > -60) barColor else Color.Gray,
            if (signalStrength > -45) barColor else Color.Gray
        )
        
        val heights = listOf(h1, h2, h3, h4)
        
        for (i in 0..3) {
            drawLine(
                color = colors[i],
                start = Offset(i * (width + gap), size.height),
                end = Offset(i * (width + gap), size.height - heights[i]),
                strokeWidth = width,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun AnimatedScanningDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "scanning")
    val dot1 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(400, delayMillis = 0, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "dot1"
    )
    val dot2 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(400, delayMillis = 200, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "dot2"
    )
    val dot3 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(400, delayMillis = 400, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "dot3"
    )
    
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color(0xFFFF9800).copy(alpha = dot1)))
        Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color(0xFFFF9800).copy(alpha = dot2)))
        Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color(0xFFFF9800).copy(alpha = dot3)))
    }
}

@Composable
private fun ScanningRing() {
    val infiniteTransition = rememberInfiniteTransition(label = "scanningRing")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(1500, easing = LinearEasing)),
        label = "angle"
    )
    
    Canvas(modifier = Modifier.size(64.dp)) {
        drawArc(
            color = Color(0xFFFF9800),
            startAngle = angle,
            sweepAngle = 270f,
            useCenter = false,
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}


