package com.elv8.crisisos.ui.screens.supply

import com.elv8.crisisos.ui.components.EmptyState
import androidx.compose.material.icons.filled.ListAlt

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)@Composable
fun SupplyScreen(
    onNavigateBack: () -> Unit,
    viewModel: SupplyViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Supply Requests") },
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
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text("What do you need?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Select the most critical supply category.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(32.dp))

        // Icons map
        val supplyTypes = SupplyType.entries.toTypedArray()
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(supplyTypes) { type ->
                val isSelected = uiState.selectedType == type
                val bgColor = if (isSelected) Color(0xFFFF9800).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                val borderColor = if (isSelected) Color(0xFFFF9800) else Color.Transparent
                val contentColor = if (isSelected) Color(0xFFFF9800) else MaterialTheme.colorScheme.onSurfaceVariant
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(96.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(bgColor)
                            .border(2.dp, borderColor, CircleShape)
                            .clickable { viewModel.selectType(type) },
                        contentAlignment = Alignment.Center
                    ) {
                        val icon = when (type) {
                            SupplyType.WATER -> Icons.Default.Build // Placeholder for water
                            SupplyType.FOOD -> Icons.Default.ShoppingCart
                            SupplyType.MEDICINE -> Icons.Default.Add
                            SupplyType.SHELTER -> Icons.Default.Home
                            SupplyType.BLANKET -> Icons.Default.Favorite
                            SupplyType.EVACUATION -> Icons.AutoMirrored.Filled.DirectionsRun
                            SupplyType.EMERGENCY -> Icons.Default.Warning
                        }
                        Icon(icon, contentDescription = type.name, tint = contentColor, modifier = Modifier.size(32.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = type.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = contentColor
                    )
                }
            }
        }
    }
}

@Composable
fun SupplyDetails(uiState: SupplyUiState, viewModel: SupplyViewModel) {
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
            Text("Provide Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text("Quantity Units", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(8.dp)
        ) {
            IconButton(onClick = { viewModel.updateQuantity(uiState.quantity - 1) }) {
                Icon(Icons.Default.Close, contentDescription = "Decrease") 
            } // Using Close/Clear instead of standard minus if missing
            Text(
                "${uiState.quantity}",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { viewModel.updateQuantity(uiState.quantity + 1) }) {
                Icon(Icons.Default.Add, contentDescription = "Increase")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = uiState.location,
            onValueChange = viewModel::updateLocation,
            label = { Text("Exact Location *") },
            placeholder = { Text("e.g. Sector 4, Building B") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = uiState.notes,
            onValueChange = viewModel::updateNotes,
            label = { Text("Additional Notes (Optional)") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = viewModel::nextStep,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = uiState.location.isNotBlank(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("REVIEW REQUEST")
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
            Text("Review Request", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        CrisisCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("SUMMARY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Type", style = MaterialTheme.typography.bodyMedium)
                    Text("${uiState.selectedType?.name}", fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Quantity", style = MaterialTheme.typography.bodyMedium)
                    Text("${uiState.quantity} Units", fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Location", style = MaterialTheme.typography.bodyMedium)
                    Text("${uiState.location}", fontWeight = FontWeight.Bold)
                }
                
                if (uiState.notes.isNotBlank()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Text("Notes", style = MaterialTheme.typography.bodyMedium)
                    Text("${uiState.notes}", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { viewModel.submitRequest() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("SUBMIT TO MESH", fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun SupplyBroadcast(onComplete: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val radius by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 300f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseRadius"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    val color = Color(0xFFFF9800)

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
                    color = color.copy(alpha = alpha),
                    radius = radius
                )
                drawCircle(
                    color = color,
                    radius = 40.dp.toPx()
                )
            }
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Broadcast", tint = Color.White, modifier = Modifier.size(32.dp))
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text("Searching for NGO nodes...", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Request propagating through mesh network DTN.\nWill be delivered in up to 72 hours.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        
        Spacer(modifier = Modifier.height(48.dp))
        
        OutlinedButton(onClick = onComplete) {
            Text("VIEW MY REQUESTS")
        }
    }
}

@Composable
fun MyRequestsTab(activeRequests: List<SupplyRequest>, onCancel: (String) -> Unit) {
    if (activeRequests.isEmpty()) {
        EmptyState(
            icon = Icons.Default.ListAlt,
            title = "No active requests",
            subtitle = "Create a request above to get help",
            modifier = Modifier.fillMaxSize().padding(16.dp)
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(activeRequests, key = { it.id }) { request ->
            val statusColor = when (request.status) {
                RequestStatus.QUEUED, RequestStatus.CANCELLED -> Color.Gray       
                RequestStatus.BROADCASTING -> Color(0xFFFF9800)
                RequestStatus.NGO_RECEIVED -> Color(0xFF2196F3)
                RequestStatus.CONFIRMED, RequestStatus.DELIVERED -> Color(0xFF4CAF50)
                RequestStatus.EXPIRED -> Color(0xFFF44336)
            }
            
            CrisisCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(request.status.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = statusColor)
                        }
                        
                        if (request.status in listOf(RequestStatus.QUEUED, RequestStatus.BROADCASTING)) {
                            TextButton(onClick = { onCancel(request.id) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(24.dp)) {
                                Text("CANCEL", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        "${request.quantity}x ${request.requestType.name}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Location: ${request.location}", style = MaterialTheme.typography.bodyMedium)
                    
                    if (request.assignedNgo != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Assigned to: ${request.assignedNgo}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    if (request.estimatedDelivery != null && request.status != RequestStatus.DELIVERED) {
                        Text("ETA: ${request.estimatedDelivery}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }}}

