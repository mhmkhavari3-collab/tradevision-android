package com.tradevision.app.data

/** Instrument presets (broker routing is decided by the backend). */
enum class Instrument(val symbol: String, val label: String, val broker: String) {
    BTCUSDT("BTCUSDT", "BTC/USDT", "Binance"),
    ETHUSDT("ETHUSDT", "ETH/USDT", "Binance"),
    SOLUSDT("SOLUSDT", "SOL/USDT", "Binance"),
    XAUUSD("XAUUSD", "XAU/USD", "OANDA"),
    EURUSD("EURUSD", "EUR/USD", "OANDA"),
    GBPUSD("GBPUSD", "GBP/USD", "OANDA"),
}

/** Timeframes supported by the backend. */
enum class Timeframe(val apiValue: String, val label: String, val seconds: Long) {
    M1("1m", "1m", 60),
    M5("5m", "5m", 300),
    M15("15m", "15m", 900),
    H1("1h", "1h", 3600),
    H4("4h", "4h", 14400),
    D1("1d", "1d", 86400),
}