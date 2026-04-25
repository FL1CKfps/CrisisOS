package com.elv8.crisisos.ui.screens.aiassistant

import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        viewModel.attachImage(uri)
    }

    // Voice input → speech-to-text via the system recognizer (works offline on most devices).
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.setListening(false)
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            .orEmpty()
        if (spoken.isNotBlank()) viewModel.appendVoiceTranscript(spoken)
    }

    val launchVoiceInput: () -> Unit = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now…")
        }
        try {
            viewModel.setListening(true)
            voiceLauncher.launch(intent)
        } catch (e: Exception) {
            viewModel.setListening(false)
            Toast.makeText(context, "Voice input not available on this device", Toast.LENGTH_SHORT).show()
        }
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
                            .padding(end = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF4CAF50).copy(alpha = 0.1f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.WifiOff, contentDescription = "Offline", modifier = Modifier.size(16.dp), tint = Color(0xFF4CAF50))
                        Spacer(Modifier.width(4.dp))
                        Text("OFFLINE", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                    }
                }
                IconButton(
                    onClick = { viewModel.clearConversation() },
                    enabled = uiState.isModelLoaded || uiState.messages.size > 1
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = "New chat")
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
            ModelStatusBanner(
                downloadProgress = uiState.downloadProgress,
                isModelLoading = uiState.isModelLoading,
                errorMessage = uiState.errorMessage,
                onDownload = { viewModel.downloadModel() },
                onWipe = { viewModel.deleteModel() }
            )
        } else {
            // Tiny live performance HUD — feels snappier when the user can see the speed.
            uiState.lastMetrics?.let { m ->
                PerformanceHud(
                    firstTokenMs = m.firstTokenLatencyMs,
                    tokensPerSecond = m.tokensPerSecond
                )
            }
        }

        // Categorized smart prompts
        PromptCategoryRow(
            active = uiState.activeCategory,
            onSelect = viewModel::selectCategory
        )

        ScrollableQuickPrompts(
            category = uiState.activeCategory,
            enabled = uiState.isModelLoaded && !uiState.isProcessing
        ) { prompt ->
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
                item { ThinkingIndicator() }
            }

            val lastAssistantId = uiState.messages.lastOrNull { it.role == AiRole.ASSISTANT }?.id

            items(uiState.messages.reversed(), key = { it.id }) { message ->
                AiMessageItem(
                    message = message,
                    isSpeaking = uiState.speakingMessageId == message.id,
                    canRegenerate = message.id == lastAssistantId && !uiState.isProcessing && uiState.isModelLoaded,
                    onActionClick = { action ->
                        onNavigateToFeature(action.feature, action.params)
                    },
                    onCopy = { viewModel.copyMessage(message) },
                    onSpeak = { viewModel.speakMessage(message) },
                    onRegenerate = { viewModel.regenerateLastResponse() }
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        // Input Area
        MessageInputSection(
            inputText = uiState.inputText,
            attachedImageUri = uiState.attachedImageUri,
            isProcessing = uiState.isProcessing,
            isListening = uiState.isListening,
            onTextChange = viewModel::updateInput,
            onSend = {
                viewModel.sendMessage(null)
                focusManager.clearFocus()
            },
            onAttachImage = { imagePickerLauncher.launch("image/*") },
            onClearAttachedImage = viewModel::clearAttachedImage,
            onStop = viewModel::stopResponse,
            onVoice = launchVoiceInput
        )
    }
}

