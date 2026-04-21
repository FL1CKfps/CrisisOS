package com.elv8.crisisos.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

import androidx.compose.animation.core.spring
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrisisBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    badgeCountViewModel: BadgeCountViewModel = hiltViewModel()
) {
    val pendingRequestCount by badgeCountViewModel.totalPendingCount.collectAsState()

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        BottomNavItem.items.forEach { item ->
            val isSelected = currentRoute == item.route

            val iconScale by animateFloatAsState(
                targetValue = if (isSelected) 1.15f else 1.0f,
                label = "IconScale"
            )

            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigate(item.route) },
                alwaysShowLabel = false,
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer  
                ),
                icon = {
                    Box(contentAlignment = Alignment.TopEnd) {
                        if (item is BottomNavItem.Chat && pendingRequestCount > 0) {
                            var badgeScale by remember { mutableStateOf(1f) }
                            val animatedBadgeScale by animateFloatAsState(
                                targetValue = badgeScale,
                                animationSpec = spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy),
                                label = "badgeScale"
                            )
                            
                            LaunchedEffect(pendingRequestCount) {
                                badgeScale = 1.35f
                                kotlinx.coroutines.delay(200)
                                badgeScale = 1f
                            }
                            
                            BadgedBox(
                                badge = {
                                    Badge(
                                        modifier = Modifier.scale(animatedBadgeScale).size(8.dp),
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    )
                                }
                            ) {
                                Icon(
                                    imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label,
                                    modifier = Modifier.scale(iconScale)
                                )
                            }
                        } else {
                            Icon(
                                imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.label,
                                modifier = Modifier.scale(iconScale)
                            )
                        }
                        
                        if (item is BottomNavItem.SOS) {
                            PulsingDot(
                                color = Color.Red,
                                size = 6.dp,
                                modifier = Modifier.offset(x = 6.dp, y = (-2).dp)
                            )
                        }
                    }
                },
                label = { Text(item.label) }
            )
        }
    }
}

