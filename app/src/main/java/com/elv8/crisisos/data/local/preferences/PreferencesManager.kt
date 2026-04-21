package com.elv8.crisisos.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

@Singleton
class PreferencesManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    companion object {
        val HAS_SEEN_DISCOVERY_ONBOARDING = booleanPreferencesKey("has_seen_discovery_onboarding")
    }

    val hasSeenDiscoveryOnboarding: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[HAS_SEEN_DISCOVERY_ONBOARDING] ?: false
        }

    suspend fun setHasSeenDiscoveryOnboarding(hasSeen: Boolean) {
        dataStore.edit { preferences ->
            preferences[HAS_SEEN_DISCOVERY_ONBOARDING] = hasSeen
        }
    }
}