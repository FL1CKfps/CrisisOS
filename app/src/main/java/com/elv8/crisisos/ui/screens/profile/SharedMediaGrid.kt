package com.elv8.crisisos.ui.screens.profile

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.elv8.crisisos.domain.model.media.MediaItem
import com.elv8.crisisos.domain.model.media.MediaType

@Composable
fun SharedMediaGrid(
    mediaItems: List<MediaItem>,
    onTapMedia: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier,
        contentPadding = PaddingValues(1.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        items(mediaItems, key = { it.mediaId ?: it.hashCode() }) { item ->
            MediaGridTile(item, onTapMedia)
        }
    }
}

@Composable
private fun MediaGridTile(item: MediaItem, onTap: (MediaItem) -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable { onTap(item) }
            .clip(RectangleShape)
    ) {
        val uri = item.thumbnailUri ?: item.localUri
        if (uri != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(Uri.parse(uri))
                    .crossfade(true)
                    .size(240, 240)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                CircularProgressIndicator(Modifier.size(20.dp).align(Alignment.Center))
            }
        }

        if (item.type == MediaType.VIDEO) {
            Box(Modifier.fillMaxSize()) {
                Icon(
                    imageVector = Icons.Filled.PlayCircle,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(28.dp).align(Alignment.Center)
                )
            }
        }
    }
}
