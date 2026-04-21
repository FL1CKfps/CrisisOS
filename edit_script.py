import os
import re

file_path = "app/src/main/java/com/elv8/crisisos/ui/screens/chatv2/ChatThreadScreen.kt"

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# 1. Update ChatThreadScreen function signature
content = re.sub(
    r'(\s*@Composable\s*\n\s*fun ChatThreadScreen\(\s*threadId:\s*String,\s*onNavigateBack:\s*\(\)\s*->\s*Unit,)(\s*viewModel:\s*ChatThreadViewModel\s*=\s*hiltViewModel\(\)\s*\))',
    r'\1\n    onNavigateToFullscreenMedia: (String) -> Unit = {},\2',
    content
)

# 2. Add imports
imports = """
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import com.elv8.crisisos.domain.model.MessageType
"""
content = re.sub(
    r'(import java\.util\.\*)',
    r'\1' + '\n' + imports,
    content
)

# 3. Add Launchers to ChatThreadScreen
launchers = """
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) viewModel.onImagePicked(uri) }

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) viewModel.onVideoPicked(uri) }
"""
content = re.sub(
    r'(val messages = uiState\.messages)',
    r'\1\n' + launchers,
    content
)

# 4. Update MessageBubble parameters
content = re.sub(
    r'(fun MessageBubble\([\s\S]*?repliedMessage:\s*Message\?\s*=\s*null,\s*onReply:\s*\(\)\s*->\s*Unit\s*=\s*onLongPress)(\s*\))',
    r'\1,\n    onNavigateToFullscreenMedia: (String) -> Unit = {},\n    onToggleAudioPlayback: (String) -> Unit = {}\2',
    content
)

# Also update the caller in ChatThreadScreen
content = re.sub(
    r'(isGroup\s*=\s*uiState\.thread\?\.type\s*==\s*ThreadType\.GROUP,\s*repliedMessage\s*=\s*repliedMsg)',
    r'\1,\n                    onNavigateToFullscreenMedia = onNavigateToFullscreenMedia,\n                    onToggleAudioPlayback = { id -> viewModel.toggleAudioPlayback(id) }',
    content
)

# 5. AttachmentPreviewSheet
sheet = """
    AttachmentPreviewSheet(
        attachment = uiState.pendingAttachment,
        isVisible = uiState.showAttachmentPreview,
        isSending = uiState.isSendingMedia,
        onConfirmSend = { viewModel.sendPendingMediaMessage() },
        onDiscard = { viewModel.discardPendingAttachment() }
    )
"""
content = re.sub(
    r'(if\s*\(uiState\.isTyping\)\s*\{\s*item\s*\{\s*TypingIndicatorRow\(\)\s*\}\s*\})([\s\S]*?)(\s*\})',
    r'\1\2\n        }\n\n        ' + sheet + '\n    }',
    content
)

# 6. Snackbar
snackbar = """
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.mediaErrorMessage) {
        uiState.mediaErrorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
            viewModel.clearMediaError()
        }
    }
"""
content = re.sub(
    r'(val messages = uiState\.messages)',
    r'\1\n' + snackbar,
    content
)

# 7. Box replacing
box_replace = """
            when (message.messageType) {
                com.elv8.crisisos.domain.model.MessageType.IMAGE,
                com.elv8.crisisos.domain.model.MessageType.IMAGE_PLACEHOLDER -> {
                    ImageMessageBubble(
                        localUri = message.mediaThumbnailUri ?: message.mediaId,  
                        isOwn = message.isOwn,
                        status = message.status,
                        timestamp = message.timestamp,
                        onTapImage = { uri -> onNavigateToFullscreenMedia(message.mediaId ?: "") }
                    )
                }
                com.elv8.crisisos.domain.model.MessageType.VIDEO -> {
                    VideoMessageBubble(
                        thumbnailUri = message.mediaThumbnailUri,
                        isOwn = message.isOwn,
                        status = message.status,
                        timestamp = message.timestamp,
                        onTapVideo = { id -> onNavigateToFullscreenMedia(id) },
                        mediaId = message.mediaId ?: ""
                    )
                }
                com.elv8.crisisos.domain.model.MessageType.AUDIO -> {
                    AudioMessageBubble(
                        mediaId = message.mediaId ?: "",
                        durationMs = message.mediaDurationMs,
                        isOwn = message.isOwn,
                        status = message.status,
                        timestamp = message.timestamp,
                        onTapPlay = { id -> onToggleAudioPlayback(id) }
                    )
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .clip(bubbleShape)
                            .background(bubbleColor)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
"""

box_replace_end = """
                        }
                    }
                }
            }
"""

content = re.sub(
    r'(\s*Box\(\s*modifier = Modifier\s*\.clip\(bubbleShape\)\s*\.background\(bubbleColor\)\s*\.padding\(horizontal = 12\.dp, vertical = 8\.dp\)\s*\)\s*\{)',
    box_replace,
    content
)

content = re.sub(
    r'(Text\(\s*text = message\.content,[\s\S]*?color = textColor\s*\)\s*\}\s*\})(\s*Text\(\s*text = formatter\.format\(Date\(message\.timestamp\)\),)',
    r'\1' + box_replace_end + r'\2',
    content
)

# 8. the Attachment button and Audio recording button

input_row_repl = """
                AnimatedVisibility(
                    visible = uiState.isRecording,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val infiniteTransition = rememberInfiniteTransition(label = "")
                        val alpha by infiniteTransition.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(500),
                                repeatMode = RepeatMode.Reverse
                            ), label = ""
                        )
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error.copy(alpha = alpha))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val totalSeconds = (uiState.recordingDurationMs ?: 0L) / 1000
                        val min = totalSeconds / 60
                        val sec = totalSeconds % 60
                        Text(
                            text = "Recording %d:%02d".format(min, sec),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "Release to send",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { imagePicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Attach media",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
"""
content = re.sub(
    r'(Row\(\s*modifier = Modifier\s*\.fillMaxWidth\(\)\s*\.padding\(horizontal = 8\.dp, vertical = 8\.dp\)\s*\.navigationBarsPadding\(\),\s*verticalAlignment = Alignment\.CenterVertically\s*\)\s*\{)',
    input_row_repl,
    content
)

button_repl = """
                    val isInputEmpty = uiState.inputText.isBlank() && uiState.pendingAttachment == null

                    if (!isInputEmpty) {
                        IconButton(
                            onClick = viewModel::sendMessage,
                            enabled = !uiState.isSending,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,       
                                contentDescription = "Send",
                                tint = Color.White
                            )
                        }
                    } else {
                        IconButton(
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    color = if (uiState.isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    shape = CircleShape
                                )
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            viewModel.startVoiceRecording()
                                            tryAwaitRelease()
                                            viewModel.stopVoiceRecording()
                                        }
                                    )
                                },
                            onClick = {}
                        ) {
                            Icon(
                                imageVector = if (uiState.isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = if (uiState.isRecording) "Stop recording" else "Hold to record",
                                tint = Color.White
                            )
                        }
                    }
"""

content = re.sub(
    r'(val isInputBlank = uiState\.inputText\.isBlank\(\)[\s\S]*?Icon\([\s\S]*?tint = Color\.White\s*\)\s*\}\s*)',
    button_repl,
    content
)


with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

print("Done")
