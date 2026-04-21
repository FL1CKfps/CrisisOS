package com.elv8.crisisos.ui.screens.onboarding

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elv8.crisisos.data.local.db.CrisisDatabase
import com.elv8.crisisos.MockDataSeeder
import com.elv8.crisisos.ui.screens.settings.settingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: CrisisDatabase
) : ViewModel() {

    private val dataStore = context.settingsDataStore

    private val _onboarded = MutableStateFlow<Boolean?>(null)
    val onboarded = _onboarded.asStateFlow()

    private object PreferencesKeys {
        val ALIAS = stringPreferencesKey("alias")
        val HAS_SEEN_ONBOARDING = booleanPreferencesKey("has_seen_onboarding")
    }

    init {
        viewModelScope.launch {
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.HAS_SEEN_ONBOARDING] ?: false
            }.collect { hasSeen ->
                _onboarded.value = hasSeen
            }
        }
    }

    fun completeOnboarding(alias: String) {
        viewModelScope.launch {
            MockDataSeeder.seed(database, alias)
            
            dataStore.edit { preferences ->
                if (alias.isNotBlank()) {
                    preferences[PreferencesKeys.ALIAS] = alias
                }
                preferences[PreferencesKeys.HAS_SEEN_ONBOARDING] = true
            }
        }
    }
}
