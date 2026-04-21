package com.elv8.crisisos.ui.screens.chatv2
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.animation.Crossfade
import kotlinx.coroutines.launch

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation

import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.elv8.crisisos.domain.model.MessageStatus
import com.elv8.crisisos.domain.model.chat.Message
import com.elv8.crisisos.domain.model.chat.ThreadType
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.layout.imePadding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import com.elv8.crisisos.domain.model.MessageType


@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChatThreadScreen(
    threadId: String,
    onNavigateBack: () -> Unit,
    onNavigateToFullscreenMedia: (String) -> Unit = {},
    onTapAlias: (String) -> Unit = {},
    viewModel: ChatThreadViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val messages = uiState.messages

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.mediaErrorMessage) {
        uiState.mediaErrorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
            viewModel.clearMediaError()
        }
    }


    val context = LocalContext.current
    val recordAudioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) Toast.makeText(context, "Permission granted. Hold mic to record.", Toast.LENGTH_SHORT).show()
    }
    val mediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val mimeType = context.contentResolver.getType(uri) ?: ""
            if (mimeType.startsWith("video/")) {
                viewModel.onVideoPicked(uri)
            } else {
                viewModel.onImagePicked(uri)
            }
        }
    }


    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        uiState.thread?.let { thread ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(thread.avatarColor)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = thread.displayName.take(1).uppercase(),
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = thread.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable { 
                                        thread.peerCrsId?.let { onTapAlias(it) } 
                                    }
                                )
                                
                                val transition = rememberInfiniteTransition()
                                val alpha by transition.animateFloat(
                                    initialValue = 0.5f,
                                    targetValue = 1f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(800),
                                        repeatMode = RepeatMode.Reverse
                                    ), label = ""
                                )
                                
                                if (uiState.isTyping) {
                                    Text(
                                        text = "typing...",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontStyle = FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
                                    )
                                } else {
                                    Text(
                                        text = if (thread.type == ThreadType.GROUP) "Group Chat" else "Direct Chat",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {}
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .background(MaterialTheme.colorScheme.background)
        ) {
            LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f).fillMaxWidth()
                ,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = messages,
                key = { it.messageId }
            ) { message ->
                val repliedMsg = message.replyToMessageId?.let { replyId -> 
                    messages.find { it.messageId == replyId } 
                }
                MessageBubble(
                    message = message,
                    localCrsId = uiState.localCrsId,
                    onLongPress = { viewModel.setReplyingTo(message) },
                    isGroup = uiState.thread?.type == ThreadType.GROUP,
                    repliedMessage = repliedMsg,
                    onNavigateToFullscreenMedia = onNavigateToFullscreenMedia,
                    onToggleAudioPlayback = { id -> viewModel.toggleAudioPlayback(id) },
                    playingAudioId = uiState.playingAudioId
                )
            }
            
            if (uiState.isTyping) {
                item {
                    TypingIndicatorRow()
                }
            }

            }
            
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                AnimatedVisibility(
                    visible = uiState.replyingTo != null,
                    enter = slideInVertically(initialOffsetY = { it }) + expandVertically(),
                    exit = slideOutVertically(targetOffsetY = { it }) + shrinkVertically()
                ) {
                    uiState.replyingTo?.let { replyMsg ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(32.dp)
                                    .background(Color(0xFFFF9800), RoundedCornerShape(2.dp))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (replyMsg.fromCrsId == uiState.localCrsId) "You" else replyMsg.fromAlias,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFFFF9800),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = replyMsg.content,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { viewModel.clearReplyingTo() }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel Reply",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                
                
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
                        ,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { mediaPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Attach media",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    TextField(
                        value = uiState.inputText,
                        onValueChange = viewModel::updateInput,
                        placeholder = { Text("Message...") },
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        )
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    
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
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (uiState.isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                                .pointerInput(Unit) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown()
                                        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                        if (hasPerm) {
                                            viewModel.startVoiceRecording()
                                            
                                            // Actively loop and consume events to prevent any parent (like swipe-to-back or scroll) from cancelling our hold gesture
                                            do {
                                                val event = awaitPointerEvent()
                                                event.changes.forEach { it.consume() }
                                            } while (event.changes.any { it.pressed })
                                            
                                            viewModel.stopVoiceRecording()
                                        } else {
                                            down.consume()
                                            recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (uiState.isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = if (uiState.isRecording) "Stop recording" else "Hold to record",
                                tint = Color.White
                            )
                        }
                    }
}
            }
        }
        
        AttachmentPreviewSheet(
        attachment = uiState.pendingAttachment,
        isVisible = uiState.showAttachmentPreview,
        isSending = uiState.isSendingMedia,
        onConfirmSend = { viewModel.sendPendingMediaMessage() },
        onDiscard = { viewModel.discardPendingAttachment() }
    )
}

}

