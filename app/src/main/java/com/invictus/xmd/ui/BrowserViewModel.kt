package com.invictus.xmd.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.invictus.xmd.core.DnsOverHttpsResolver
import com.invictus.xmd.core.MediaSniffer
import com.invictus.xmd.core.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Phase 5 (Browser) migration, step 1: pure state/logic extracted out of
 * BrowserFragment, no UI change yet -- see COMPOSE_MIGRATION.md.
 *
 * Owns tab *metadata* (this survives BrowserFragment's view being
 * recreated, e.g. a config change) and the DNS-over-HTTPS client used by
 * the Browser's Private DNS setting. Deliberately does NOT own the actual
 * WebView instances or their saveState() bundles -- those are
 * Context-bound/View-lifecycle-bound and stay in BrowserFragment's own
 * tabId->WebView pool (see webViews/webViewStates + the "WebView pool"
 * section there). A tab surviving here with no live WebView behind it is
 * exactly the same "cold tab" path activateTab()/ensureWebView() already
 * handle for LRU-evicted tabs, so this doesn't need any new handling on
 * the Fragment side.
 */
class BrowserViewModel : ViewModel() {

    /**
     * One open tab's metadata. Same shape as the old BrowserFragment-local
     * BrowserTab data class, minus [webView]/[webViewState] (see class doc
     * above for why those stayed behind in the Fragment).
     */
    data class BrowserTabState(
        val id: Long,
        var url: String? = null,
        var title: String = "New tab",
        var isLoading: Boolean = false,
        var progress: Int = 0,
        // Chrome-style per-tab "Desktop site" toggle -- swaps the WebView's
        // user agent + viewport handling and reloads. Lives on the tab (not
        // globally) since real browsers scope this to the page you're on.
        var isDesktopMode: Boolean = false,
        // Private/incognito tab: no HistoryRepository writes, and its own
        // isolated cookie jar torn down when the tab closes instead of the
        // shared persistent one.
        val isPrivate: Boolean = false,
        // Streams MediaSniffer has found on this tab's current page, keyed
        // by URL to dedupe -- insertion-ordered so the sheet lists them in
        // discovery order. Cleared on every navigation (onPageStarted).
        // shouldInterceptRequest can fire concurrently from more than one
        // WebView background thread for parallel sub-resource loads, so
        // this needs to be a synchronized map, not a plain LinkedHashMap.
        val sniffedMedia: MutableMap<String, MediaSniffer.Sniffed> =
            java.util.Collections.synchronizedMap(LinkedHashMap())
    )

    val tabs = mutableListOf(BrowserTabState(id = 0L))
    var currentTabIndex = 0
    var nextTabId = 1L

    // ── DNS-over-HTTPS client (Browser-only; see DnsOverHttpsResolver) ─────
    // Rebuilt whenever the DNS setting changes (mode or custom URL) --
    // cheap to construct, and this keeps every subsequent request using
    // whatever the user picked without needing a restart. Null when DNS
    // mode is OFF, in which case BrowserFragment's shouldInterceptRequest
    // lets WebView handle the request itself (system DNS) instead of
    // intercepting. shouldInterceptRequest fires on WebView's own
    // background thread(s) and can run for several sub-resources -- across
    // potentially several live tabs -- concurrently, so this cache is
    // guarded rather than plain vars, and the client itself is sized for
    // real concurrency (see currentDohClient) instead of OkHttp's default
    // 5-per-host cap, which was serializing sub-resource fetches from the
    // same CDN host.
    @Volatile private var dohClient: OkHttpClient? = null
    @Volatile private var dohClientSignature: String? = null
    private val dohClientLock = Any()

    /** (Re)builds dohClient only if the effective DNS setting actually changed. */
    fun currentDohClient(): OkHttpClient? {
        val mode = Settings.dnsMode()
        if (mode == Settings.DnsMode.OFF) {
            return null
        }
        val dohUrl = when (mode) {
            Settings.DnsMode.CUSTOM -> Settings.dnsCustomUrl().ifBlank { DnsOverHttpsResolver.ADGUARD_DOH_URL }
            Settings.DnsMode.GOOGLE -> DnsOverHttpsResolver.GOOGLE_DOH_URL
            Settings.DnsMode.CLOUDFLARE -> DnsOverHttpsResolver.CLOUDFLARE_DOH_URL
            Settings.DnsMode.CLOUDFLARE_ADBLOCK -> DnsOverHttpsResolver.CLOUDFLARE_ADBLOCK_DOH_URL
            else -> DnsOverHttpsResolver.ADGUARD_DOH_URL
        }
        val signature = "$mode:$dohUrl"
        if (signature == dohClientSignature) return dohClient

        synchronized(dohClientLock) {
            if (signature == dohClientSignature) return dohClient
            val built = OkHttpClient.Builder()
                .dns(DnsOverHttpsResolver(dohUrl))
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                // Default OkHttp concurrency (64 total / 5 per host) throttles
                // pages that pull many sub-resources from the same CDN host --
                // each shouldInterceptRequest call blocks its WebView thread
                // until the response comes back, so a tight per-host cap
                // serializes what should be parallel fetches. Raised to match
                // what a real browser keeps open per origin.
                .dispatcher(Dispatcher().apply {
                    maxRequests = 64
                    maxRequestsPerHost = 16
                })
                .connectionPool(ConnectionPool(24, 5, TimeUnit.MINUTES))
                .build()
            dohClient = built
            dohClientSignature = signature
            return built
        }
    }

    /** Warms the DoH resolver's host cache for [url] in the background right
     *  as navigation starts, so by the time shouldInterceptRequest actually
     *  needs the address it's often already resolved instead of paying a
     *  DNS round-trip on the critical path of the very first request. */
    fun prefetchDns(url: String) {
        val client = currentDohClient() ?: return
        val host = runCatching { android.net.Uri.parse(url).host }.getOrNull() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { client.dns.lookup(host) }
        }
    }
}
