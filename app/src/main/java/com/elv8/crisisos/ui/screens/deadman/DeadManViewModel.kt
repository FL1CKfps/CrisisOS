package com.elv8.crisisos.ui.screens.deadman

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.elv8.crisisos.core.event.AppEvent
import com.elv8.crisisos.core.event.EventBus
import com.elv8.crisisos.work.DeadManWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class DeadManUiState(
    val isActive: Boolean = false,
    val intervalMinutes: Int = 60,
    val timeRemainingSeconds: Long = 3600L,
    val escalationContacts: List<String> = listOf("Alice (Wife)", "Bob (Brother)"),
    val alertMessage: String = "If you receive this, I haven't checked in. Send help.",
    val lastCheckIn: String = "--:--",
    val timerExpired: Boolean = false
)

@HiltViewModel
class DeadManViewModel @Inject constructor(
    private val workManager: WorkManager,
    private val eventBus: EventBus,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _uiState = MutableStateFlow(DeadManUiState())
    val uiState: StateFlow<DeadManUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null

    init {
        val prefs = context.getSharedPreferences("crisisos_prefs", Context.MODE_PRIVATE)
        val savedInterval = prefs.getInt("deadman_interval", 60)
        val savedMessage = prefs.getString("deadman_message", "If you receive this, I haven't checked in. Send help.") ?: ""
        val savedContactsStr = prefs.getString("deadman_contacts", "[\"Alice (Wife)\", \"Bob (Brother)\"]") ?: ""
        val savedContacts = try {
            val arr = JSONArray(savedContactsStr)
            List(arr.length()) { i -> arr.getString(i) }
        } catch (e: Exception) {
            listOf("Alice (Wife)", "Bob (Brother)")
        }
        val savedLastCheckIn = prefs.getString("deadman_last_checkin", "--:--") ?: "--:--"
        val savedDeadline = prefs.getLong("deadman_deadline", 0L)

        _uiState.update {
            it.copy(
                intervalMinutes = savedInterval,
                alertMessage = savedMessage,
                escalationContacts = savedContacts,
                lastCheckIn = savedLastCheckIn
            )
        }

        try {
            val infos = workManager.getWorkInfosByTag(DeadManWorker.WORK_TAG).get()
            val isEnqueued = infos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
            if (isEnqueued) {
                val now = System.currentTimeMillis()
                var remaining = if (savedDeadline > now) (savedDeadline - now) / 1000L else 0L
                _uiState.update { it.copy(isActive = true, timeRemainingSeconds = remaining) }
                startLocalCountdown()
            }
        } catch (e: Exception) {
            // Ignore interruption in init
        }

        viewModelScope.launch {
            workManager.getWorkInfosByTagFlow(DeadManWorker.WORK_TAG).collect { infos ->
                val isScheduled = infos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
                if (!isScheduled && _uiState.value.isActive) {
                    _uiState.update { it.copy(isActive = false) }
                }
            }
        }
    }

    private fun getCurrentTimeString(): String = 
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

    fun activate() {
        val currentState = _uiState.value
        val nowString = getCurrentTimeString()
        val deadline = System.currentTimeMillis() + (currentState.intervalMinutes * 60L * 1000L)
        
        val prefs = context.getSharedPreferences("crisisos_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("deadman_interval", currentState.intervalMinutes)
            .putString("deadman_message", currentState.alertMessage)
            .putString("deadman_contacts", JSONArray(currentState.escalationContacts).toString())
            .putString("deadman_last_checkin", nowString)
            .putLong("deadman_deadline", deadline)
            .apply()

        val request = DeadManWorker.buildRequest(
            currentState.intervalMinutes,
            currentState.alertMessage,
            currentState.escalationContacts
        )
        workManager.enqueueUniquePeriodicWork(
            DeadManWorker.WORK_TAG,
            ExistingPeriodicWorkPolicy.REPLACE,
            request
        )

        _uiState.update {
            it.copy(
                isActive = true,
                lastCheckIn = nowString,
                timerExpired = false,
                timeRemainingSeconds = currentState.intervalMinutes * 60L
            )
        }
        startLocalCountdown()
    }

    fun deactivate() {
        workManager.cancelAllWorkByTag(DeadManWorker.WORK_TAG)
        _uiState.update {
            it.copy(
                isActive = false,
                timerExpired = false
            )
        }
        countdownJob?.cancel()
    }

    fun checkIn() {
        if (_uiState.value.isActive) {
            val nowString = getCurrentTimeString()
            val currentState = _uiState.value
            val deadline = System.currentTimeMillis() + (currentState.intervalMinutes * 60L * 1000L)
            
            val prefs = context.getSharedPreferences("crisisos_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("deadman_last_checkin", nowString)
                .putLong("deadman_deadline", deadline)
                .apply()
                
            _uiState.update { it.copy(lastCheckIn = nowString, timerExpired = false, timeRemainingSeconds = currentState.intervalMinutes * 60L) }

            workManager.cancelAllWorkByTag(DeadManWorker.WORK_TAG)
            val request = DeadManWorker.buildRequest(
                currentState.intervalMinutes,
                currentState.alertMessage,
                currentState.escalationContacts
            )
            workManager.enqueueUniquePeriodicWork(
                DeadManWorker.WORK_TAG,
                ExistingPeriodicWorkPolicy.REPLACE,
                request
            )
            
            startLocalCountdown()
            eventBus.tryEmit(AppEvent.DeadManEvent.CheckInReceived)
        }
    }

    fun setInterval(minutes: Int) {
        _uiState.update {
            it.copy(
                intervalMinutes = minutes,
                timeRemainingSeconds = minutes * 60L
            )
        }
    }

    fun updateAlertMessage(message: String) {
        _uiState.update { it.copy(alertMessage = message) }
    }

    fun addContact(contact: String) {
        _uiState.update { it.copy(escalationContacts = it.escalationContacts + contact) }
    }

    fun removeContact(contact: String) {
        _uiState.update { it.copy(escalationContacts = it.escalationContacts - contact) }
    }

    private fun startLocalCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var remaining = _uiState.value.timeRemainingSeconds
            
            while (remaining > 0 && _uiState.value.isActive) {
                _uiState.update { it.copy(timeRemainingSeconds = remaining) }
                delay(1000L)
                remaining--
            }
            
            if (remaining <= 0) {
                _uiState.update { it.copy(timerExpired = true, timeRemainingSeconds = 0) }
                eventBus.tryEmit(AppEvent.DeadManEvent.TimerExpired)
            }
        }

    }
}

