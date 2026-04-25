package com.elv8.crisisos.ui.screens.maps

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elv8.crisisos.core.map.MapConfiguration
import com.elv8.crisisos.core.map.MapOverlayManager
import com.elv8.crisisos.domain.model.SafeZone
import com.elv8.crisisos.domain.model.SafeZoneType
import com.elv8.crisisos.domain.model.ZoneStatus
import com.elv8.crisisos.domain.model.status
import com.elv8.crisisos.ui.components.CrisisCard
import com.elv8.crisisos.ui.components.LocalTopBarState
import org.osmdroid.util.GeoPoint

// ---------- Color palette (single source of truth for both pins and legend) ----------

private val ColorOpen      = Color(0xFF1D9E75)   // green
private val ColorNearFull  = Color(0xFFEF9F27)   // orange
private val ColorFullClosed = Color(0xFFE24B4A)  // red

private val ColorCamp       = Color(0xFF2196F3)
private val ColorHospital   = Color(0xFFE91E63)
private val ColorWater      = Color(0xFF03A9F4)
private val ColorFood       = Color(0xFFFF9800)
private val ColorEvacuation = Color(0xFF9C27B0)
private val ColorSafeHouse  = Color(0xFF4CAF50)

private fun typeColor(type: SafeZoneType): Color = when (type) {
    SafeZoneType.CAMP              -> ColorCamp
    SafeZoneType.HOSPITAL          -> ColorHospital
    SafeZoneType.WATER_POINT       -> ColorWater
    SafeZoneType.FOOD_DISTRIBUTION -> ColorFood
    SafeZoneType.EVACUATION_POINT  -> ColorEvacuation
    SafeZoneType.SAFE_HOUSE        -> ColorSafeHouse
}

private fun statusColor(s: ZoneStatus): Color = when (s) {
    ZoneStatus.OPEN           -> ColorOpen
    ZoneStatus.NEAR_FULL      -> ColorNearFull
    ZoneStatus.FULL_OR_CLOSED -> ColorFullClosed
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapsScreen(
    onNavigateBack: () -> Unit,
    viewModel: MapsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val topBarState = LocalTopBarState.current

    LaunchedEffect(Unit) {
        topBarState.update(
            title = { Text("OFFLINE MAPS", fontWeight = FontWeight.Bold) },
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

        Crossfade(targetState = uiState.mapMode, label = "map_mode_crossfade") { mode ->
            when (mode) {
                MapMode.MAP -> MapPane(uiState = uiState, viewModel = viewModel)
                MapMode.LIST -> ListPane(uiState = uiState, onZoneClick = viewModel::selectZone)
            }
        }
    }

    if (uiState.selectedZone != null) {
        ZoneDetailSheet(
            zone = uiState.selectedZone!!,
            onDismiss = { viewModel.selectZone(null) },
            onShowOnMap = {
                viewModel.centerOnZone(uiState.selectedZone!!)
                viewModel.selectZone(null)
                viewModel.setMapMode(MapMode.MAP)
            }
        )
    }
}

// ---------- MAP PANE ----------

@Composable
private fun MapPane(uiState: MapsUiState, viewModel: MapsViewModel) {
    val context = LocalContext.current
    var overlayManager by remember { mutableStateOf<MapOverlayManager?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        CrisisMapView(
            modifier = Modifier.fillMaxSize(),
            onMapReady = { mapView ->
                overlayManager = MapOverlayManager(context, mapView)
                android.util.Log.d("CrisisOS_Map", "Overlay manager initialized")
            }
        )

        // Sync user-location pin
        LaunchedEffect(overlayManager, uiState.userLocation) {
            val mgr = overlayManager ?: return@LaunchedEffect
            val location = uiState.userLocation ?: return@LaunchedEffect
            mgr.updateUserLocation(GeoPoint(location.latitude, location.longitude))
        }

        // Sync safe-zone markers (full replace whenever the list changes)
        LaunchedEffect(overlayManager, uiState.safeZones) {
            val mgr = overlayManager ?: return@LaunchedEffect
            mgr.setSafeZones(
                zones = uiState.safeZones,
                typeColorOf = { typeColor(it).toArgb() },
                statusColorOf = { statusColor(it).toArgb() },
                onTap = { viewModel.selectZone(it) }
            )
        }

        // Suggested-route polyline: user → nearest open safe zone
        LaunchedEffect(overlayManager, uiState.userLocation, uiState.nearestOpenZone) {
            val mgr = overlayManager ?: return@LaunchedEffect
            val loc = uiState.userLocation
            val nearest = uiState.nearestOpenZone
            if (loc == null || nearest == null) {
                mgr.drawRoute(MapConfiguration.ROUTE_TO_NEAREST_ID, emptyList(), 0)
                return@LaunchedEffect
            }
            mgr.drawRoute(
                id = MapConfiguration.ROUTE_TO_NEAREST_ID,
                points = listOf(
                    GeoPoint(loc.latitude, loc.longitude),
                    GeoPoint(nearest.coordinates.first, nearest.coordinates.second)
                ),
                colorInt = ColorOpen.copy(alpha = 0.85f).toArgb(),
                widthPx = 10f
            )
        }

        // Center request — animates the map when centerRequest counter ticks up
        LaunchedEffect(overlayManager, uiState.centerRequest) {
            val mgr = overlayManager ?: return@LaunchedEffect
            val center = uiState.mapCenter ?: return@LaunchedEffect
            mgr.animateTo(GeoPoint(center.first, center.second), uiState.centerZoom)
        }

        // Top-left: legend
        MapLegend(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 12.dp)
        )

        // Top-right: nearest-zone hint badge
        if (uiState.nearestOpenZone != null) {
            NearestZoneBadge(
                zone = uiState.nearestOpenZone!!,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 12.dp, top = 12.dp),
                onClick = { viewModel.centerOnZone(uiState.nearestOpenZone!!) }
            )
        }

        // Bottom-left: offline status pill
        OfflineBadge(
            isOffline = uiState.isOffline,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 16.dp)
        )

        // Bottom-right: locate-me FAB
        FloatingActionButton(
            onClick = { viewModel.centerOnUserLocation() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(
                imageVector = if (uiState.userLocation != null) Icons.Default.MyLocation else Icons.Default.LocationSearching,
                contentDescription = "Locate me"
            )
        }
    }
}

