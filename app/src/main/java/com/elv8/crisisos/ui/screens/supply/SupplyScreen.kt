package com.elv8.crisisos.ui.screens.supply

import com.elv8.crisisos.ui.components.EmptyState
import androidx.compose.material.icons.filled.ListAlt

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elv8.crisisos.domain.model.RequestStatus
import com.elv8.crisisos.domain.model.SupplyRequest
import com.elv8.crisisos.domain.model.SupplyType
import com.elv8.crisisos.ui.components.CrisisCard
import com.elv8.crisisos.ui.components.LocalTopBarState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)@Composable
fun SupplyScreen(
    initialCategory: String? = null,
    initialNotes: String? = null,
    onNavigateBack: () -> Unit,
    viewModel: SupplyViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(initialCategory, initialNotes) {
        viewModel.applyPrefill(initialCategory, initialNotes)
    }
    val topBarState = LocalTopBarState.current

    LaunchedEffect(uiState.currentStep) {
        topBarState.update(
            title = { Text("SUPPLY REQUESTS", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (uiState.currentStep < 3) {
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.background
            ) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text("NEW REQUEST") }
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text("MY REQUESTS") }
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = uiState.currentStep < 3 // Disable scroll during active wizard
        ) { page ->
            when (page) {
                0 -> NewRequestTab(uiState, viewModel)
                1 -> MyRequestsTab(uiState.activeRequests, viewModel::cancelRequest)
            }
        }
    }
}

@Composable
fun NewRequestTab(uiState: SupplyUiState, viewModel: SupplyViewModel) {
    AnimatedContent(
        targetState = uiState.currentStep,
        transitionSpec = {
            slideInHorizontally(initialOffsetX = { it }) + fadeIn() togetherWith
            slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
        },
        label = "wizardSteps"
    ) { step ->
        when (step) {
            0 -> SupplyTypeSelection(uiState, viewModel)
            1 -> SupplyDetails(uiState, viewModel)
            2 -> SupplyReview(uiState, viewModel)
            3 -> SupplyBroadcast(viewModel::resetWizard)
        }
    }
}

@Composable
fun SupplyTypeSelection(uiState: SupplyUiState, viewModel: SupplyViewModel) {
    val supplyTypes = SupplyType.entries.toTypedArray()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Select assistance needed",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "Your request will be broadcasted to all nearby rescue nodes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(supplyTypes) { type ->
                val isSelected = uiState.selectedType == type
                SupplyTypeCard(
                    type = type,
                    isSelected = isSelected,
                    onClick = { viewModel.selectType(type) }
                )
            }
        }
    }
}

