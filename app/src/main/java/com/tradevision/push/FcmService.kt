package com.tradevision.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FcmService : FirebaseMessagingService() {
    override fun onNewToken(token: String) { Log.d("FCM", "Token: ${token.take(20)}") }
    override fun onMessageReceived(message: RemoteMessage) { Log.d("FCM", "Received: ${message.data}") }
}
