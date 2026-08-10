package com.tradevision.app.data

/** Settings persisted in DataStore — backend base URL + API key (user-entered, stored locally only). */
data class AppSettings(
    val baseUrl: String = "http://10.0.2.2:8000",
    val apiKey: String = "",
) {
    val hasValidConfig: Boolean get() = baseUrl.isNotBlank() && apiKey.isNotBlank()
}