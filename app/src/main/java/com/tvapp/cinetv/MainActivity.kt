package com.tvapp.cinetv

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.os.Message
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import java.util.ArrayDeque

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private var popupWebView: WebView? = null

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private lateinit var rootLayout: FrameLayout
    private lateinit var diagnosticView: TextView

    private val diagnostics = ArrayDeque<String>()

    /*
     * TEMPORARY DIAGNOSTIC BUILD
     *
     * IMPORTANT:
     * The previous ad blocker is intentionally DISABLED here.
     *
     * We need to determine whether the third-party video player itself
     * works before we reintroduce ad blocking.
     */

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

        /*
         * Root layout allows us to display temporary diagnostic information
         * over the WebView.
         */
        rootLayout = FrameLayout(this)

        webView = createWebView()

        rootLayout.addView(
            webView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        diagnosticView = TextView(this)

        diagnosticView.setTextColor(Color.WHITE)
        diagnosticView.setBackgroundColor(0xCC000000.toInt())
        diagnosticView.textSize = 12f
        diagnosticView.setPadding(20, 12, 20, 12)
        diagnosticView.gravity = Gravity.CENTER_VERTICAL

        val diagnosticParams =
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )

        diagnosticParams.gravity = Gravity.TOP

        rootLayout.addView(
            diagnosticView,
            diagnosticParams
        )

        setContentView(rootLayout)

        addDiagnostic("App started")
        addDiagnostic("Ad blocking TEMPORARILY disabled")

        webView.loadUrl("https://cine.su/en")
    }

    private fun addDiagnostic(message: String) {

        val cleanMessage =
            message
                .replace("\n", " ")
                .take(220)

        diagnostics.addLast(cleanMessage)

        while (diagnostics.size > 5) {
            diagnostics.removeFirst()
        }

        val text =
            diagnostics.joinToString("\n")

        runOnUiThread {
            diagnosticView.text = text
        }
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

            cacheMode = WebSettings.LOAD_DEFAULT

            allowFileAccess = false
            allowContentAccess = false

            mixedContentMode =
                WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

            /*
             * These help third-party web players behave more like they
             * would in a normal browser.
             */
            loadsImagesAutomatically = true
            blockNetworkLoads = false
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

                override fun onPageStarted(
                    view: WebView,
                    url: String,
                    favicon: android.graphics.Bitmap?
                ) {

                    super.onPageStarted(
                        view,
                        url,
                        favicon
                    )

                    addDiagnostic(
                        "PAGE START: $url"
                    )
                }

                override fun onPageFinished(
                    view: WebView,
                    url: String
                ) {

                    super.onPageFinished(
                        view,
                        url
                    )

                    addDiagnostic(
                        "PAGE FINISHED: $url"
                    )

                    view.evaluateJavascript(
                        tvNavigationScript,
                        null
                    )
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {

                    addDiagnostic(
                        "NAV: ${request.url}"
                    )

                    return false
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError
                ) {

                    super.onReceivedError(
                        view,
                        request,
                        error
                    )

                    addDiagnostic(
                        "ERROR ${error.errorCode}: ${error.description} | ${request.url}"
                    )
                }

                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): android.webkit.WebResourceResponse? {

                    /*
                     * IMPORTANT:
                     * We intentionally do NOT block anything in this
                     * diagnostic build.
                     *
                     * Third-party video players can use unexpected
                     * domains/URLs, and we need to see whether the player
                     * works without our blocker interfering.
                     */

                    return super.shouldInterceptRequest(
                        view,
                        request
                    )
                }
            }

        view.webChromeClient =
            object : WebChromeClient() {

                override fun onConsoleMessage(
                    consoleMessage: ConsoleMessage
                ): Boolean {

                    addDiagnostic(
                        "JS: ${consoleMessage.message()} @${consoleMessage.lineNumber()}"
                    )

                    return true
                }

                override fun onProgressChanged(
                    view: WebView,
                    newProgress: Int
                ) {

                    super.onProgressChanged(
                        view,
                        newProgress
                    )

                    if (newProgress == 100) {
                        addDiagnostic("LOAD 100%")
                    }
                }

                override fun onCreateWindow(
                    view: WebView,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message
                ): Boolean {

                    addDiagnostic(
                        "NEW WINDOW | userGesture=$isUserGesture"
                    )

                    val newWebView =
                        createPopupWebView()

                    popupWebView =
                        newWebView

                    val decorView =
                        window.decorView as ViewGroup

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

                    addDiagnostic(
                        "WINDOW CLOSED"
                    )

                    closePopupWebView()
                }

                override fun onShowCustomView(
                    view: View,
                    callback: CustomViewCallback
                ) {

                    addDiagnostic(
                        "FULLSCREEN PLAYER REQUESTED"
                    )

                    if (customView != null) {

                        callback.onCustomViewHidden()
                        return
                    }

                    customView = view
                    customViewCallback = callback

                    webView.visibility = View.GONE
                    popupWebView?.visibility = View.GONE
                    diagnosticView.visibility = View.GONE

                    val decorView =
                        window.decorView as ViewGroup

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

                    addDiagnostic(
                        "FULLSCREEN PLAYER CLOSED"
                    )

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

                    addDiagnostic(
                        "REMOTE OK PRESSED"
                    )

                    activeWebView.evaluateJavascript(
                        "window.__cineTvSelect();",
                        null
                    )

                    return true
                }

                KeyEvent.KEYCODE_BACK -> {

                    if (popupWebView != null) {

                        closePopupWebView()
                  
