package com.elv8.crisisos.ui.screens.discovery

import androidx.lifecycle.ViewModel
import com.elv8.crisisos.core.debug.MeshDiagnostics
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    meshDiagnostics: MeshDiagnostics
) : ViewModel() {
    val snapshot = meshDiagnostics.snapshot

    init {
        meshDiagnostics.startMonitoring()
    }
}
