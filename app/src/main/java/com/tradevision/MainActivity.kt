package com.tradevision

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.tradevision.auth.AuthManager
import com.tradevision.util.TradeVisionWebViewClient
import com.tradevision.ws.PriceWebSocket

class MainActivity : AppCompatActivity(), PriceWebSocket.PriceUpdateListener {

    companion object {
        private const val TAG = "MainActivity"
        private const val WEB_APP_URL = "https://tradevision.app"
        private const val WEB_APP_URL_DEBUG = "http://10.0.2.2:3000"
    }

    private lateinit var webView: WebView
    private lateinit var webViewClient: TradeVisionWebViewClient
    private lateinit var authManager: AuthManager
    private var priceWebSocket: PriceWebSocket? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")

        setContentView(R.layout.activity_main)

        authManager = AuthManager.getInstance()

        setupWebView()
        setupSwipeRefresh()
        loadWebApp()

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun setupWebView() {
        webView = findViewById(R.id.webview)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            databaseEnabled = true
            setSupportMultipleWindows(false)
            mediaPlaybackRequiresUserGesture = false
        }

        webViewClient = TradeVisionWebViewClient(this)
        webView.webViewClient = webViewClient
        webView.webChromeClient = WebChromeClient()

        webView.addJavascriptInterface(WebAppBridge(), "TradeVisionBridge")
    }

    private fun setupSwipeRefresh() {
        val swipeRefresh = findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipe_refresh)
        swipeRefresh.setOnRefreshListener {
            webView.reload()
            swipeRefresh.isRefreshing = false
        }
    }

    private fun loadWebApp() {
        val url = if (BuildConfig.DEBUG) WEB_APP_URL_DEBUG else WEB_APP_URL
        Log.d(TAG, "Loading web app: $url")
        webView.loadUrl(url)
    }

    private fun handleIntent(intent: Intent) {
        val screen = intent.getStringExtra("screen")
        val symbol = intent.getStringExtra("symbol")
        val alertId = intent.getStringExtra("alert_id")

        if (screen != null) {
            Log.d(TAG, "Deep link: screen=$screen, symbol=$symbol, alertId=$alertId")
            val js = "javascript:handleDeepLink('$screen', '$symbol', '$alertId')"
            webView.evaluateJavascript(js, null)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        startPriceWebSocket()
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
stopPriceWebSocket()
    }

    override fun onDestroy() {
        stopPriceWebSocket()
        webView.destroy()
        super.onDestroy()
    }

    private fun startPriceWebSocket() {
        if (priceWebSocket == null) {
            priceWebSocket = PriceWebSocket(this)
            priceWebSocket?.start()
        }
    }

    private fun stopPriceWebSocket() {
        priceWebSocket?.stop()
        priceWebSocket = null
    }

    override fun onPriceUpdate(symbol: String, price: Double) {
        Log.d(TAG, "Price update: $symbol = $price")
        runOnUiThread {
            webView.evaluateJavascript("javascript:onPriceUpdate('$symbol', $price)", null)
        }
    }

    override fun onConnectionStateChange(connected: Boolean) {
        Log.d(TAG, "WebSocket connected: $connected")
    }

    inner class WebAppBridge {
        @JavascriptInterface
        fun getAppVersion(): String = BuildConfig.VERSION_NAME

        @JavascriptInterface
        fun getPlatform(): String = "android"

        @JavascriptInterface
        fun showToast(message: String) {
            runOnUiThread {
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun isLoggedIn(): Boolean = authManager.isLoggedIn()

        @JavascriptInterface
        fun getAccessToken(): String? = authManager.getAccessToken()

        @JavascriptInterface
        fun getUserId(): String? = authManager.getUserId()
    }
}
