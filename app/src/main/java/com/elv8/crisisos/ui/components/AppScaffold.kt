package com.elv8.crisisos.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elv8.crisisos.ui.navigation.Screen
import kotlinx.coroutines.launch

@Composable
fun AppScaffold(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    showBottomBar: Boolean = true,
    content: @Composable (PaddingValues) -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val topBarState = LocalTopBarState.current

    val navItems = listOf(
        NavItem("Home", Screen.Home.route, Icons.Default.Home),
        NavItem("Mesh Chat", Screen.ChatHub.route, Icons.AutoMirrored.Filled.Chat),
        NavItem("SOS Broadcast", Screen.Sos.route, Icons.Default.Warning),
        NavItem("Supply Request", Screen.Supply().route, Icons.Default.Inventory),
        NavItem("Offline Maps", Screen.Maps.route, Icons.Default.Map),
        NavItem("Danger Zones", Screen.DangerZone.route, Icons.Default.LocationOff),
        NavItem("Find Person + Child Alerts", Screen.MissingPerson.route, Icons.Default.PersonSearch),
        NavItem("Dead Man's Switch", Screen.DeadManSwitch.route, Icons.Default.Timer),
        NavItem("Checkpoint Intel", Screen.Checkpoint.route, Icons.Default.Security),
        NavItem("AI Assistant", Screen.AiAssistant.route, Icons.Default.Psychology),
        NavItem("Fake News Detector", Screen.FakeNews.route, Icons.AutoMirrored.Filled.FactCheck),
        NavItem("CrisisNews Feed", Screen.CrisisNews.route, Icons.Default.Newspaper),
        NavItem("Community Board", Screen.CommunityBoard.route, Icons.Default.Groups),
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = currentRoute != Screen.Onboarding.route,
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "CrisisOS",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    navItems.forEach { item ->
                        NavigationDrawerItem(
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.title) },
                            selected = currentRoute == item.route,
                            onClick = {
                                scope.launch { drawerState.close() }
                                onNavigate(item.route)
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                if (currentRoute != Screen.Onboarding.route && topBarState.isVisible) {
                    CrisisTopBar(
                        title = topBarState.title,
                        actions = topBarState.actions ?: {},
                        navigationIcon = topBarState.navigationIcon ?: {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        }
                    )
                }
            },
            contentWindowInsets = WindowInsets(0.dp)
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                content(paddingValues)
            }
        }
    }
}

data class NavItem(
    val title: String,
    val route: String,
    val icon: ImageVector
)
