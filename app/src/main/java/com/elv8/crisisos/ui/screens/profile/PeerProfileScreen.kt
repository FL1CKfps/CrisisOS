package com.elv8.crisisos.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elv8.crisisos.domain.model.contact.TrustLevel
import com.elv8.crisisos.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeerProfileScreen(
    crsId: String,
    threadId: String?,
    isFromChat: Boolean,
    onNavigateBack: () -> Unit,
    onNavigateToFullscreenMedia: (mediaId: String) -> Unit,
    viewModel: PeerProfileViewModel = hiltViewModel()
) {
    LaunchedEffect(crsId) { viewModel.loadProfile(crsId, threadId, isFromChat) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val topBarState = LocalTopBarState.current

    LaunchedEffect(uiState.profile) {
        topBarState.update(
            title = { Text(uiState.profile?.alias ?: "PROFILE", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp)
                ) {
                    CrsAvatar(
                        crsId = uiState.profile?.crsId ?: "",
                        alias = uiState.profile?.alias ?: "",
                        avatarColor = uiState.profile?.avatarColor ?: 0,
                        size = 80.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = uiState.profile?.alias ?: "",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = uiState.profile?.crsId ?: "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .clickable { /* copy CRS-ID to clipboard */ }
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                    if (uiState.profile?.isSelf == true) {
                        Spacer(modifier = Modifier.height(8.dp))
                        StatusBadge(text = "You", status = BadgeStatus.OK)
                    } else if (uiState.profile?.trustLevel != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        StatusBadge(
                            text = uiState.profile!!.trustLevel!!.name.lowercase()
                                .replaceFirstChar { it.uppercase() },
                            status = when (uiState.profile!!.trustLevel!!) {
                                TrustLevel.FAMILY -> BadgeStatus.OK
                                TrustLevel.TRUSTED -> BadgeStatus.ACTIVE
                                TrustLevel.BASIC -> BadgeStatus.WARNING
                            }
                        )
                    }
                }
            }
            
            item {
                CrisisCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "CRS-ID",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = androidx.compose.ui.unit.TextUnit(0.12f, androidx.compose.ui.unit.TextUnitType.Em)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        SelectionContainer {
                            Text(
                                text = uiState.profile?.crsId ?: "—",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            if (uiState.isFromChatContext && uiState.sharedMedia.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Shared Media",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${uiState.sharedMediaCount} items",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    SharedMediaGrid(
                        mediaItems = uiState.sharedMedia,
                        onTapMedia = { viewModel.selectMedia(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .padding(horizontal = 1.dp)
                    )
                }
            } else if (uiState.isFromChatContext) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Shared Media",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "0 items",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No shared photos or videos yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }

        LaunchedEffect(uiState.selectedMediaItem) {
            uiState.selectedMediaItem?.let { item ->
                onNavigateToFullscreenMedia(item.mediaId)
                viewModel.clearSelectedMedia()
            }
        }
    }
}
