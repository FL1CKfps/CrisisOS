package com.elv8.crisisos.ui.screens.chat

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.elv8.crisisos.ui.screens.discovery.PeerDiscoveryScreen
import com.elv8.crisisos.ui.screens.requests.MessageRequestsScreen
import com.elv8.crisisos.ui.components.InputField
import com.elv8.crisisos.ui.components.LocalTopBarState

@Composable
fun ChatHubScreen(
    onNavigateToThread: (threadId: String) -> Unit,
    viewModel: ChatHubViewModel = hiltViewModel(),
    listViewModel: ChatListViewModel = hiltViewModel()
) {
    val activeTab by viewModel.activeTab.collectAsState()
    val listUiState by listViewModel.uiState.collectAsState()
    val topBarState = LocalTopBarState.current

    LaunchedEffect(Unit) {
        topBarState.update(title = { Text("MESSAGES", fontWeight = FontWeight.Bold) })
    }

    val pagerState = rememberPagerState(pageCount = { 2 })

    LaunchedEffect(pagerState.currentPage) {
        viewModel.setTab(pagerState.currentPage)
    }

    LaunchedEffect(activeTab) {
        if (pagerState.currentPage != activeTab && activeTab < 2) {
            pagerState.animateScrollToPage(activeTab)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        
        Text(
            text = "Messages",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 8.dp)
        )

        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            InputField(
                value = listUiState.searchQuery,
                onValueChange = listViewModel::updateSearch,
                label = "Search peers...",
                modifier = Modifier.fillMaxWidth()
            )
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    TabItem(
                        title = "Active Chats",
                        isSelected = activeTab == 0,
                        count = 0,
                        onClick = { viewModel.setTab(0) },
                        modifier = Modifier.weight(1f)
                    )
                    TabItem(
                        title = "Nearby Peers",
                        isSelected = activeTab == 1,
                        count = 0,
                        onClick = { viewModel.setTab(1) },
                        modifier = Modifier.weight(1f)
                    )
                }

                val screenWidth = LocalConfiguration.current.screenWidthDp.dp    
                val tabWidth = screenWidth / 2
                val indicatorOffset by animateDpAsState(
                    targetValue = tabWidth * activeTab,
                    label = "indicatorOffset"
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .width(tabWidth)
                        .height(2.dp)
                        .offset(x = indicatorOffset)
                        .background(Color(0xFFFF9800)) // Orange indicator       
                )

                HorizontalDivider(
                    modifier = Modifier.align(Alignment.BottomStart),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = true
        ) { page ->
            when (page) {
                0 -> {
                    ChatListScreen(
                        onNavigateToThread = onNavigateToThread,
                        onNavigateToRequests = {},
                        onNavigateToDiscover = { viewModel.setTab(1) }
                    )
                }
                1 -> {
                    PeerDiscoveryScreen(
                        onNavigateBack = { viewModel.setTab(0) },
                        onNavigateToChat = onNavigateToThread
                    )
                }
            }
        }
    }
}

@Composable
private fun TabItem(
    title: String,
    isSelected: Boolean,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = if (isSelected) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (count > 0) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ) {
                    Text(text = count.toString())
                }
            }
        }
    }
}



