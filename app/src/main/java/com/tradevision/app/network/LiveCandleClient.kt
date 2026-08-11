package com.tradevision.app.network

import com.tradevision.app.data.Candle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

/**
 * Live candle stream: connects to /ws, subscribes symbol+tf, parses candle updates,
 * and reconnects with bounded exponential backoff ONLY after the connection actually breaks.
 *
 * Fix (Phase 3): tryConnect now blocks until the socket is closed/failed, so the
 * backoff loop never spawns duplicate sockets while a connection is alive.
 */
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
    @Volatile private var subscribedSymbol: String? = null
    @Volatile private var subscribedTf: String? = null
    @Volatile private var attempt = 0
    @Volatile private var connected = false
    private val generation = AtomicLong(0)   // cancels stale reconnect loops
    private val intentionallyClosed = AtomicBoolean(false)

    private val maxBackoffMs = 30_000L
    private val baseDelayMs = 1_000L

    /** Connect and keep the subscription alive. Cancels any previous connection first. */
    fun connect(symbol: String, tf: String) {
        if (subscribedSymbol == symbol && subscribedTf == tf && connected) {
            return  // already connected to the same symbol/tf — no duplicate
        }
        disconnect()
        subscribedSymbol = symbol
        subscribedTf = tf
        intentionallyClosed.set(false)
        onStatus(WsStatus.CONNECTING)
        val gen = generation.incrementAndGet()
        connectJob = scope.launch { connectWithBackoff(symbol, tf, gen) }
    }

    private suspend fun connectWithBackoff(symbol: String, tf: String, gen: Long) {
        attempt = 0
        while (scope.isActive && gen == generation.get()) {
            val ok = tryConnect(symbol, tf, gen)
            if (!ok) return  // superseded by a newer connect/disconnect
            if (gen != generation.get()) return
            // connection died (closed/failed) → schedule reconnect
            attempt++
            val delayMs = (baseDelayMs * (1L shl attempt.coerceAtMost(5))).coerceAtMost(maxBackoffMs)
            onStatus(WsStatus.RECONNECTING)
            delay(delayMs)
        }
    }

    /** Opens a socket and BLOCKS until it is closed/failed.
     *  Returns false if this attempt was superseded (new connect or disconnect happened). */
    private suspend fun tryConnect(symbol: String, tf: String, gen: Long): Boolean = withContext(Dispatchers.IO) {
        val base = baseUrlProvider().trimEnd('/')
        val wsUrl = base.replaceFirst("http", "ws") + "/ws"
        val req = Request.Builder()
            .url(wsUrl)
            .header("X-API-Key", apiKeyProvider())
            .build()

        val closed = kotlinx.coroutines.CompletableDeferred<Boolean>()

        val listener = object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                if (gen != generation.get()) { ws.close(1000, "superseded"); return }
                connected = true
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
                connected = false
                if (gen == generation.get() && !intentionallyClosed.get()) {
                    onStatus(WsStatus.RECONNECTING)
                }
                closed.complete(true)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                connected = false
                if (gen == generation.get() && !intentionallyClosed.get()) {
                    onStatus(WsStatus.RECONNECTING)
                }
                closed.complete(true)
            }
        }

        val sock = client.newWebSocket(req, listener)
        if (gen != generation.get()) {
            sock.close(1000, "superseded")
            return@withContext false
        }
        this@LiveCandleClient.ws = sock
        // BLOCK here until the socket is actually closed/failed — no duplicate sockets
        closed.await()
        if (gen == generation.get() && this@LiveCandleClient.ws === sock) {
            this@LiveCandleClient.ws = null
        }
        true
    }

    /** Cleanly close everything; the backoff loop sees generation bump and stops. */
    fun disconnect() {
        generation.incrementAndGet()
        intentionallyClosed.set(true)
        connected = false
        connectJob?.cancel()
        connectJob = null
        ws?.close(1000, "bye")
        ws = null
        onStatus(WsStatus.CLOSED)
    }
}