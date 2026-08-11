package com.tradevision.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** DataStore-backed settings repository. Values stay on-device (never sent to GitHub). */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val API_KEY = stringPreferencesKey("api_key")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            baseUrl = prefs[Keys.BASE_URL] ?: "http://10.0.2.2:8000",
            apiKey = prefs[Keys.API_KEY] ?: "",
        )
    }

    /** First emission, blocking (used at startup for initial ViewModel). */
    fun blockingSettings(): AppSettings = runBlocking { settings.first() }

    suspend fun save(baseUrl: String, apiKey: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BASE_URL] = baseUrl.trim().trimEnd('/')
            prefs[Keys.API_KEY] = apiKey.trim()
        }
    }
}
