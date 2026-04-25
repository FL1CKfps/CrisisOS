package com.elv8.crisisos.ui.screens.deadman

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.elv8.crisisos.core.event.AppEvent
import com.elv8.crisisos.core.event.EventBus
import com.elv8.crisisos.domain.model.contact.Contact
import com.elv8.crisisos.domain.repository.ContactRepository
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

/**
 * @param crsId    Stable mesh identity for the contact.
 * @param label    Human-readable display label, e.g. "Sister — Priya".
 */
data class EscalationContact(val crsId: String, val label: String)

data class DeadManUiState(
    val isActive: Boolean = false,
    val intervalMinutes: Int = 60,
    val timeRemainingSeconds: Long = 3600L,
    /** Family/Emergency contacts available from the user's address book — picker source. */
    val availableFamilyContacts: List<EscalationContact> = emptyList(),
    /** Contacts currently selected for escalation. */
    val escalationContacts: List<EscalationContact> = emptyList(),
    val alertMessage: String = "If you receive this, I haven't checked in. Send help.",
    val lastCheckIn: String = "--:--",
    val timerExpired: Boolean = false,
    /** Surfaced inline so taps on Activate / Add Contact never silently no-op. */
    val errorMessage: String? = null,
    /** True while the contact picker dialog is open. */
    val showContactPicker: Boolean = false
)

