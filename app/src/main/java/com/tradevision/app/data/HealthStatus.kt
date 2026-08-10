package com.tradevision.app.data

/** Health payload from GET /health (view of live backend state). */
data class HealthStatus(
    val status: String,
    val uptimeSeconds: Long,
    val oanda: String,
    val binance: String,
    val cacheHits: Long,
    val brokerFetches: Long,
    val invalid: Long,
    val candleCount: Long,
    val storageMb: Double,
    val raw: String,
    val sourceUrl: String,
) {
    val allConnected: Boolean get() = oanda == "connected" && binance == "connected"
}