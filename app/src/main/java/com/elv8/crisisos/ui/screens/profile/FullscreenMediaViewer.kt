package com.elv8.crisisos.ui.screens.profile

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.elv8.crisisos.domain.model.media.MediaType
import com.elv8.crisisos.ui.components.CrisisTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullscreenMediaViewer(
    mediaId: String,
    onNavigateBack: () -> Unit,
    viewModel: PeerProfileViewModel = hiltViewModel()
) {
    LaunchedEffect(mediaId) {
        viewModel.loadSingleMedia(mediaId)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mediaItem = uiState.selectedMediaItem

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            when (mediaItem?.type) {
                MediaType.IMAGE -> {
                    var scale by remember { mutableStateOf(1f) }
                    var offsetX by remember { mutableStateOf(0f) }
                    var offsetY by remember { mutableStateOf(0f) }

                    val uriStr = mediaItem.localUri ?: mediaItem.remoteUri
                    if (uriStr != null) {
                        AsyncImage(
                            model = Uri.parse(uriStr),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offsetX,
                                    translationY = offsetY
                                )
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        scale = (scale * zoom).coerceIn(1f, 5f)
                                        if (scale > 1f) {
                                            offsetX += pan.x * scale
                                            offsetY += pan.y * scale
                                        } else {
                                            offsetX = 0f
                                            offsetY = 0f
                                        }
                                    }
                                }
                        )
                    } else {
                        Text("Image unavailable", color = Color.White)
                    }
                }
                MediaType.VIDEO -> {
                    Text("Video playback — ExoPlayer integration in next phase", color = Color.White)
                }
                MediaType.AUDIO -> {
                    com.elv8.crisisos.ui.screens.chatv2.AudioMessageBubble(
                        mediaId = mediaItem.mediaId ?: "",
                        durationMs = mediaItem.durationMs,
                        isOwn = false,
                        status = com.elv8.crisisos.domain.model.MessageStatus.DELIVERED,
                        timestamp = 0L,
                        isPlaying = false,
                        onTapPlay = { }
                    )
                }
                null -> {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }
    }
}
