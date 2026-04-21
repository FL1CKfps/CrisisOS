package com.elv8.crisisos.core.recovery

import android.content.Context
import android.util.Log
import com.elv8.crisisos.core.debug.MeshLogger
import com.elv8.crisisos.core.event.AppEvent
import com.elv8.crisisos.core.event.EventBus
import com.elv8.crisisos.core.permissions.MeshPermissionManager
import com.elv8.crisisos.data.mesh.MeshConnectionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class RecoveryState(
    val lastRecoveryAttemptAt: Long = 0L,
    val totalRecoveryCount: Int = 0,
    val consecutiveFailures: Int = 0,
    val isInRecovery: Boolean = false
)

@Singleton
class MeshRecoveryManager @Inject constructor(
    private val connectionManager: MeshConnectionManager,
    private val permissionManager: MeshPermissionManager,
    private val eventBus: EventBus,
    private val scope: CoroutineScope,
    @ApplicationContext private val context: Context
) {
    private val _recoveryState = MutableStateFlow(RecoveryState())
    val recoveryState: StateFlow<RecoveryState> = _recoveryState.asStateFlow()

    private val MIN_RECOVERY_INTERVAL_MS = 15_000L
    private val MAX_CONSECUTIVE_FAILURES = 5

    fun startMonitoring() {
        scope.launch {
            while (isActive) {
                delay(20_000)
                checkAndRecover()
            }
        }

        scope.launch {
            eventBus.events
                .filterIsInstance<AppEvent.MeshEvent.MeshStatusChanged>()
                .filter { !it.isActive && it.peerCount == 0 }
                .collect {
                    Log.d("CrisisOS_Recovery", "MeshStatusChanged to inactive — scheduling recovery check")
                    delay(10_000)
                    checkAndRecover()
                }
        }
    }

    private suspend fun checkAndRecover() {
        val state = _recoveryState.value
        val now = System.currentTimeMillis()

        if (now - state.lastRecoveryAttemptAt < MIN_RECOVERY_INTERVAL_MS) {
            Log.v("CrisisOS_Recovery", "Recovery skipped — too soon since last attempt")
            return
        }

        if (state.consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
            Log.e("CrisisOS_Recovery", "Max consecutive failures ($MAX_CONSECUTIVE_FAILURES) reached — backing off 5 minutes")
            delay(5 * 60_000)
            _recoveryState.update { it.copy(consecutiveFailures = 0) }
            return
        }

        val isAdvertising = connectionManager.isAdvertising.value
        val isDiscovering = connectionManager.isDiscovering.value
        val missingPerms = permissionManager.getMissingPermissions()
        val hasMissingPerms = missingPerms.isNotEmpty()

        Log.d("CrisisOS_Recovery", "Health check — advertising=$isAdvertising discovering=$isDiscovering missingPerms=$hasMissingPerms consecutiveFailures=${state.consecutiveFailures}")

        if (hasMissingPerms) {
            Log.e("CrisisOS_Recovery", "Cannot recover — missing permissions: $missingPerms")
            // SystemEvent.PermissionDenied takes a String according to AppEvent.kt
            eventBus.emit(AppEvent.SystemEvent.PermissionDenied("mesh_permissions"))
            return
        }

        var recovered = false

        if (!isAdvertising && !isDiscovering) {
            Log.e("CrisisOS_Recovery", "Both down — performing full mesh restart")
            performFullRestart()
            recovered = true
        } else {
            if (!isAdvertising) {
                Log.w("CrisisOS_Recovery", "Advertising is down — restarting")
                connectionManager.startAdvertising()
                recovered = true
            }

            if (!isDiscovering) {
                Log.w("CrisisOS_Recovery", "Discovery is down — restarting")
                connectionManager.startDiscovery()
                recovered = true
            }
        }

        _recoveryState.update { it.copy(
            lastRecoveryAttemptAt = now,
            totalRecoveryCount = if (recovered) it.totalRecoveryCount + 1 else it.totalRecoveryCount,
            consecutiveFailures = if (recovered) it.consecutiveFailures + 1 else 0,
            isInRecovery = false
        ) }

        if (recovered) {
            Log.i("CrisisOS_Recovery", "Recovery attempt #${_recoveryState.value.totalRecoveryCount} completed")
        } else {
            Log.d("CrisisOS_Recovery", "Health check passed — no recovery needed")
        }
    }

    private suspend fun performFullRestart() {
        Log.w("CrisisOS_Recovery", "Full mesh restart initiated")
        _recoveryState.update { it.copy(isInRecovery = true) }

        connectionManager.stopAll()
        delay(3_000)

        val alias = context.getSharedPreferences("crisisos_prefs", Context.MODE_PRIVATE)
            .getString("user_alias", "Survivor") ?: "Survivor"

        connectionManager.startMesh(alias)

        Log.i("CrisisOS_Recovery", "Full mesh restart completed")
        eventBus.emit(AppEvent.MeshEvent.MeshStatusChanged(true, 0))
    }

    fun triggerManualRecovery() {
        scope.launch {
            Log.i("CrisisOS_Recovery", "Manual recovery triggered by user")
            _recoveryState.update { it.copy(consecutiveFailures = 0, lastRecoveryAttemptAt = 0L) }
            checkAndRecover()
        }
    }
}