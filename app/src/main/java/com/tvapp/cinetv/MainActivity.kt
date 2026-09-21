package com.tvapp.cinetv

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : Activity() {

    private lateinit var webView: WebView

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private val tvNavigationScript = """
        (function() {
            if (window.__cineTvNavInstalled) return;
            window.__cineTvNavInstalled = true;

            var current = null;

            var style = document.createElement('style');
            style.id = 'cine-tv-focus-style';
            style.textContent = `
                .cine-tv-focused {
                    outline: 4px solid #ffffff !important;
                    outline-offset: 4px !important;
                    border-radius: 4px !important;
                    position: relative !important;
                    z-index: 9999 !important;
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

                if (current) {
                    current.classList.remove('cine-tv-focused');
                }

                current = el;

                current.classList.add('cine-tv-focused');

                try {
                    current.focus({preventScroll: true});
                } catch (e) {
                    current.focus();
                }

                var r = current.getBoundingClientRect();

                if (r.top < 0 || r.bottom > window.innerHeight ||
                    r.left < 0 || r.right > window.innerWidth) {
                    current.scrollIntoView({
                        behavior: 'auto',
                        block: 'center',
                        inline: 'center'
                    });
                }
            }

            function start() {
                var list = candidates();

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

                    var score = primary * 1.0 + secondary * 1.8;

                    if (score < bestScore) {
                        bestScore = score;
                        best = el;
                    }
                });

                if (best) {
                    focusElement(best);
                }
            }

            window.__cineTvMove = function(dx, dy) {
                move(dx, dy);
            };

            window.__cineTvSelect = function() {
                if (current) {
                    current.click();
                }
            };

            window.__cineTvStart = function() {
                start();
            };

            setTimeout(start, 500);
            setTimeout(start, 1500);

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

        webView.isFocusable = true
        webView.isFocusableInTouchMode = true

        webView.webViewClient = object : WebViewClient() {

            override fun onPageFinished(
                view: WebView,
                url: String
            ) {
                super.onPageFinished(view, url)

                view.evaluateJavascript(
                    tvNavigationScript,
                    null
                )
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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {

        if (event.action == KeyEvent.ACTION_DOWN) {

            when (event.keyCode) {

                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (customView == null) {
                        webView.evaluateJavascript(
                            "window.__cineTvMove(-1,0);",
                            null
                        )
                        return true
                    }
                }

                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (customView == null) {
                        webView.evaluateJavascript(
                            "window.__cineTvMove(1,0);",
                            null
                        )
                        return true
                    }
                }

                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (customView == null) {
                        webView.evaluateJavascript(
                            "window.__cineTvMove(0,-1);",
                            null
                        )
                        return true
                    }
                }

                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (customView == null) {
                        webView.evaluateJavascript(
                            "window.__cineTvMove(0,1);",
                            null
                        )
                        return true
                    }
                }

                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER -> {
                    if (customView == null) {
                        webView.evaluateJavascript(
                            "window.__cineTvSelect();",
                            null
                        )
                        return true
                    }
                }

                KeyEvent.KEYCODE_BACK -> {
                    if (customView != null) {
                        exitFullscreen()
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

    private fun exitFullscreen() {

        val view = customView ?: return

        val decorView = window.decorView as ViewGroup

        decorView.removeView(view)

        customView = null

        customViewCallback?.onCustomViewHidden()
        customViewCallback = null

        webView.visibility = View.VISIBLE

        webView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        requestedOrientation =
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
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
