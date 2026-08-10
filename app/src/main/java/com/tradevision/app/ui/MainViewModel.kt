package com.tradevision.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tradevision.app.data.AppSettings
import com.tradevision.app.data.HealthStatus
import com.tradevision.app.data.SettingsRepository
import com.tradevision.app.network.RestClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface HealthUiState {
    data object Idle : HealthUiState
    data class Loading(val settings: AppSettings) : HealthUiState
    data class Ok(val health: HealthStatus, val ts: Long) : HealthUiState
    data class Error(val message: String, val ts: Long) : HealthUiState
}

class MainViewModel(
    private val settingsRepository: SettingsRepository,
    private val restClient: RestClient,
) : ViewModel() {

    private val _ui = MutableStateFlow<HealthUiState>(HealthUiState.Idle)
    val ui: StateFlow<HealthUiState> = _ui.asStateFlow()

    private var pollJob: Job? = null

    init {
        viewModelScope.launch {
            val s = settingsRepository.settings.first()
            _ui.value = HealthUiState.Loading(s)
            startPolling(s)
        }
    }

    fun onSettingsChanged(baseUrl: String, apiKey: String) {
        viewModelScope.launch {
            settingsRepository.save(baseUrl, apiKey)
            val s = AppSettings(baseUrl, apiKey)
            _ui.value = HealthUiState.Loading(s)
            startPolling(s)
        }
    }

    private fun startPolling(settings: AppSettings) {
        pollJob?.cancel()
        if (!settings.hasValidConfig) {
            _ui.value = HealthUiState.Error("Base URL and API key required", System.currentTimeMillis())
            return
        }
        pollJob = viewModelScope.launch {
            while (true) {
                try {
                    val h = restClient.health()
                    _ui.value = HealthUiState.Ok(h, System.currentTimeMillis())
                } catch (e: Exception) {
                    _ui.value = HealthUiState.Error(
                        e.message ?: "Unknown error",
                        System.currentTimeMillis(),
                    )
                }
                delay(5_000)
            }
        }
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }
}