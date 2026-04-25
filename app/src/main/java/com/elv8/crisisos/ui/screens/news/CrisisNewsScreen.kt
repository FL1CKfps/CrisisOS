package com.elv8.crisisos.ui.screens.news

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.elv8.crisisos.ui.components.EmptyState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Newspaper

@Composable
fun CrisisNewsScreen(onNavigateBack: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            EmptyState(
                title = "CrisisNews Feed",
                subtitle = "Hyperlocal conflict news delivered via mesh. No news currently available in this shard.",
                icon = Icons.Default.Newspaper
            )
        }
    }
}
