package com.elv8.crisisos.ui.screens.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.elv8.crisisos.domain.model.peer.PeerStatus
import com.elv8.crisisos.domain.repository.SendRequestResult
import com.elv8.crisisos.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendConnectionRequestScreen(
    peerCrsId: String,
    onNavigateBack: () -> Unit,
    onRequestSent: () -> Unit,
    viewModel: SendConnectionRequestViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(peerCrsId) {
        viewModel.loadPeer(peerCrsId)
    }

    LaunchedEffect(uiState.result) {
        val result = uiState.result
        if (result != null) {
            when (result) {
                is SendRequestResult.Success -> {
                    snackbarHostState.showSnackbar("Request sent!")
                    onRequestSent()
                }
                is SendRequestResult.AlreadyRequested -> {
                    snackbarHostState.showSnackbar("You already sent a request to this person")
                }
                is SendRequestResult.AlreadyConnected -> {
                    snackbarHostState.showSnackbar("You are already connected")
                }
                is SendRequestResult.Error -> {
                    snackbarHostState.showSnackbar(result.message)
                }
            }
            viewModel.clearResult()
        }
    }

    Scaffold(
        topBar = {
            CrisisTopBar(
                
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            val peer = uiState.peer
            if (peer != null) {
                CrisisCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(peer.avatarColor)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = peer.alias.take(1).uppercase(),
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = peer.alias,
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = peer.crsId,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val statusColor = when (peer.status) {
                                    PeerStatus.AVAILABLE -> Color(0xFF4CAF50)
                                    PeerStatus.BUSY -> Color(0xFFF44336)
                                    PeerStatus.OFFLINE -> Color.Gray
                                    PeerStatus.UNKNOWN -> Color.Gray
                                }
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(statusColor)
                                )
                                Text(
                                    text = peer.status.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = statusColor
                                )
                                Text(
                                    text = "• m",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("Add a note (optional)")
                InputField(
                    value = uiState.messageText,
                    onValueChange = viewModel::updateMessage,
                    label = "Introduce yourself or explain why you want to connect...",
                    modifier = Modifier.fillMaxWidth().height(120.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "/120",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), MaterialTheme.shapes.small)
            ) {
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    Box(modifier = Modifier.fillMaxHeight().width(4.dp).background(Color(0xFFFF9800)))
                    Text(
                        text = "They will receive your CRS-ID and alias. They must accept before you can chat.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            PrimaryButton(
                text = if (uiState.isSending) "Sending..." else "Send Connection Request",
                isLoading = uiState.isSending,
                onClick = viewModel::sendRequest,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

