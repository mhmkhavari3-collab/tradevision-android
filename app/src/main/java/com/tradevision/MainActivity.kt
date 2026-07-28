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
import com.tradevision.auth.AuthManager
import com.tradevision.ws.PriceWebSocket
class MainActivity : AppCompatActivity(), PriceWebSocket.PriceUpdateListener {
    companion object { private const val TAG = "MainActivity"; private const val WEB_URL = "https://tradevision.app"; private const val WEB_DEBUG = "http://10.0.2.2:3000" }
    private lateinit var webView: WebView
    private var authManager: AuthManager? = null
    private var priceWebSocket: PriceWebSocket? = null
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        try { authManager = AuthManager.getInstance() } catch (e: Exception) { Log.e(TAG, "Auth err", e) }
        webView = findViewById(R.id.webview)
        webView.settings.apply { javaScriptEnabled = true; domStorageEnabled = true; allowFileAccess = true; loadWithOverviewMode = true; useWideViewPort = true; setSupportZoom(false); cacheMode = WebSettings.LOAD_DEFAULT; databaseEnabled = true }
        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) { if (request?.isForMainFrame == true) view?.loadData("<html><body style='background:#0f0f1a;color:#fff;display:flex;align-items:center;justify-content:center;height:100vh;font-family:sans-serif;text-align:center'><div><h1 style='color:#4fc3f7'>TradeVision</h1><p>Loading...</p></div></body></html>","text/html","UTF-8") }
        }
        webView.webChromeClient = WebChromeClient()
        webView.addJavascriptInterface(WebAppBridge(), "TradeVisionBridge")
        webView.loadUrl(if (BuildConfig.DEBUG) WEB_DEBUG else WEB_URL)
        handleIntent(intent)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleIntent(intent) }
    private fun handleIntent(intent: Intent) { val s = intent.getStringExtra("screen") ?: return; webView.evaluateJavascript("javascript:handleDeepLink('$s','${intent.getStringExtra("symbol")}','')", null) }
    @Deprecated("Deprecated in Java") override fun onBackPressed() { if (webView.canGoBack()) webView.goBack() else @Suppress("DEPRECATION") super.onBackPressed() }
    override fun onResume() { super.onResume(); webView.onResume(); try { if (priceWebSocket == null) { priceWebSocket = PriceWebSocket(this); priceWebSocket?.start() } } catch (e: Exception) {} }
    override fun onPause() { super.onPause(); webView.onPause(); try { priceWebSocket?.stop(); priceWebSocket = null } catch (e: Exception) {} }
    override fun onDestroy() { try { priceWebSocket?.stop() } catch (e: Exception) {}; webView.destroy(); super.onDestroy() }
    override fun onPriceUpdate(symbol: String, price: Double) { try { runOnUiThread { webView.evaluateJavascript("javascript:onPriceUpdate('$symbol',$price)", null) } } catch (e: Exception) {} }
    override fun onConnectionStateChange(connected: Boolean) {}
    inner class WebAppBridge {
        @JavascriptInterface fun getAppVersion(): String = BuildConfig.VERSION_NAME
        @JavascriptInterface fun getPlatform(): String = "android"
@JavascriptInterface fun showToast(msg: String) { runOnUiThread { Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show() } }
        @JavascriptInterface fun isLoggedIn(): Boolean = try { authManager?.isLoggedIn() ?: false } catch (e: Exception) { false }
        @JavascriptInterface fun getAccessToken(): String? = try { authManager?.getAccessToken() } catch (e: Exception) { null }
        @JavascriptInterface fun getUserId(): String? = try { authManager?.getUserId() } catch (e: Exception) { null }
    }
}
