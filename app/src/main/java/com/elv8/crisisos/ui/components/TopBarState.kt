package com.elv8.crisisos.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.*

@Stable
class TopBarState {
    var title by mutableStateOf<(@Composable () -> Unit)?>(null)
    var actions by mutableStateOf<(@Composable RowScope.() -> Unit)?>(null)
    var navigationIcon by mutableStateOf<(@Composable () -> Unit)?>(null)
    var isVisible by mutableStateOf(true)

    fun update(
        title: (@Composable () -> Unit)? = null,
        actions: (@Composable RowScope.() -> Unit)? = null,
        navigationIcon: (@Composable () -> Unit)? = null,
        isVisible: Boolean = true
    ) {
        this.title = title
        this.actions = actions
        this.navigationIcon = navigationIcon
        this.isVisible = isVisible
    }

    fun reset() {
        title = null
        actions = null
        navigationIcon = null
        isVisible = true
    }
}

val LocalTopBarState = staticCompositionLocalOf { TopBarState() }
