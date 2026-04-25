package com.elv8.crisisos.ui.screens.community

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.elv8.crisisos.ui.components.EmptyState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups

@Composable
fun CommunityBoardScreen(onNavigateBack: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            EmptyState(
                title = "Community Board",
                subtitle = "Anonymous, mesh-distributed public posting board. No posts in your area yet.",
                icon = Icons.Default.Groups
            )
        }
    }
}
