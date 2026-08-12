package com.tradevision.app.data

/** Asset class / category for the Watchlist grouping. */
enum class Category(val label: String) {
    CRYPTO("CRYPTO"),
    FOREX("FOREX"),
}

/** Instrument presets (broker routing is decided by the backend). */
enum class Instrument(
    val symbol: String,
    val label: String,
    val broker: String,
    val category: Category,
    val priceDecimals: Int,
) {
    BTCUSDT("BTCUSDT", "BTC/USDT", "Binance", Category.CRYPTO, 2),
    ETHUSDT("ETHUSDT", "ETH/USDT", "Binance", Category.CRYPTO, 2),
    SOLUSDT("SOLUSDT", "SOL/USDT", "Binance", Category.CRYPTO, 2),

    XAUUSD("XAUUSD", "XAU/USD", "OANDA", Category.FOREX, 2),
    EURUSD("EURUSD", "EUR/USD", "OANDA", Category.FOREX, 5),
    GBPUSD("GBPUSD", "GBP/USD", "OANDA", Category.FOREX, 5),
    USDJPY("USDJPY", "USD/JPY", "OANDA", Category.FOREX, 3),
    USDCHF("USDCHF", "USD/CHF", "OANDA", Category.FOREX, 5),
    AUDUSD("AUDUSD", "AUD/USD", "OANDA", Category.FOREX, 5),
    USDCAD("USDCAD", "USD/CAD", "OANDA", Category.FOREX, 5),
    NZDUSD("NZDUSD", "NZD/USD", "OANDA", Category.FOREX, 5),
    ;

    companion object {
        fun fromSymbol(s: String): Instrument? = entries.firstOrNull { it.symbol == s }

        /** Format a price with the instrument's precision (display only — raw value never changes). */
        fun formatPrice(symbol: String, value: Double): String {
            val decimals = fromSymbol(symbol)?.priceDecimals ?: 2
            return String.format(java.util.Locale.US, "%,.${decimals}f", value)
        }
    }
}

/** Timeframes supported by the backend. */
enum class Timeframe(val apiValue: String, val label: String, val seconds: Long) {
    M1("1m", "1m", 60),
    M5("5m", "5m", 300),
    M15("15m", "15m", 900),
    H1("1h", "1h", 3600),
    H4("4h", "4h", 14400),
    D1("1d", "1d", 86400),
    ;

    companion object {
        fun fromApi(v: String): Timeframe = entries.firstOrNull { it.apiValue == v } ?: M1
    }
}

/** Live market quote for the Watchlist (computed from candles — never fabricated). */
data class MarketQuote(
    val symbol: String,
    val last: Double,
    val change: Double,        // last - dayOpen
    val changePercent: Double, // (last - dayOpen) / dayOpen * 100
    val dayOpen: Double,
    val high: Double,
    val low: Double,
    val volume: Double,
    val updatedAt: Long,
) {
    val isPositive: Boolean get() = change >= 0.0
}

/** One point of a drawing in candle (x) / price (y) space. */
data class ChartPoint(
    val candleIndex: Float, // fractional candle index (int = candle boundary)
    val price: Double,
)

enum class DrawingTool {
    CURSOR, TREND, RAY, EXTENDED_LINE, HORIZONTAL_LINE, HORIZONTAL_RAY, VERTICAL_LINE, CROSS_LINE,
    CHANNEL, FIB_RETRACEMENT, FIB_EXTENSION,
    RECTANGLE, ROTATED_RECTANGLE, CIRCLE, ELLIPSE, TRIANGLE, POLYLINE, PATH,
    LONG_POSITION, SHORT_POSITION,
    PRICE_RANGE, DATE_RANGE, DATE_PRICE_RANGE,
    TEXT, ARROW, PRICE_LABEL,
    VOLUME_PROFILE, FIB_CIRCLE, MEASURE, POSITION, ANNOTATION, INDICATORS, VOLUME,
}

/** A persisted drawing (per symbol+timeframe). */
data class Drawing(
    val id: String = java.util.UUID.randomUUID().toString(),
    val tool: DrawingTool,
    val points: List<ChartPoint>,
    val color: Int = 0xFFFFFFFF.toInt(),
    val text: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Volume-profile analysis result — computed from real candle data only. */
data class VolumeProfileResult(
    val pocPrice: Double,          // price with max volume
    val vah: Double,               // value area high
    val valLow: Double,            // value area low
    val hvn: Double,               // high volume node price
    val lvn: Double,               // low volume node price
    val bins: List<Pair<Double, Double>>, // (priceBin, volume) for histogram
)

/** Indicator types supported by the chart engine. */
enum class IndicatorType { EMA, SMA, RSI, VOLUME }

/** An enabled indicator on the chart. */
data class IndicatorConfig(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: IndicatorType,
    val period: Int = 14,
    val color: Int = 0xFF3D8BFF.toInt(),
)

/** A user alert (architecture only — triggering arrives with WS later). */
enum class AlertCondition { PRICE_ABOVE, PRICE_BELOW, CROSSING }

data class PriceAlert(
    val id: String = java.util.UUID.randomUUID().toString(),
    val symbol: String,
    val condition: AlertCondition,
    val price: Double,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)
