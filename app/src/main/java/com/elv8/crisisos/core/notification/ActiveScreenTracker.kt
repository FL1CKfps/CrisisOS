package com.elv8.crisisos.core.notification

import android.util.Log
import com.elv8.crisisos.core.debug.MeshLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActiveScreenTracker @Inject constructor() {
    private val _activeScreen = MutableStateFlow<String?>(null)
    val activeScreen: StateFlow<String?> = _activeScreen.asStateFlow()

    fun setActiveScreen(route: String?) {
        _activeScreen.value = route
        Log.d("CrisisOS_ScreenTracker", "Active screen: $route")
    }
}