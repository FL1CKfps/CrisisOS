package com.elv8.crisisos.ui.screens.aiassistant

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mikepenz.markdown.m3.Markdown
import com.elv8.crisisos.domain.model.AiMessage
import com.elv8.crisisos.domain.model.AiRole
import com.elv8.crisisos.ui.components.LocalTopBarState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantScreen(
    onNavigateBack: () -> Unit,
    onNavigateToFeature: (String, Map<String, String>) -> Unit,
    viewModel: AiViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val topBarState = LocalTopBarState.current
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        viewModel.attachImage(uri)
    }

    LaunchedEffect(Unit) {
        topBarState.update(
            title = {
                Column {
                    Text("SURVIVAL AI", fontWeight = FontWeight.Bold)
                    Text(
                        "ON-DEVICE · GEMMA 4 E2B",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                if (uiState.isOffline) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF4CAF50).copy(alpha = 0.1f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.WifiOff, contentDescription = "Offline", modifier = Modifier.size(16.dp), tint = Color(0xFF4CAF50))
                        Spacer(Modifier.width(4.dp))
                        Text("OFFLINE", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                    }
                }
            }
        )
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        
        if (!uiState.isModelLoaded) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val statusText = when {
                        uiState.downloadProgress != null -> "Downloading model shard..."
                        uiState.isModelLoading -> "Initializing Gemma 4 E2B engine..."
                        else -> "Survival AI model is not installed. Download Gemma 4 E2B to enable offline guidance."
                    }

                    Text(
                        text = uiState.errorMessage ?: statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    if (uiState.downloadProgress != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            LinearProgressIndicator(
                                progress = { uiState.downloadProgress ?: 0f },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Downloading: ${(uiState.downloadProgress!! * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    } else if (uiState.isModelLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Button(
                                onClick = { viewModel.downloadModel() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("DOWNLOAD MODEL (SHARD)")
                            }
                            
                            if (uiState.errorMessage != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(onClick = { viewModel.deleteModel() }) {
                                    Text("WIPE CORRUPT FILE & RETRY", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Quick Prompts
        ScrollableQuickPrompts { prompt ->
            viewModel.sendMessage(prompt)
            focusManager.clearFocus()
        }

        // Chat Messages
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(Modifier.height(16.dp)) }
            
            if (uiState.isThinking) {
                item {
                    ThinkingIndicator()
                }
            }

            items(uiState.messages.reversed(), key = { it.id }) { message ->
                AiMessageItem(
                    message = message,
                    onActionClick = { action ->
                        onNavigateToFeature(action.feature, action.params)
                    }
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        // Input Area
        MessageInputSection(
            inputText = uiState.inputText,
            attachedImageUri = uiState.attachedImageUri,
            isProcessing = uiState.isProcessing,
            onTextChange = viewModel::updateInput,
            onSend = {
                viewModel.sendMessage(null)
                focusManager.clearFocus()
            },
            onAttachImage = { imagePickerLauncher.launch("image/*") },
            onClearAttachedImage = viewModel::clearAttachedImage,
            onStop = viewModel::stopResponse
        )
    }
}

@Composable
fun ScrollableQuickPrompts(onPromptClicked: (String) -> Unit) {
    val prompts = listOf(
        "First aid for bleeding",
        "How to find water",
        "Safe evacuation route",
        "Who to contact for help",
        "How to signal for rescue"
    )
    
    androidx.compose.foundation.lazy.LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(prompts) { prompt ->
            SuggestionChip(
                onClick = { onPromptClicked(prompt) },
                label = { Text(prompt) },
                icon = { Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
        }
    }
}

@Composable
fun AiMessageItem(
    message: AiMessage,
    onActionClick: (com.elv8.crisisos.domain.model.AiAction) -> Unit = {}
) {
    when (message.role) {
        AiRole.SYSTEM -> {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            )
        }
        AiRole.USER -> {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(16.dp)
                ) {
                    Text(
                        text = message.content,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
        AiRole.ASSISTANT -> {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                Column(
                    modifier = Modifier.fillMaxWidth(0.85f)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(16.dp)
                    ) {
                        Column {
                            if (message.isStreaming) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = message.content,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    BlinkingCursor()
                                }
                            } else {
                                // Use a key to prevent re-rendering issues with the Markdown library
                                key(message.id) {
                                    Markdown(content = message.content)
                                }
                            }
                        }
                    }

                    if (!message.isStreaming && message.actions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        message.actions.forEach { action ->
                            ActionSuggestionChip(action = action, onClick = { onActionClick(action) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActionSuggestionChip(
    action: com.elv8.crisisos.domain.model.AiAction,
    onClick: () -> Unit
) {
    val label = when (action.feature) {
        "SUPPLY_REQUEST" -> "Create Supply Request"
        "OFFLINE_MAPS" -> "Open Maps"
        "MISSING_PERSON" -> "Find Person"
        "SOS" -> "Broadcast SOS"
        "CHECKPOINT_INTEL" -> "Check Checkpoints"
        "CRISIS_NEWS" -> "Read News"
        else -> "Open ${action.feature}"
    }
    
    val icon = when (action.feature) {
        "SUPPLY_REQUEST" -> Icons.Default.Inventory
        "OFFLINE_MAPS" -> Icons.Default.Map
        "MISSING_PERSON" -> Icons.Default.PersonSearch
        "SOS" -> Icons.Default.Warning
        "CHECKPOINT_INTEL" -> Icons.Default.Security
        "CRISIS_NEWS" -> Icons.Default.Newspaper
        else -> Icons.AutoMirrored.Filled.Launch
    }

    Button(
        onClick = onClick,
        modifier = Modifier.padding(vertical = 4.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun ThinkingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .alpha(alpha),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Psychology,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "AI is thinking",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                BlinkingDots()
            }
        }
    }
}

@Composable
fun BlinkingDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val dotCount = 3
    
    Row {
        repeat(dotCount) { index ->
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = index * 200, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dotAlpha"
            )
            Text(
                text = ".",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
            )
        }
    }
}

@Composable
fun BlinkingCursor() {
    val infiniteTransition = rememberInfiniteTransition(label = "cursorBlink")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorAlpha"
    )
    
    Box(
        modifier = Modifier
            .padding(start = 4.dp)
            .width(4.dp)
            .height(18.dp)
            .alpha(alpha)
            .background(MaterialTheme.colorScheme.primary)
    )
}

@Composable
fun MessageInputSection(
    inputText: String,
    attachedImageUri: Uri?,
    isProcessing: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttachImage: () -> Unit,
    onClearAttachedImage: () -> Unit,
    onStop: () -> Unit
) {
    val canSend = (inputText.isNotBlank() || attachedImageUri != null) && !isProcessing

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (attachedImageUri != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = attachedImageUri,
                        contentDescription = "Attached image",
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Image attached",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onClearAttachedImage,
                        enabled = !isProcessing
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Remove image")
                    }
                }
            }

            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onAttachImage,
                    enabled = !isProcessing
                ) {
                    Icon(
                        Icons.Default.AddPhotoAlternate,
                        contentDescription = "Attach image",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.width(8.dp))

                OutlinedTextField(
                    value = inputText,
                    onValueChange = onTextChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(if (isProcessing) "AI is responding..." else "Ask anything...") },
                    enabled = !isProcessing,
                    shape = CircleShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        disabledBorderColor = Color.Transparent,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    maxLines = 4
                )

                Spacer(Modifier.width(12.dp))

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isProcessing -> MaterialTheme.colorScheme.error
                                canSend -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                        .clickable(enabled = isProcessing || canSend) {
                            if (isProcessing) onStop() else onSend()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isProcessing) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = "Stop response",
                            tint = MaterialTheme.colorScheme.onError
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
