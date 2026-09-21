package com.tvapp.cinetv

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import java.io.ByteArrayInputStream

class MainActivity : Activity() {

    private lateinit var webView: WebView

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private val blockedHosts = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adservice.google.com",
        "popads.net",
        "popcash.net",
        "propellerads.com",
        "adsterra.com"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

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

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {

                val host = request.url.host?.lowercase() ?: ""

                if (blockedHosts.any {
                        host == it || host.endsWith(".$it")
                    }) {
                    return WebResourceResponse(
                        "text/plain",
                        "UTF-8",
                        ByteArrayInputStream(ByteArray(0))
                    )
                }

                return null
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                return false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {

            override fun onShowCustomView(
                view: View,
                callback: CustomViewCallback
            ) {
                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }

                customView = view
                customViewCallback = callback

                webView.visibility = View.GONE

                val decorView = window.decorView as ViewGroup

                decorView.addView(
                    view,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )

                window.decorView.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

                requestedOrientation =
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }

            override fun onHideCustomView() {
                exitFullscreen()
            }
        }

        webView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        setContentView(webView)

        webView.loadUrl("https://cine.su/en")
    }

    private fun exitFullscreen() {
        val view = customView ?: return

        val decorView = window.decorView as ViewGroup

        decorView.removeView(view)

        customView = null

        customViewCallback?.onCustomViewHidden()
        customViewCallback = null

        webView.visibility = View.VISIBLE

        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        requestedOrientation =
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {

        if (keyCode == KeyEvent.KEYCODE_BACK) {

            if (customView != null) {
                exitFullscreen()
                return true
            }

            if (webView.canGoBack()) {
                webView.goBack()
                return true
            }
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {

        customView?.let {
            (window.decorView as ViewGroup).removeView(it)
        }

        webView.stopLoading()
        webView.loadUrl("about:blank")
        webView.clearHistory()

        (webView.parent as? ViewGroup)?.removeView(webView)

        webView.destroy()

        super.onDestroy()
    }
}
