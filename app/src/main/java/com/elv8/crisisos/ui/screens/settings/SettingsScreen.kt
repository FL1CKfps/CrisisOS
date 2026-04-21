package com.elv8.crisisos.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showClearDataDialog by remember { mutableStateOf(false) }

    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text("Clear all local data?") },
            text = { Text("This is a destructive action and will remove all messages, contacts, and settings from this device forever.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllData()
                        showClearDataDialog = false
                    }
                ) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings / Profile", fontWeight = FontWeight.Bold) },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Profile Section
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val initial = uiState.profile.alias.takeIf { it.isNotBlank() }?.take(1)?.uppercase() ?: "U"
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initial,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = uiState.profile.alias,
                        onValueChange = viewModel::updateAlias,
                        label = { Text("Alias (Display Name)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "User ID: " + uiState.profile.userId + "",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Visible to nearby mesh peers",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Network Section
            item { SettingsSectionHeader("Network Mesh") }
            item {
                SettingsSliderRow(
                    title = "Broadcast Range (m)",
                    value = uiState.broadcastRange.toFloat(),
                    onValueChange = { viewModel.updateBroadcastRange(it.toInt()) },
                    valueRange = 10f..100f,
                    steps = 2
                )
            }
            item {
                SettingsSwitchRow(
                    title = "Auto-connect on launch",
                    subtitle = "Automatically join nearest mesh network",
                    icon = Icons.Default.Wifi,
                    checked = uiState.autoConnect,
                    onCheckedChange = { viewModel.updateAutoConnect(it) }
                )
            }
            item {
                SettingsChipsRow(
                    title = "Discovery Mode",
                    options = listOf("Active", "Passive", "Off"),
                    selectedOption = uiState.discoveryMode,
                    onOptionSelected = { viewModel.updateDiscoveryMode(it) }
                )
            }

            // Safety Section
            item { SettingsSectionHeader("Safety") }
            item {
                ListItem(
                    headlineContent = { Text("Emergency Contacts") },
                    supportingContent = { Text(" assigned contacts") },
                    leadingContent = { Icon(Icons.Default.Contacts, contentDescription = null) },
                    trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                    modifier = Modifier.clickable { /* TBD */ }
                )
            }
            item {
                SettingsSwitchRow(
                    title = "Auto-SOS on low battery",
                    subtitle = "Trigger broadcast when battery drops below threshold",
                    icon = Icons.Default.BatteryAlert,
                    checked = uiState.autoSosLowBattery,
                    onCheckedChange = { viewModel.updateAutoSosLowBattery(it) }
                )
            }
            item {
                if (uiState.autoSosLowBattery) {
                    SettingsSliderRow(
                        title = "Battery Threshold (%)",
                        value = uiState.autoSosThreshold.toFloat(),
                        onValueChange = { viewModel.updateAutoSosThreshold(it.toInt()) },
                        valueRange = 5f..30f,
                        steps = 4
                    )
                }
            }

            // Appearance Section
            item { SettingsSectionHeader("Appearance") }
            item {
                SettingsSwitchRow(
                    title = "High contrast mode",
                    icon = Icons.Default.Contrast,
                    checked = uiState.profile.highContrastMode,
                    onCheckedChange = { viewModel.toggleHighContrast(it) }
                )
            }
            item {
                SettingsSwitchRow(
                    title = "Reduced animations",
                    icon = Icons.Default.Animation,
                    checked = uiState.profile.reducedAnimations,
                    onCheckedChange = { viewModel.toggleReducedAnimations(it) }
                )
            }
            item {
                SettingsSliderRow(
                    title = "Text Size (%)",
                    value = uiState.textSize.toFloat(),
                    onValueChange = { viewModel.updateTextSize(it.toInt()) },
                    valueRange = 80f..150f,
                    steps = 4
                )
            }

            // Data Section
            item { SettingsSectionHeader("Data & Storage") }
            item {
                ListItem(
                    headlineContent = { Text("Storage Used") },
                    supportingContent = { Text("34 MB") },
                    leadingContent = { Icon(Icons.Default.Storage, contentDescription = null) }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Export my data") },
                    leadingContent = { Icon(Icons.Default.Download, contentDescription = null) },
                    modifier = Modifier.clickable { /* Stub */ }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Clear all local data", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable { showClearDataDialog = true }
                )
            }
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsSwitchRow(
    title: String,
    subtitle: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (subtitle != null) { { Text(subtitle) } } else null,
        leadingContent = if (icon != null) { { Icon(icon, contentDescription = null) } } else null,
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
        modifier = Modifier.clickable { onCheckedChange(!checked) }
    )
}

@Composable
fun SettingsSliderRow(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsChipsRow(
    title: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = selectedOption == option,
                    onClick = { onOptionSelected(option) },
                    label = { Text(option) }
                )
            }
        }
    }
}
