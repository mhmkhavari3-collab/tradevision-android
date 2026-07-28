package com.tradevision

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.tradevision.auth.AuthManager

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(false)
            cacheMode = WebSettings.LOAD_DEFAULT
            databaseEnabled = true
            allowFileAccess = true
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(
                view: WebView?,
                request: android.webkit.WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    view?.loadData(
                        "<html><body style='background:#0f0f1a;color:#fff;display:flex;align-items:center;justify-content:center;height:100vh;font-family:sans-serif;text-align:center'><div><h1 style='color:#4fc3f7'>TradeVision</h1><p>Loading...</p></div></body></html>",
                        "text/html", "UTF-8"
                    )
                }
            }
        }
        webView.webChromeClient = WebChromeClient()
        webView.addJavascriptInterface(WebAppBridge(), "TradeVisionBridge")
        webView.loadUrl("https://tradevision.app")
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    inner class WebAppBridge {
        @JavascriptInterface
        fun getAppVersion(): String = BuildConfig.VERSION_NAME

        @JavascriptInterface
        fun getPlatform(): String = "android"

        @JavascriptInterface
        fun showToast(msg: String) {
            runOnUiThread { Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show() }
        }

        @JavascriptInterface
        fun isLoggedIn(): Boolean = try { AuthManager.getInstance().isLoggedIn() } catch (e: Exception) { false }

        @JavascriptInterface
        fun getAccessToken(): String? = try { AuthManager.getInstance().getAccessToken() } catch (e: Exception) { null }
    }
}
