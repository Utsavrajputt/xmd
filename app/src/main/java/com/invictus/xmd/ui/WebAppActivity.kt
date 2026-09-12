package com.invictus.xmd.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.invictus.xmd.ui.theme.XmdTheme

/**
 * Bare, chrome-less WebView window launched from a pinned "Add to Home
 * screen" shortcut (see BrowserFragment.addCurrentPageAsApp()). No
 * toolbar/tabs/speed-dial -- just the page, so a pinned site reads as its
 * own standalone app rather than another XMD browser tab.
 *
 * Navigation within the WebView is completely unrestricted (any link,
 * any domain, keeps loading in this same window) -- this is a chrome-less
 * *window* onto the browser engine, not a scope-locked PWA. Non-http(s)
 * schemes (intent://, market://, upi://, etc.) are resolved to a real
 * Intent exactly like BrowserFragment's tabs do, so app hand-offs
 * (UPI, WhatsApp, Play Store...) behave the same as in a normal tab.
 *
 * Back press: goes back through in-page history first; once there's
 * nothing left to go back to, it closes the activity (confirmed with the
 * user -- no "drop to launcher" behavior here).
 */
class WebAppActivity : ComponentActivity() {

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
    }

    private var webViewRef: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        com.invictus.xmd.ui.theme.AppTheme.applyTo(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val url = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }
        intent.getStringExtra(EXTRA_TITLE)?.let { title = it }

        onBackPressedDispatcher.addCallback(this) {
            val webView = webViewRef
            if (webView != null && webView.canGoBack()) {
                webView.goBack()
            } else {
                finish()
            }
        }

        setContent {
            XmdTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    WebAppScreen(
                        url = url,
                        onWebViewReady = { webViewRef = it },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        webViewRef?.apply {
            stopLoading()
            destroy()
        }
        webViewRef = null
        super.onDestroy()
    }
}

@Composable
private fun WebAppScreen(
    url: String,
    onWebViewReady: (WebView) -> Unit,
) {
    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        factory = { context ->
            WebView(context).apply {
                configureAsStandaloneApp()
                webViewClient = webAppWebViewClient()
                loadUrl(url)
            }.also(onWebViewReady)
        },
    )
}

/** Mirrors BrowserFragment.configureWebView's core settings -- JS, DOM
 *  storage, zoom, autoplay, cache/mixed-content -- for parity with a
 *  normal browser tab. Desktop-site emulation, tab lifecycle (LRU
 *  eviction, state save/restore) and the long-press link menu are
 *  tab-only concerns this single-page window doesn't need. */
@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configureAsStandaloneApp() {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.databaseEnabled = true
    settings.setSupportZoom(true)
    settings.builtInZoomControls = true
    settings.displayZoomControls = false
    settings.mediaPlaybackRequiresUserGesture = false
    settings.cacheMode = WebSettings.LOAD_DEFAULT
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
}

/** Same non-http(s) scheme hand-off as BrowserFragment's WebViewClient
 *  (see its shouldOverrideUrlLoading doc comment) -- everything else
 *  (including cross-domain http/https links) just keeps loading in this
 *  same WebView, per the "same chrome-less window" behavior confirmed
 *  for this feature. */
private fun webAppWebViewClient() = object : WebViewClient() {
    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: android.webkit.WebResourceRequest
    ): Boolean {
        val uri = request.url
        val scheme = uri.scheme?.lowercase()
        if (scheme == "http" || scheme == "https") return false

        try {
            val intent = if (scheme == "intent") {
                android.content.Intent.parseUri(uri.toString(), android.content.Intent.URI_INTENT_SCHEME)
            } else {
                android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
            }
            intent.addCategory(android.content.Intent.CATEGORY_BROWSABLE)
            intent.component = null
            intent.selector = null
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK

            val pm = view.context.packageManager
            if (intent.resolveActivity(pm) != null) {
                view.context.startActivity(intent)
            } else {
                val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                if (fallbackUrl != null) view.loadUrl(fallbackUrl)
            }
        } catch (e: Exception) {
            // Malformed intent URI, or the resolved app rejected the
            // launch -- nothing more we can do, same as the browser tab.
        }
        return true
    }
}
