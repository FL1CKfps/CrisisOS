package com.elv8.crisisos.ui.home

import androidx.lifecycle.ViewModel
import com.elv8.crisisos.core.permissions.MeshPermissionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val permissionManager: MeshPermissionManager
) : ViewModel() {
    private val _hasPermissions = MutableStateFlow(false)
    val hasPermissions: StateFlow<Boolean> = _hasPermissions.asStateFlow()

    init {
        checkPermissions()
    }

    fun checkPermissions() {
        permissionManager.logPermissionState()
        _hasPermissions.value = permissionManager.areAllPermissionsGranted()    
    }
}
