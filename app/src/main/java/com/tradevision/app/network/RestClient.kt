package com.tradevision.app.network

import com.tradevision.app.data.HealthStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * REST client for the TradeVision backend.
 * Base URL is user-configurable (defaults to emulator loopback).
 */
class RestClient(
    private val baseUrlProvider: () -> String,
    private val apiKeyProvider: () -> String,
    private val ok: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) {

    suspend fun health(): HealthStatus = withContext(Dispatchers.IO) {
        val url = baseUrlProvider().trimEnd('/') + "/health"
        val req = Request.Builder()
            .url(url)
            .header("X-API-Key", apiKeyProvider())
            .build()
        ok.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw RuntimeException("HTTP ${resp.code}: $body")
            }
            val j = JSONObject(body)
            val brokers = j.optJSONObject("brokers") ?: JSONObject()
            val history = j.optJSONObject("history") ?: JSONObject()
            val storage = j.optJSONObject("storage") ?: JSONObject()
            HealthStatus(
                status = j.optString("status"),
                uptimeSeconds = j.optLong("uptime_seconds"),
                oanda = brokers.optString("oanda"),
                binance = brokers.optString("binance"),
                cacheHits = history.optLong("cache_hits"),
                brokerFetches = history.optLong("broker_fetches"),
                invalid = history.optLong("invalid"),
                candleCount = storage.optLong("closed_candle_count"),
                storageMb = storage.optDouble("candles_size_mb_est"),
                raw = body,
                sourceUrl = url,
            )
        }
    }
}