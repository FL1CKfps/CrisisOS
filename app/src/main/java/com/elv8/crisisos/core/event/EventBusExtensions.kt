package com.elv8.crisisos.core.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.reflect.KClass

fun <T : AppEvent> ViewModel.observeEvent(bus: EventBus, type: KClass<T>, action: suspend (T) -> Unit) {
    bus.observe(viewModelScope, type, action)
}

suspend fun EventBus.emitMeshStatus(isActive: Boolean, peerCount: Int) {
    emit(AppEvent.MeshEvent.MeshStatusChanged(isActive, peerCount))
}