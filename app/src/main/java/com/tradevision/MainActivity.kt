package com.tradevision

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val TAG = "TradeVision"

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Enable WebView remote debugging for chrome://inspect (works in release too)
        WebView.setWebContentsDebuggingEnabled(true)

        webView = findViewById(R.id.webview)
        setupWebView()
        loadApp()
    }

    private fun setupWebView() {
        val settings = webView.settings
        
        // JavaScript
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        
        // File access - REQUIRED for WebSocket from file://
        settings.allowUniversalAccessFromFileURLs = true
        settings.allowFileAccessFromFileURLs = true
        settings.allowContentAccess = true
        settings.allowFileAccess = true
        
        // Mixed content
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        
        // Viewport
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        
        // Hardware acceleration
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
        }
        
        // WebChromeClient for console.log
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage) {
                Log.d(TAG, "[JS] ${consoleMessage.message()} (${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})")
            }
        }
        
        // WebViewClient
        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(view: WebView, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                Log.e(TAG, "WebView error: ${error?.description}")
            }
        }
        
        // JS Interface for native calls
        webView.addJavascriptInterface(AndroidBridge(), "Android")
    }

    private fun loadApp() {
        // Load from local asset - this is the key!
        webView.loadUrl("file:///android_asset/index.html")
    }

    inner class AndroidBridge {
        @JavascriptInterface
        fun toast(message: String) {
            runOnUiThread { android.widget.Toast.makeText(this@MainActivity, message, android.widget.Toast.LENGTH_SHORT).show() }
        }
        
        @JavascriptInterface
        fun log(message: String) {
            Log.d(TAG, "[JS Bridge] $message")
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}