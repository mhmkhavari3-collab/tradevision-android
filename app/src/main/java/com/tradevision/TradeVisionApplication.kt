package com.tradevision
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.tradevision.auth.AuthManager
class TradeVisionApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try { Class.forName("com.google.firebase.FirebaseApp").getMethod("initializeApp", Application::class.java).invoke(null, this) } catch (e: Exception) { Log.w("App", "Firebase skip") }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { try { val nm = getSystemService(NotificationManager::class.java); nm.createNotificationChannels(listOf(NotificationChannel("tradevision_channel","Alerts",NotificationManager.IMPORTANCE_HIGH), NotificationChannel("tradevision_general","General",NotificationManager.IMPORTANCE_DEFAULT))) } catch (e: Exception) {} }
        try { AuthManager.initialize(this) } catch (e: Exception) {}
    }
}
