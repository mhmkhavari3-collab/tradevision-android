package com.tradevision.app.network

import com.tradevision.app.data.Candle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Live candle stream: connects to /ws, subscribes symbol+tf, parses candle updates, reconnects with backoff. */
class LiveCandleClient(
    private val baseUrlProvider: () -> String,
    private val apiKeyProvider: () -> String,
    private val scope: CoroutineScope,
    private val onCandle: (Candle) -> Unit,
    private val onStatus: (WsStatus) -> Unit = {},
) {
    enum class WsStatus { CONNECTING, CONNECTED, RECONNECTING, CLOSED }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // infinite for WS
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    @Volatile private var ws: WebSocket? = null
    private var connectJob: Job? = null
    private var subscribedSymbol: String? = null
    private var subscribedTf: String? = null

    fun connect(symbol: String, tf: String) {
        disconnect()
        subscribedSymbol = symbol
        subscribedTf = tf
        onStatus(WsStatus.CONNECTING)
        connectJob = scope.launch { connectWithBackoff(symbol, tf) }
    }

    private suspend fun connectWithBackoff(symbol: String, tf: String) {
        var attempt = 0
        while (scope.isActive) {
            val ok = tryConnect(symbol, tf)
            if (!ok) return  // closed intentionally
            attempt++
            val delayMs = (1000L * 2f.pow(attempt.coerceAtMost(5))).toLong()
            onStatus(WsStatus.RECONNECTING)
            delay(delayMs)
        }
    }

    private suspend fun tryConnect(symbol: String, tf: String): Boolean {
        return withContext(Dispatchers.IO) {
            val base = baseUrlProvider().trimEnd('/')
            val wsUrl = base.replaceFirst("http", "ws") + "/ws"
            val req = Request.Builder()
                .url(wsUrl)
                .header("X-API-Key", apiKeyProvider())
                .build()
            val listener = object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) {
                    onStatus(WsStatus.CONNECTED)
                    attempt = 0
                    val sub = JSONObject()
                        .put("action", "subscribe")
                        .put("symbol", symbol)
                        .put("timeframe", tf)
                    ws.send(sub.toString())
                }

                override fun onMessage(ws: WebSocket, text: String) {
                    val msg = try { JSONObject(text) } catch (e: Exception) { return }
                    when {
                        msg.optString("status") == "subscribed" -> Unit
                        msg.has("symbol") && msg.has("open_time") -> {
                            try {
                                val c = Candle.fromJson(msg)
                                onCandle(c)
                            } catch (e: Exception) { /* malformed candle */ }
                        }
                    }
                }

                override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                    // let connectWithBackoff retry
                }

                override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                    onStatus(WsStatus.CLOSED)
                }
            }
            val ws = client.newWebSocket(req, listener)
            this@LiveCandleClient.ws = ws
            // block until closed/failed? OkHttp ws runs on its own thread; return true to keep backoff loop alive
            true
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        connectJob = null
        ws?.close(1000, "bye")
        ws = null
        subscribedSymbol = null
        subscribedTf = null
        onStatus(WsStatus.CLOSED)
    }

    private fun Float.pow(n: Int): Float {
        var r = 1f
        repeat(n) { r *= this }
        return r
    }
}