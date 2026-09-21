package com.tvapp.cinetv

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.os.Message
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

class MainActivity : Activity() {

    private lateinit var webView: WebView

    private var popupWebView: WebView? = null

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    /*
     * Conservative advertising / tracking filter.
     *
     * IMPORTANT:
     * We deliberately do NOT block every third-party request.
     * Video players commonly use third-party domains.
     */
    private val blockedAdHosts = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "googletagservices.com",
        "adservice.google.com",
        "adnxs.com",
        "adsrvr.org",
        "adform.net",
        "advertising.com",
        "pubmatic.com",
        "rubiconproject.com",
        "openx.net",
        "criteo.com",
        "outbrain.com",
        "taboola.com",
        "popads.net",
        "popcash.net",
        "propellerads.com",
        "exoclick.com",
        "trafficjunky.net",
        "juicyads.com",
        "onclickads.net"
    )

    /*
     * Obvious ad URL patterns.
     *
     * These are only used when the request URL itself strongly
     * indicates advertising.
     */
    private val blockedAdPatterns = listOf(
        "/ads/",
        "/adserver/",
        "/advert/",
        "/advertising/",
        "/banner/",
        "/popunder/",
        "/popup/",
        "/prebid/",
        "doubleclick",
        "googlesyndication",
        "googleadservices",
        "adservice",
        "popunder",
        "popads",
        "popcash"
    )

    private val tvNavigationScript = """
        (function() {
            if (window.__cineTvNavInstalled) return;
            window.__cineTvNavInstalled = true;

            var current = null;

            var style = document.createElement('style');

            style.id = 'cine-tv-focus-style';

            style.textContent = `
                *:focus {
                    outline: none !important;
                }

                .cine-tv-focused {
                    outline: 4px solid #ffffff !important;
                    outline-offset: 4px !important;
                    border-radius: 4px !important;
                    position: relative !important;
                    z-index: 9999 !important;
                }

                video {
                    -webkit-user-select: none !important;
                    user-select: none !important;
                }
            `;

            document.head.appendChild(style);

            function candidates() {

                return Array.from(
                    document.querySelectorAll(
                        'a[href], button, input, select, textarea, [role="button"]'
                    )
                ).filter(function(el) {

                    var r = el.getBoundingClientRect();
                    var s = getComputedStyle(el);

                    return r.width > 0 &&
                           r.height > 0 &&
                           s.display !== 'none' &&
                           s.visibility !== 'hidden' &&
                           s.opacity !== '0' &&
                           !el.disabled;
                });
            }

            function focusElement(el) {

                if (!el) return;

                if (current && current !== el) {
                    current.classList.remove('cine-tv-focused');
                }

                current = el;

                current.classList.add('cine-tv-focused');

                try {
                    current.focus({
                        preventScroll: true
                    });
                } catch (e) {

                    try {
                        current.focus();
                    } catch (ignore) {}
                }

                var r = current.getBoundingClientRect();

                if (r.top < 0 ||
                    r.bottom > window.innerHeight ||
                    r.left < 0 ||
                    r.right > window.innerWidth) {

                    current.scrollIntoView({
                        behavior: 'auto',
                        block: 'center',
                        inline: 'center'
                    });
                }
            }

            function start() {

                var list = candidates();

                if (!list.length) return;

                if (!current || !list.includes(current)) {

                    var first = list.find(function(el) {

                        var r = el.getBoundingClientRect();

                        return r.bottom > 0 &&
                               r.top < window.innerHeight;
                    });

                    focusElement(first || list[0]);
                }
            }

            function move(dx, dy) {

                var list = candidates();

                if (!list.length) return;

                if (!current || !list.includes(current)) {
                    start();
                    return;
                }

                var a = current.getBoundingClientRect();

                var ax = a.left + a.width / 2;
                var ay = a.top + a.height / 2;

                var best = null;
                var bestScore = Infinity;

                list.forEach(function(el) {

                    if (el === current) return;

                    var r = el.getBoundingClientRect();

                    if (r.width <= 0 || r.height <= 0) return;

                    var bx = r.left + r.width / 2;
                    var by = r.top + r.height / 2;

                    var vx = bx - ax;
                    var vy = by - ay;

                    if ((dx > 0 && vx <= 5) ||
                        (dx < 0 && vx >= -5) ||
                        (dy > 0 && vy <= 5) ||
                        (dy < 0 && vy >= -5)) {
                        return;
                    }

                    var primary = dx !== 0
                        ? Math.abs(vx)
                        : Math.abs(vy);

                    var secondary = dx !== 0
                        ? Math.abs(vy)
                        : Math.abs(vx);

                    var score =
                        primary +
                        secondary * 1.8;

                    if (score < bestScore) {
                        bestScore = score;
                        best = el;
                    }
                });

                if (best) {
                    focusElement(best);
                }
            }

            function activate() {

                if (!current) {
                    start();
                    return;
                }

                try {

                    current.click();

                } catch (e) {

                    try {

                        current.dispatchEvent(
                            new MouseEvent(
                                'click',
                                {
                                    bubbles: true,
                                    cancelable: true,
                                    view: window
                                }
                            )
                        );

                    } catch (ignore) {}
                }
            }

            window.__cineTvMove = function(dx, dy) {
                move(dx, dy);
            };

            window.__cineTvSelect = function() {
                activate();
            };

            window.__cineTvStart = function() {
                start();
            };

            setTimeout(start, 500);
            setTimeout(start, 1500);
            setTimeout(start, 3000);

            new MutationObserver(function() {

                if (!current || !document.contains(current)) {

                    current = null;

                    setTimeout(start, 100);
                }

            }).observe(document.documentElement, {
                childList: true,
                subtree: true
            });

        })();
    """.trimIndent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation =
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        webView = createWebView()

        setContentView(webView)

        webView.loadUrl("https://cine.su/en")
    }

    /*
     * Returns true only for requests that look clearly like
     * advertising/tracking.
     */
    private fun isAdRequest(urlString: String): Boolean {

        val url = urlString.lowercase()

        /*
         * Never apply our URL-pattern filter to actual media files.
         */
        if (url.contains(".mp4") ||
            url.contains(".m3u8") ||
            url.contains(".mpd") ||
            url.contains(".webm") ||
            url.contains(".mkv") ||
            url.contains(".m4v") ||
            url.contains(".ts")
        ) {
            return false
        }

        val host = try {
            java.net.URI(url).host?.lowercase() ?: ""
        } catch (e: Exception) {
            ""
        }

        /*
         * Exact / subdomain host matching.
         */
        for (blockedHost in blockedAdHosts) {

            if (host == blockedHost ||
                host.endsWith("." + blockedHost)
            ) {
                return true
            }
        }

        /*
         * Only block obvious advertising paths/identifiers.
         */
        for (pattern in blockedAdPatterns) {

            if (url.contains(pattern)) {
                return true
            }
        }

        return false
    }

    private fun emptyResponse(): WebResourceResponse {

        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            204,
            "No Content",
            emptyMap(),
            ByteArrayInputStream(ByteArray(0))
        )
    }

    private fun configureWebView(view: WebView) {

        view.setBackgroundColor(Color.BLACK)

        view.isFocusable = true
        view.isFocusableInTouchMode = true

        view.settings.apply {

            javaScriptEnabled = true

            domStorageEnabled = true

            mediaPlaybackRequiresUserGesture = false

            builtInZoomControls = false

            displayZoomControls = false

            setSupportZoom(false)

            javaScriptCanOpenWindowsAutomatically = true

            setSupportMultipleWindows(true)

            databaseEnabled = true

            cacheMode =
                WebSettings.LOAD_DEFAULT

            allowFileAccess = false

            allowContentAccess = false

            mixedContentMode =
                WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }

        CookieManager.getInstance()
            .setAcceptCookie(true)

        CookieManager.getInstance()
            .setAcceptThirdPartyCookies(
                view,
                true
            )

        view.webViewClient =
            object : WebViewClient() {

                override fun onPageFinished(
                    view: WebView,
                    url: String
                ) {

                    super.onPageFinished(
                        view,
                        url
                    )

                    view.evaluateJavascript(
                        tvNavigationScript,
                        null
                    )
                }

                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? {

                    val url =
                        request.url.toString()

                    /*
                     * Block only requests that our conservative
                     * filter identifies as advertisements.
                     */
                    if (isAdRequest(url)) {
                        return emptyResponse()
                    }

                    /*
                     * Everything else, including player,
                     * iframe and media requests, continues
                     * normally through WebView.
                     */
                    return super.shouldInterceptRequest(
                        view,
                        request
                    )
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {

                    return false
                }
            }

        view.webChromeClient =
            object : WebChromeClient() {

                override fun onCreateWindow(
                    view: WebView,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message
                ): Boolean {

                    val newWebView =
                        createPopupWebView()

                    popupWebView =
                        newWebView

                    val decorView =
                        window.decorView
                            as ViewGroup

                    decorView.addView(
                        newWebView,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )

                    newWebView.bringToFront()

                    val transport =
                        resultMsg.obj
                            as WebView.WebViewTransport

                    transport.webView =
                        newWebView

                    resultMsg.sendToTarget()

                    return true
                }

                override fun onCloseWindow(
                    window: WebView
                ) {

                    closePopupWebView()
                }

                override fun onShowCustomView(
                    view: View,
                    callback: CustomViewCallback
                ) {

                    if (customView != null) {

                        callback.onCustomViewHidden()

                        return
                    }

                    customView = view

                    customViewCallback =
                        callback

                    webView.visibility =
                        View.GONE

                    popupWebView?.visibility =
                        View.GONE

                    val decorView =
                        window.decorView
                            as ViewGroup

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
                }

                override fun onHideCustomView() {

                    exitFullscreen()
                }
            }

        view.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    private fun createWebView(): WebView {

        val view =
            WebView(this)

        configureWebView(view)

        return view
    }

    private fun createPopupWebView(): WebView {

        val view =
            WebView(this)

        configureWebView(view)

        return view
    }

    override fun dispatchKeyEvent(
        event: KeyEvent
    ): Boolean {

        if (event.action ==
            KeyEvent.ACTION_DOWN
        ) {

            if (event.keyCode ==
                KeyEvent.KEYCODE_BACK &&
                customView != null
            ) {

                exitFullscreen()

                return true
            }

            val activeWebView =
                popupWebView ?: webView

            when (event.keyCode) {

                KeyEvent.KEYCODE_DPAD_LEFT -> {

                    activeWebView.evaluateJavascript(
                        "window.__cineTvMove(-1,0);",
                        null
                    )

                    return true
                }

                KeyEvent.KEYCODE_DPAD_RIGHT -> {

                    activeWebView.evaluateJavascript(
                        "window.__cineTvMove(1,0);",
                        null
                    )

                    return true
                }

                KeyEvent.KEYCODE_DPAD_UP -> {

                    activeWebView.evaluateJavascript(
                        "window.__cineTvMove(0,-1);",
                        null
                    )

                    return true
                }

                KeyEvent.KEYCODE_DPAD_DOWN -> {

                    activeWebView.evaluateJavascript(
                        "window.__cineTvMove(0,1);",
                        null
                    )

                    return true
                }

                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER -> {

                    activeWebView.evaluateJavascript(
                        "window.__cineTvSelect();",
                        null
                    )

                    return true
                }

                KeyEvent.KEYCODE_BACK -> {

                    if (popupWebView != null) {

                        closePopupWebView()

                        return true
                    }

                    if (webView.canGoBack()) {

                        webView.goBack()

                        return true
                    }
                }
            }
        }

        return super.dispatchKeyEvent(event)
    }

    private fun closePopupWebView() {

        val popup =
            popupWebView ?: return

        (popup.parent as? ViewGroup)
            ?.removeView(popup)

        popup.stopLoading()

        popup.loadUrl("about:blank")

        popup.destroy()

        popupWebView = null

        webView.visibility =
            View.VISIBLE

        webView.bringToFront()
    }

    private fun exitFullscreen() {

        val view =
            customView ?: return

        val decorView =
            window.decorView
                as ViewGroup

        decorView.removeView(view)

        customView = null

        customViewCallback
            ?.onCustomViewHidden()

        customViewCallback = null

        if (popupWebView != null) {

            popupWebView?.visibility =
                View.VISIBLE

            popupWebView?.bringToFront()

        } else {

            webView.visibility =
                View.VISIBLE

            webView.bringToFront()
        }

        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        requestedOrientation =
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }

    override fun onDestroy() {

        customView?.let {

            (window.decorView as ViewGroup)
                .remov
