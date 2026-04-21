package com.elv8.crisisos.ui.screens.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.elv8.crisisos.data.local.entity.ConnectionRequestStatus
import com.elv8.crisisos.domain.model.connection.ConnectionRequest
import com.elv8.crisisos.ui.components.*
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.TabRowDefaults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomingRequestsScreen(
    onNavigateBack: () -> Unit,
    viewModel: IncomingRequestsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.successMessage, uiState.errorMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            CrisisTopBar(
                
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(
                selectedTabIndex = uiState.activeTab.ordinal,
                containerColor = Color.Transparent,
                divider = {
                    TabRowDefaults.SecondaryIndicator(
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                Tab(
                    selected = uiState.activeTab == RequestTab.INCOMING,
                    onClick = { viewModel.setActiveTab(RequestTab.INCOMING) },
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("INCOMING")
                            if (uiState.incomingRequests.isNotEmpty()) {
                                Badge(containerColor = MaterialTheme.colorScheme.error) {
                                    Text(uiState.incomingRequests.size.toString())
                                }
                            }
                        }
                    }
                )
                Tab(
                    selected = uiState.activeTab == RequestTab.OUTGOING,
                    onClick = { viewModel.setActiveTab(RequestTab.OUTGOING) },
                    text = { Text("OUTGOING") }
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (uiState.activeTab) {
                    RequestTab.INCOMING -> {
                        if (uiState.incomingRequests.isEmpty()) {
                            EmptyState(title = "No pending requests", subtitle = "Start by discovering nearby devices", icon = Icons.Default.Warning)
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(uiState.incomingRequests) { request ->
                                    ConnectionRequestCard(
                                        request = request,
                                        onAccept = { viewModel.acceptRequest(request.requestId) },
                                        onReject = { viewModel.rejectRequest(request.requestId) },
                                        isProcessing = uiState.processingRequestId == request.requestId
                                    )
                                }
                            }
                        }
                    }
                    RequestTab.OUTGOING -> {
                        if (uiState.outgoingRequests.isEmpty()) {
                            EmptyState(title = "No outgoing requests", subtitle = "Connect with peers safely over the mesh.", icon = Icons.Default.Send)
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(uiState.outgoingRequests) { request ->
                                    OutgoingRequestCard(
                                        request = request,
                                        onCancel = { viewModel.cancelOutgoingRequest(request.requestId) }
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
fun ConnectionRequestCard(
    request: ConnectionRequest,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    isProcessing: Boolean
) {
    CrisisCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                  com.elv8.crisisos.ui.components.CrsAvatar(
                      crsId = request.fromCrsId,
                      alias = request.fromAlias,
                      avatarColor = request.fromAvatarColor,
                      size = 56.dp
                  )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = request.fromAlias, style = MaterialTheme.typography.titleMedium)
                        val timestamp = (System.currentTimeMillis() - request.sentAt) / 3600000
                        Text(
                            text = "${timestamp}h ago",
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

                    if (request.message.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), MaterialTheme.shapes.small)
                        ) {
                            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                                Box(modifier = Modifier.fillMaxHeight().width(3.dp).background(Color(0xFFFF9800)))
                                Text(
                                    text = "\"${request.message}\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontStyle = FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }

                    if (request.status == ConnectionRequestStatus.PENDING) {
                        val hoursLeft = (request.expiresAt - System.currentTimeMillis()) / 3600000
                        if (hoursLeft > 0) {
                            StatusBadge(text = "Expires in ${hoursLeft}h", status = BadgeStatus.WARNING)
                        } else {
                            StatusBadge(text = "Expired", status = BadgeStatus.OFFLINE)
                        }
                    }
                }
            }

            if (request.status == ConnectionRequestStatus.PENDING) {
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
                            onClick = onReject,
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
                val badgeStatus = when (request.status) {
                    ConnectionRequestStatus.ACCEPTED -> BadgeStatus.ACTIVE
                    ConnectionRequestStatus.REJECTED -> BadgeStatus.WARNING
                    ConnectionRequestStatus.CANCELLED -> BadgeStatus.OFFLINE
                    ConnectionRequestStatus.EXPIRED -> BadgeStatus.OFFLINE
                    else -> BadgeStatus.OFFLINE
                }
                StatusBadge(text = request.status.name, status = badgeStatus)
            }
        }
    }
}

@Composable
fun OutgoingRequestCard(
    request: ConnectionRequest,
    onCancel: () -> Unit
) {
    CrisisCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.Gray),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "PEER",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    Text(text = "To: ${request.toCrsId.take(8)}...", style = MaterialTheme.typography.titleMedium)
                    if (request.message.isNotBlank()) {
                        Text(
                            text = request.message,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1
                        )
                    }
                    val timestamp = (System.currentTimeMillis() - request.sentAt) / 3600000
                    Text(
                        text = "Sent ${timestamp}h ago",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val badgeStatus = when (request.status) {
                    ConnectionRequestStatus.PENDING -> BadgeStatus.WARNING
                    ConnectionRequestStatus.ACCEPTED -> BadgeStatus.ACTIVE
                    ConnectionRequestStatus.REJECTED -> BadgeStatus.WARNING
                    ConnectionRequestStatus.CANCELLED -> BadgeStatus.OFFLINE
                    ConnectionRequestStatus.EXPIRED -> BadgeStatus.OFFLINE
                    else -> BadgeStatus.OFFLINE
                }
                StatusBadge(text = request.status.name, status = badgeStatus)

                if (request.status == ConnectionRequestStatus.PENDING) {
                    TextButton(onClick = onCancel) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}














