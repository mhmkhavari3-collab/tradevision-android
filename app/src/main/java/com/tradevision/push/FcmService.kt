package com.tradevision.push

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.tradevision.MainActivity
import com.tradevision.TradeVisionApplication
import com.tradevision.auth.AuthManager
import com.tradevision.network.ApiClient
import com.tradevision.network.PushRegistrationRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FcmService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FcmService"
        private const val CHANNEL_PRICE_ALERTS = "tradevision_channel"
        private const val CHANNEL_GENERAL = "tradevision_general"
        @Volatile
        private var fcmToken: String? = null
        fun getToken(): String? = fcmToken
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: ${token.take(20)}...")
        fcmToken = token
        serviceScope.launch {
            val authManager = AuthManager.getInstance()
            val accessToken = authManager.getAccessToken() ?: return@launch
            try {
                val deviceId = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "unknown"
                val request = PushRegistrationRequest(fcmToken = token, deviceId = deviceId, deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}")
                ApiClient.getApi().registerPushToken("Bearer $accessToken", request)
            } catch (e: Exception) { Log.e(TAG, "FCM registration error", e) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "Push received: ${message.messageId}")
        val title = message.notification?.title ?: message.data["title"] ?: "TradeVision"
        val body = message.notification?.body ?: message.data["body"] ?: ""
        val type = message.data["type"] ?: "generic"
        val symbol = message.data["symbol"]

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            symbol?.let { putExtra("symbol", it) }
        }
        val pendingIntent = PendingIntent.getActivity(this, System.currentTimeMillis().toInt(), intent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)

        val channelId = if (type == "price_alert") CHANNEL_PRICE_ALERTS else CHANNEL_GENERAL
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
