package com.elv8.crisisos.ui.screens.requests

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.elv8.crisisos.domain.model.chat.MessageRequest
import com.elv8.crisisos.domain.model.chat.MessageRequestStatus
import com.elv8.crisisos.ui.components.CrisisCard
import com.elv8.crisisos.ui.components.CrisisTopBar
import com.elv8.crisisos.ui.components.EmptyState
import com.elv8.crisisos.ui.screens.connection.ConnectionRequestCard
import com.elv8.crisisos.domain.model.connection.ConnectionRequest
import com.elv8.crisisos.data.local.entity.ConnectionRequestStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageRequestsScreen(
    onNavigateToThread: (threadId: String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: MessageRequestsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showBanner by remember { mutableStateOf(true) }

    LaunchedEffect(uiState.successEvent) {
        when (val event = uiState.successEvent) {
            is RequestSuccessEvent.MessageAccepted -> {
                onNavigateToThread(event.threadId)
                viewModel.clearSuccessEvent()
            }
            is RequestSuccessEvent.ConnectionAccepted -> {
                onNavigateToThread(event.threadId)
                viewModel.clearSuccessEvent()
            }
            RequestSuccessEvent.MessageRejected, RequestSuccessEvent.ConnectionRejected -> {
                viewModel.clearSuccessEvent()
            }
            null -> Unit
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Requests", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(
                selectedTabIndex = uiState.activeTab.ordinal,
                containerColor = Color.Transparent,
                divider = { TabRowDefaults.SecondaryIndicator(color = MaterialTheme.colorScheme.primary) },
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[uiState.activeTab.ordinal]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                Tab(
                    selected = uiState.activeTab == RequestsTab.MESSAGES,
                    onClick = { viewModel.setTab(RequestsTab.MESSAGES) },
                    text = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Messages")
                            if (uiState.messageRequests.isNotEmpty()) {
                                Badge(containerColor = MaterialTheme.colorScheme.error) {
                                    Text(uiState.messageRequests.size.toString())
                                }
                            }
                        }
                    }
                )
                Tab(
                    selected = uiState.activeTab == RequestsTab.CONNECTIONS,
                    onClick = { viewModel.setTab(RequestsTab.CONNECTIONS) },
                    text = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Connections")
                            if (uiState.connectionRequests.isNotEmpty()) {
                                Badge(containerColor = MaterialTheme.colorScheme.error) {
                                    Text(uiState.connectionRequests.size.toString())
                                }
                            }
                        }
                    }
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (uiState.activeTab) {
                    RequestsTab.MESSAGES -> {
                        if (uiState.messageRequests.isEmpty()) {
                            EmptyState(
                                title = "No message requests",
                                subtitle = "Only people you've connected with can message you directly.",
                                icon = Icons.Default.Warning
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                if (showBanner) {
                                    item {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            shape = MaterialTheme.shapes.medium,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxHeight()
                                                        .width(4.dp)
                                                        .background(Color(0xFFFF9800))
                                                )
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "Messages from people you haven't connected with yet. Accept to open a conversation.",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    IconButton(onClick = { showBanner = false }) {
                                                        Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(20.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                items(uiState.messageRequests, key = { it.requestId }) { request ->
                                    MessageRequestCard(
                                        request = request,
                                        isProcessing = uiState.processingId == request.requestId,
                                        onAccept = { viewModel.acceptMessageRequest(request.requestId) },
                                        onDecline = { viewModel.rejectMessageRequest(request.requestId) }
                                    )
                                }
                            }
                        }
                    }
                    RequestsTab.CONNECTIONS -> {
                        if (uiState.connectionRequests.isEmpty()) {
                            EmptyState(
                                title = "No pending connection requests",
                                subtitle = "Share your CRS ID to connect.",
                                icon = Icons.Default.PersonAdd
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(uiState.connectionRequests, key = { it.requestId }) { request ->
                                    ConnectionRequestCard(
                                        request = request,
                                        onAccept = { viewModel.acceptConnectionRequest(request.requestId) },
                                        onReject = { viewModel.rejectConnectionRequest(request.requestId) },
                                        isProcessing = uiState.processingId == request.requestId
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageRequestCard(
    request: MessageRequest,
    isProcessing: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val indicatorColor = when (request.status) {
        MessageRequestStatus.PENDING -> Color(0xFFFF9800)
        MessageRequestStatus.ACCEPTED -> Color(0xFF4CAF50)
        else -> Color.Gray
    }

    CrisisCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(indicatorColor)
            )
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Avatar
                      com.elv8.crisisos.ui.components.CrsAvatar(
                          crsId = request.fromCrsId,
                          alias = request.fromAlias,
                          avatarColor = request.fromAvatarColor,
                          size = 48.dp
                      )

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = request.fromAlias, style = MaterialTheme.typography.titleMedium)
                            
                            val timeAgo = (System.currentTimeMillis() - request.sentAt) / 60000
                            val timeText = if (timeAgo < 60) "${timeAgo}m" else "${timeAgo / 60}h"
                            Text(
                                text = timeText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = request.fromCrsId,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                val preview = if (request.previewText.length > 80) {
                    request.previewText.take(80) + "..."
                } else {
                    request.previewText
                }

                Text(
                    text = "\"$preview\"",
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (request.status == MessageRequestStatus.PENDING) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isProcessing) {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        } else {
                            OutlinedButton(
                                onClick = onDecline,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Decline", color = MaterialTheme.colorScheme.onSurface)
                            }
                            Button(
                                onClick = onAccept,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                            ) {
                                Text("Accept")
                            }
                        }
                    }
                } else {
                    Text(
                        text = request.status.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = indicatorColor,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
    }
}
