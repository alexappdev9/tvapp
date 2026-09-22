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
     * ============================================================
     * TEMPORARY DIAGNOSTIC BUILD
     * ============================================================
     *
     * IMPORTANT:
     * The previous ad blocker is DISABLED in this version.
     *
     * We need to determine whether the third-party video player
     * works when our request filtering is completely out of the way.
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

            }).observe(
                document.documentElement,
                {
                    childList: true,
                    subtree: true
                }
            );

        })();
    """.trimIndent()

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        requestedOrientation =
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

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

        diagnosticView.setPadding(
            20,
            12,
            20,
            12
        )

        diagnosticView.gravity =
            Gravity.CENTER_VERTICAL

        val diagnosticParams =
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )

        diagnosticParams.gravity =
            Gravity.TOP

        rootLayout.addView(
            diagnosticView,
            diagnosticParams
        )

        setContentView(rootLayout)

        addDiagnostic("CineTV diagnostic build")
        addDiagnostic("Ad blocking: OFF")

        webView.loadUrl("https://cine.su/en")
    }

    private fun addDiagnostic(message: String) {

        val cleanMessage =
            message
                .replace("\n", " ")
                .take(240)

        diagnostics.addLast(cleanMessage)

        while (diagnostics.size > 5) {
            diagnostics.removeFirst()
        }

        val displayText =
            diagnostics.joinToString("\n")

        runOnUiThread {

            diagnosticView.text =
                displayText
        }
    }

    private fun configureWebView(view: WebView) {

        view.setBackgroundColor(Color.BLACK)

        view.isFocusable = true
        view.isFocusableInTouchMode =
