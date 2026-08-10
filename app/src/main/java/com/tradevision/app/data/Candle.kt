package com.tradevision.app.data

import org.json.JSONArray
import org.json.JSONObject

/** Candle as returned by GET /history (open_time is epoch seconds as Double). */
data class Candle(
    val symbol: String,
    val timeframe: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    val openTime: Long,
    val isClosed: Boolean,
) {
    companion object {
        fun fromJson(j: JSONObject): Candle = Candle(
            symbol = j.optString("symbol"),
            timeframe = j.optString("timeframe"),
            open = j.optDouble("open"),
            high = j.optDouble("high"),
            low = j.optDouble("low"),
            close = j.optDouble("close"),
            volume = j.optDouble("volume"),
            openTime = (j.optDouble("open_time") * 1000).toLong(), // s → ms
            isClosed = j.optBoolean("is_closed"),
        )

        fun fromJsonArray(arr: JSONArray): List<Candle> {
            val out = ArrayList<Candle>(arr.length())
            for (i in 0 until arr.length()) out.add(fromJson(arr.getJSONObject(i)))
            return out
        }
    }
}

/** Full /history response. */
data class HistoryResponse(
    val candles: List<Candle>,
    val count: Int,
    val source: String, // "db" | "broker"
)