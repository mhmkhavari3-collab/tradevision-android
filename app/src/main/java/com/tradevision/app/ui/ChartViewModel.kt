package com.tradevision.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tradevision.app.data.AppSettings
import com.tradevision.app.data.Candle
import com.tradevision.app.data.Instrument
import com.tradevision.app.data.Timeframe
import com.tradevision.app.network.HistoryClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface ChartUiState {
    data object Idle : ChartUiState
    data object Loading : ChartUiState
    data class Success(val candles: List<Candle>) : ChartUiState
    data class Error(val message: String) : ChartUiState
}

class ChartViewModel(
    private val settings: AppSettings,
) : ViewModel() {

    private val historyClient = HistoryClient(
        baseUrlProvider = { settings.baseUrl },
        apiKeyProvider = { settings.apiKey },
    )

    private val _uiState = MutableStateFlow<ChartUiState>(ChartUiState.Idle)
    val uiState: StateFlow<ChartUiState> = _uiState

    private val _symbol = MutableStateFlow(Instrument.BTCUSDT.symbol)
    val symbol: StateFlow<String> = _symbol

    private val _timeframe = MutableStateFlow(Timeframe.M1.apiValue)
    val timeframe: StateFlow<String> = _timeframe

    init {
        load()
    }

    fun selectSymbol(sym: String) {
        if (_symbol.value != sym) {
            _symbol.value = sym
            load()
        }
    }

    fun selectTimeframe(tf: String) {
        if (_timeframe.value != tf) {
            _timeframe.value = tf
            load()
        }
    }

    /** Fetches the latest [limit] candles for the selected symbol/timeframe. */
    fun load(limit: Int = 50) {
        viewModelScope.launch {
            _uiState.value = ChartUiState.Loading
            try {
                val resp = historyClient.history(
                    symbol = _symbol.value,
                    timeframe = _timeframe.value,
                    limit = limit,
                )
                if (resp.candles.isEmpty()) {
                    _uiState.value = ChartUiState.Error("No candles returned for ${_symbol.value} ${_timeframe.value}")
                } else {
                    _uiState.value = ChartUiState.Success(resp.candles)
                }
            } catch (e: Exception) {
                _uiState.value = ChartUiState.Error(e.message ?: "Network error")
            }
        }
    }
}