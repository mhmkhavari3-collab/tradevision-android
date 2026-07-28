package com.tradevision.ws

class PriceWebSocket(private val listener: PriceUpdateListener) {
    interface PriceUpdateListener {
        fun onPriceUpdate(symbol: String, price: Double)
        fun onConnectionStateChange(connected: Boolean)
    }
    fun start() {}
    fun stop() {}
}