@HiltViewModel
class DeadManViewModel @Inject constructor(
    private val workManager: WorkManager,
    private val eventBus: EventBus,
    private val contactRepository: ContactRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val PREFS = "crisisos_prefs"
        private const val K_INTERVAL = "deadman_interval"
        private const val K_MESSAGE = "deadman_message"
        private const val K_CONTACTS = "deadman_contacts_v2"
        private const val K_LAST_CHECKIN = "deadman_last_checkin"
        private const val K_DEADLINE = "deadman_deadline"
    }

    private val _uiState = MutableStateFlow(DeadManUiState())
    val uiState: StateFlow<DeadManUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null

    init {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val savedInterval = prefs.getInt(K_INTERVAL, 60)
        val savedMessage = prefs.getString(K_MESSAGE, "If you receive this, I haven't checked in. Send help.") ?: ""
        val savedContacts = decodeContacts(prefs.getString(K_CONTACTS, "[]") ?: "[]")
        val savedLastCheckIn = prefs.getString(K_LAST_CHECKIN, "--:--") ?: "--:--"
        val savedDeadline = prefs.getLong(K_DEADLINE, 0L)

        _uiState.update {
            it.copy(
                intervalMinutes = savedInterval,
                alertMessage = savedMessage,
                escalationContacts = savedContacts,
                lastCheckIn = savedLastCheckIn
            )
        }

        // Watch the user's family-trust contact list and expose it as the picker source.
        viewModelScope.launch {
            contactRepository.getFamilyContacts().collect { fam ->
                _uiState.update { state ->
                    state.copy(
                        availableFamilyContacts = fam.map { it.toEscalation() }
                    )
                }
            }
        }

        try {
            val infos = workManager.getWorkInfosByTag(DeadManWorker.WORK_TAG).get()
            val isEnqueued = infos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
            if (isEnqueued) {
                val now = System.currentTimeMillis()
                val remaining = if (savedDeadline > now) (savedDeadline - now) / 1000L else 0L
                _uiState.update { it.copy(isActive = true, timeRemainingSeconds = remaining) }
                startLocalCountdown()
            }
        } catch (_: Exception) {
            // Best-effort restore on init.
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
        if (currentState.escalationContacts.isEmpty()) {
            _uiState.update {
                it.copy(errorMessage = "Add at least one escalation contact before activating.")
            }
            return
        }
        val nowString = getCurrentTimeString()
        val deadline = System.currentTimeMillis() + (currentState.intervalMinutes * 60L * 1000L)

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(K_INTERVAL, currentState.intervalMinutes)
            .putString(K_MESSAGE, currentState.alertMessage)
            .putString(K_CONTACTS, encodeContacts(currentState.escalationContacts))
            .putString(K_LAST_CHECKIN, nowString)
            .putLong(K_DEADLINE, deadline)
            .apply()

        scheduleWorker(currentState.intervalMinutes, currentState.alertMessage, currentState.escalationContacts)

        _uiState.update {
            it.copy(
                isActive = true,
                lastCheckIn = nowString,
                timerExpired = false,
                timeRemainingSeconds = currentState.intervalMinutes * 60L,
                errorMessage = null
            )
        }
        startLocalCountdown()
    }

    fun deactivate() {
        workManager.cancelUniqueWork(DeadManWorker.WORK_NAME)
        workManager.cancelAllWorkByTag(DeadManWorker.WORK_TAG)
        _uiState.update { it.copy(isActive = false, timerExpired = false, errorMessage = null) }
        countdownJob?.cancel()
    }

    fun checkIn() {
        if (_uiState.value.isActive) {
            val nowString = getCurrentTimeString()
            val currentState = _uiState.value
            val deadline = System.currentTimeMillis() + (currentState.intervalMinutes * 60L * 1000L)

            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(K_LAST_CHECKIN, nowString)
                .putLong(K_DEADLINE, deadline)
                .apply()

            _uiState.update {
                it.copy(
                    lastCheckIn = nowString,
                    timerExpired = false,
                    timeRemainingSeconds = currentState.intervalMinutes * 60L
                )
            }

            scheduleWorker(currentState.intervalMinutes, currentState.alertMessage, currentState.escalationContacts)

            startLocalCountdown()
            eventBus.tryEmit(AppEvent.DeadManEvent.CheckInReceived)
        }
    }

    /**
     * Single source of truth for scheduling the one-shot deadline worker.
     * `enqueueUniqueWork(REPLACE, ...)` cancels any in-flight worker before
     * enqueuing the new one, so a check-in cleanly resets the deadline
     * without leaving a stale fire pending.
     */
    private fun scheduleWorker(
        intervalMinutes: Int,
        message: String,
        contacts: List<EscalationContact>
    ) {
        // Pass a stable descriptor (CRS ID + label) so the worker payload
        // carries something the recipient can act on even if the alias is
        // renamed before the worker fires.
        val descriptors = contacts.map {
            if (it.crsId.isNotBlank()) "${it.label} <${it.crsId}>" else it.label
        }
        val request = DeadManWorker.buildRequest(intervalMinutes, message, descriptors)
        workManager.enqueueUniqueWork(
            DeadManWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun setInterval(minutes: Int) {
        // Locked while active — the worker has already been scheduled with
        // the previous deadline, and silently letting the user move the chip
        // would desync the visible countdown from the real fire time.
        if (_uiState.value.isActive) {
            _uiState.update {
                it.copy(errorMessage = "Deactivate the switch before changing the interval.")
            }
            return
        }
        _uiState.update {
            it.copy(intervalMinutes = minutes, timeRemainingSeconds = minutes * 60L)
        }
    }

    fun openContactPicker() {
        if (_uiState.value.isActive) {
            _uiState.update {
                it.copy(errorMessage = "Deactivate the switch before changing contacts.")
            }
            return
        }
        if (_uiState.value.availableFamilyContacts.isEmpty()) {
            _uiState.update {
                it.copy(errorMessage = "No family/emergency contacts found. Add them on the Contacts screen first.")
            }
            return
        }
        _uiState.update { it.copy(showContactPicker = true, errorMessage = null) }
    }

    fun dismissContactPicker() {
        _uiState.update { it.copy(showContactPicker = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun updateAlertMessage(message: String) {
        _uiState.update { it.copy(alertMessage = message) }
    }

    fun toggleContact(contact: EscalationContact) {
        _uiState.update { state ->
            val exists = state.escalationContacts.any { it.crsId == contact.crsId }
            val next = if (exists) {
                state.escalationContacts.filterNot { it.crsId == contact.crsId }
            } else {
                state.escalationContacts + contact
            }
            state.copy(escalationContacts = next)
        }
    }

    fun removeContact(contact: EscalationContact) {
        _uiState.update {
            it.copy(escalationContacts = it.escalationContacts.filterNot { c -> c.crsId == contact.crsId })
        }
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

    private fun Contact.toEscalation(): EscalationContact =
        EscalationContact(crsId = crsId, label = alias)

    private fun encodeContacts(contacts: List<EscalationContact>): String {
        val arr = JSONArray()
        contacts.forEach { c ->
            val obj = org.json.JSONObject()
            obj.put("crsId", c.crsId)
            obj.put("label", c.label)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun decodeContacts(json: String): List<EscalationContact> = try {
        val arr = JSONArray(json)
        List(arr.length()) { i ->
            val obj = arr.getJSONObject(i)
            EscalationContact(
                crsId = obj.optString("crsId"),
                label = obj.optString("label")
            )
        }.filter { it.crsId.isNotBlank() }
    } catch (_: Exception) {
        emptyList()
    }
}
