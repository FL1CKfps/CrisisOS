package com.elv8.crisisos.ui.screens.maps

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elv8.crisisos.core.map.MapOverlayManager
import com.elv8.crisisos.domain.model.SafeZone
import com.elv8.crisisos.domain.model.SafeZoneType
import com.elv8.crisisos.ui.components.CrisisCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapsScreen(
    onNavigateBack: () -> Unit,
    viewModel: MapsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Offline Maps", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            // Mode Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                ModeToggleButton(
                    title = "MAP VIEW",
                    isSelected = uiState.mapMode == MapMode.MAP,
                    onClick = { viewModel.setMapMode(MapMode.MAP) },
                    modifier = Modifier.weight(1f)
                )
                ModeToggleButton(
                    title = "LIST VIEW",
                    isSelected = uiState.mapMode == MapMode.LIST,
                    onClick = { viewModel.setMapMode(MapMode.LIST) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Crossfade for views
            Crossfade(targetState = uiState.mapMode, label = "map_mode_crossfade") { mode ->
                when (mode) {
                    MapMode.MAP -> {
                        val context = androidx.compose.ui.platform.LocalContext.current
                        var overlayManager by remember { mutableStateOf<MapOverlayManager?>(null) }

                        CrisisMapView(
                            modifier = Modifier.fillMaxSize(),
                            onMapReady = { mapView ->
                                overlayManager = MapOverlayManager(context, mapView)
                                android.util.Log.d("CrisisOS_Map", "Overlay manager initialized")
                            }
                        )

                        // React to location updates and move the marker
                        LaunchedEffect(overlayManager, uiState.userLocation) {
                            val mgr = overlayManager ?: return@LaunchedEffect
                            val location = uiState.userLocation ?: return@LaunchedEffect
                            val geoPoint = org.osmdroid.util.GeoPoint(location.latitude, location.longitude)
                            mgr.updateUserLocation(geoPoint)
                        }

                        // Center map when mapCenter changes
                        LaunchedEffect(overlayManager, uiState.mapCenter) {
                            val mgr = overlayManager ?: return@LaunchedEffect
                            val center = uiState.mapCenter ?: return@LaunchedEffect
                            mgr.animateTo(
                                org.osmdroid.util.GeoPoint(center.first, center.second)
                            )
                        }
                    }
                    MapMode.LIST -> {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            item {
                                Text(
                                    "SAFE ZONES NEARBY (${uiState.safeZones.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            
                            items(uiState.safeZones, key = { it.id }) { zone ->
                                ZoneCard(zone = zone, onClick = { viewModel.selectZone(zone) })
                            }
                            
                            item { Spacer(modifier = Modifier.height(60.dp)) }
                        }
                    }
                }
            }
        }
    }

    if (uiState.selectedZone != null) {
        ZoneDetailSheet(
            zone = uiState.selectedZone!!,
            onDismiss = { viewModel.selectZone(null) }
        )
    }
}

