package com.elv8.crisisos.ui.screens.home

import androidx.compose.ui.graphics.vector.ImageVector

enum class FeatureStatus {
    AVAILABLE, BETA, OFFLINE_READY, COMING_SOON
}

data class FeatureItem(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val route: String,
    val status: FeatureStatus
)
