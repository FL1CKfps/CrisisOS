package com.elv8.crisisos.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Home : BottomNavItem("home", "Home", Icons.Filled.Home, Icons.Outlined.Home)
    data object Chat : BottomNavItem("chat_hub", "Chat", Icons.Filled.Chat, Icons.Outlined.Chat)
    data object SOS : BottomNavItem("sos", "SOS", Icons.Filled.Warning, Icons.Outlined.Warning)
    data object Maps : BottomNavItem("maps", "Maps", Icons.Filled.Map, Icons.Outlined.Map)
    data object More : BottomNavItem("more", "More", Icons.Filled.GridView, Icons.Outlined.GridView)

    companion object {
        val items = listOf(Home, Chat, SOS, Maps, More)
    }
}