@Composable
fun ModeToggleButton(title: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun MapPlaceholderView() {
    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        CrisisCard(
            modifier = Modifier.fillMaxSize().padding(bottom = 16.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val gridSize = 40.dp.toPx()
                    val gridColor = Color.Gray.copy(alpha = 0.2f)
                    
                    // Grid
                    for (x in 0..size.width.toInt() step gridSize.toInt()) {
                        drawLine(gridColor, Offset(x.toFloat(), 0f), Offset(x.toFloat(), size.height), strokeWidth = 2f)
                    }
                    for (y in 0..size.height.toInt() step gridSize.toInt()) {
                        drawLine(gridColor, Offset(0f, y.toFloat()), Offset(size.width, y.toFloat()), strokeWidth = 2f)
                    }
                    
                    // Map Pins overlay
                    drawCircle(color = Color(0xFF4CAF50), radius = 24f, center = Offset(size.width * 0.3f, size.height * 0.4f))
                    drawCircle(color = Color(0xFF2196F3), radius = 24f, center = Offset(size.width * 0.7f, size.height * 0.6f))
                    drawCircle(color = Color(0xFFFF9800), radius = 24f, center = Offset(size.width * 0.5f, size.height * 0.8f))
                }

                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("OpenStreetMap — Offline Tiles", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Map SDK integration point — OSMDroid / Mapbox", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }

                // Badge
                Surface(
                    color = Color(0xFF4CAF50).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Text(
                        "OFFLINE TILES: 450 MB CACHED",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ZoneCard(zone: SafeZone, onClick: () -> Unit) {
    val (icon, color) = getZoneMetadata(zone.type)
    
    CrisisCard(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = color.copy(alpha = 0.15f)
                ) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.padding(8.dp).size(24.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(zone.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Operated by: ${zone.operatedBy}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        zone.distance,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // Capacity indicator
                if (zone.capacity != null && zone.currentOccupancy != null) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Capacity", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${zone.currentOccupancy} / ${zone.capacity}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        val ratio = (zone.currentOccupancy.toFloat() / zone.capacity.toFloat()).coerceIn(0f, 1f)
                        val progressColor = if (ratio > 0.9f) Color(0xFFF44336) else if (ratio > 0.7f) Color(0xFFFF9800) else Color(0xFF4CAF50)
                        
                        LinearProgressIndicator(
                            progress = { ratio },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = progressColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                }

                // Operational Status
                val statusColor = if (zone.isOperational) Color(0xFF4CAF50) else Color(0xFFF44336)
                Surface(
                    color = statusColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        if (zone.isOperational) "ACTIVE" else "FULL/CLOSED",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoneDetailSheet(zone: SafeZone, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val (icon, color) = getZoneMetadata(zone.type)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.15f)) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.padding(12.dp).size(32.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(zone.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(zone.type.name.replace("_", " "), style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                InfoColumn(Icons.Default.LocationOn, "Distance", zone.distance)
                InfoColumn(Icons.Default.Security, "Operator", zone.operatedBy)
                InfoColumn(Icons.Default.Update, "Verified", zone.lastVerified)
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            if (zone.capacity != null && zone.currentOccupancy != null) {
                var progressVisible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { progressVisible = true }
                
                val ratio = (zone.currentOccupancy.toFloat() / zone.capacity.toFloat()).coerceIn(0f, 1f)
                val animatedRatio by animateFloatAsState(targetValue = if (progressVisible) ratio else 0f, animationSpec = tween(1000), label = "capacity_anim")
                val progressColor = if (ratio > 0.9f) Color(0xFFF44336) else if (ratio > 0.7f) Color(0xFFFF9800) else Color(0xFF4CAF50)

                CrisisCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Current Capacity", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text("${(animatedRatio * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = progressColor)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { animatedRatio },
                            modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
                            color = progressColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("${zone.currentOccupancy} of ${zone.capacity} slots filled", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onDismiss, // Stub
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Navigation, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("NAVIGATE MGRS", fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onDismiss, // Stub
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("REPORT STATUS CHANGE")
            }
        }
    }
}

@Composable
fun InfoColumn(icon: ImageVector, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun getZoneMetadata(type: SafeZoneType): Pair<ImageVector, Color> {
    return when (type) {
        SafeZoneType.CAMP -> Pair(Icons.Default.Home, Color(0xFF2196F3))
        SafeZoneType.HOSPITAL -> Pair(Icons.Default.LocalHospital, Color(0xFFE91E63))
        SafeZoneType.WATER_POINT -> Pair(Icons.Default.WaterDrop, Color(0xFF03A9F4))
        SafeZoneType.FOOD_DISTRIBUTION -> Pair(Icons.Default.Restaurant, Color(0xFFFF9800))
        SafeZoneType.EVACUATION_POINT -> Pair(Icons.AutoMirrored.Filled.DirectionsRun, Color(0xFF9C27B0))
        SafeZoneType.SAFE_HOUSE -> Pair(Icons.Default.Security, Color(0xFF4CAF50))
    }
}