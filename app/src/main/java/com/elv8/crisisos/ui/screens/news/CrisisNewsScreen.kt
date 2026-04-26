package com.elv8.crisisos.ui.screens.news

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
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
import com.elv8.crisisos.data.local.entity.NewsItemEntity
import com.elv8.crisisos.ui.components.CrisisCard
import com.elv8.crisisos.ui.components.EmptyState
import com.elv8.crisisos.ui.components.LocalTopBarState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrisisNewsScreen(
    onNavigateBack: () -> Unit,
    viewModel: CrisisNewsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val topBarState = LocalTopBarState.current

    LaunchedEffect(state.isRefreshing, state.canPublish) {
        topBarState.update(
            title = { Text("CRISIS NEWS", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(
                    onClick = viewModel::refresh,
                    enabled = !state.isRefreshing
                ) {
                    if (state.isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            }
        )
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.lastRefreshMessage) {
        val msg = state.lastRefreshMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearRefreshMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (state.canPublish) {
                ExtendedFloatingActionButton(
                    onClick = viewModel::openComposer,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("POST UPDATE") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (state.items.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Article,
                    title = if (state.isRefreshing) "Loading crisis news…" else "No crisis news yet",
                    subtitle = if (state.isRefreshing) {
                        "Fetching ACLED and GDELT updates for your region."
                    } else {
                        "Verified updates from NGOs, ACLED, GDELT, and trusted nodes will appear here. Tap refresh to pull live data."
                    }
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.items, key = { it.id }) { item ->
                        NewsCard(item)
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (state.isComposerOpen) {
        ComposerSheet(
            state = state,
            onClose = viewModel::closeComposer,
            onHeadlineChange = viewModel::updateHeadline,
            onBodyChange = viewModel::updateBody,
            onCategorySelected = viewModel::selectCategory,
            onPublish = viewModel::publish
        )
    }
}

@Composable
private fun NewsCard(item: NewsItemEntity) {
    CrisisCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryChip(item.category)
                Spacer(modifier = Modifier.width(8.dp))
                if (item.isOfficial) {
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text("OFFICIAL", fontWeight = FontWeight.Bold) },
                        leadingIcon = {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            disabledContainerColor = Color(0xFF1E5128).copy(alpha = 0.85f),
                            disabledLabelColor = Color.White,
                            disabledLeadingIconContentColor = Color.White
                        )
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = formatRelative(item.publishedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(item.headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (item.body.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(item.body, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "— ${item.sourceAlias} • expires ${formatRelative(item.expiresAt, future = true)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CategoryChip(category: String) {
    val color = when (category.uppercase()) {
        "ALERT" -> Color(0xFFD32F2F)
        "INFRASTRUCTURE" -> Color(0xFF1976D2)
        "AID" -> Color(0xFF388E3C)
        "SAFETY" -> Color(0xFFFFA000)
        else -> MaterialTheme.colorScheme.primary
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposerSheet(
    state: CrisisNewsUiState,
    onClose: () -> Unit,
    onHeadlineChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onCategorySelected: (NewsCategory) -> Unit,
    onPublish: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("PUBLISH CRISIS UPDATE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = state.draftHeadline,
                onValueChange = onHeadlineChange,
                label = { Text("Headline") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = state.draftBody,
                onValueChange = onBodyChange,
                label = { Text("Details (optional)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text("Category", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NewsCategory.values().forEach { c ->
                    FilterChip(
                        selected = state.draftCategory == c,
                        onClick = { onCategorySelected(c) },
                        label = { Text(c.label) }
                    )
                }
            }
            state.error?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onPublish,
                enabled = !state.isPublishing && state.draftHeadline.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (state.isPublishing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("BROADCASTING...", fontWeight = FontWeight.Bold)
                } else {
                    Text("PUBLISH TO MESH", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
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
