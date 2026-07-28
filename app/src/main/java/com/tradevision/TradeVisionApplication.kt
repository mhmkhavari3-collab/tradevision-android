package com.tradevision

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.google.firebase.FirebaseApp
import com.tradevision.auth.AuthManager

class TradeVisionApplication : Application() {

    companion object {
        private const val TAG = "TradeVisionApp"
        const val NOTIFICATION_CHANNEL_ID = "tradevision_channel"
        const val NOTIFICATION_CHANNEL_NAME = "TradeVision Alerts"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "TradeVisionApplication onCreate")

        try {
            FirebaseApp.initializeApp(this)
            Log.d(TAG, "Firebase initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Firebase init failed (no google-services.json?)", e)
        }

        createNotificationChannels()
        AuthManager.initialize(this)

        Log.d(TAG, "TradeVisionApplication initialization complete")
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            val priceAlertChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Price alerts and important market notifications"
                enableVibration(true)
                enableLights(true)
            }

            val generalChannel = NotificationChannel(
                "tradevision_general",
                "General Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "General TradeVision notifications"
            }

            notificationManager.createNotificationChannels(
                listOf(priceAlertChannel, generalChannel)
            )
        }
    }
}
