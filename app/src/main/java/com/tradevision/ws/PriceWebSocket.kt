package com.tradevision.ws

import android.util.Log
import okhttp3.*
import java.util.concurrent.TimeUnit

class PriceWebSocket(private val listener: PriceUpdateListener) {

    companion object {
        private const val TAG = "PriceWebSocket"
        private const val WS_URL = "wss://ws.okx.com/api/v5/public/ws"
    }

    interface PriceUpdateListener {
        fun onPriceUpdate(symbol: String, price: Double)
        fun onConnectionStateChange(connected: Boolean)
    }

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    fun start() {
        val request = Request.Builder().url(WS_URL).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                listener.onConnectionStateChange(true)
                val msg = "{\"op\":\"subscribe\",\"args\":[{\"channel\":\"tickers\",\"instId\":\"XAU-USDT-SWAP\"}]}"
                webSocket.send(msg)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Message: $text")
                try {
                    val json = com.google.gson.JsonParser.parseString(text).asJsonObject
                    val data = json.getAsJsonArray("data")
                    if (data != null && data.size() > 0) {
                        val item = data[0].asJsonObject
                        val symbol = item.get("instId")?.asString ?: ""
                        val price = item.get("last")?.asString?.toDoubleOrNull() ?: 0.0
                        listener.onPriceUpdate(symbol, price)
                    }
                } catch (e: Exception) { Log.e(TAG, "Parse error", e) }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code")
                listener.onConnectionStateChange(false)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket error", t)
                listener.onConnectionStateChange(false)
            }
        })
    }

    fun stop() {
        webSocket?.close(1000, "Stopping")
        webSocket = null
    }
}
