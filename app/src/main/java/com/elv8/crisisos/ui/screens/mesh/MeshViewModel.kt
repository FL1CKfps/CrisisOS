package com.elv8.crisisos.ui.screens.mesh

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elv8.crisisos.data.local.entity.PeerEntity
import com.elv8.crisisos.domain.usecase.ObservePeersUseCase
import com.elv8.crisisos.service.MeshForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MeshViewModel @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
    private val observePeersUseCase: ObservePeersUseCase
) : ViewModel() {

    val peers: StateFlow<List<PeerEntity>> = observePeersUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun startDiscovery(crsId: String, alias: String) {
        MeshForegroundService.start(applicationContext, crsId, alias)
    }

    fun stopDiscovery() {
        MeshForegroundService.stop(applicationContext)
    }
}
