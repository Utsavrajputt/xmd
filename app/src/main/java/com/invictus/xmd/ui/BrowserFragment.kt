package com.invictus.xmd.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.invictus.xmd.R
import com.invictus.xmd.core.BookmarkRepository
import com.invictus.xmd.core.Shortcut
import com.invictus.xmd.core.ShortcutRepository
import com.invictus.xmd.core.DnsOverHttpsResolver
import com.invictus.xmd.core.DownloadEngine
import com.invictus.xmd.core.FaviconLoader
import com.invictus.xmd.core.HistoryRepository
import com.invictus.xmd.core.LinkParser
import com.invictus.xmd.core.Settings
import com.invictus.xmd.core.SuggestApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Mini in-app browser: address bar + WebView pool, with a Chrome-style
 * speed-dial grid shown in place of the WebView on "new tab" (i.e.
 * whenever there's no URL loaded). Typing in the address bar shows
 * generic Google suggest results (see SuggestApi) -- no site list is
 * bundled with this app. Auto-detects fuckingfast/fitgirl links on the
 * current page and surfaces a FAB to send them to the Home download
 * queue; also intercepts any file download the page itself triggers
 * (WebView's native download signal) behind a confirm dialog.
 *
 * Each open tab owns its own WebView instance (up to [MAX_LIVE_WEBVIEWS]
 * kept alive at once, LRU-recycled beyond that -- see the "Tab pool"
 * section) instead of one WebView being re-pointed at different tabs.
 * That means switching tabs is a plain view swap (crossfaded) with no
 * reload and no restoreState() round-trip for whichever tabs are still
 * live in the pool; a tab that got evicted restores from its saved
 * WebView state on next visit instead of hitting the network again.
 *
 * The overflow (3-dot) menu is Browser-specific -- Private DNS and
 * History only, deliberately with no download-related options, kept
 * entirely separate from the app-wide download Settings dialog reachable
 * from Home/Downloads. When Private DNS isn't off, every request the
 * WebView makes (page + every sub-resource) is routed through an OkHttp
 * client using DnsOverHttpsResolver instead of the system resolver.
 */
class BrowserFragment : Fragment() {

    interface Callbacks {
        /** Same handoff HomeFragment uses for pasted links -- expands + queues + resolves. */
        fun triggerPrepare(lines: List<String>)
        /** Opens the Browser's own overflow menu (Private DNS, History) --
         *  deliberately separate from the app-wide download Settings dialog,
         *  which the Browser's overflow no longer opens. [anchor] is the
         *  3-dot button itself, so the menu can be anchored/dropped down
         *  from it Chrome-style instead of popping up as a centered dialog. */
        fun openBrowserMenu(anchor: View)
        /** A stream MediaSniffer picked up was tapped in the "videos found"
         *  sheet. HLS/DASH ([needsPicker] true) routes through the same
         *  quality-picker flow as a YouTube link (resolveYoutube reused
         *  as-is -- yt-dlp's generic extractor handles a raw manifest URL
         *  the same way); direct video/audio goes straight to READY like
         *  any other direct-download link. */
        fun triggerSniffedMedia(url: String, needsPicker: Boolean)
    }

    companion object {
        /** Max WebView instances kept alive across all tabs at once. Beyond
         *  this, the least-recently-used *non-current* tab's WebView is
         *  torn down (state saved first) to keep memory bounded, same
         *  general idea as Chrome's background tab discarding. */
        private const val MAX_LIVE_WEBVIEWS = 5
        private const val TAB_SWITCH_ANIM_MS = 130L
        /** Cap on local history matches shown in the address-bar dropdown --
         *  Chrome-style: a handful of your own visited pages, not a full list,
         *  since the remaining rows are Google's live search suggestions. */
        private const val MAX_HISTORY_SUGGESTIONS = 5
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Safari/537.36"
        // WebView's own default UA embeds a "; wv)" marker (and a
        // "Version/4.0 " token before "Chrome/") identifying it as an
        // in-app WebView rather than the real Chrome browser -- Google
        // (accounts.google.com) actively detects and blocks sign-in on
        // exactly that marker ("Error 403: disallowed_useragent"), even
        // though the WebView is otherwise fully capable of the login flow.
        // Used for every non-desktop tab (not just left as WebView's
        // default) so Google -- and any other site doing the same
        // useragent sniffing -- treats this browser like a normal one.
        private const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Mobile Safari/537.36"
    }

    /**
     * One open tab. Each tab owns its WebView lazily -- created on first
     * navigation, possibly torn down later under pool pressure -- so a
     * pile of "New tab" entries sitting on the speed dial costs nothing.
     * [webViewState] is the WebView.saveState() snapshot taken whenever
     * this tab's WebView gets torn down (LRU eviction, or explicitly
     * reset to blank), letting a later visit restore instantly instead
     * of reloading from the network.
     */
    private data class BrowserTab(
        val id: Long,
        var url: String? = null,
        var title: String = "New tab",
        var webView: WebView? = null,
        var webViewState: android.os.Bundle? = null,
        var isLoading: Boolean = false,
        var progress: Int = 0,
        // Chrome-style per-tab "Desktop site" toggle -- swaps the WebView's
        // user agent + viewport handling and reloads. Lives on the tab (not
        // globally) since real browsers scope this to the page you're on.
        var isDesktopMode: Boolean = false,
        // Private/incognito tab: no HistoryRepository writes, and its own
        // isolated cookie jar torn down when the tab closes (see
        // closeTab/destroyTabWebView) instead of the shared persistent one.
        val isPrivate: Boolean = false,
        // Streams MediaSniffer has found on this tab's current page, keyed
        // by URL to dedupe -- insertion-ordered so the sheet lists them in
        // discovery order. Cleared on every navigation (onPageStarted).
        // shouldInterceptRequest can fire concurrently from more than one
        // WebView background thread for parallel sub-resource loads, so
        // this needs to be a synchronized map, not a plain LinkedHashMap.
        val sniffedMedia: MutableMap<String, com.invictus.xmd.core.MediaSniffer.Sniffed> =
            java.util.Collections.synchronizedMap(LinkedHashMap())
    )

    private lateinit var newTabButton: ImageButton
    private lateinit var homeButton: ImageButton
    private lateinit var urlInput: EditText
    private lateinit var tabsButton: FrameLayout
    private lateinit var tabsCount: android.widget.TextView
    private lateinit var overflowButton: ImageButton
    private lateinit var pageProgress: LinearProgressIndicator
    private lateinit var siteSecurityIcon: ImageView
    private lateinit var bookmarkStarButton: ImageButton
    private lateinit var webViewSwipeRefresh: SwipeRefreshLayout
    private lateinit var webViewContainer: FrameLayout
    private lateinit var navLoadingVeil: View
    private lateinit var speedDialContainer: View
    private lateinit var speedDialGrid: RecyclerView
    private lateinit var shortcutReorderToggle: android.widget.TextView
    private var shortcutTouchHelper: androidx.recyclerview.widget.ItemTouchHelper? = null
    private lateinit var addLinkFab: FloatingActionButton
    private lateinit var sniffedMediaFab: com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
    private lateinit var suggestionsCard: MaterialCardView
    private lateinit var suggestionsList: RecyclerView
    private lateinit var findInPageBar: MaterialCardView
    private lateinit var findInPageInput: EditText
    private lateinit var findInPageMatchCount: android.widget.TextView
    private lateinit var findInPagePrev: ImageButton
    private lateinit var findInPageNext: ImageButton
    private lateinit var findInPageClose: ImageButton

    private lateinit var adapter: ShortcutAdapter
    private lateinit var suggestionAdapter: SuggestionAdapter

    // The icon Uri the user just picked in the add/edit shortcut dialog,
    // set by pickIconLauncher's callback and read back when the dialog's
    // positive button is tapped. Cleared once consumed or when the dialog
    // is dismissed without saving.
    private var pendingIconUri: android.net.Uri? = null
    private var pendingIconPreview: ImageView? = null
    private val pickIconLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                pendingIconUri = uri
                pendingIconPreview?.let { preview ->
                    preview.imageTintList = null
                    preview.setPadding(0, 0, 0, 0)
                    preview.setImageURI(uri)
                }
            }
        }
    private var lastDetectedLink: String? = null
    private var suggestJob: Job? = null
    // Mirrors BookmarkRepository.bookmarks (URLs only) so the address-bar
    // star can flip filled/outline instantly without a DB round-trip on
    // every tab switch -- kept in sync by the observer in setupSpeedDial().
    private var bookmarkedUrls: Set<String> = emptySet()

    private val tabs = mutableListOf(BrowserTab(id = 0L))
    private var currentTabIndex = 0
    private var nextTabId = 1L
    // Most-recently-used order of tab IDs that currently have a live
    // WebView, oldest first. Drives LRU eviction in evictIfNeeded().
    private val tabAccessOrder = mutableListOf<Long>()

    // Own client instead of reusing MainActivity's -- this is a short-timeout,
    // fire-and-forget lookup that shouldn't share connection pool pressure
    // with the resolve/download clients.
    private val suggestClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    // Same short-timeout shape as suggestClient, dedicated to the confirm
    // dialog's real-filename probe (see onWebViewDownloadRequested) --
    // fire-and-forget, shouldn't share pool pressure with anything else.
    private val filenameClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    // ── DNS-over-HTTPS client (Browser-only; see DnsOverHttpsResolver) ─────
    // Rebuilt whenever the DNS setting changes (mode or custom URL) --
    // cheap to construct, and this keeps every subsequent request using
    // whatever the user picked without needing a restart. Null when DNS
    // mode is OFF, in which case shouldInterceptRequest below lets WebView
    // handle the request itself (system DNS) instead of intercepting.
    // shouldInterceptRequest fires on WebView's own background thread(s)
    // and can run for several sub-resources -- across potentially several
    // live tabs -- concurrently, so this cache is guarded rather than
    // plain vars, and the client itself is sized for real concurrency
    // (see currentDohClient) instead of OkHttp's default 5-per-host cap,
    // which was serializing sub-resource fetches from the same CDN host.
    @Volatile private var dohClient: OkHttpClient? = null
    @Volatile private var dohClientSignature: String? = null
    private val dohClientLock = Any()

    /** (Re)builds dohClient only if the effective DNS setting actually changed. */
    private fun currentDohClient(): OkHttpClient? {
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
    private fun prefetchDns(url: String) {
        val client = currentDohClient() ?: return
        val host = runCatching { android.net.Uri.parse(url).host }.getOrNull() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { client.dns.lookup(host) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_browser, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        newTabButton = view.findViewById(R.id.newTabButton)
        homeButton = view.findViewById(R.id.homeButton)
        urlInput = view.findViewById(R.id.urlInput)
        tabsButton = view.findViewById(R.id.tabsButton)
        tabsCount = view.findViewById(R.id.tabsCount)
        overflowButton = view.findViewById(R.id.overflowButton)
        pageProgress = view.findViewById(R.id.pageProgress)
        siteSecurityIcon = view.findViewById(R.id.siteSecurityIcon)
        bookmarkStarButton = view.findViewById(R.id.bookmarkStarButton)
        webViewSwipeRefresh = view.findViewById(R.id.webViewSwipeRefresh)
        webViewContainer = view.findViewById(R.id.webViewContainer)
        navLoadingVeil = view.findViewById(R.id.navLoadingVeil)
        speedDialContainer = view.findViewById(R.id.speedDialContainer)
        speedDialGrid = view.findViewById(R.id.speedDialGrid)
        shortcutReorderToggle = view.findViewById(R.id.shortcutReorderToggle)
        addLinkFab = view.findViewById(R.id.addLinkFab)
        sniffedMediaFab = view.findViewById(R.id.sniffedMediaFab)
        suggestionsCard = view.findViewById(R.id.suggestionsCard)
        suggestionsList = view.findViewById(R.id.suggestionsList)
        findInPageBar = view.findViewById(R.id.findInPageBar)
        findInPageInput = view.findViewById(R.id.findInPageInput)
        findInPageMatchCount = view.findViewById(R.id.findInPageMatchCount)
        findInPagePrev = view.findViewById(R.id.findInPagePrev)
        findInPageNext = view.findViewById(R.id.findInPageNext)
        findInPageClose = view.findViewById(R.id.findInPageClose)

        setupSpeedDial()
        setupAddressBar()
        setupSuggestions()
        setupPullToRefresh()
        setupFindInPage()

        newTabButton.setOnClickListener { addNewTab() }
        homeButton.setOnClickListener { goHome() }
        tabsButton.setOnClickListener { showTabsDialog() }
        overflowButton.setOnClickListener { (activity as? Callbacks)?.openBrowserMenu(overflowButton) }
        addLinkFab.setOnClickListener { onAddLinkClicked() }
        sniffedMediaFab.setOnClickListener { showSniffedMediaSheet() }
        bookmarkStarButton.setOnClickListener { onBookmarkStarTapped() }

        // Start on the speed-dial ("new tab") page.
        showSpeedDial()
        updateTabsCount()
    }

    /**
     * Chromium's WebView cookie store is written lazily -- it can still be
     * sitting in an in-memory buffer, not yet on disk, when Android kills
     * a backgrounded app's process (common on battery-aggressive OEM
     * skins). Without an explicit flush here, a session cookie set moments
     * earlier (e.g. finishing a Google sign-in) can simply vanish the next
     * time the app is opened, looking like "it didn't stay logged in" even
     * though the sign-in itself worked fine.
     */
    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    /**
     * Chrome-style pull-to-refresh: only fires when the active WebView is
     * already scrolled to the top (setOnChildScrollUpCallback), same as
     * Chrome -- otherwise a downward scroll mid-page would trigger a
     * refresh instead of just scrolling. Replaces the old dedicated reload
     * button; manual reload also still available via the overflow menu's
     * "Refresh" item (see MainActivity.openBrowserMenu -> reloadActiveTab()).
     */
    private fun setupPullToRefresh() {
        webViewSwipeRefresh.setColorSchemeColors(resolveThemeColor(com.google.android.material.R.attr.colorPrimary))
        webViewSwipeRefresh.setOnChildScrollUpCallback { _, _ ->
            tabs.getOrNull(currentTabIndex)?.webView?.canScrollVertically(-1) == true
        }
        webViewSwipeRefresh.setOnRefreshListener {
            val webView = tabs.getOrNull(currentTabIndex)?.webView
            if (webView == null) {
                webViewSwipeRefresh.isRefreshing = false
            } else {
                webView.reload()
            }
        }
    }

    /** Called from MainActivity's overflow menu "Refresh" item. */
    fun reloadActiveTab() {
        tabs.getOrNull(currentTabIndex)?.webView?.reload()
    }

    /** Called from MainActivity's overflow menu "Desktop site" checkbox. */
    fun toggleDesktopModeForCurrentTab() = toggleDesktopMode()

    /** Called from MainActivity to set the checkbox's checked state before showing the menu. */
    fun isDesktopModeOn(): Boolean = isCurrentTabDesktopMode()

    /** Overflow menu's "Clear browsing data" dialog result. Cache/cookies
     *  are cleared through every currently-live WebView (any tab whose
     *  WebView has been torn down by LRU eviction has nothing left to
     *  clear anyway) since there's no single global handle for either --
     *  each WebView instance owns its own cache, though the cookie jar
     *  itself is shared, so clearing it once via any instance is enough. */
    fun clearBrowsingData(clearHistory: Boolean, clearCookies: Boolean, clearCache: Boolean) {
        if (clearHistory) HistoryRepository.clearAll()
        if (clearCache) {
            tabs.mapNotNull { it.webView }.forEach { it.clearCache(true) }
        }
        if (clearCookies) {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            tabs.mapNotNull { it.webView }.forEach {
                android.webkit.WebStorage.getInstance().deleteAllData()
                it.clearFormData()
            }
        }
    }

    /** Home button: returns the *current* tab to the speed dial (unlike New
     *  Tab, which opens an additional tab) -- reuses the existing tab slot
     *  instead of growing the tab count. */
    private fun goHome() {
        val tab = tabs.getOrNull(currentTabIndex) ?: return
        resetTabToBlank(tab)
        showSpeedDial()
    }

    // ── WebView pool ─────────────────────────────────────────────────────

    private fun isCurrentTab(tab: BrowserTab): Boolean = tabs.getOrNull(currentTabIndex)?.id == tab.id

    private fun touchLru(id: Long) {
        tabAccessOrder.remove(id)
        tabAccessOrder.add(id)
    }

    /** Returns [tab]'s live WebView, creating (or restoring) it if needed. */
    private fun ensureWebView(tab: BrowserTab): WebView {
        tab.webView?.let { touchLru(tab.id); return it }

        val wv = WebView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            alpha = 0f
            visibility = View.GONE
        }
        configureWebView(wv, tab)
        webViewContainer.addView(wv)
        tab.webView = wv
        touchLru(tab.id)
        evictIfNeeded()
        return wv
    }

    /** Tears down [tab]'s WebView, snapshotting its state first so a later
     *  visit can restore instantly instead of reloading from the network. */
    private fun destroyTabWebView(tab: BrowserTab) {
        tabAccessOrder.remove(tab.id)
        val wv = tab.webView ?: return
        if (!tab.isPrivate) {
            val bundle = android.os.Bundle()
            if (wv.saveState(bundle) != null) tab.webViewState = bundle
        }
        webViewContainer.removeView(wv)
        wv.stopLoading()
        wv.destroy()
        tab.webView = null
    }

    /** Never evicts the currently active tab, even if it's the oldest entry. */
    private fun evictIfNeeded() {
        val currentId = tabs.getOrNull(currentTabIndex)?.id
        while (tabAccessOrder.size > MAX_LIVE_WEBVIEWS) {
            val victimId = tabAccessOrder.firstOrNull { it != currentId } ?: break
            val victim = tabs.find { it.id == victimId }
            if (victim != null) destroyTabWebView(victim) else tabAccessOrder.remove(victimId)
        }
    }

    /** Fully resets [tab] to a blank "New tab" state, tearing down its WebView. */
    private fun resetTabToBlank(tab: BrowserTab) {
        destroyTabWebView(tab)
        tab.url = null
        tab.title = "New tab"
        tab.webViewState = null
        tab.isLoading = false
        tab.progress = 0
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(webView: WebView, tab: BrowserTab) {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        // Android's WebView default (true) blocks any <video>/<audio> from
        // starting until a real tap on the player itself -- a page that
        // autoplays or auto-resumes via JS (no direct tap) silently never
        // starts, showing a stuck loading/buffering spinner with no error.
        // A real browser wouldn't gate this either, so match that.
        webView.settings.mediaPlaybackRequiresUserGesture = false
        // LOAD_DEFAULT: serve straight from cache whenever the cached
        // response is still valid per its own headers, only hitting the
        // network for stuff that's actually stale -- cache-first without
        // risking served-stale content on pages that opt out via headers.
        webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
        webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

        if (tab.isPrivate) {
            // Incognito: don't let this tab's requests read or write the
            // shared persistent cookie jar at all -- every other tab
            // (private or not) still shares the normal jar as before.
            CookieManager.getInstance().setAcceptCookie(false)
        } else {
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        }

        applyDesktopMode(webView, tab.isDesktopMode)

        webView.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            if (isCurrentTab(tab)) onWebViewDownloadRequested(url, contentDisposition, mimeType)
        }

        // Chrome-style long-press menu on links/images. hitTestResult never
        // carries the touch coordinates itself, so a lightweight touch
        // listener tracks the last down-point purely to anchor the popup
        // where the finger actually was; it never consumes the event
        // (always returns false) so normal scrolling/tapping/scrubbing is
        // completely untouched.
        var lastTouchX = 0f
        var lastTouchY = 0f
        webView.setOnTouchListener { _, event ->
            if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                lastTouchX = event.x
                lastTouchY = event.y
            }
            false
        }
        webView.setOnLongClickListener {
            val result = webView.hitTestResult
            showLinkContextMenu(webView, result, lastTouchX, lastTouchY)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                tab.url = url
                tab.isLoading = true
                tab.progress = 0
                tab.sniffedMedia.clear()
                if (isCurrentTab(tab)) {
                    pageProgress.setProgressCompat(0, false)
                    pageProgress.show()
                    urlInput.setText(url)
                    updateSecurityIcon(tab)
                    clearDetectedLink()
                    updateSniffedMediaFab(tab)
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                tab.isLoading = false
                val title = view.title?.takeIf { t -> t.isNotBlank() } ?: url.orEmpty()
                tab.url = url
                tab.title = title
                if (!tab.isPrivate && !url.isNullOrBlank() && url.startsWith("http")) {
                    HistoryRepository.record(url, title)
                }
                if (isCurrentTab(tab)) {
                    pageProgress.hide()
                    webViewSwipeRefresh.isRefreshing = false
                    hideNavLoadingVeil()
                    url?.let { checkPageForLinks(it) }
                }
            }

            // Safety net: if a navigation fails outright (no connectivity, bad
            // host, etc.) onPageFinished still fires afterwards for the failed
            // load in practice, but hiding here too means the veil can never
            // get stuck up on an error path.
            override fun onReceivedError(
                view: WebView,
                request: android.webkit.WebResourceRequest,
                error: android.webkit.WebResourceError
            ) {
                tab.isLoading = false
                if (request.isForMainFrame && isCurrentTab(tab)) {
                    hideNavLoadingVeil()
                    webViewSwipeRefresh.isRefreshing = false
                }
            }

            /**
             * Pages (FB/Instagram/WhatsApp/etc.) love redirecting to a
             * non-http deep-link scheme -- `fb://native_post/...`,
             * `intent://applink.instagram.com/...#Intent;...;end`,
             * `whatsapp://`, `market://`, `upi://`, `mailto:`, `tel:` -- meant
             * to hand off to that app's native handler. WebView has no idea
             * what to do with those itself and fails hard with
             * net::ERR_UNKNOWN_URL_SCHEME ("Web page not available"). Only
             * plain http/https is left for WebView to load normally; every
             * other scheme is resolved to a real Intent and fired at
             * whatever app on the device claims it, so the same links behave
             * the way they would in Chrome instead of dead-ending here.
             */
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
                    // Never let a deep link boomerang back into XMD itself.
                    intent.component = null
                    intent.selector = null

                    val pm = requireContext().packageManager
                    if (intent.resolveActivity(pm) != null) {
                        startActivity(intent)
                    } else {
                        // No app installed to catch it (e.g. FB app absent).
                        // `intent://` links commonly carry a
                        // S.browser_fallback_url extra for exactly this case
                        // -- follow it so the user lands on the web version
                        // instead of a dead "page not available" screen.
                        val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                        if (fallbackUrl != null) view.loadUrl(fallbackUrl)
                    }
                } catch (e: Exception) {
                    // Malformed intent URI, or the resolved app rejected the
                    // launch -- nothing more we can do, just don't crash or
                    // fall through to WebView trying (and failing) to load it.
                }
                return true
            }

            /**
             * Passive media sniff -- runs on every GET request regardless of
             * the Private DNS/DoH setting below (unlike that path, this never
             * touches the network itself: pure URL-pattern matching against
             * MediaSniffer, so it's effectively free per call). Always
             * returns null after recording a match so the request continues
             * completely untouched -- this must never be the thing that
             * decides how a request is actually served.
             */
            private fun sniffRequest(view: WebView, request: android.webkit.WebResourceRequest) {
                if (request.method != "GET") return
                val url = request.url.toString()
                val sniffed = com.invictus.xmd.core.MediaSniffer.classifyUrl(url) ?: return
                val isNew = tab.sniffedMedia.put(url, sniffed) == null
                if (isNew && isCurrentTab(tab)) {
                    view.post { updateSniffedMediaFab(tab) }
                }
            }

            /**
             * Routes every request the page makes -- the page itself and
             * every sub-resource (images, JS, CSS, XHR, etc.) -- through
             * OkHttp using DnsOverHttpsResolver, so DNS resolution follows
             * the Browser's Private DNS setting instead of the system
             * resolver. Only GET requests with no body are intercepted;
             * anything else (POST forms, main-frame navigations WebView
             * needs to handle itself for redirects/cookies/etc.) is left
             * to fall through to WebView's own network stack by returning
             * null, same as if this override didn't exist. When DNS mode
             * is OFF, currentDohClient() returns null and every request
             * falls through untouched -- zero overhead in that mode.
             */
            override fun shouldInterceptRequest(
                view: WebView, request: android.webkit.WebResourceRequest
            ): android.webkit.WebResourceResponse? {
                // Cheapest possible check first, ahead of even the media
                // sniff -- a Set lookup on the request's own host, no
                // network, no DNS. Applies regardless of method or DNS
                // mode: an ad request is an ad request whether it's a GET
                // for an image or a POST beacon. An empty 200 (rather than
                // returning null and letting it 404/timeout naturally) is
                // what keeps pages from stalling on a blocked request or
                // logging it as a load failure.
                if (Settings.adblockEnabled() &&
                    com.invictus.xmd.core.AdblockFilter.isBlocked(request.url.host)
                ) {
                    return android.webkit.WebResourceResponse(
                        "text/plain", "UTF-8", java.io.ByteArrayInputStream(ByteArray(0))
                    )
                }

                sniffRequest(view, request)

                if (request.method != "GET") return null
                val client = currentDohClient() ?: return null
                val url = request.url.toString()
                if (!url.startsWith("http")) return null

                // <video>/<audio> playback lives and dies by HTTP Range
                // requests (seeking, adaptive buffering) -- this proxy path
                // does a single synchronous OkHttp call per request and was
                // never built to stream partial-content responses back to
                // WebView correctly, so routing media through it silently
                // broke playback on any site that isn't YouTube (whose
                // player fetches through its own JS pipeline rather than a
                // plain WebView-level GET). Let WebView's native network
                // stack handle anything media-shaped, or anything already
                // asking for a byte range, regardless of the DNS setting --
                // this is the one exception to "everything goes through the
                // DoH client when a mode is set."
                if (request.requestHeaders.keys.any { it.equals("Range", ignoreCase = true) }) return null
                if (com.invictus.xmd.core.MediaSniffer.classifyUrl(url) != null) return null

                return try {
                    val reqBuilder = Request.Builder().url(url)
                    request.requestHeaders.forEach { (name, value) -> reqBuilder.header(name, value) }
                    val response = client.newCall(reqBuilder.build()).execute()
                    // OkHttp's default CookieJar is CookieJar.NO_COOKIES -- it
                    // doesn't touch WebView's CookieManager at all, so any
                    // Set-Cookie this DoH-routed fetch receives would
                    // otherwise just vanish instead of being stored. That's
                    // silent and easy to miss on an ordinary page, but it's
                    // exactly what breaks Google's cross-domain single
                    // sign-on: staying logged into Drive/Gmail/etc. after
                    // signing into YouTube depends on a background
                    // sub-resource request (not the main-frame navigation
                    // WebView still handles itself) setting a shared
                    // .google.com session cookie. Every hop of the redirect
                    // chain is walked -- not just the final response -- since
                    // OkHttp only exposes each hop's own headers via
                    // priorResponse, and a cookie can legitimately be set on
                    // an intermediate redirect rather than the final URL.
                    generateSequence(response) { it.priorResponse }.toList().asReversed().forEach { hop ->
                        hop.headers("Set-Cookie").forEach { cookie ->
                            CookieManager.getInstance().setCookie(hop.request.url.toString(), cookie)
                        }
                    }
                    val body = response.body
                    if (body == null) {
                        response.close()
                        return null
                    }
                    val mimeType = body.contentType()?.let { "${it.type}/${it.subtype}" }
                    val charset = body.contentType()?.charset()?.name() ?: "utf-8"
                    val responseHeaders = response.headers.toMultimap()
                        .mapValues { it.value.joinToString(", ") }
                    // WebResourceResponse requires a status code >= 100; a
                    // malformed/unexpected response code from a broken DoH
                    // path would otherwise crash the WebView renderer.
                    val statusCode = response.code.takeIf { it in 100..599 } ?: 200
                    android.webkit.WebResourceResponse(
                        mimeType, charset, statusCode,
                        response.message.ifBlank { "OK" },
                        responseHeaders, body.byteStream()
                    )
                } catch (e: Exception) {
                    // DoH lookup/connection failed for this specific request --
                    // let WebView retry it through the normal system-DNS path
                    // rather than breaking the whole page load over one asset.
                    null
                }
            }
        }
        webView.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                tab.progress = newProgress
                if (isCurrentTab(tab)) {
                    // Fade out the instant the bar hits 100%, rather than
                    // waiting for onPageFinished (which can lag behind on
                    // pages that keep loading subresources after DOM-ready).
                    if (newProgress >= 100) {
                        pageProgress.hide()
                    } else {
                        pageProgress.setProgressCompat(newProgress, true)
                    }
                }
            }

            // HTML5 <video> going fullscreen (requestFullscreen(), or many
            // players' own fullscreen button) routes through here, not
            // through normal page layout -- without this override there is
            // no surface for WebView to actually render the fullscreen video
            // into, so playback silently never starts even after a real tap
            // on the player. view is the native video surface WebView built;
            // just needs a place to live and a way back out.
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (fullscreenView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                fullscreenView = view
                fullscreenCallback = callback
                val decor = requireActivity().window.decorView as ViewGroup
                decor.addView(
                    view,
                    ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                )
                setImmersiveMode(true)
            }

            override fun onHideCustomView() {
                val decor = requireActivity().window.decorView as ViewGroup
                fullscreenView?.let { decor.removeView(it) }
                fullscreenView = null
                fullscreenCallback?.onCustomViewHidden()
                fullscreenCallback = null
                setImmersiveMode(false)
            }
        }
    }

    // Fullscreen <video> state -- see onShowCustomView/onHideCustomView.
    // Fragment-level (not per-tab): only one tab can be showing a
    // fullscreen video at a time regardless of how many tabs are open.
    private var fullscreenView: View? = null
    private var fullscreenCallback: android.webkit.WebChromeClient.CustomViewCallback? = null

    /** True while a fullscreen <video> is up -- MainActivity's back handler
     *  checks this first so back exits fullscreen instead of navigating
     *  the page underneath it. */
    fun isInFullscreenVideo(): Boolean = fullscreenView != null

    /** Called by MainActivity's back handler when [isInFullscreenVideo] is
     *  true, and directly by onHideCustomView's own decor cleanup path --
     *  webView.webChromeClient?.onHideCustomView() is the documented way to
     *  ask WebView to exit fullscreen from the app side (it then calls our
     *  onHideCustomView override above to actually tear the view down). */
    fun exitFullscreenVideo() {
        tabs.getOrNull(currentTabIndex)?.webView?.webChromeClient?.onHideCustomView()
    }

    private fun setImmersiveMode(enabled: Boolean) {
        val window = requireActivity().window
        val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        if (enabled) {
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }

    /**
     * Called by MainActivity to consume system/gesture back presses while the
     * Browser tab is visible.
     *
     * If the current tab's WebView is showing a page, back either steps
     * through its in-page history or, with none left, resets the tab back
     * to the speed dial (still consumed). Only once we're already on the
     * speed dial does this return false, so MainActivity's callback can
     * fall back to the Home tab instead of exiting.
     */
    fun onBackPressed(): Boolean {
        val tab = tabs.getOrNull(currentTabIndex) ?: return false
        val view = tab.webView
        if (view != null && view.visibility == View.VISIBLE) {
            if (view.canGoBack()) {
                showNavLoadingVeil()
                view.goBack()
            } else {
                resetTabToBlank(tab)
                showSpeedDial()
            }
            return true
        }
        return false
    }

    // ── Address bar ──────────────────────────────────────────────────────

    private fun setupAddressBar() {
        urlInput.setOnEditorActionListener { _, actionId, event ->
            val isGo = actionId == EditorInfo.IME_ACTION_GO ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            if (isGo) {
                loadUrl(urlInput.text?.toString().orEmpty())
                true
            } else false
        }

        urlInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!urlInput.hasFocus()) return // programmatic sets (e.g. onPageStarted) shouldn't trigger suggest
                scheduleSuggest(s?.toString().orEmpty())
            }
        })

        // Chrome-style collapse: while not focused, show just the domain so
        // the bar reads clean; focusing expands it back to the full URL for
        // editing (full text is always what's actually in the EditText --
        // this only swaps the *displayed* selection/cursor state on focus).
        urlInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                hideSuggestions()
            } else {
                urlInput.selectAll()
            }
        }
    }

    private fun setupSuggestions() {
        suggestionAdapter = SuggestionAdapter(
            onTap = { item ->
                when (item) {
                    is SuggestionAdapter.Suggestion.History -> {
                        urlInput.setText(item.url)
                        loadUrl(item.url)
                    }
                    is SuggestionAdapter.Suggestion.Search -> {
                        urlInput.setText(item.text)
                        loadUrl(item.text)
                    }
                }
            },
            onAddTap = { phrase ->
                val url = normalizeToUrl(phrase)
                ShortcutRepository.add(title = phrase, url = url)
                Toast.makeText(requireContext(), R.string.shortcut_added_toast, Toast.LENGTH_SHORT).show()
            }
        )
        suggestionsList.layoutManager = LinearLayoutManager(requireContext())
        suggestionsList.adapter = suggestionAdapter
    }

    /**
     * 2-3 letters is enough to start querying, debounced ~150ms so we're not
     * firing a network request on every keystroke. Merges two sources,
     * history first then search (Chrome-style):
     *  - local visited-page history (HistoryRepository's already-cached
     *    LiveData value -- no DB round-trip needed here), matched by
     *    title/URL substring, capped at [MAX_HISTORY_SUGGESTIONS]
     *  - Google's public suggest endpoint, filtered to search-phrase
     *    results only (see SuggestApi) -- no bundled/bare-URL "website"
     *    suggestions of any kind
     */
    private fun scheduleSuggest(query: String) {
        suggestJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            hideSuggestions()
            return
        }
        val historyMatches = (HistoryRepository.entries.value ?: emptyList())
            .filter { it.title.contains(trimmed, ignoreCase = true) || it.url.contains(trimmed, ignoreCase = true) }
            .take(MAX_HISTORY_SUGGESTIONS)
            .map { SuggestionAdapter.Suggestion.History(text = it.title, url = it.url) }

        // History is already in memory, so it renders on this frame instead
        // of waiting on the debounce + network round-trip below -- only the
        // search half of the list is provisional at this point.
        if (historyMatches.isNotEmpty()) {
            suggestionAdapter.submitList(historyMatches)
            suggestionsCard.visibility = View.VISIBLE
        }

        suggestJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(150)
            val searchResults = withContext(Dispatchers.IO) { SuggestApi.suggest(trimmed, suggestClient) }
            if (!isAdded) return@launch
            val merged = historyMatches + searchResults.map { SuggestionAdapter.Suggestion.Search(it) }
            if (merged.isEmpty()) {
                hideSuggestions()
            } else {
                suggestionAdapter.submitList(merged)
                suggestionsCard.visibility = View.VISIBLE
            }
        }
    }

    private fun hideSuggestions() {
        suggestJob?.cancel()
        suggestionsCard.visibility = View.GONE
    }

    // ── Find in page ──────────────────────────────────────────────────────

    private fun setupFindInPage() {
        findInPageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val webView = tabs.getOrNull(currentTabIndex)?.webView ?: return
                val query = s?.toString().orEmpty()
                if (query.isEmpty()) {
                    webView.clearMatches()
                    findInPageMatchCount.text = "0/0"
                } else {
                    webView.findAllAsync(query)
                }
            }
        })
        findInPagePrev.setOnClickListener {
            tabs.getOrNull(currentTabIndex)?.webView?.findNext(false)
        }
        findInPageNext.setOnClickListener {
            tabs.getOrNull(currentTabIndex)?.webView?.findNext(true)
        }
        findInPageClose.setOnClickListener { hideFindInPage() }
    }

    /** Opened from the overflow menu's "Find in page" item. Wires the
     *  active tab's WebView.FindListener fresh each time (rather than once
     *  up front) since the active WebView instance can change between
     *  opens as tabs get created/switched/evicted. */
    fun showFindInPage() {
        val webView = tabs.getOrNull(currentTabIndex)?.webView ?: return
        webView.setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
            if (!isDoneCounting) return@setFindListener
            val current = if (numberOfMatches == 0) 0 else activeMatchOrdinal + 1
            findInPageMatchCount.text = "$current/$numberOfMatches"
        }
        findInPageBar.visibility = View.VISIBLE
        findInPageInput.requestFocus()
        val imm = requireContext().getSystemService(android.view.inputmethod.InputMethodManager::class.java)
        imm?.showSoftInput(findInPageInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideFindInPage() {
        tabs.getOrNull(currentTabIndex)?.webView?.let {
            it.clearMatches()
            it.setFindListener(null)
        }
        findInPageInput.setText("")
        findInPageBar.visibility = View.GONE
        val imm = requireContext().getSystemService(android.view.inputmethod.InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(findInPageInput.windowToken, 0)
    }

    private fun updateSecurityIcon(tab: BrowserTab) {
        val url = tab.url
        if (url.isNullOrBlank() || !url.startsWith("http")) {
            siteSecurityIcon.visibility = View.GONE
            return
        }
        siteSecurityIcon.visibility = View.VISIBLE
        siteSecurityIcon.setImageResource(
            if (url.startsWith("https")) R.drawable.ic_lock else R.drawable.ic_lock_open
        )
    }

    /** Filled star when the loaded page's URL is already saved as a
     *  bookmark, outline otherwise; hidden entirely on the speed dial (no
     *  page yet). */
    private fun updateBookmarkStar(tab: BrowserTab) {
        val url = tab.url
        if (url.isNullOrBlank() || !url.startsWith("http")) {
            bookmarkStarButton.visibility = View.GONE
            return
        }
        bookmarkStarButton.visibility = View.VISIBLE
        bookmarkStarButton.setImageResource(
            if (url in bookmarkedUrls) R.drawable.ic_star_filled else R.drawable.ic_star_outline
        )
    }

    /** Star tapped: adds the current page as a bookmark (via the Add
     *  Bookmark dialog, prefilled -- with a checkbox to also add it as a
     *  speed-dial Shortcut) if it isn't one yet, or removes the bookmark
     *  in one tap if it already is -- Chrome-style toggle. Never touches
     *  Shortcuts on removal; those are independent once created. */
    private fun onBookmarkStarTapped() {
        val tab = tabs.getOrNull(currentTabIndex) ?: return
        val url = tab.url ?: return
        val existing = BookmarkRepository.bookmarks.value?.firstOrNull { it.url == url }
        if (existing != null) {
            BookmarkRepository.remove(existing)
            Toast.makeText(requireContext(), R.string.bookmark_removed_toast, Toast.LENGTH_SHORT).show()
        } else {
            showAddBookmarkDialog(prefillUrl = url, prefillTitle = tab.title)
        }
    }

    /** Syncs the shared toolbar (address text, lock icon, progress, reload/
     *  stop icon, download-link FAB) from [tab]'s own state. Call whenever
     *  [tab] becomes the active one. */
    private fun applyTabUiState(tab: BrowserTab) {
        urlInput.setText(tab.url)
        updateSecurityIcon(tab)
        updateBookmarkStar(tab)
        pageProgress.setProgressCompat(tab.progress, false)
        if (tab.isLoading) pageProgress.show() else pageProgress.hide()
        webViewSwipeRefresh.isRefreshing = false
        val url = tab.url
        if (url != null) checkPageForLinks(url) else clearDetectedLink()
        updateSniffedMediaFab(tab)
    }

    /** Called from MainActivity (e.g. reopening a History entry) to load a
     *  URL in the current tab, same as typing it into the address bar. */
    fun openUrl(url: String) {
        loadUrl(url)
    }

    private fun loadUrl(raw: String) {
        val input = raw.trim()
        if (input.isEmpty()) return
        val url = normalizeToUrl(input)
        val tab = tabs.getOrNull(currentTabIndex) ?: return

        hideSuggestions()
        showWebView()
        tab.url = url
        tab.isLoading = true
        tab.progress = 0
        // Fresh explicit navigation -- any previously saved restore state
        // is now stale, so don't let a future pool eviction/recreate bring
        // back the old page instead of this one.
        tab.webViewState = null
        applyTabUiState(tab)
        showNavLoadingVeil()
        prefetchDns(url)

        val view = ensureWebView(tab)
        view.alpha = 1f
        view.visibility = View.VISIBLE
        view.loadUrl(url)

        // Drop keyboard focus so the address bar doesn't stay expanded.
        urlInput.clearFocus()
        val imm = requireContext().getSystemService(android.view.inputmethod.InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(urlInput.windowToken, 0)
    }

    /** Bare host/search text -> https URL; anything already URL-shaped is passed through. */
    private fun normalizeToUrl(input: String): String {
        val looksLikeUrl = input.contains(".") && !input.contains(" ")
        return when {
            input.startsWith("http://") || input.startsWith("https://") -> input
            looksLikeUrl -> "https://$input"
            else -> "https://www.google.com/search?q=${android.net.Uri.encode(input)}"
        }
    }

    // ── Speed dial (new tab) ─────────────────────────────────────────────

    private fun setupSpeedDial() {
        adapter = ShortcutAdapter(
            onTap = { shortcut -> urlInput.setText(shortcut.url); loadUrl(shortcut.url) },
            onLongPress = { shortcut -> showShortcutOptionsDialog(shortcut) },
            onAddTap = { showAddShortcutDialog(prefillUrl = null) },
            onStartDrag = { holder -> shortcutTouchHelper?.startDrag(holder) }
        )
        speedDialGrid.layoutManager = GridLayoutManager(requireContext(), 4)
        speedDialGrid.adapter = adapter

        val touchHelper = androidx.recyclerview.widget.ItemTouchHelper(ShortcutDragCallback(adapter))
        touchHelper.attachToRecyclerView(speedDialGrid)
        shortcutTouchHelper = touchHelper

        shortcutReorderToggle.setOnClickListener {
            if (adapter.reorderMode) {
                // "Done" -- persist whatever order dragging left the grid in.
                ShortcutRepository.reorder(adapter.currentIds())
                adapter.reorderMode = false
                shortcutReorderToggle.setText(R.string.action_reorder)
            } else {
                adapter.reorderMode = true
                shortcutReorderToggle.setText(R.string.action_done)
            }
        }

        ShortcutRepository.shortcuts.observe(viewLifecycleOwner) { list ->
            // While actively dragging, the adapter's in-memory order is the
            // source of truth -- don't let a DB observer fire (e.g. from an
            // unrelated add/remove elsewhere) and stomp the drag in
            // progress. Once reorder mode ends, this resumes normally.
            if (!adapter.reorderMode) adapter.submitList(list)
        }

        // Separate from the speed-dial tiles above -- this drives the star
        // toggle in the toolbar (updateBookmarkStar), which reflects real
        // Bookmarks, not Shortcuts.
        BookmarkRepository.bookmarks.observe(viewLifecycleOwner) { list ->
            bookmarkedUrls = list.map { it.url }.toSet()
            tabs.getOrNull(currentTabIndex)?.let { updateBookmarkStar(it) }
        }
    }

    /** Drag-only (no swipe-to-dismiss) ItemTouchHelper callback for the
     *  speed-dial grid. Only active while [ShortcutAdapter.reorderMode] is
     *  on -- the fragment starts a drag itself via onStartDrag when a tile
     *  is long-pressed in that mode, so this doesn't need to detect
     *  long-press starts on its own. The trailing "+" add tile is never a
     *  drag target in either direction. */
    private class ShortcutDragCallback(
        private val adapter: ShortcutAdapter
    ) : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
        androidx.recyclerview.widget.ItemTouchHelper.UP or androidx.recyclerview.widget.ItemTouchHelper.DOWN or
            androidx.recyclerview.widget.ItemTouchHelper.LEFT or androidx.recyclerview.widget.ItemTouchHelper.RIGHT,
        0
    ) {
        override fun isLongPressDragEnabled(): Boolean = false

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
            // Last item is always the "+" add tile -- never a valid drag target.
            if (to >= adapter.itemCount - 1) return false
            adapter.moveItem(from, to)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            // No swipe-to-dismiss on speed-dial tiles; unused.
        }

        override fun canDropOver(
            recyclerView: RecyclerView,
            current: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean = target.bindingAdapterPosition < adapter.itemCount - 1
    }

    private fun showSpeedDial() {
        speedDialContainer.visibility = View.VISIBLE
        urlInput.setText("")
        siteSecurityIcon.visibility = View.GONE
        bookmarkStarButton.visibility = View.GONE
        pageProgress.hide()
        webViewSwipeRefresh.isRefreshing = false
        hideSuggestions()
        clearDetectedLink()
        sniffedMediaFab.visibility = View.GONE
        hideNavLoadingVeil()
    }

    private fun showWebView() {
        speedDialContainer.visibility = View.GONE
    }

    /**
     * Covers the content area the instant we're about to actually fetch a
     * new page over the network (typed URL/search, back/forward, or a pool
     * miss on tab switch) so the outgoing page's pixels are never visible
     * mid-load. A same-pool tab switch (the common case) never shows this --
     * it just crossfades between the two already-live WebViews instead.
     * Paired with hideNavLoadingVeil(), called once the new page has
     * actually finished (or failed) loading.
     */
    private fun showNavLoadingVeil() {
        navLoadingVeil.visibility = View.VISIBLE
        navLoadingVeil.bringToFront()
    }

    private fun hideNavLoadingVeil() {
        navLoadingVeil.visibility = View.GONE
    }

    private fun showAddShortcutDialog(prefillUrl: String?, prefillTitle: String? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_shortcut, null)
        val titleInput = dialogView.findViewById<EditText>(R.id.shortcutTitleInput)
        val urlField = dialogView.findViewById<EditText>(R.id.shortcutUrlInput)
        urlField.setText(prefillUrl ?: tabs.getOrNull(currentTabIndex)?.url)
        titleInput.setText(prefillTitle)
        wireIconPicker(dialogView)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.add_bookmark_title)
            .setView(dialogView)
            .setPositiveButton(R.string.action_add) { _, _ ->
                val url = urlField.text?.toString()?.trim().orEmpty()
                if (url.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.bookmark_needs_url, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val normalized = normalizeToUrl(url)
                val title = titleInput.text?.toString()?.trim().orEmpty()
                val pickedIcon = pendingIconUri
                if (pickedIcon != null) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        ShortcutRepository.addWithIcon(requireContext(), title, normalized, pickedIcon)
                    }
                } else {
                    ShortcutRepository.add(title, normalized)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setOnDismissListener { pendingIconUri = null; pendingIconPreview = null }
            .show()
    }

    /** Wires the icon-preview tile in dialog_add_shortcut.xml to launch the
     *  system photo picker, and (for edits) shows the shortcut's current
     *  icon -- custom if it has one, else its live favicon. */
    private fun wireIconPicker(dialogView: View, existing: Shortcut? = null) {
        pendingIconUri = null
        val previewCard = dialogView.findViewById<MaterialCardView>(R.id.shortcutIconPreviewCard)
        val preview = dialogView.findViewById<ImageView>(R.id.shortcutIconPreview)
        val pickLabel = dialogView.findViewById<android.widget.TextView>(R.id.shortcutIconPickLabel)
        pendingIconPreview = preview

        val customPath = existing?.customIconPath
        if (customPath != null) {
            val bitmap = runCatching { android.graphics.BitmapFactory.decodeFile(customPath) }.getOrNull()
            if (bitmap != null) {
                preview.imageTintList = null
                preview.setPadding(0, 0, 0, 0)
                preview.setImageBitmap(bitmap)
            }
        } else if (existing != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                val bitmap = kotlinx.coroutines.withContext(Dispatchers.IO) { FaviconLoader.load(existing.url) }
                if (bitmap != null) {
                    preview.imageTintList = null
                    preview.setPadding(0, 0, 0, 0)
                    preview.setImageBitmap(bitmap)
                }
            }
        }

        val launchPicker = { pickIconLauncher.launch("image/*") }
        previewCard.setOnClickListener { launchPicker() }
        pickLabel.setOnClickListener { launchPicker() }
    }

    private fun showShortcutOptionsDialog(shortcut: Shortcut) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(shortcut.title)
            .setItems(arrayOf(getString(R.string.edit_bookmark_title), getString(R.string.action_delete))) { _, which ->
                when (which) {
                    0 -> showEditShortcutDialog(shortcut)
                    1 -> ShortcutRepository.remove(shortcut)
                }
            }
            .show()
    }

    private fun showEditShortcutDialog(shortcut: Shortcut) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_shortcut, null)
        val titleInput = dialogView.findViewById<EditText>(R.id.shortcutTitleInput)
        val urlField = dialogView.findViewById<EditText>(R.id.shortcutUrlInput)
        titleInput.setText(shortcut.title)
        urlField.setText(shortcut.url)
        wireIconPicker(dialogView, existing = shortcut)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.edit_bookmark_title)
            .setPositiveButton(R.string.settings_save) { _, _ ->
                val url = urlField.text?.toString()?.trim().orEmpty()
                if (url.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.bookmark_needs_url, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val normalized = normalizeToUrl(url)
                val newTitle = (titleInput.text?.toString()?.trim().orEmpty())
                    .ifBlank { runCatching { java.net.URI(normalized).host }.getOrNull() ?: normalized }
                val pickedIcon = pendingIconUri
                if (pickedIcon != null) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val path = ShortcutRepository.copyIconToInternalStorage(requireContext(), pickedIcon, shortcut.id)
                        // update() preserves id + sortOrder -- editing a
                        // tile no longer bumps it to the end of the grid.
                        ShortcutRepository.update(
                            shortcut.copy(
                                title = newTitle,
                                url = normalized,
                                customIconPath = path ?: shortcut.customIconPath
                            )
                        )
                    }
                } else {
                    ShortcutRepository.update(
                        shortcut.copy(title = newTitle, url = normalized)
                    )
                }
            }
            .setView(dialogView)
            .setNegativeButton(android.R.string.cancel, null)
            .setOnDismissListener { pendingIconUri = null; pendingIconPreview = null }
            .show()
    }

    /** Star-button flow: saves a real Bookmark for the current page. The
     *  checkbox additionally creates a matching speed-dial Shortcut in the
     *  same tap -- the two lists stay independent after that (removing the
     *  bookmark later never removes the shortcut, and vice versa). */
    private fun showAddBookmarkDialog(prefillUrl: String?, prefillTitle: String? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_bookmark, null)
        val titleInput = dialogView.findViewById<EditText>(R.id.bookmarkTitleInput)
        val urlField = dialogView.findViewById<EditText>(R.id.bookmarkUrlInput)
        val alsoAddShortcutCheckbox = dialogView.findViewById<android.widget.CheckBox>(R.id.bookmarkAlsoAddShortcutCheckbox)
        urlField.setText(prefillUrl ?: tabs.getOrNull(currentTabIndex)?.url)
        titleInput.setText(prefillTitle)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.add_bookmark_dialog_title)
            .setView(dialogView)
            .setPositiveButton(R.string.action_add) { _, _ ->
                val url = urlField.text?.toString()?.trim().orEmpty()
                if (url.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.bookmark_needs_url, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val normalized = normalizeToUrl(url)
                val title = titleInput.text?.toString()?.trim().orEmpty()
                BookmarkRepository.add(title, normalized)
                if (alsoAddShortcutCheckbox.isChecked) {
                    ShortcutRepository.add(title, normalized)
                }
                Toast.makeText(requireContext(), R.string.bookmark_added_toast, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ── Tabs ─────────────────────────────────────────────────────────────

    private fun updateTabsCount() {
        tabsCount.text = tabs.size.toString()
    }

    private fun addNewTab() {
        val previousView = tabs.getOrNull(currentTabIndex)?.webView
        tabs.add(BrowserTab(id = nextTabId++))
        currentTabIndex = tabs.lastIndex
        previousView?.let {
            it.animate().cancel()
            it.alpha = 0f
            it.visibility = View.GONE
        }
        showSpeedDial()
        updateTabsCount()
    }

    /**
     * Switches the content area to show [index]'s tab. Since every tab owns
     * its own WebView (up to the pool cap), this is a crossfade between two
     * already-rendered views for the common case -- no reload, no
     * restoreState() -- and only falls back to a real load (with the
     * loading veil) when the tab's WebView isn't currently live, i.e. it
     * either just got LRU-evicted or has genuinely never been opened.
     *
     * [previousView] is the outgoing WebView to crossfade away from; left
     * at its default (the current tab's live WebView, if any) for a normal
     * switch, but passed explicitly as null by callers that already tore
     * down the outgoing tab themselves (e.g. closeTab) so it isn't touched
     * twice.
     */
    private fun activateTab(
        index: Int,
        previousView: WebView? = tabs.getOrNull(currentTabIndex)?.webView
    ) {
        currentTabIndex = index
        val tab = tabs[index]
        // CookieManager.setAcceptCookie is a single global flag, not scoped
        // to one WebView -- re-applied on every switch so whichever tab is
        // now active (private or not) is the one whose cookie policy is in
        // effect, regardless of what the previously-active tab last set it to.
        CookieManager.getInstance().setAcceptCookie(!tab.isPrivate)

        if (tab.url.isNullOrBlank()) {
            previousView?.let {
                it.animate().cancel()
                it.alpha = 0f
                it.visibility = View.GONE
            }
            showSpeedDial()
            return
        }

        showWebView()
        applyTabUiState(tab)
        val hadLiveView = tab.webView != null
        val view = ensureWebView(tab)
        if (!hadLiveView) {
            showNavLoadingVeil()
            val state = tab.webViewState
            if (state != null) {
                // Restores from WebView's own cache/history -- no network
                // round-trip, so this is still fast even on a pool miss.
                view.restoreState(state)
            } else {
                view.loadUrl(tab.url!!)
            }
        }
        crossfadeSwap(view, previousView.takeIf { it !== view })
    }

    /** Crossfades [newView] in over [oldView] (if any, and if different). */
    private fun crossfadeSwap(newView: WebView, oldView: WebView?) {
        if (oldView == null) {
            newView.animate().cancel()
            newView.alpha = 1f
            newView.visibility = View.VISIBLE
            return
        }
        newView.animate().cancel()
        newView.alpha = 0f
        newView.visibility = View.VISIBLE
        newView.animate().alpha(1f).setDuration(TAB_SWITCH_ANIM_MS).start()

        oldView.animate().cancel()
        oldView.animate().alpha(0f).setDuration(TAB_SWITCH_ANIM_MS).withEndAction {
            oldView.visibility = View.GONE
        }.start()
    }

    /**
     * Closes a tab. Never drops below one tab -- closing the last remaining
     * one just resets it to a fresh "New tab" instead of removing it, same
     * as closing the last tab in a normal browser (a new tab effectively
     * "opens" automatically since the speed dial is shown right away).
     */
    private fun closeTab(index: Int) {
        if (index !in tabs.indices) return
        if (tabs.size == 1) {
            resetTabToBlank(tabs[0])
            showSpeedDial()
            updateTabsCount()
            return
        }
        val closingTab = tabs[index]
        val closingCurrent = index == currentTabIndex
        destroyTabWebView(closingTab)
        tabs.removeAt(index)
        when {
            // previousView = null: closingTab's WebView is already torn
            // down above, so activateTab shouldn't try to crossfade/hide it again.
            closingCurrent -> activateTab(index.coerceAtMost(tabs.size - 1), previousView = null)
            index < currentTabIndex -> currentTabIndex--
        }
        updateTabsCount()
    }

    private fun switchToTab(index: Int) {
        if (index !in tabs.indices || index == currentTabIndex) return
        activateTab(index)
    }

    /**
     * Tabs tray: a bottom sheet (not a modal dialog) listing every open tab
     * as a compact pill -- round icon, title, close X -- with a floating
     * "+" beneath the list instead of a dialog footer button.
     */
    private fun showTabsDialog() {
        val context = requireContext()
        fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(context)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(20))
        }

        val rowsContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        root.addView(rowsContainer)

        val addRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
        }
        addRow.addView(
            com.google.android.material.floatingactionbutton.FloatingActionButton(context).apply {
                setImageResource(R.drawable.ic_add)
                size = com.google.android.material.floatingactionbutton.FloatingActionButton.SIZE_MINI
                contentDescription = getString(R.string.action_new_tab)
                setOnClickListener {
                    addNewTab()
                    dialog.dismiss()
                }
            }
        )
        root.addView(addRow)

        fun refreshRows() {
            rowsContainer.removeAllViews()
            tabs.forEachIndexed { index, tab ->
                val isActive = index == currentTabIndex
                // Private tabs get a fixed dark tonal treatment regardless of
                // active/inactive state or app theme -- same idea as Chrome's
                // distinct grey/black incognito tab strip, so it's visually
                // obvious at a glance which tabs won't show up in history.
                val tonalColor = when {
                    tab.isPrivate -> android.graphics.Color.parseColor(if (isActive) "#3A3A3A" else "#2A2A2A")
                    isActive -> resolveThemeColor(com.google.android.material.R.attr.colorSecondaryContainer)
                    else -> resolveThemeColor(com.google.android.material.R.attr.colorSurfaceContainerHigh)
                }
                val onTonalColor = if (tab.isPrivate) {
                    android.graphics.Color.WHITE
                } else {
                    resolveThemeColor(
                        if (isActive) com.google.android.material.R.attr.colorOnSecondaryContainer
                        else com.google.android.material.R.attr.colorOnSurface
                    )
                }

                // Pill-shaped card (fully rounded, not just rounded-corner)
                // with a round icon avatar -- the reference tray's rows read
                // as floating chips rather than settings-style list items.
                val row = MaterialCardView(context).apply {
                    radius = dp(28).toFloat()
                    cardElevation = 0f
                    strokeWidth = 0
                    setCardBackgroundColor(tonalColor)
                    isClickable = true
                    isFocusable = true
                    rippleColor = android.content.res.ColorStateList.valueOf(onTonalColor)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 0, 0, dp(8)) }
                    alpha = 0f
                    translationY = dp(14).toFloat()
                }

                val innerRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(dp(8), dp(8), dp(10), dp(8))
                }

                val faviconBox = FrameLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply {
                        marginEnd = dp(12)
                    }
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(android.graphics.Color.WHITE)
                    }
                }
                val favicon = ImageView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(dp(16), dp(16)).apply {
                        gravity = android.view.Gravity.CENTER
                    }
                    setImageResource(if (tab.isPrivate) R.drawable.ic_private_tab else R.drawable.ic_link)
                    setColorFilter(android.graphics.Color.parseColor("#1A1A1A"))
                }
                faviconBox.addView(favicon)
                // Private tabs always show the incognito glyph, never the
                // site's real favicon -- fetching/showing it here would be a
                // minor but real leak of what a "private" tab is looking at.
                if (!tab.isPrivate) {
                    tab.url?.let { url ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            val bitmap = withContext(Dispatchers.IO) { FaviconLoader.load(url) }
                            if (bitmap != null) {
                                favicon.clearColorFilter()
                                favicon.layoutParams = FrameLayout.LayoutParams(dp(20), dp(20)).apply {
                                    gravity = android.view.Gravity.CENTER
                                }
                                favicon.setImageBitmap(bitmap)
                            }
                        }
                    }
                }

                val label = android.widget.TextView(context).apply {
                    text = tab.title.ifBlank { tab.url ?: "New tab" }
                    setTextColor(onTonalColor)
                    textSize = 14f
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }

                val closeBtn = ImageButton(context).apply {
                    setImageResource(R.drawable.ic_close)
                    background = null
                    setColorFilter(onTonalColor)
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                    contentDescription = getString(R.string.action_dismiss)
                    // Every tab is closable, including the last one -- closeTab()
                    // resets it to a fresh "New tab" (speed dial) in that case,
                    // so a new tab effectively opens automatically.
                    setOnClickListener {
                        row.animate().alpha(0f).translationX(dp(40).toFloat()).setDuration(120).withEndAction {
                            closeTab(index)
                            refreshRows()
                        }.start()
                    }
                }
                row.setOnClickListener {
                    switchToTab(index)
                    dialog.dismiss()
                }
                innerRow.addView(faviconBox)
                innerRow.addView(label)
                innerRow.addView(closeBtn)
                row.addView(innerRow)
                rowsContainer.addView(row)
                // Small staggered fade+rise entrance so the list doesn't just pop in.
                row.animate().alpha(1f).translationY(0f)
                    .setStartDelay((index * 24L).coerceAtMost(200L))
                    .setDuration(160)
                    .start()
            }
        }
        refreshRows()

        dialog.setContentView(root)
        dialog.show()
    }

    // ── Link auto-detect ─────────────────────────────────────────────────

    /**
     * Fires for ANY download the WebView's content triggers -- an <a
     * download> click, a redirect to a file with a Content-Disposition
     * header, or navigation straight to a file mimetype (apk/zip/mp4/pdf/
     * etc). This is a completely different path from checkPageForLinks:
     * that one watches the page's own URL for fuckingfast/fitgirl links
     * (site-specific, auto-shows a FAB); this one catches the browser's
     * native "start a download" signal for arbitrary files from any site.
     * Always confirms before queuing since it fires on real clicks, not
     * just heuristics.
     *
     * The contentDisposition WebView hands us here is frequently missing
     * or generic on sites like this (vcloud/gofile-style hosts serving a
     * token URL with no filename in the path) -- URLUtil.guessFileName then
     * has nothing real to work with and falls back to a mostly-made-up name
     * (e.g. "Outer.bin"). The actual filename only reliably shows up in the
     * *response's* Content-Disposition header, so show the dialog right
     * away with the best guess, then probe the URL directly and swap in
     * the real name if it resolves before the user taps a button.
     */
    private fun onWebViewDownloadRequested(url: String, contentDisposition: String?, mimeType: String?) {
        val guessedName = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType)
        var resolvedName = guessedName

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.download_confirm_title)
            .setMessage(getString(R.string.download_confirm_message, guessedName))
            .setPositiveButton(R.string.action_add_to_downloads) { _, _ ->
                (activity as? Callbacks)?.triggerPrepare(listOf(url))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.action_copy_link) { _, _ -> copyLinkToClipboard(url) }
            .show()

        lifecycleScope.launch {
            val probed = withContext(Dispatchers.IO) {
                DownloadEngine.probeRealFilename(filenameClient, url)
            }
            if (probed != null && probed != resolvedName && dialog.isShowing) {
                resolvedName = probed
                dialog.setMessage(getString(R.string.download_confirm_message, probed))
            }
        }
    }

    /**
     * Cheap, synchronous check against the page's own URL first (covers the
     * common case: user navigated straight to a share link or a
     * fitgirl-repacks post). We don't scrape the rendered DOM for
     * further off-URL share links here -- LinkParser.expandSources already
     * does that server-side (via Jsoup) once the link is handed to
     * triggerPrepare, so re-implementing it against WebView's DOM would be
     * redundant.
     */
    private fun checkPageForLinks(url: String) {
        if (LinkParser.isShareLink(url) || LinkParser.isFitgirlPage(url)) {
            lastDetectedLink = url
            addLinkFab.visibility = View.VISIBLE
        } else {
            clearDetectedLink()
        }
    }

    private fun clearDetectedLink() {
        lastDetectedLink = null
        addLinkFab.visibility = View.GONE
    }

    /** Reflects [tab]'s current sniffedMedia count onto the chip -- called
     *  from onPageStarted (clears it), and from shouldInterceptRequest's
     *  sniff hook every time a genuinely new stream URL is found. No-op
     *  visually unless [tab] is the tab currently on screen. */
    private fun updateSniffedMediaFab(tab: BrowserTab) {
        if (!isCurrentTab(tab)) return
        val count = tab.sniffedMedia.size
        if (count == 0) {
            sniffedMediaFab.visibility = View.GONE
            return
        }
        sniffedMediaFab.text = if (count == 1) {
            getString(R.string.sniffed_media_chip_one)
        } else {
            getString(R.string.sniffed_media_chip_many, count)
        }
        sniffedMediaFab.visibility = View.VISIBLE
    }

    /** Bottom sheet listing every stream in the current tab's sniffedMedia,
     *  tapping a row hands it straight to Callbacks.triggerSniffedMedia; each
     *  row also carries a copy button to grab the raw URL without starting
     *  a download. */
    private fun showSniffedMediaSheet() {
        val tab = tabs.getOrNull(currentTabIndex) ?: return
        // Snapshot under the same lock shouldInterceptRequest writes under --
        // sniffedMedia is a synchronizedMap precisely so this read (main
        // thread) can't race a concurrent write (WebView background thread).
        val streams = synchronized(tab.sniffedMedia) { tab.sniffedMedia.values.toList() }
        if (streams.isEmpty()) return

        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.sheet_sniffed_media, null)
        dialog.setContentView(view)

        val list = view.findViewById<LinearLayout>(R.id.sniffedMediaList)
        val density = resources.displayMetrics.density
        streams.forEach { stream ->
            // Row is now a label (tap = download, same as before) plus a
            // trailing copy button, instead of one full-width TextView --
            // lets the user grab the raw media URL without kicking off a
            // download, same tonal round-icon-button pattern used for
            // "Copy link" in the Add Torrent dialog.
            val rowContainer = LinearLayout(requireContext())
            rowContainer.orientation = LinearLayout.HORIZONTAL
            rowContainer.gravity = android.view.Gravity.CENTER_VERTICAL
            rowContainer.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

            val row = android.widget.TextView(requireContext())
            row.text = com.invictus.xmd.core.MediaSniffer.guessLabel(stream.url)
            row.isClickable = true
            row.isFocusable = true
            row.setBackgroundResource(R.drawable.bg_radio_row_selector)
            row.setTextColor(androidx.core.content.ContextCompat.getColorStateList(requireContext(), R.color.text_radio_row))
            row.textSize = 14f
            row.maxLines = 1
            row.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            row.gravity = android.view.Gravity.CENTER_VERTICAL
            row.setPadding((16 * density).toInt(), (14 * density).toInt(), (8 * density).toInt(), (14 * density).toInt())
            row.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            val icon = when (stream.kind) {
                com.invictus.xmd.core.MediaSniffer.Kind.DIRECT_AUDIO -> R.drawable.ic_music_note
                else -> R.drawable.ic_video
            }
            row.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0)
            row.compoundDrawablePadding = (12 * density).toInt()
            row.setOnClickListener {
                val needsPicker = with(com.invictus.xmd.core.MediaSniffer) { stream.kind.needsQualityPicker() }
                (activity as? Callbacks)?.triggerSniffedMedia(stream.url, needsPicker)
                dialog.dismiss()
            }

            val copyButton = ImageButton(requireContext())
            val buttonSize = (32 * density).toInt()
            copyButton.layoutParams = LinearLayout.LayoutParams(buttonSize, buttonSize).apply {
                marginEnd = (12 * density).toInt()
            }
            copyButton.setBackgroundResource(R.drawable.bg_icon_button_tonal)
            copyButton.setImageResource(R.drawable.ic_link)
            copyButton.imageTintList = android.content.res.ColorStateList.valueOf(
                resolveThemeColor(com.google.android.material.R.attr.colorOnSecondaryContainer)
            )
            copyButton.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            val iconInset = (8 * density).toInt()
            copyButton.setPadding(iconInset, iconInset, iconInset, iconInset)
            copyButton.contentDescription = getString(R.string.torrent_dialog_copy_link)
            // Doesn't dismiss the sheet -- copying one stream's link
            // shouldn't stop the user from picking or copying another.
            copyButton.setOnClickListener { copyLinkToClipboard(stream.url) }

            rowContainer.addView(row)
            rowContainer.addView(copyButton)
            list.addView(rowContainer)
        }

        dialog.show()
    }

    /** Applies (or reverts) desktop-site emulation on [webView]: a desktop
     *  Chrome UA string plus wide-viewport rendering, same two settings a
     *  real browser's "Desktop site" toggle flips. Doesn't reload itself --
     *  callers that change this on an already-loaded page (the overflow
     *  menu toggle) are responsible for reloading afterwards so the new UA
     *  actually takes effect. */
    private fun applyDesktopMode(webView: WebView, desktop: Boolean) {
        webView.settings.userAgentString = if (desktop) DESKTOP_USER_AGENT else MOBILE_USER_AGENT
        webView.settings.useWideViewPort = desktop
        webView.settings.loadWithOverviewMode = desktop
    }

    /** Overflow menu's "Desktop site" checkbox -- flips the current tab
     *  only. Uses loadUrl() (a real fresh network request) instead of
     *  reload(), which was the actual bug: WebView's reload() can be
     *  served straight from its own HTTP cache, so a page fetched under
     *  the old UA string just came back byte-for-byte identical from
     *  cache -- the new UA never even reached the server on some sites.
     *  loadUrl() with the exact current URL forces a genuine new request.
     *  Cache mode is also forced to LOAD_NO_CACHE for just this one
     *  navigation (restored to the normal LOAD_DEFAULT right after
     *  starting it) so even a cached response under the *new* UA from an
     *  earlier visit can't mask a real mismatch -- guarantees this one
     *  load actually hits the server fresh. */
    private fun toggleDesktopMode() {
        val tab = tabs.getOrNull(currentTabIndex) ?: return
        tab.isDesktopMode = !tab.isDesktopMode
        val webView = tab.webView ?: return
        applyDesktopMode(webView, tab.isDesktopMode)
        val currentUrl = webView.url ?: tab.url
        if (currentUrl != null) {
            webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
            webView.loadUrl(currentUrl)
            webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
        } else {
            webView.reload()
        }
    }

    private fun isCurrentTabDesktopMode(): Boolean = tabs.getOrNull(currentTabIndex)?.isDesktopMode == true

    private fun onAddLinkClicked() {
        val link = lastDetectedLink ?: return
        (activity as? Callbacks)?.triggerPrepare(listOf(link))
        clearDetectedLink()
    }

    // ── Long-press link/image context menu ──────────────────────────────

    /**
     * Chrome-style long-press menu. [webView].hitTestResult only ever
     * reports SRC_ANCHOR_TYPE (plain link), SRC_IMAGE_ANCHOR_TYPE (an
     * image wrapped in a link, e.g. `<a href><img></a>`), or IMAGE_TYPE
     * (a bare image, no link) for what we care about here -- anything
     * else (plain text, unlinked page area) shows no menu at all, same
     * as a real browser. Returns true from the long-click listener only
     * when a menu was actually shown, so an unrecognized hit falls
     * through to WebView's own default long-press behavior (text
     * selection) instead of silently eating the gesture.
     */
    private fun showLinkContextMenu(
        webView: WebView,
        result: WebView.HitTestResult,
        touchX: Float,
        touchY: Float
    ): Boolean {
        val linkUrl: String?
        val imageUrl: String?
        when (result.type) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                linkUrl = result.extra
                imageUrl = null
            }
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                // extra is the <img>'s src here; the wrapping <a>'s href isn't
                // exposed by this API at all, so "open in new tab" for this
                // case opens the image itself -- same as "open image in new
                // tab" would -- rather than silently doing nothing.
                linkUrl = result.extra
                imageUrl = result.extra
            }
            WebView.HitTestResult.IMAGE_TYPE -> {
                linkUrl = null
                imageUrl = result.extra
            }
            else -> return false
        }
        if (linkUrl.isNullOrBlank() && imageUrl.isNullOrBlank()) return false

        // Popup has no view of its own to anchor to at an arbitrary point,
        // so drop a 1x1 invisible anchor into the container at the last
        // touch position and remove it once the menu closes.
        val density = resources.displayMetrics.density
        val anchor = View(requireContext())
        val anchorSize = (1 * density).toInt().coerceAtLeast(1)
        webViewContainer.addView(
            anchor,
            FrameLayout.LayoutParams(anchorSize, anchorSize).apply {
                leftMargin = touchX.toInt()
                topMargin = touchY.toInt()
            }
        )

        val popup = android.widget.PopupMenu(requireContext(), anchor)
        popup.menuInflater.inflate(R.menu.link_context_menu, popup.menu)
        popup.menu.findItem(R.id.link_menu_open_new_tab).isVisible = !linkUrl.isNullOrBlank()
        popup.menu.findItem(R.id.link_menu_copy_link_address).isVisible = !linkUrl.isNullOrBlank()
        popup.menu.findItem(R.id.link_menu_share_link).isVisible = !linkUrl.isNullOrBlank()
        popup.menu.findItem(R.id.link_menu_open_image_new_tab).isVisible = !imageUrl.isNullOrBlank()
        popup.menu.findItem(R.id.link_menu_download_image).isVisible = !imageUrl.isNullOrBlank()

        popup.setOnDismissListener { webViewContainer.removeView(anchor) }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.link_menu_open_new_tab -> linkUrl?.let { openUrlInNewTab(it) }
                R.id.link_menu_open_image_new_tab -> imageUrl?.let { openUrlInNewTab(it) }
                R.id.link_menu_download_image -> imageUrl?.let {
                    onWebViewDownloadRequested(it, null, "image/*")
                }
                R.id.link_menu_copy_link_address -> linkUrl?.let { copyLinkToClipboard(it) }
                R.id.link_menu_share_link -> linkUrl?.let { shareLink(it) }
            }
            true
        }
        popup.show()
        return true
    }

    /** Opens [url] in a brand-new background... actually foreground tab,
     *  Chrome-style: the new tab becomes current and is shown immediately. */
    private fun openUrlInNewTab(url: String) {
        val previousView = tabs.getOrNull(currentTabIndex)?.webView
        val newTab = BrowserTab(id = nextTabId++, url = url)
        tabs.add(newTab)
        currentTabIndex = tabs.lastIndex
        showWebView()
        val view = ensureWebView(newTab)
        showNavLoadingVeil()
        view.loadUrl(url)
        crossfadeSwap(view, previousView)
        updateTabsCount()
    }

    private fun copyLinkToClipboard(url: String) {
        val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Link", url))
        Toast.makeText(requireContext(), R.string.link_copied_toast, Toast.LENGTH_SHORT).show()
    }

    private fun shareLink(url: String) {
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, url)
        }
        startActivity(android.content.Intent.createChooser(intent, getString(R.string.link_menu_share_link)))
    }

    /** Resolves a color from the current active theme (Theme.Xmd.*) instead
     *  of a static @color resource, so tab-switcher rows, the favicon tint,
     *  and the pull-to-refresh spinner all follow the selected app theme. */
    private fun resolveThemeColor(attrResId: Int): Int {
        val tv = android.util.TypedValue()
        requireContext().theme.resolveAttribute(attrResId, tv, true)
        return tv.data
    }
}
