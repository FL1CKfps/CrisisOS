package com.elv8.crisisos.ui.screens.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elv8.crisisos.domain.model.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

data class SettingsUiState(
    val profile: UserProfile = UserProfile(),
    val isSaving: Boolean = false,
    val broadcastRange: Int = 10,
    val autoConnect: Boolean = true,
    val discoveryMode: String = "Active",
    val autoSosLowBattery: Boolean = false,
    val autoSosThreshold: Int = 15,
    val textSize: Int = 100
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val dataStore = context.settingsDataStore

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    private object PreferencesKeys {
        val USER_ID = stringPreferencesKey("user_id")
        val ALIAS = stringPreferencesKey("alias")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val EMERGENCY_CONTACTS = stringSetPreferencesKey("emergency_contacts")
        val DEFAULT_LANGUAGE = stringPreferencesKey("default_language")
        val HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
        val REDUCED_ANIMATIONS = booleanPreferencesKey("reduced_animations")
        
        val BROADCAST_RANGE = intPreferencesKey("broadcast_range")
        val AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        val DISCOVERY_MODE = stringPreferencesKey("discovery_mode")
        val AUTO_SOS_BATTERY = booleanPreferencesKey("auto_sos_battery")
        val AUTO_SOS_THRESHOLD = intPreferencesKey("auto_sos_threshold")
        val TEXT_SIZE = intPreferencesKey("text_size")
    }

    init {
        viewModelScope.launch {
            dataStore.data
                .catch { emit(emptyPreferences()) }
                .map { preferences ->
                    var currentUserId = preferences[PreferencesKeys.USER_ID]
                    if (currentUserId == null) {
                        currentUserId = UUID.randomUUID().toString().split("-").last() ?: "user-xxxx"
                        dataStore.edit { it[PreferencesKeys.USER_ID] = currentUserId!! }
                    }
                    
                    var currentDeviceId = preferences[PreferencesKeys.DEVICE_ID]
                    if (currentDeviceId == null) {
                        currentDeviceId = UUID.randomUUID().toString()
                        dataStore.edit { it[PreferencesKeys.DEVICE_ID] = currentDeviceId!! }
                    }

                    SettingsUiState(
                        profile = UserProfile(
                            userId = currentUserId,
                            alias = preferences[PreferencesKeys.ALIAS] ?: "New User",
                            deviceId = currentDeviceId,
                            emergencyContacts = preferences[PreferencesKeys.EMERGENCY_CONTACTS]?.toList() ?: emptyList(),
                            defaultLanguage = preferences[PreferencesKeys.DEFAULT_LANGUAGE] ?: "en",
                            highContrastMode = preferences[PreferencesKeys.HIGH_CONTRAST] ?: false,
                            reducedAnimations = preferences[PreferencesKeys.REDUCED_ANIMATIONS] ?: false
                        ),
                        broadcastRange = preferences[PreferencesKeys.BROADCAST_RANGE] ?: 50,
                        autoConnect = preferences[PreferencesKeys.AUTO_CONNECT] ?: true,
                        discoveryMode = preferences[PreferencesKeys.DISCOVERY_MODE] ?: "Active",
                        autoSosLowBattery = preferences[PreferencesKeys.AUTO_SOS_BATTERY] ?: false,
                        autoSosThreshold = preferences[PreferencesKeys.AUTO_SOS_THRESHOLD] ?: 15,
                        textSize = preferences[PreferencesKeys.TEXT_SIZE] ?: 100
                    )
                }
                .collect { state ->
                    _uiState.value = state
                }
        }
    }

    fun updateAlias(newAlias: String) = saveSetting(PreferencesKeys.ALIAS, newAlias)
    
    fun updateEmergencyContacts(contacts: List<String>) = saveSetting(PreferencesKeys.EMERGENCY_CONTACTS, contacts.toSet())
    
    fun toggleHighContrast(enabled: Boolean) = saveSetting(PreferencesKeys.HIGH_CONTRAST, enabled)

    fun toggleReducedAnimations(enabled: Boolean) = saveSetting(PreferencesKeys.REDUCED_ANIMATIONS, enabled)
    
    fun updateBroadcastRange(range: Int) = saveSetting(PreferencesKeys.BROADCAST_RANGE, range)
    
    fun updateAutoConnect(enabled: Boolean) = saveSetting(PreferencesKeys.AUTO_CONNECT, enabled)

    fun updateDiscoveryMode(mode: String) = saveSetting(PreferencesKeys.DISCOVERY_MODE, mode)
    
    fun updateAutoSosLowBattery(enabled: Boolean) = saveSetting(PreferencesKeys.AUTO_SOS_BATTERY, enabled)
    
    fun updateAutoSosThreshold(threshold: Int) = saveSetting(PreferencesKeys.AUTO_SOS_THRESHOLD, threshold)

    fun updateTextSize(size: Int) = saveSetting(PreferencesKeys.TEXT_SIZE, size)

    private fun <T> saveSetting(key: Preferences.Key<T>, value: T) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            dataStore.edit { preferences ->
                preferences[key] = value
            }
            _uiState.update { it.copy(isSaving = false) }
        }
    }
    
    fun saveSettings() {
        // Dummy, settings are saved on each change as per requirements
    }

    fun clearAllData() {
        viewModelScope.launch {
            dataStore.edit { it.clear() }
        }
    }
}

