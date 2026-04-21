package com.elv8.crisisos.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elv8.crisisos.domain.model.ChatMessage
import com.elv8.crisisos.domain.model.MessageStatus
import com.elv8.crisisos.domain.model.MessageType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.elv8.crisisos.ui.components.EmptyState
import androidx.compose.material.icons.filled.Chat

@Composable
fun ChatScreen(
    onNavigateBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        topBar = { ChatTopBar(onNavigateBack = onNavigateBack, uiState = uiState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.messages.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Chat,
                    title = "No messages yet",
                    subtitle = "The mesh is listening",
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    items(uiState.messages, key = { it.id }) { message ->
                        MessageItem(message = message)
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }

            ChatInputBar(
                inputText = uiState.inputText,
                onTextChange = viewModel::updateInputText,
                onSend = viewModel::sendMessage
            )
        }
    }
}

@Composable
fun ChatTopBar(onNavigateBack: () -> Unit, uiState: ChatUiState) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "MESH CHAT",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (uiState.isConnected) Color(0xFF4CAF50) else Color.Red)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${uiState.peersOnline} peers online",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun MessageItem(message: ChatMessage) {
    when (message.messageType) {
        MessageType.SYSTEM -> SystemMessageItem(message)
        MessageType.SOS_ALERT -> SosAlertMessageItem(message)
        MessageType.TEXT -> TextMessageItem(message)
        MessageType.IMAGE_PLACEHOLDER -> SystemMessageItem(message)
        MessageType.IMAGE -> TextMessageItem(message) // Placeholder until UI implemented
        MessageType.VIDEO -> TextMessageItem(message) // Placeholder until UI implemented
        MessageType.AUDIO -> TextMessageItem(message) // Placeholder until UI implemented
    }
}

@Composable
fun SystemMessageItem(message: ChatMessage) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message.content,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun SosAlertMessageItem(message: ChatMessage) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = "Warning",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = message.senderAlias.uppercase(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "via ${message.hopsCount} hops",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message.content,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatTime(message.timestamp),
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.5f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
fun TextMessageItem(message: ChatMessage) {
    val isOwn = message.isOwn
    val bubbleShape = if (isOwn) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
    }

    val bubbleColor = if (isOwn) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = if (isOwn) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start
    ) {
        if (!isOwn) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = message.senderAlias,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "via ${message.hopsCount} hops",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
        
        Box(
            modifier = Modifier
                .background(bubbleColor, bubbleShape)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .widthIn(max = 280.dp)
        ) {
            Text(
                text = message.content,
                color = textColor,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isOwn) {
                DeliveryStatusIcon(status = message.deliveryStatus)
                Spacer(modifier = Modifier.width(4.dp))
            }

            Text(
                text = formatTime(message.timestamp),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun DeliveryStatusIcon(status: MessageStatus) {
    val iconSize = 14.dp
    val tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val tintDelivered = MaterialTheme.colorScheme.primary

    Row(verticalAlignment = Alignment.CenterVertically) {
        when (status) {
            MessageStatus.SENDING -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(iconSize),
                    strokeWidth = 1.dp,
                    color = tint
                )
            }
            MessageStatus.FAILED -> {
                Icon(Icons.Default.Close, contentDescription = "Failed", modifier = Modifier.size(iconSize), tint = MaterialTheme.colorScheme.error)
            }
            MessageStatus.SENT, MessageStatus.DELIVERED, MessageStatus.READ -> {
                val isRead = status == MessageStatus.READ
                val isDelivered = status == MessageStatus.DELIVERED
                val finalTint = if (isRead) MaterialTheme.colorScheme.tertiary else if (isDelivered) tintDelivered else tint

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Check, 
                        contentDescription = "Sent", 
                        modifier = Modifier.size(iconSize).offset(x = 4.dp), 
                        tint = finalTint
                    )
                    
                    AnimatedVisibility(
                        visible = isDelivered || isRead,
                        enter = fadeIn() + slideInHorizontally(initialOffsetX = { -it/2 })
                    ) {
                        Icon(
                            Icons.Default.Check, 
                            contentDescription = "Delivered", 
                            modifier = Modifier.size(iconSize).offset(x = (-2).dp), 
                            tint = finalTint
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatInputBar(
    inputText: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit
) {
    var isSending by remember { mutableStateOf(false) }
    
    // Animate rotation when sending
    val rotationAngle by animateFloatAsState(
        targetValue = if (isSending) 360f else 0f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        finishedListener = { isSending = false },
        label = "sendIconRotation"
    )

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message mesh network...") },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            FloatingActionButton(
                onClick = { 
                    if (inputText.isNotBlank()) {
                        isSending = true
                        onSend() 
                    }
                },
                modifier = Modifier.size(48.dp),
                containerColor = Color(0xFFFF9800), // Orange as requested
                contentColor = Color.White,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(0.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send, 
                    contentDescription = "Send",
                    modifier = Modifier.rotate(rotationAngle)
                )
            }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

