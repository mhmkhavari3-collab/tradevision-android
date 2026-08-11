package com.tradevision.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tradevision.app.data.AppSettings
import com.tradevision.app.data.Candle
import com.tradevision.app.data.Instrument
import com.tradevision.app.data.Timeframe
import com.tradevision.app.network.HistoryClient
import com.tradevision.app.network.LiveCandleClient
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

    // Live WS client — reconnects with backoff, feeds candle updates into the chart
    private val _wsStatus = MutableStateFlow(LiveCandleClient.WsStatus.CONNECTING)
    val wsStatus: StateFlow<LiveCandleClient.WsStatus> = _wsStatus

    private val liveClient = LiveCandleClient(
        baseUrlProvider = { settings.baseUrl },
        apiKeyProvider = { settings.apiKey },
        scope = viewModelScope,
        onCandle = { updateLiveCandle(it) },
        onStatus = { _wsStatus.value = it },
    )

    private val _uiState = MutableStateFlow<ChartUiState>(ChartUiState.Idle)
    val uiState: StateFlow<ChartUiState> = _uiState

    @Volatile private var liveCandles: MutableList<Candle>? = null

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
            refreshSymbolTf()
        }
    }

    fun selectTimeframe(tf: String) {
        if (_timeframe.value != tf) {
            _timeframe.value = tf
            refreshSymbolTf()
        }
    }

    private fun refreshSymbolTf() {
        load()  // history load also (re)connects WS
    }

    /** Fetches the latest [limit] candles for the selected symbol/timeframe, then starts WS. */
    fun load(limit: Int = 50) {
        viewModelScope.launch {
            _uiState.value = ChartUiState.Loading
            // (re)connect WS first so live updates flow once history arrives
            liveClient.connect(_symbol.value, _timeframe.value)
            try {
                val resp = historyClient.history(
                    symbol = _symbol.value,
                    timeframe = _timeframe.value,
                    limit = limit,
                )
                if (resp.candles.isEmpty()) {
                    _uiState.value = ChartUiState.Error("No candles returned for ${_symbol.value} ${_timeframe.value}")
                } else {
                    val list = resp.candles.toMutableList()
                    liveCandles = list
                    _uiState.value = ChartUiState.Success(list)
                }
            } catch (e: Exception) {
                _uiState.value = ChartUiState.Error(e.message ?: "Network error")
            }
        }
    }

    /** Merge an incoming live candle into the chart list:
     *  same openTime → replace (update in place, no reload)
     *  new openTime  → append at the end (new candle)
     *  keeps max 200 candles in memory. */
    private fun updateLiveCandle(c: Candle) {
        val list = liveCandles ?: return
        synchronized(list) {
            val idx = list.indexOfLast { it.openTime == c.openTime }
            if (idx >= 0) {
                list[idx] = c
            } else {
                list.add(c)
                if (list.size > 200) list.removeAt(0)
            }
        }
        // emit updated list (state flows re-render chart)
        _uiState.value = ChartUiState.Success(list.toList())
    }

    override fun onCleared() {
        liveClient.disconnect()
        super.onCleared()
    }
}