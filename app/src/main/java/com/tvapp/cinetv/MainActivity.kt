package com.tvapp.cinetv

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : Activity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)

        webView.setBackgroundColor(0xFF000000.toInt())

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true

            mediaPlaybackRequiresUserGesture = false

            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)

            cacheMode = WebSettings.LOAD_DEFAULT

            allowFileAccess = false
            allowContentAccess = false

            setSupportMultipleWindows(false)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                return false
            }
        }

        webView.webChromeClient = WebChromeClient()

        webView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        setContentView(webView)

        webView.loadUrl("https://cine.su/en")
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        webView.stopLoading()
        webView.loadUrl("about:blank")
        webView.clearHistory()

        (webView.parent as? android.view.ViewGroup)?.removeView(webView)

        webView.destroy()

        super.onDestroy()
    }
}