@Composable
fun MessageBubble(
    message: Message,
    localCrsId: String,
    onLongPress: () -> Unit,
    isGroup: Boolean,
    repliedMessage: Message? = null,
    onReply: () -> Unit = onLongPress,
    onNavigateToFullscreenMedia: (String) -> Unit = {},
    onToggleAudioPlayback: (String) -> Unit = {},
    playingAudioId: String? = null
) {
    val visibleState = remember { androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true } }
    val offsetX = remember { androidx.compose.animation.core.Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    
    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(tween(200)) + 
                slideInVertically(tween(200)) { it / 4 }
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.toInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        if (message.fromCrsId != localCrsId && delta > 0) { // swipe others right
                            coroutineScope.launch {
                                offsetX.snapTo((offsetX.value + delta).coerceIn(0f, 150f))
                            }
                        } else if (message.fromCrsId == localCrsId && delta < 0) { // swipe own left
                            coroutineScope.launch {
                                offsetX.snapTo((offsetX.value + delta).coerceIn(-150f, 0f))
                            }
                        }
                    },
                    onDragStopped = {
                        coroutineScope.launch {
                            val isOwn = message.fromCrsId == localCrsId
                            if (!isOwn && offsetX.value > 100f) {
                                // Trigger vibration logic
                                onReply()
                            } else if (isOwn && offsetX.value < -100f) {
                                onReply()
                            }
                            offsetX.animateTo(0f, spring())
                        }
                    }
                )
        ) {
    val isOwn = message.fromCrsId == localCrsId
    val formatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start
    ) {
        if (!isOwn && isGroup) {
            com.elv8.crisisos.ui.components.CrsAvatar(
                crsId = message.fromCrsId,
                alias = message.fromAlias,
                avatarColor = android.graphics.Color.GRAY,
                size = 28.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        val isMedia = message.messageType in listOf(
            com.elv8.crisisos.domain.model.MessageType.IMAGE,
            com.elv8.crisisos.domain.model.MessageType.IMAGE_PLACEHOLDER,
            com.elv8.crisisos.domain.model.MessageType.VIDEO,
            com.elv8.crisisos.domain.model.MessageType.AUDIO
        )

        if (isOwn && !isMedia) {
            Box(modifier = Modifier.align(Alignment.Bottom)) {
                Crossfade(targetState = message.status, animationSpec = tween(300), label = "DeliveryStatusCrossfade") { s -> DeliveryStatusIcon(status = s) }
            }
            Spacer(modifier = Modifier.width(4.dp))
        }

        Column(
            horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start,
            modifier = Modifier.pointerInput(Unit) {
                detectTapGestures(onLongPress = { onLongPress() })
            }
        ) {
            if (!isOwn && isGroup) {
                Text(
                    text = message.fromAlias,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFF9800),
                    modifier = Modifier.padding(bottom = 2.dp, start = 4.dp)
                )
            }
            
            val bubbleShape = if (isOwn) {
                RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
            } else {
                RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
            }
            
            val bubbleColor = if (isOwn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            val textColor = if (isOwn) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
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
                        isPlaying = playingAudioId == message.mediaId,
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



                Column {
                    if (repliedMessage != null) {
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (repliedMessage.fromCrsId == localCrsId) "You" else repliedMessage.fromAlias,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFF9800),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = repliedMessage.content,
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor
                    )
                }
            }
                        }
                    }


            
            if (!isMedia) {
                Text(
                    text = formatter.format(Date(message.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 2.dp, end = 4.dp, start = 4.dp)
                )
            }
        }
        }
        }
    }
}

@Composable
fun DeliveryStatusIcon(status: MessageStatus) {
    val iconSize = 12.dp
    val tintMuted = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val tintRead = Color(0xFFFF9800) // Orange

    Crossfade(targetState = status, label = "") { currentStatus ->
        when (currentStatus) {
            MessageStatus.SENDING -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(iconSize),
                    strokeWidth = 1.5.dp,
                    color = tintMuted
                )
            }
            MessageStatus.SENT -> {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Sent",
                    modifier = Modifier.size(iconSize),
                    tint = tintMuted
                )
            }
            MessageStatus.DELIVERED -> {
                Row {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(iconSize),
                        tint = tintMuted
                    )
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Delivered",
                        modifier = Modifier.size(iconSize).offset(x = (-4).dp),
                        tint = tintMuted
                    )
                }
            }
            MessageStatus.READ -> {
                Row {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(iconSize),
                        tint = tintRead
                    )
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Read",
                        modifier = Modifier.size(iconSize).offset(x = (-4).dp),
                        tint = tintRead
                    )
                }
            }
            MessageStatus.FAILED -> {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Failed",
                    modifier = Modifier.size(iconSize),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun TypingIndicatorRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color.Gray),
            contentAlignment = Alignment.Center
        ) {
            Text("...", color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(modifier = Modifier.width(8.dp))
        
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row {
                val infiniteTransition = rememberInfiniteTransition(label = "")
                for (i in 0..2) {
                    val scale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.3f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(300, delayMillis = i * 200),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = ""
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .size(6.dp)
                            .scale(scale)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    )
}
}
}
}
}