// ---------- LIST PANE ----------

@Composable
private fun ListPane(uiState: MapsUiState, onZoneClick: (SafeZone) -> Unit) {
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

        // Already sorted by distanceKm in MapsViewModel.applyLocation().
        items(uiState.safeZones, key = { it.id }) { zone ->
            ZoneCard(zone = zone, onClick = { onZoneClick(zone) })
        }

        item { Spacer(modifier = Modifier.height(60.dp)) }
    }
}

// ---------- WIDGETS ----------

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
private fun MapLegend(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                "STATUS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            LegendDot(color = ColorOpen,        label = "Open")
            LegendDot(color = ColorNearFull,    label = "Near full")
            LegendDot(color = ColorFullClosed,  label = "Full / closed")
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun NearestZoneBadge(zone: SafeZone, modifier: Modifier, onClick: () -> Unit) {
    // Use a darker green for ~4.5:1 white-on-color contrast (WCAG AA compliant)
    val deepGreen = Color(0xFF0F6B4F)
    Surface(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        color = deepGreen,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(Icons.Default.Navigation, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    "NEAREST OPEN",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    "${zone.name} · ${zone.distance}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun OfflineBadge(isOffline: Boolean, modifier: Modifier) {
    val (label, bg) = if (isOffline) {
        "OFFLINE TILES" to Color(0xFF1D9E75)
    } else {
        "ONLINE" to Color(0xFF2196F3)
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = bg.copy(alpha = 0.18f),
        tonalElevation = 0.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(Icons.Default.CloudOff, contentDescription = null, tint = bg, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = bg)
        }
    }
}

@Composable
fun ZoneCard(zone: SafeZone, onClick: () -> Unit) {
    val (icon, color) = getZoneMetadata(zone.type)
    val statusC = statusColor(zone.status())

    CrisisCard(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                // Type icon with status ring color border
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = color.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(2.dp, statusC)
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
                if (zone.capacity != null && zone.currentOccupancy != null) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Capacity", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${zone.currentOccupancy} / ${zone.capacity}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        val ratio = (zone.currentOccupancy.toFloat() / zone.capacity.toFloat()).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { ratio },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = statusC,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                }

                val statusLabel = when (zone.status()) {
                    ZoneStatus.OPEN           -> "OPEN"
                    ZoneStatus.NEAR_FULL      -> "NEAR FULL"
                    ZoneStatus.FULL_OR_CLOSED -> if (zone.isOperational) "FULL" else "CLOSED"
                }
                Surface(
                    color = statusC.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, statusC.copy(alpha = 0.6f))
                ) {
                    Text(
                        statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusC,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoneDetailSheet(zone: SafeZone, onDismiss: () -> Unit, onShowOnMap: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val (icon, color) = getZoneMetadata(zone.type)
    val statusC = statusColor(zone.status())

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = color.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(2.dp, statusC)
                ) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.padding(12.dp).size(32.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(zone.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(zone.type.name.replace("_", " "), style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
                }
                val statusLabel = when (zone.status()) {
                    ZoneStatus.OPEN           -> "OPEN"
                    ZoneStatus.NEAR_FULL      -> "NEAR FULL"
                    ZoneStatus.FULL_OR_CLOSED -> if (zone.isOperational) "FULL" else "CLOSED"
                }
                Surface(
                    color = statusC.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, statusC)
                ) {
                    Text(
                        statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusC,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
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

                CrisisCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Current Capacity", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text("${(animatedRatio * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = statusC)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { animatedRatio },
                            modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
                            color = statusC,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("${zone.currentOccupancy} of ${zone.capacity} slots filled", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onShowOnMap,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Navigation, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("SHOW ON MAP", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onDismiss,
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
        SafeZoneType.CAMP              -> Pair(Icons.Default.Home, ColorCamp)
        SafeZoneType.HOSPITAL          -> Pair(Icons.Default.LocalHospital, ColorHospital)
        SafeZoneType.WATER_POINT       -> Pair(Icons.Default.WaterDrop, ColorWater)
        SafeZoneType.FOOD_DISTRIBUTION -> Pair(Icons.Default.Restaurant, ColorFood)
        SafeZoneType.EVACUATION_POINT  -> Pair(Icons.AutoMirrored.Filled.DirectionsRun, ColorEvacuation)
        SafeZoneType.SAFE_HOUSE        -> Pair(Icons.Default.Security, ColorSafeHouse)
    }
}
