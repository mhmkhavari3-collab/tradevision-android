package com.tradevision

import android.app.Application
import android.util.Log
import com.tradevision.auth.AuthManager

class TradeVisionApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try { AuthManager.initialize(this) } catch (e: Exception) { Log.e("App", "Auth init failed", e) }
    }
}