@Composable
private fun ModelStatusBanner(
    downloadProgress: Float?,
    isModelLoading: Boolean,
    errorMessage: String?,
    onDownload: () -> Unit,
    onWipe: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val statusText = when {
                downloadProgress != null -> "Downloading model shard..."
                isModelLoading -> "Initializing Gemma 4 E2B engine..."
                else -> "Survival AI model is not installed. Download Gemma 4 E2B to enable offline guidance."
            }

            Text(
                text = errorMessage ?: statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (downloadProgress != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Downloading: ${(downloadProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            } else if (isModelLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = onDownload,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("DOWNLOAD MODEL (SHARD)")
                    }

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = onWipe) {
                            Text("WIPE CORRUPT FILE & RETRY", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PerformanceHud(firstTokenMs: Long, tokensPerSecond: Float) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Bolt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Last response · first token ${firstTokenMs}ms · ${"%.1f".format(tokensPerSecond)} tok/s",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PromptCategoryRow(
    active: PromptCategory,
    onSelect: (PromptCategory) -> Unit
) {
    val categories = remember { PromptCategory.values().toList() }
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories) { category ->
            FilterChip(
                selected = category == active,
                onClick = { onSelect(category) },
                label = {
                    Text(
                        category.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (category == active) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
        }
    }
}

private fun promptsFor(category: PromptCategory): List<String> = when (category) {
    PromptCategory.GENERAL -> listOf(
        "What should I do right now?",
        "How do I find a safe camp?",
        "How do I purify water?",
        "How do I signal for rescue?"
    )
    PromptCategory.MEDICAL -> listOf(
        "Severe bleeding — what do I do?",
        "Child with high fever and diarrhea",
        "Suspected broken leg",
        "Treat a burn in the field",
        "CPR steps",
        "Shock — symptoms and response"
    )
    PromptCategory.CHECKPOINT -> listOf(
        "I'm at a checkpoint — Arabic script please",
        "Hostile soldier asks for ID — what to say",
        "Ukrainian: I'm a civilian with my children",
        "Russian: please let us pass to the camp",
        "French: we are a civilian convoy",
        "Hindi: हम शरणार्थी हैं"
    )
    PromptCategory.LEGAL -> listOf(
        "They took my documents — what are my rights?",
        "Can soldiers separate me from my child?",
        "What is the Fourth Geneva Convention?",
        "Detained without charge — what to do",
        "Are NGO camps legally protected?"
    )
    PromptCategory.SURVIVAL -> listOf(
        "How do I purify water without tablets?",
        "Build a shelter from rubble",
        "Navigate without a phone or GPS",
        "Make food last 7 days",
        "Stay warm in the cold without power"
    )
    PromptCategory.SIGNAL -> listOf(
        "Signal a helicopter for rescue",
        "Make a fire visible at night",
        "Mirror flash — Morse SOS",
        "Mark a roof as a civilian site",
        "Whistle code for help"
    )
}

@Composable
fun ScrollableQuickPrompts(
    category: PromptCategory,
    enabled: Boolean = true,
    onPromptClicked: (String) -> Unit
) {
    val prompts = promptsFor(category)
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(prompts) { prompt ->
            SuggestionChip(
                onClick = { if (enabled) onPromptClicked(prompt) },
                enabled = enabled,
                label = { Text(prompt) },
                icon = { Icon(iconForCategory(category), contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
        }
    }
}

private fun iconForCategory(category: PromptCategory): ImageVector = when (category) {
    PromptCategory.MEDICAL -> Icons.Default.LocalHospital
    PromptCategory.CHECKPOINT -> Icons.Default.Security
    PromptCategory.LEGAL -> Icons.Default.Newspaper
    PromptCategory.SURVIVAL -> Icons.Default.Warning
    PromptCategory.SIGNAL -> Icons.Default.Bolt
    PromptCategory.GENERAL -> Icons.Default.Memory
}

@Composable
fun AiMessageItem(
    message: AiMessage,
    isSpeaking: Boolean = false,
    canRegenerate: Boolean = false,
    onActionClick: (com.elv8.crisisos.domain.model.AiAction) -> Unit = {},
    onCopy: () -> Unit = {},
    onSpeak: () -> Unit = {},
    onRegenerate: () -> Unit = {}
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
                    modifier = Modifier.fillMaxWidth(0.9f)
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

                    if (!message.isStreaming) {
                        AssistantMessageActions(
                            isSpeaking = isSpeaking,
                            canRegenerate = canRegenerate,
                            tokensPerSecond = message.tokensPerSecond,
                            firstTokenLatencyMs = message.firstTokenLatencyMs,
                            onCopy = onCopy,
                            onSpeak = onSpeak,
                            onRegenerate = onRegenerate
                        )
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
private fun AssistantMessageActions(
    isSpeaking: Boolean,
    canRegenerate: Boolean,
    tokensPerSecond: Float?,
    firstTokenLatencyMs: Long?,
    onCopy: () -> Unit,
    onSpeak: () -> Unit,
    onRegenerate: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SmallIconButton(
            icon = Icons.Default.ContentCopy,
            contentDescription = "Copy",
            onClick = onCopy
        )
        Spacer(Modifier.width(4.dp))
        SmallIconButton(
            icon = if (isSpeaking) Icons.Default.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = if (isSpeaking) "Stop speaking" else "Read aloud",
            tint = if (isSpeaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = onSpeak
        )
        if (canRegenerate) {
            Spacer(Modifier.width(4.dp))
            SmallIconButton(
                icon = Icons.Default.Refresh,
                contentDescription = "Regenerate",
                onClick = onRegenerate
            )
        }

        Spacer(Modifier.weight(1f))

        if (tokensPerSecond != null && firstTokenLatencyMs != null) {
            Text(
                text = "${"%.1f".format(tokensPerSecond)} tok/s · ${firstTokenLatencyMs}ms",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun SmallIconButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(18.dp), tint = tint)
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
    isListening: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttachImage: () -> Unit,
    onClearAttachedImage: () -> Unit,
    onStop: () -> Unit,
    onVoice: () -> Unit
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
                    .padding(horizontal = 12.dp, vertical = 12.dp)
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

                IconButton(
                    onClick = onVoice,
                    enabled = !isProcessing
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "Voice input",
                        tint = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.width(4.dp))

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

                Spacer(Modifier.width(10.dp))

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
