package com.elv8.crisisos.ui.screens.community

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elv8.crisisos.data.local.entity.CommunityPostEntity
import com.elv8.crisisos.ui.components.CrisisCard
import com.elv8.crisisos.ui.components.EmptyState
import com.elv8.crisisos.ui.components.LocalTopBarState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityBoardScreen(
    onNavigateBack: () -> Unit,
    viewModel: CommunityBoardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val topBarState = LocalTopBarState.current

    LaunchedEffect(Unit) {
        topBarState.update(
            title = { Text("COMMUNITY BOARD", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Composer(state = state, viewModel = viewModel)
        Spacer(modifier = Modifier.height(12.dp))
        Divider()
        Spacer(modifier = Modifier.height(12.dp))
        if (state.posts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Default.Forum,
                    title = "Board is quiet",
                    subtitle = "Posts are anonymous and disappear after 24 hours. Share what you know."
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.posts, key = { it.id }) { post ->
                    PostCard(
                        post = post,
                        canPin = state.canPin,
                        onTogglePin = { viewModel.togglePinned(post) }
                    )
                }
                item { Spacer(modifier = Modifier.height(40.dp)) }
            }
        }
    }
}

@Composable
private fun Composer(state: CommunityBoardUiState, viewModel: CommunityBoardViewModel) {
    Column {
        OutlinedTextField(
            value = state.draft,
            onValueChange = viewModel::updateDraft,
            placeholder = { Text("Share an anonymous update… (24h expiry)") },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 88.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = !state.isPosting
        )
        Spacer(modifier = Modifier.height(8.dp))
        val categoryScroll = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(categoryScroll),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CommunityCategory.values().forEach { c ->
                FilterChip(
                    selected = state.draftCategory == c,
                    onClick = { viewModel.selectCategory(c) },
                    label = { Text(c.label) }
                )
            }
        }
        if (state.canPin) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = state.pinDraft, onCheckedChange = viewModel::togglePin)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Pin (NGO official)", style = MaterialTheme.typography.bodySmall)
            }
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = viewModel::submit,
            enabled = !state.isPosting && state.draft.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (state.isPosting) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("POSTING...", fontWeight = FontWeight.Bold)
            } else {
                Text("POST ANONYMOUSLY", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PostCard(
    post: CommunityPostEntity,
    canPin: Boolean,
    onTogglePin: () -> Unit
) {
    CrisisCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryChip(post.category)
                if (post.pinned) {
                    Spacer(modifier = Modifier.width(6.dp))
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text("PINNED", fontWeight = FontWeight.Bold) },
                        leadingIcon = {
                            Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            disabledContainerColor = Color(0xFF1E5128).copy(alpha = 0.85f),
                            disabledLabelColor = Color.White,
                            disabledLeadingIconContentColor = Color.White
                        )
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(formatRelative(post.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(post.body, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Anonymous • expires ${formatRelative(post.expiresAt, future = true)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (canPin) {
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onTogglePin) {
                        Text(if (post.pinned) "Unpin" else "Pin")
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(category: String) {
    val color = when (category.uppercase()) {
        "HELP"   -> Color(0xFFD32F2F)
        "OFFER"  -> Color(0xFF388E3C)
        "REUNITE" -> Color(0xFF8E24AA)
        "UPDATE" -> Color(0xFF1976D2)
        else      -> MaterialTheme.colorScheme.primary
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = category.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

private fun formatRelative(ts: Long, future: Boolean = false): String {
    val now = System.currentTimeMillis()
    val diff = if (future) ts - now else now - ts
    val mins = max(0L, diff / 60_000L)
    return when {
        mins < 1 -> if (future) "soon" else "Just now"
        mins < 60 -> if (future) "in ${mins}m" else "${mins}m ago"
        mins < 1440 -> if (future) "in ${mins / 60}h" else "${mins / 60}h ago"
        else -> SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(ts))
    }
}
