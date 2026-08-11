package com.tradevision.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * DataStore-backed settings repository. Values stay on-device (never sent to GitHub).
 *
 * Exposes a reactive [settings] StateFlow so ViewModels can observe live changes
 * (base URL / API key) without holding a stale snapshot in their constructor.
 */
class SettingsRepository(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val API_KEY = stringPreferencesKey("api_key")
    }

    private val _settings: StateFlow<AppSettings> = context.dataStore.data
        .map { prefs ->
            AppSettings(
                baseUrl = prefs[Keys.BASE_URL] ?: "",
                apiKey = prefs[Keys.API_KEY] ?: "",
            )
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = AppSettings(baseUrl = "", apiKey = ""),
        )

    /** Live settings — ViewModels collect this to react to changes. */
    val settings: StateFlow<AppSettings> = _settings

    /** First emission, blocking (used at startup for initial ViewModel). */
    fun blockingSettings(): AppSettings = runBlocking { _settings.first() }

    /** Persist new settings; DataStore flow emits the new value to all collectors. */
    suspend fun save(baseUrl: String, apiKey: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BASE_URL] = baseUrl.trim().trimEnd('/')
            prefs[Keys.API_KEY] = apiKey.trim()
        }
    }
}