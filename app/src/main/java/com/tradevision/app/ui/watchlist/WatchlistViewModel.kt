package com.tradevision.app.ui.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tradevision.app.data.AppSettings
import com.tradevision.app.data.Candle
import com.tradevision.app.data.Instrument
import com.tradevision.app.data.MarketQuote
import com.tradevision.app.data.SettingsRepository
import com.tradevision.app.network.HistoryClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class WatchSort { SYMBOL, PRICE, CHANGE_PCT, GAINERS, LOSERS }

/**
 * Watchlist VM: polls /history (1m, limit 1) per symbol to derive live quotes.
 * Daily change uses the first candle of the current day as dayOpen — real data, never fabricated.
 *
 * Settings are observed reactively via [SettingsRepository.settings]; when the base URL or
 * API key changes, the polling restarts with the new values (no stale snapshot).
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

    @Volatile private var pollJob: Job? = null

    init {
        viewModelScope.launch {
            repo.settings.collect { s ->
                applySettings(s)
            }
        }
    }

    /** (Re)creates the polling client when baseUrl/apiKey actually change. */
    private fun applySettings(s: AppSettings) {
        if (_baseUrl == s.baseUrl && _apiKey == s.apiKey) return
        _baseUrl = s.baseUrl
        _apiKey = s.apiKey
        historyClient = HistoryClient(
            baseUrlProvider = { _baseUrl },
            apiKeyProvider = { _apiKey },
        )
        startPolling()
    }

    @Volatile private var _baseUrl: String = ""
    @Volatile private var _apiKey: String = ""
    @Volatile private var historyClient: HistoryClient = HistoryClient({ "" }, { "" })

    fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                refreshAll()
                delay(5_000)
            }
        }
    }

    fun setSort(s: WatchSort) { _sort.value = s }

    /** Polls each instrument in parallel; failures are skipped silently (next cycle retries). */
    private suspend fun refreshAll() {
        val results = Instrument.entries.map { ins ->
            asyncQuote(ins)
        }
        val merged = mutableMapOf<String, MarketQuote>()
        for (r in results) if (r != null) merged[r.symbol] = r
        if (merged.isNotEmpty()) _quotes.value = merged
    }

    private suspend fun asyncQuote(ins: Instrument): MarketQuote? {
        return try {
            val resp = historyClient.history(ins.symbol, "1m", limit = 50)
            val candles = resp.candles
            if (candles.isEmpty()) return null
            val last = candles.last()

            // day open: first candle whose openTime is within today (UTC)
            val nowMs = System.currentTimeMillis()
            val startOfDay = nowMs - (nowMs % 86_400_000L)
            val dayCandles = candles.filter { it.openTime >= startOfDay - 3_600_000L } // allow tz slack
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
        pollJob?.cancel()
        super.onCleared()
    }
}