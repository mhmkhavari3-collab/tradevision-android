package com.tradevision

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")

        setContentView(R.layout.activity_main)
        setupWebView()
        loadWebApp()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView = findViewById(R.id.webview)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
            databaseEnabled = true
            setSupportMultipleWindows(false)
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = true
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                Log.e(TAG, "WebView error: ${error?.description}")
                if (request?.isForMainFrame == true) {
                    view?.loadData(getFallbackHtml(), "text/html", "UTF-8")
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url.toString()
                return if (url.startsWith("http://") || url.startsWith("https://")) {
                    false
                } else {
                    true
                }
            }
        }

        webView.webChromeClient = WebChromeClient()

        webView.addJavascriptInterface(WebAppBridge(), "TradeVisionBridge")
    }

    private fun loadWebApp() {
        // Load from local asset - works offline and allows WebSocket from file://
        val url = "file:///android_asset/index.html"
        Log.d(TAG, "Loading: $url")
        webView.loadUrl(url)
    }

    private fun getFallbackHtml(): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <title>TradeVision</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                           background: #0a0e14; color: #c5c8c9; display: flex; flex-direction: column;
                           align-items: center; justify-content: center; min-height: 100vh; padding: 20px; }
                    .logo { font-size: 48px; margin-bottom: 16px; }
                    h1 { font-size: 28px; margin-bottom: 8px; color: #2196f3; }
                    p { font-size: 16px; color: #787b86; text-align: center; max-width: 300px; line-height: 1.5; }
                    .status { margin-top: 24px; padding: 12px 24px; background: #131722; border-radius: 12px;
                              border: 1px solid #1e222d; }
                    .status span { color: #ff9800; }
                </style>
            </head>
            <body>
                <div class="logo">📊</div>
                <h1>TradeVision</h1>
                <p>Trading Terminal for XAUUSD</p>
                <div class="status">
                    <span>⏳ Loading chart...</span>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun handleIntent(intent: Intent) {
        val screen = intent.getStringExtra("screen")
        val symbol = intent.getStringExtra("symbol")

        if (screen != null) {
            Log.d(TAG, "Deep link: screen=$screen, symbol=$symbol")
            val js = "javascript:handleDeepLink('$screen', '$symbol', '')"
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
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
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
    }
}