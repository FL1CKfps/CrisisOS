package com.elv8.crisisos.core.notification

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "notification_prefs")

@Singleton
class NotificationSettings @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val KEY_DND_ENABLED = booleanPreferencesKey("dnd_enabled")
    private val KEY_CHAT_ENABLED = booleanPreferencesKey("chat_notif_enabled")
    private val KEY_SOS_ENABLED = booleanPreferencesKey("sos_notif_enabled")
    private val KEY_REQUEST_ENABLED = booleanPreferencesKey("request_notif_enabled")
    private val KEY_SYSTEM_ENABLED = booleanPreferencesKey("system_notif_enabled")
    private val KEY_PEER_NEARBY_ENABLED = booleanPreferencesKey("peer_nearby_enabled")

    val isDndEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_DND_ENABLED] ?: false }
    val isChatEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_CHAT_ENABLED] ?: true }
    val isSosEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_SOS_ENABLED] ?: true }
    val isRequestEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_REQUEST_ENABLED] ?: true }
    val isSystemEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_SYSTEM_ENABLED] ?: false }
    val isPeerNearbyEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_PEER_NEARBY_ENABLED] ?: false }

    fun isSosAllowedDuringDnd(): Boolean = true

    suspend fun setDndEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DND_ENABLED] = enabled }
    }

    suspend fun setChatEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_CHAT_ENABLED] = enabled }
    }

    suspend fun setSosEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SOS_ENABLED] = enabled }
    }

    suspend fun setRequestEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_REQUEST_ENABLED] = enabled }
    }

    suspend fun setSystemEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SYSTEM_ENABLED] = enabled }
    }

    suspend fun setPeerNearbyEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PEER_NEARBY_ENABLED] = enabled }
    }
}
