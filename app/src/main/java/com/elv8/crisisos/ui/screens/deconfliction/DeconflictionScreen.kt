package com.elv8.crisisos.ui.screens.deconfliction

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elv8.crisisos.domain.model.DeconflictionReport
import com.elv8.crisisos.domain.model.ProtectionStatus
import com.elv8.crisisos.domain.model.ReportType
import com.elv8.crisisos.ui.components.CrisisCard
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeconflictionScreen(
    onNavigateBack: () -> Unit,
    viewModel: DeconflictionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AddBox, contentDescription = null, tint = Color(0xFFF44336), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("DECONFLICTION SYSTEM", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("Geneva Convention Protection Reports", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
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
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Stepper Header
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    StepperHeader(
                        currentStep = uiState.currentStep,
                        onStepClick = { step -> 
                            if (step < uiState.currentStep) {
                                if (step == 1) viewModel.resetDraft() else viewModel.previousStep()
                            }
                        }
                    )
                }

                // Step Content
                item {
                    AnimatedContent(
                        targetState = uiState.currentStep,
                        label = "step_content"
                    ) { step ->
                        when(step) {
                            1 -> StepOneTypeSelection(
                                selectedType = uiState.draftType,
                                onSelect = viewModel::updateDraftType,
                                onNext = viewModel::nextStep
                            )
                            2 -> StepTwoDetails(
                                facilityName = uiState.draftFacilityName,
                                onNameChange = viewModel::updateFacilityName,
                                coordinates = uiState.draftCoordinates,
                                onCoordsChange = viewModel::updateCoordinates,
                                onUseGps = viewModel::useCurrentLocation,
                                status = uiState.draftStatus,
                                onStatusChange = viewModel::updateProtectionStatus,
                                onNext = viewModel::nextStep,
                                onBack = viewModel::previousStep
                            )
                            3 -> StepThreeGenerate(
                                state = uiState,
                                onGenerate = viewModel::generateReport,
                                onReset = viewModel::resetDraft,
                                onBack = viewModel::previousStep
                            )
                        }
                    }
                }

                // Divider before list
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Text("ACTIVE BROADCASTS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }

                // Past Reports List
                items(uiState.reports, key = { it.id }) { report ->
                    ReportCard(report = report)
                }
                
                item { Spacer(modifier = Modifier.height(40.dp)) }
            }
        }
    }
}

@Composable
fun StepperHeader(currentStep: Int, onStepClick: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf("TYPE", "DETAILS", "BROADCAST").forEachIndexed { index, title ->
            val stepNumber = index + 1
            val isActive = currentStep == stepNumber
            val isCompleted = currentStep > stepNumber
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = isCompleted) { onStepClick(stepNumber) }
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            when {
                                isActive -> MaterialTheme.colorScheme.primary
                                isCompleted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCompleted) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                    } else {
                        Text(
                            text = stepNumber.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isActive || isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                )
            }
            
            if (index < 2) {
                HorizontalDivider(
                    modifier = Modifier.weight(0.5f).padding(horizontal = 8.dp),
                    color = if (isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@Composable
fun StepOneTypeSelection(
    selectedType: ReportType?,
    onSelect: (ReportType) -> Unit,
    onNext: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ReportType.values().forEach { type ->
            val isSelected = selectedType == type
            CrisisCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(type) }
                    .then(
                        if (isSelected) Modifier.border(2.dp, Color(0xFFFF9800), RoundedCornerShape(16.dp))
                        else Modifier
                    )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = getTypeIcon(type),
                        contentDescription = null,
                        tint = if (isSelected) Color(0xFFFF9800) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(text = type.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(text = type.article, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = selectedType != null,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("CONTINUE", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun StepTwoDetails(
    facilityName: String,
    onNameChange: (String) -> Unit,
    coordinates: String,
    onCoordsChange: (String) -> Unit,
    onUseGps: () -> Unit,
    status: ProtectionStatus,
    onStatusChange: (ProtectionStatus) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = facilityName,
            onValueChange = onNameChange,
            label = { Text("Facility / Zone Name") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Column {
            OutlinedTextField(
                value = coordinates,
                onValueChange = onCoordsChange,
                label = { Text("Coordinates (MGRS or Lat/Lon)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onUseGps,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("FETCH CURRENT LOCATION")
            }
        }
        
        Text("CURRENT STATUS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProtectionStatus.values().forEach { stat ->
                val isSelected = status == stat
                val (color, _) = getStatusInfo(stat)
                
                Surface(
                    onClick = { onStatusChange(stat) },
                    modifier = Modifier.weight(1f).height(60.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                    border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, color) else null
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = stat.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("BACK")
            }
            Button(
                onClick = onNext,
                modifier = Modifier.weight(1f).height(56.dp),
                enabled = facilityName.isNotBlank() && coordinates.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("REVIEW", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun StepThreeGenerate(
    state: DeconflictionUiState,
    onGenerate: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit
) {
    var scramblingText by remember { mutableStateOf("0000000000000000") }
    
    LaunchedEffect(state.isGenerating) {
        if (state.isGenerating) {
            val chars = "0123456789abcdef"
            while (true) {
                scramblingText = (1..16).map { chars.random() }.joinToString("")
                delay(50)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        CrisisCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("REVIEW REPORT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                DetailRow("Type", state.draftType?.label ?: "")
                DetailRow("Article", state.draftType?.article ?: "")
                DetailRow("Facility", state.draftFacilityName)
                DetailRow("Location", state.draftCoordinates)
                DetailRow("Status", state.draftStatus.label, getStatusInfo(state.draftStatus).first)
            }
        }

        if (state.generatedHash != null) {
            // Success State
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("REPORT BROADCASTED", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = state.generatedHash.uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Verified locally. Replicating across mesh.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Button(
                onClick = onReset,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("FILE ANOTHER REPORT", fontWeight = FontWeight.Bold)
            }
            
        } else {
            // Generatiing State
            if (state.isGenerating) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("CRYPTOGRAPHICALLY SIGNING...", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = scramblingText,
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Pre-generate
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("BACK")
                    }
                    Button(
                        onClick = onGenerate,
                        modifier = Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.BroadcastOnPersonal, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("BROADCAST", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = if (valueColor != Color.Unspecified) valueColor else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun ReportCard(report: DeconflictionReport) {
    val (statusColor, statusIcon) = getStatusInfo(report.protectionStatus)
    
    CrisisCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(getTypeIcon(report.reportType), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(report.reportType.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                
                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = report.protectionStatus.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(report.facilityName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(report.coordinates, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("BROADCAST HASH", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = report.id.uppercase(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = report.genevaArticle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

fun getTypeIcon(type: ReportType): ImageVector {
    return when (type) {
        ReportType.MEDICAL_FACILITY -> Icons.Default.LocalHospital
        ReportType.HUMANITARIAN_CORRIDOR -> Icons.AutoMirrored.Filled.DirectionsWalk
        ReportType.CIVILIAN_ZONE -> Icons.Default.Home
        ReportType.CULTURAL_SITE -> Icons.Default.Museum
        ReportType.WATER_SOURCE -> Icons.Default.WaterDrop
    }
}

fun getStatusInfo(status: ProtectionStatus): Pair<Color, ImageVector> {
    return when (status) {
        ProtectionStatus.PROTECTED -> Pair(Color(0xFF4CAF50), Icons.Default.Shield)
        ProtectionStatus.AT_RISK -> Pair(Color(0xFFFF9800), Icons.Default.Warning)
        ProtectionStatus.VIOLATED -> Pair(Color(0xFFF44336), Icons.Default.Report)
    }
}