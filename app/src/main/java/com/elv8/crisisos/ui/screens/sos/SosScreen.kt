package com.elv8.crisisos.ui.screens.sos

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FireExtinguisher
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SosScreen(
    onNavigateBack: () -> Unit,
    viewModel: SosViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val infiniteTransition = rememberInfiniteTransition(label = "sosPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val bgColor = if (uiState.isBroadcasting) {
        Color(0xFF1A0000).copy(alpha = 1f - pulseAlpha)
    } else {
        Color(0xFF1A0000)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = {
                    if (uiState.isBroadcasting) {
                        viewModel.cancelBroadcast()
                    } else {
                        onNavigateBack()
                    }
                }) {
                    Icon(
                        imageVector = if (uiState.isBroadcasting) Icons.Filled.Close else Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Text(
                    text = if (uiState.isBroadcasting) "BROADCASTING SOS" else "SELECT EMERGENCY TYPE",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedVisibility(
                visible = !uiState.isBroadcasting,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column {
                    SosTypeGrid(
                        selectedType = uiState.sosType,
                        onTypeSelect = viewModel::selectSosType
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    if (uiState.sosType != null) {
                        OutlinedTextField(
                            value = uiState.messageText,
                            onValueChange = viewModel::updateMessage,
                            label = { Text("Add location or details (optional)", color = Color.White.copy(0.7f)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.White.copy(0.3f),
                                cursorColor = MaterialTheme.colorScheme.primary
                            ),
                            maxLines = 3
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (uiState.isBroadcasting) {
                BroadcastingState(uiState = uiState, onStop = viewModel::cancelBroadcast)
            } else if (uiState.sosType != null) {
                BroadcastButton(
                    confirmStep = uiState.confirmStep,
                    onConfirmStepChange = viewModel::confirmStep,
                    onCancel = onNavigateBack
                )
            }
        }
    }
}

@Composable
fun BroadcastingState(uiState: SosUiState, onStop: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "iconPulse")
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "iconScale"
        )

        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = "Broadcasting",
            tint = Color.Red,
            modifier = Modifier
                .size(80.dp)
                .scale(scale)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Broadcasting to ${uiState.broadcastCount} peers",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Keep your device turned on and nearby.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f)
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Button(
            onClick = onStop,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
            modifier = Modifier.fillMaxWidth().height(64.dp)
        ) {
            Text("STOP BROADCAST", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun BroadcastButton(
    confirmStep: Int,
    onConfirmStepChange: (Int) -> Unit,
    onCancel: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val btnColor by animateColorAsState(
            targetValue = if (confirmStep == 1) Color(0xFFFF5252) else Color(0xFFD32F2F),
            label = "btnColor"
        )

        Button(
            onClick = {
                if (confirmStep == 0) onConfirmStepChange(1) else onConfirmStepChange(2)
            },
            colors = ButtonDefaults.buttonColors(containerColor = btnColor),
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = if (confirmStep == 0) "BROADCAST SOS" else "TAP TO CONFIRM",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TextButton(onClick = onCancel) {
            Text("CANCEL", color = Color.White.copy(alpha = 0.7f))
        }
    }
}

@Composable
fun SosTypeGrid(
    selectedType: SosType?,
    onTypeSelect: (SosType) -> Unit
) {
    val types = SosType.entries.toTypedArray()
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(types) { type ->
            SosTypeCard(
                type = type,
                isSelected = type == selectedType,
                onClick = { onTypeSelect(type) }
            )
        }
    }
}

@Composable
fun SosTypeCard(
    type: SosType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "cardScale"
    )

    val bgColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFFFF9800).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
        label = "bgColor"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFFFF9800) else Color.Transparent,
        label = "borderColor"
    )

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .scale(scale)
            .border(2.dp, borderColor, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = getTypeIcon(type),
                contentDescription = type.title,
                tint = if (isSelected) Color(0xFFFF9800) else Color.White,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = type.title,
                color = if (isSelected) Color(0xFFFF9800) else Color.White,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center
            )
        }
    }
}

fun getTypeIcon(type: SosType): ImageVector {
    return when (type) {
        SosType.MEDICAL -> Icons.Filled.LocalHospital
        SosType.TRAPPED -> Icons.Filled.Report
        SosType.MISSING -> Icons.Filled.PersonSearch
        SosType.ARMED_THREAT -> Icons.Filled.Warning
        SosType.FIRE -> Icons.Filled.FireExtinguisher
        SosType.GENERAL -> Icons.Filled.Warning
    }
}

