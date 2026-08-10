package com.tradevision.app.data

/** Settings persisted in DataStore — backend base URL + API key.
 * Values are user-entered and stored on-device only (never sent to GitHub). */
data class AppSettings(
    var baseUrl: String = "http://10.0.2.2:8000",
    var apiKey: String = "",
) {
    val hasValidConfig: Boolean get() = baseUrl.isNotBlank() && apiKey.isNotBlank()
}