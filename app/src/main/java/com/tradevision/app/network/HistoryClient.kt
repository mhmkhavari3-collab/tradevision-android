package com.tradevision.app.network

import com.tradevision.app.data.Candle
import com.tradevision.app.data.HistoryResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** REST client for GET /history — fetches candles, parses, handles HTTP errors. */
class HistoryClient(
    private val baseUrlProvider: () -> String,
    private val apiKeyProvider: () -> String,
    private val ok: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {

    /**
     * @param before epoch seconds (exclusive bound) — pass now/0 for latest
     */
    suspend fun history(
        symbol: String,
        timeframe: String,
        limit: Int = 50,
        before: Long = Long.MAX_VALUE,
    ): HistoryResponse = withContext(Dispatchers.IO) {
        val base = baseUrlProvider().trimEnd('/')
        val url = StringBuilder("$base/history?symbol=$symbol&timeframe=$timeframe&limit=$limit")
        if (before != Long.MAX_VALUE) url.append("&before=$before")

        val req = Request.Builder()
            .url(url.toString())
            .header("X-API-Key", apiKeyProvider())
            .build()

        ok.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw HistoryApiException(resp.code, body.take(200))
            }
            val j = JSONObject(body)
            val candles = Candle.fromJsonArray(j.optJSONArray("candles") ?: org.json.JSONArray())
            HistoryResponse(
                candles = candles,
                count = j.optInt("count"),
                source = j.optString("source"),
            )
        }
    }
}

class HistoryApiException(val code: Int, val detail: String) :
    RuntimeException("HTTP $code: $detail")