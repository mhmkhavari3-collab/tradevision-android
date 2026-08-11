package com.tradevision.app.ui.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tradevision.app.data.AppSettings
import com.tradevision.app.data.Candle
import com.tradevision.app.data.Instrument
import com.tradevision.app.data.MarketQuote
import com.tradevision.app.data.SettingsRepository
import com.tradevision.app.network.HistoryClient
import com.tradevision.app.network.LiveCandleClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

enum class WatchSort { SYMBOL, PRICE, CHANGE_PCT, GAINERS, LOSERS }

/**
 * Watchlist VM: uses WebSocket (LiveCandleClient) for tick-by-tick live quotes across all symbols.
 * Falls back to /history for initial load, then streams live updates.
 */
class WatchlistViewModel(
    private val repo: SettingsRepository,
) : ViewModel() {

    private val _quotes = MutableStateFlow<Map<String, MarketQuote>>(emptyMap())
    val quotes: StateFlow<Map<String, MarketQuote>> = _quotes

    private val _sort = MutableStateFlow(WatchSort.SYMBOL)
    val sort: StateFlow<WatchSort> = _sort

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    @Volatile private var historyClient: HistoryClient = HistoryClient({ "" }, { "" })
    @Volatile private var _baseUrl: String = ""
    @Volatile private var _apiKey: String = ""
    
    // LiveCandleClient for streaming quotes
    private val liveClients = mutableMapOf<String, LiveCandleClient>()
    private val quoteChannels = mutableMapOf<String, Channel<Candle>>()

    init {
        viewModelScope.launch {
            repo.settings.collect { s ->
                applySettings(s)
            }
        }
    }

    /** (Re)creates clients when baseUrl/apiKey actually change. */
    private fun applySettings(s: AppSettings) {
        if (_baseUrl == s.baseUrl && _apiKey == s.apiKey) return
        _baseUrl = s.baseUrl
        _apiKey = s.apiKey
        historyClient = HistoryClient(
            baseUrlProvider = { _baseUrl },
            apiKeyProvider = { _apiKey },
        )
        // Restart all live connections
        stopAllLive()
        if (_baseUrl.isNotBlank() && _apiKey.isNotBlank()) {
            loadInitialAndStartLive()
        }
    }

    /** Initial load from history, then start WebSocket streaming for all symbols. */
    private fun loadInitialAndStartLive() {
        viewModelScope.launch {
            // Parallel initial load from history
            val deferredResults = Instrument.entries.map { ins ->
                async { asyncQuoteFromHistory(ins) }
            }
            val results = awaitAll(*deferredResults.toTypedArray())
            val merged = mutableMapOf<String, MarketQuote>()
            for (r in results) if (r != null) merged[r.symbol] = r
            if (merged.isNotEmpty()) _quotes.value = merged
            
            // Start WebSocket for all symbols
            startAllLive()
        }
    }

    /** Load initial quote from /history (single candle). */
    private suspend fun asyncQuoteFromHistory(ins: Instrument): MarketQuote? {
        return try {
            val resp = historyClient.history(ins.symbol, "1m", limit = 50)
            val candles = resp.candles
            if (candles.isEmpty()) return null
            val last = candles.last()
            
            // day open: first candle whose openTime is within today (UTC)
            val nowMs = System.currentTimeMillis()
            val startOfDay = nowMs - (nowMs % 86_400_000L)
            val dayCandles = candles.filter { it.openTime >= startOfDay - 3_600_000L }
            val dayOpen = dayCandles.firstOrNull()?.open ?: candles.first().open

            MarketQuote(
                symbol = ins.symbol,
                last = last.close,
                change = last.close - dayOpen,
                changePercent = if (dayOpen != 0.0) (last.close - dayOpen) / dayOpen * 100.0 else 0.0,
                dayOpen = dayOpen,
                high = candles.maxOf { it.high },
                low = candles.minOf { it.low },
                volume = candles.sumOf { it.volume },
                updatedAt = System.currentTimeMillis(),
            )
        } catch (e: Exception) {
            _error.value = e.message
            null
        }
    }

    /** Start WebSocket streaming for all instruments. */
    private fun startAllLive() {
        for (ins in Instrument.entries) {
            startLiveForSymbol(ins.symbol)
        }
    }

    /** Start WebSocket for a single symbol. */
    private fun startLiveForSymbol(symbol: String) {
        val channel = Channel<Candle>(capacity = 100)
        quoteChannels[symbol] = channel
        
        val client = LiveCandleClient(
            baseUrlProvider = { _baseUrl },
            apiKeyProvider = { _apiKey },
            scope = viewModelScope,
            onCandle = { candle ->
                if (candle.symbol == symbol) {
                    channel.trySend(candle)
                }
            },
            onStatus = { status ->
                // Status changes handled per client if needed
            },
        )
        liveClients[symbol] = client
        client.connect(symbol, "1m")
        
        // Consume channel and update quotes
        viewModelScope.launch {
            for (candle in channel) {
                updateQuoteFromCandle(candle)
            }
        }
    }

    /** Update quote from live candle tick. */
    private fun updateQuoteFromCandle(candle: Candle) {
        val current = _quotes.value
        val dayOpen = current[candle.symbol]?.dayOpen ?: candle.open
        val newQuote = current[candle.symbol]?.copy(
            last = candle.close,
            change = candle.close - dayOpen,
            changePercent = if (dayOpen != 0.0) (candle.close - dayOpen) / dayOpen * 100.0 else 0.0,
            high = maxOf(candle.high, current[candle.symbol]?.high ?: 0.0),
            low = minOf(candle.low, current[candle.symbol]?.low ?: Double.MAX_VALUE),
            volume = (current[candle.symbol]?.volume ?: 0L) + candle.volume,
            updatedAt = System.currentTimeMillis(),
        ) ?: MarketQuote(
            symbol = candle.symbol,
            last = candle.close,
            change = 0.0,
            changePercent = 0.0,
            dayOpen = dayOpen,
            high = candle.high,
            low = candle.low,
            volume = candle.volume,
            updatedAt = System.currentTimeMillis(),
        )
        _quotes.value = current.toMutableMap().apply { put(candle.symbol, newQuote) }
    }

    /** Stop all live connections. */
    private fun stopAllLive() {
        for ((_, client) in liveClients) {
            client.disconnect()
        }
        liveClients.clear()
        for ((_, channel) in quoteChannels) {
            channel.close()
        }
        quoteChannels.clear()
    }

    fun setSort(s: WatchSort) { _sort.value = s }

    /** Sorted list for display. */
    fun sortedQuotes(): List<MarketQuote> {
        val q = _quotes.value.values.toList()
        return when (_sort.value) {
            WatchSort.SYMBOL -> q.sortedBy { it.symbol }
            WatchSort.PRICE -> q.sortedByDescending { it.last }
            WatchSort.CHANGE_PCT -> q.sortedByDescending { it.changePercent }
            WatchSort.GAINERS -> q.filter { it.isPositive }.sortedByDescending { it.changePercent }
            WatchSort.LOSERS -> q.filter { !it.isPositive }.sortedBy { it.changePercent }
        }
    }

    override fun onCleared() {
        stopAllLive()
        super.onCleared()
    }
}