@Composable
fun SupplyTypeCard(
    type: SupplyType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
    
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val icon = when (type) {
                    SupplyType.WATER -> Icons.Default.WaterDrop
                    SupplyType.FOOD -> Icons.Default.Restaurant
                    SupplyType.MEDICINE -> Icons.Default.MedicalServices
                    SupplyType.SHELTER -> Icons.Default.Cottage
                    SupplyType.BLANKET -> Icons.Default.Bed
                    SupplyType.EVACUATION -> Icons.AutoMirrored.Filled.DirectionsRun
                    SupplyType.EMERGENCY -> Icons.Default.PriorityHigh
                    SupplyType.OTHER -> Icons.Default.MoreHoriz
                }
                Icon(
                    icon, 
                    contentDescription = null, 
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = type.name.replace("_", " "),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = getSupplyDescription(type),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

fun getSupplyDescription(type: SupplyType): String {
    return when (type) {
        SupplyType.WATER -> "Drinking water or purification"
        SupplyType.FOOD -> "Emergency rations or meals"
        SupplyType.MEDICINE -> "First aid or chronic medication"
        SupplyType.SHELTER -> "Tents or temporary housing"
        SupplyType.BLANKET -> "Bedding or thermal insulation"
        SupplyType.EVACUATION -> "Assistance leaving the area"
        SupplyType.EMERGENCY -> "Life-threatening situation"
        SupplyType.OTHER -> "Custom supply or assistance"
    }
}

@Composable
fun SupplyDetails(uiState: SupplyUiState, viewModel: SupplyViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = viewModel::previousStep, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Request details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text("QUANTITY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(8.dp)
        ) {
            IconButton(
                onClick = { viewModel.updateQuantity(uiState.quantity - 1) },
                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease") 
            }
            Text(
                "${uiState.quantity}",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black
            )
            IconButton(
                onClick = { viewModel.updateQuantity(uiState.quantity + 1) },
                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Increase")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("LOCATION", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = uiState.location,
            onValueChange = viewModel::updateLocation,
            placeholder = { Text("Where should it be delivered?") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        )

        Spacer(modifier = Modifier.height(24.dp))

        val notesLabel = if (uiState.selectedType == SupplyType.OTHER) "DESCRIBE YOUR NEED *" else "ADDITIONAL NOTES (OPTIONAL)"
        Text(notesLabel, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = uiState.notes,
            onValueChange = viewModel::updateNotes,
            placeholder = { Text(if (uiState.selectedType == SupplyType.OTHER) "Specify what you need exactly..." else "Any details that help rescue teams") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            minLines = 4,
            maxLines = 6
        )

        Spacer(modifier = Modifier.height(32.dp))

        val isEnabled = uiState.location.isNotBlank() && (uiState.selectedType != SupplyType.OTHER || uiState.notes.isNotBlank())
        
        Button(
            onClick = viewModel::nextStep,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = isEnabled,
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("REVIEW REQUEST", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun SupplyReview(uiState: SupplyUiState, viewModel: SupplyViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = viewModel::previousStep, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Review request", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        CrisisCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("ORDER SUMMARY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                
                ReviewRow(label = "Supply Category", value = uiState.selectedType?.name?.replace("_", " ") ?: "")
                ReviewRow(label = "Requested Quantity", value = "${uiState.quantity} Unit(s)")
                ReviewRow(label = "Delivery Location", value = uiState.location)
                
                if (uiState.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("ADDITIONAL NOTES", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = uiState.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    "This request will be stored in your outbox and sent to any rescue node that comes into range.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { viewModel.submitRequest() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("BROADCAST REQUEST", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun ReviewRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(1f).padding(start = 16.dp))
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
fun SupplyBroadcast(onComplete: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val radius by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 400f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseRadius"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(300.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color(0xFFFF9800).copy(alpha = alpha),
                    radius = radius
                )
                drawCircle(
                    color = Color(0xFFFF9800),
                    radius = 50.dp.toPx()
                )
            }
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Broadcast", tint = Color.White, modifier = Modifier.size(36.dp))
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Text("Request active", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Your request is propagating through the mesh network using Delay Tolerant Networking (DTN).",
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(64.dp))
        
        Button(
            onClick = onComplete,
            modifier = Modifier.padding(horizontal = 32.dp).fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("DONE", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun MyRequestsTab(activeRequests: List<SupplyRequest>, onCancel: (String) -> Unit) {
    if (activeRequests.isEmpty()) {
        EmptyState(
            icon = Icons.Default.Inventory,
            title = "No active requests",
            subtitle = "You haven't requested any supplies yet. Tap 'New Request' to begin.",
            modifier = Modifier.fillMaxSize().padding(16.dp)
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(activeRequests, key = { it.id }) { request ->
                SupplyRequestCard(request = request, onCancel = { onCancel(request.id) })
            }
        }
    }
}

@Composable
fun SupplyRequestCard(request: SupplyRequest, onCancel: () -> Unit) {
    val statusColor = when (request.status) {
        RequestStatus.QUEUED -> Color.Gray
        RequestStatus.BROADCASTING -> Color(0xFFFF9800)
        RequestStatus.NGO_RECEIVED -> Color(0xFF2196F3)
        RequestStatus.CONFIRMED -> Color(0xFF4CAF50)
        RequestStatus.DELIVERED -> Color(0xFF4CAF50)
        RequestStatus.EXPIRED -> MaterialTheme.colorScheme.error
        RequestStatus.CANCELLED -> Color.Gray
    }

    CrisisCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = request.status.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(request.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "${request.quantity}x ${request.requestType.name.replace("_", " ")}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
            
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = request.location,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (request.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = request.notes,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (request.assignedNgo != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Business, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(text = "NGO Assigned", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text(text = request.assignedNgo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        if (request.estimatedDelivery != null) {
                            Text(text = "ETA: ${request.estimatedDelivery}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (request.status in listOf(RequestStatus.QUEUED, RequestStatus.BROADCASTING)) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("CANCEL REQUEST")
                }
            }
        }
    }
}

