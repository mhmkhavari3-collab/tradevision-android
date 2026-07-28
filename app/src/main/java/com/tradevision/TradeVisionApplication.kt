package com.tradevision

import android.app.Application

class TradeVisionApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            com.tradevision.auth.AuthManager.initialize(this)
        } catch (_: Exception) {
        }
    }
}
