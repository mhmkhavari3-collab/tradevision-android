package com.tradevision.ws
import android.util.Log
import okhttp3.*
import java.util.concurrent.TimeUnit
class PriceWebSocket(private val listener: PriceUpdateListener) {
    interface PriceUpdateListener { fun onPriceUpdate(symbol: String, price: Double); fun onConnectionStateChange(connected: Boolean) }
    private var ws: WebSocket? = null
    private val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).connectTimeout(10, TimeUnit.SECONDS).build()
    fun start() { try { ws = client.newWebSocket(Request.Builder().url("wss://ws.okx.com:8443/api/v5/public/ws").build(), object : WebSocketListener() {
        override fun onOpen(w: WebSocket, r: Response) { listener.onConnectionStateChange(true); try { w.send("{\"op\":\"subscribe\",\"args\":[{\"channel\":\"tickers\",\"instId\":\"XAU-USDT-SWAP\"}]}") } catch (e: Exception) {} }
        override fun onMessage(w: WebSocket, text: String) { try { val d = com.google.gson.JsonParser.parseString(text).asJsonObject.getAsJsonArray("data"); if (d != null && d.size() > 0) { val i = d[0].asJsonObject; val p = i.get("last")?.asString?.toDoubleOrNull() ?: 0.0; if (p > 0) listener.onPriceUpdate(i.get("instId")?.asString ?: "", p) } } catch (e: Exception) {} }
        override fun onFailure(w: WebSocket, t: Throwable, r: Response?) { listener.onConnectionStateChange(false) }
        override fun onClosed(w: WebSocket, c: Int, r: String) { listener.onConnectionStateChange(false) }
    }) } catch (e: Exception) { listener.onConnectionStateChange(false) } }
    fun stop() { try { ws?.close(1000, "Stop"); ws = null } catch (e: Exception) {} }
}
