package com.elv8.crisisos.ui.screens.childalert

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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elv8.crisisos.domain.model.ChildRecord
import com.elv8.crisisos.domain.model.ChildStatus
import com.elv8.crisisos.ui.components.CrisisCard
import com.elv8.crisisos.ui.components.LocalTopBarState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Amber/Orange tones for child alert
val ChildAlertAmber = Color(0xFFFFB300)
val ChildAlertOrange = Color(0xFFFF9800)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildAlertScreen(
    onNavigateBack: () -> Unit,
    viewModel: ChildAlertViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activeCount = uiState.registeredChildren.count { it.status == ChildStatus.SEARCHING }
    val topBarState = LocalTopBarState.current

    LaunchedEffect(activeCount) {
        topBarState.update(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ChildCare, contentDescription = null, tint = ChildAlertOrange, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("CHILD ALERTS", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("Mesh Reunification", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                Surface(
                    color = ChildAlertAmber.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "$activeCount ACTIVE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = ChildAlertOrange,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Determine whether to show Form or Confirmation Card
            item {
                Spacer(modifier = Modifier.height(8.dp))
                AnimatedContent(
                    targetState = uiState.newlyRegisteredChild != null,
                    label = "form_or_success"
                ) { showSuccess ->
                    if (showSuccess) {
                        uiState.newlyRegisteredChild?.let { newChild ->
                            SuccessCard(
                                childRecord = newChild,
                                onDismiss = viewModel::dismissConfirmation
                            )
                        }
                    } else {
                        RegistrationForm(
                            formState = uiState.registrationForm,
                            isRegistering = uiState.isRegistering,
                            onUpdateForm = viewModel::updateForm,
                            onIncrementAge = viewModel::incrementAge,
                            onDecrementAge = viewModel::decrementAge,
                            onRegister = viewModel::registerChild
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "ACTIVE ALERTS",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            }

            items(uiState.registeredChildren, key = { it.crsChildId }) { alert ->
                ChildAlertCard(record = alert)
            }

            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }
}

@Composable
fun RegistrationForm(
    formState: ChildFormState,
    isRegistering: Boolean,
    onUpdateForm: ((ChildFormState) -> ChildFormState) -> Unit,
    onIncrementAge: () -> Unit,
    onDecrementAge: () -> Unit,
    onRegister: () -> Unit
) {
    CrisisCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("REGISTER MISSING CHILD", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = ChildAlertOrange)
            
            OutlinedTextField(
                value = formState.childName,
                onValueChange = { onUpdateForm { form -> form.copy(childName = it) } },
                label = { Text("Child's Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Age Stepper
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Approximate Age", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDecrementAge) {
                        Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Decrease Age")
                    }
                    Text(
                        text = "${formState.approximateAge}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(36.dp),
                        textAlign = TextAlign.Center
                    )
                    IconButton(onClick = onIncrementAge) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = "Increase Age")
                    }
                }
            }

            OutlinedTextField(
                value = formState.physicalDescription,
                onValueChange = { onUpdateForm { form -> form.copy(physicalDescription = it) } },
                label = { Text("Physical Description & Clothing") },
                modifier = Modifier.fillMaxWidth().height(100.dp),
                shape = RoundedCornerShape(12.dp),
                maxLines = 4
            )

            OutlinedTextField(
                value = formState.lastKnownLocation,
                onValueChange = { onUpdateForm { form -> form.copy(lastKnownLocation = it) } },
                label = { Text("Last Known Location") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = formState.registeredBy,
                onValueChange = { onUpdateForm { form -> form.copy(registeredBy = it) } },
                label = { Text("Your Contact Identifier") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Button(
                onClick = onRegister,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ChildAlertOrange),
                enabled = !isRegistering && 
                         formState.childName.isNotBlank() && 
                         formState.physicalDescription.isNotBlank() &&
                         formState.lastKnownLocation.isNotBlank() && 
                         formState.registeredBy.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isRegistering) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("BROADCASTING...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.WifiTethering, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("REGISTER & BROADCAST", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SuccessCard(childRecord: ChildRecord, onDismiss: () -> Unit) {
    CrisisCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = "Success", tint = Color(0xFF4CAF50), modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("ALERT BROADCASTED", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Your alert is replicating across local mesh nodes.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Generated ID Block
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                    Text("UNIQUE RECORD ID", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = childRecord.crsChildId,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = ChildAlertOrange
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Text("Share this ID with rescue workers", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.height(16.dp))

            // Placeholder QR Code
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .border(2.dp, Color.Black, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("QR", color = Color.Black, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium)
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("CLOSE FILE")
            }
        }
    }
}

@Composable
fun ChildAlertCard(record: ChildRecord) {
    CrisisCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: ID and Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = record.crsChildId,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.secondary
                )
                StatusBadge(status = record.status)
            }
            
            Spacer(modifier = Modifier.height(12.dp))

            // Name and Age
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = record.childName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        "Age: ${record.approximateAge}", 
                        style = MaterialTheme.typography.labelSmall, 
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Physical description
            Text(record.physicalDescription, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 3, overflow = TextOverflow.Ellipsis)
            
            Spacer(modifier = Modifier.height(16.dp))

            // Last known & location
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                if (record.status == ChildStatus.LOCATED && record.locatedAt != null) {
                    Text("Located at: ${record.locatedAt}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                } else {
                    Text("Last matching loc: ${record.lastKnownLocation}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(8.dp))

            // Footer metadata
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Rep: ${record.registeredBy}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NetworkCell, contentDescription = null, tint = ChildAlertOrange, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${record.broadcastCount} hops", style = MaterialTheme.typography.labelSmall, color = ChildAlertOrange)
                }
            }
            
            val dateStr = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(record.registeredAt))
            Text(dateStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun StatusBadge(status: ChildStatus) {
    when (status) {
        ChildStatus.SEARCHING -> {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse_alpha"
            )
            
            Surface(
                color = ChildAlertAmber.copy(alpha = alpha * 0.3f), // Base color plus pulsing alpha
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ChildAlertAmber.copy(alpha = alpha))
            ) {
                Text(
                    text = "SEARCHING",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = ChildAlertAmber.copy(alpha = alpha * 0.5f + 0.5f), // Ensure text remains visible
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        ChildStatus.LOCATED -> {
            Surface(
                color = Color(0xFF4CAF50).copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = "LOCATED",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        ChildStatus.REUNITED -> {
             Surface(
                color = Color.Gray.copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = "REUNITED",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
