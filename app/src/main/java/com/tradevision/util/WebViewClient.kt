package com.tradevision.util

import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.*
import android.util.Log

open class TradeVisionWebViewClient(private val context: Context) : WebViewClient() {

    companion object {
        private const val TAG = "TradeVisionWebView"
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        Log.d(TAG, "Page started: $url")
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        Log.d(TAG, "Page finished: $url")
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return false // Let WebView handle it
        }
        return true
    }

    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
        Log.e(TAG, "SSL Error: $error")
        handler?.proceed() // Accept all SSL (dev only)
    }
}
