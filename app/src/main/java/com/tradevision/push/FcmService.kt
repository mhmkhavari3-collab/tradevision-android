package com.tradevision.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class FcmService : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("FcmService", "Received: ${intent.action}")
    }
}
