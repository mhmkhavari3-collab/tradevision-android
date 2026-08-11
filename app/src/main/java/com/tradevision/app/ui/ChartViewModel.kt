package com.tradevision.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tradevision.app.data.AppSettings
import com.tradevision.app.data.Candle
import com.tradevision.app.data.ChartPoint
import com.tradevision.app.data.Drawing
import com.tradevision.app.data.DrawingTool
import com.tradevision.app.data.IndicatorConfig
import com.tradevision.app.data.Instrument
import com.tradevision.app.data.Timeframe
import com.tradevision.app.data.VolumeProfileResult
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

    // ---- Phase 4: drawings / indicators / live-follow / tools ----
    private val _drawings = MutableStateFlow<List<Drawing>>(emptyList())
    val drawings: StateFlow<List<Drawing>> = _drawings

    private val _indicators = MutableStateFlow<List<IndicatorConfig>>(emptyList())
    val indicators: StateFlow<List<IndicatorConfig>> = _indicators

    private val _liveFollow = MutableStateFlow(true)
    val liveFollow: StateFlow<Boolean> = _liveFollow

    private val _selectedTool = MutableStateFlow<DrawingTool?>(null)
    val selectedTool: StateFlow<DrawingTool?> = _selectedTool

    // per symbol+tf drawing cache (local persistence in-memory for this phase)
    private val drawingStore = mutableMapOf<String, MutableList<Drawing>>()

    init {
        load()
    }

    fun selectSymbol(sym: String) {
        if (_symbol.value != sym) {
            _symbol.value = sym
            loadDrawings()
            load()
        }
    }

    fun selectTimeframe(tf: String) {
        if (_timeframe.value != tf) {
            _timeframe.value = tf
            loadDrawings()
            load()
        }
    }

    private fun storeKey() = "${_symbol.value}/${_timeframe.value}"

    private fun loadDrawings() {
        _drawings.value = drawingStore[storeKey()]?.toList() ?: emptyList()
    }

    fun addDrawing(d: Drawing) {
        val key = storeKey()
        val list = drawingStore.getOrPut(key) { mutableListOf() }
        list.add(d)
        _drawings.value = list.toList()
    }

    fun undoDrawing() {
        val key = storeKey()
        val list = drawingStore[key] ?: return
        if (list.isNotEmpty()) list.removeAt(list.size - 1)
        _drawings.value = list.toList()
    }

    fun clearDrawings() {
        val key = storeKey()
        drawingStore[key]?.clear()
        _drawings.value = emptyList()
    }

    fun selectTool(t: DrawingTool?) { _selectedTool.value = t }

    fun setLiveFollow(on: Boolean) { _liveFollow.value = on }

    fun addIndicator(type: com.tradevision.app.data.IndicatorType, period: Int = 14) {
        val cfg = IndicatorConfig(type = type, period = period)
        _indicators.value = _indicators.value + cfg
    }

    fun removeIndicator(id: String) {
        _indicators.value = _indicators.value.filter { it.id != id }
    }

    /** Compute Fixed-Range Volume Profile over candle indices [a, b] — real data only. */
    fun computeVolumeProfile(a: Int, b: Int) {
        val list = liveCandles ?: return
        if (a !in list.indices || b !in list.indices || a > b) return
        val range = list.subList(a, b + 1)
        if (range.isEmpty()) return

        val bins = 40
        val hi = range.maxOf { it.high }
        val lo = range.minOf { it.low }
        if (hi <= lo) return
        val binW = (hi - lo) / bins
        val vols = DoubleArray(bins)
        for (c in range) {
            val mid = (c.high + c.low) / 2.0
            val idx = ((mid - lo) / binW).toInt().coerceIn(0, bins - 1)
            vols[idx] += c.volume
        }
        val maxVol = vols.maxOrNull() ?: 0.0
        if (maxVol <= 0.0) return
        val pocIdx = vols.indexOfFirst { it == maxVol }
        val pocPrice = lo + binW * (pocIdx + 0.5)

        // value area = prices around POC containing 70% of volume
        var total = vols.sum()
        var acc = vols[pocIdx]
        var vaHighIdx = pocIdx
        var vaLowIdx = pocIdx
        var up = pocIdx + 1
        var down = pocIdx - 1
        while (acc < total * 0.7 && (up < bins || down >= 0)) {
            val upV = if (up < bins) vols[up] else -1.0
            val downV = if (down >= 0) vols[down] else -1.0
            if (upV >= downV) { acc += upV; vaHighIdx = up; up++ }
            else { acc += downV; vaLowIdx = down; down-- }
        }
        val vah = lo + binW * (vaHighIdx + 0.5)
        val valLow = lo + binW * (vaLowIdx + 0.5)

        // HVN/LVN: bins above/below average volume
        val avg = total / bins
        val hvnIdx = vols.withIndex().filter { it.value > avg * 1.5 }.maxByOrNull { it.value }?.index ?: pocIdx
        val lvnIdx = vols.withIndex().filter { it.value > 0 && it.value < avg * 0.5 }.minByOrNull { it.value }?.index ?: pocIdx

        val result = VolumeProfileResult(
            pocPrice = pocPrice,
            vah = vah,
            valLow = valLow,
            hvn = lo + binW * (hvnIdx + 0.5),
            lvn = lo + binW * (lvnIdx + 0.5),
            bins = List(bins) { i -> lo + binW * (i + 0.5) to vols[i] },
        )
        _lastVolumeProfile.value = result
    }

    private val _lastVolumeProfile = MutableStateFlow<VolumeProfileResult?>(null)
    val lastVolumeProfile: StateFlow<VolumeProfileResult?> = _lastVolumeProfile

    fun load(limit: Int = 100) {
        viewModelScope.launch {
            _uiState.value = ChartUiState.Loading
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

    /** Merge live candle: same openTime → replace, new → append; max 200. */
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
        _uiState.value = ChartUiState.Success(list.toList())
    }

    override fun onCleared() {
        liveClient.disconnect()
        super.onCleared()
    }
}
