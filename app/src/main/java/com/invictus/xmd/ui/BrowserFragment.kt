package com.invictus.xmd.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.invictus.xmd.R
import com.invictus.xmd.core.BookmarkRepository
import com.invictus.xmd.core.ShortcutRepository
import com.invictus.xmd.core.DownloadEngine
import com.invictus.xmd.core.HistoryRepository
import com.invictus.xmd.core.LinkParser
import com.invictus.xmd.core.Settings
import com.invictus.xmd.core.SuggestApi
import com.invictus.xmd.ui.BrowserViewModel.BrowserTabState as BrowserTab
import com.invictus.xmd.ui.theme.resolveCurrentXmdColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        fun onBrowserMenuAction(action: BrowserMenuAction)
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

    // Tab metadata now lives in BrowserViewModel (see BrowserViewModel.kt --
    // Phase 5 migration, step 1) so it survives this Fragment's view being
    // recreated. The `BrowserTab` alias (import at top of file; Kotlin
    // doesn't allow a typealias nested inside a class) keeps every existing
    // `BrowserTab` reference in this file (type annotations,
    // `BrowserTab(id = ...)` constructor calls) working unchanged. The
    // WebView itself and its saveState() snapshot are deliberately NOT part
    // of this class -- see webViews/webViewStates below and the "WebView
    // pool" section for why those stayed Fragment-side.

    private lateinit var browserRoot: androidx.compose.ui.platform.ComposeView
    private lateinit var webViewSwipeRefresh: SwipeRefreshLayout
    private lateinit var webViewContainer: FrameLayout

    private val shortcutsViewModel: ShortcutsViewModel by viewModels()

    // Photo picker has to stay registered here -- a ViewModel can't hold an
    // ActivityResultLauncher -- but everything downstream of the picked Uri
    // (preview, persistence) now lives in ShortcutsViewModel.
    private val pickIconLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) shortcutsViewModel.onIconPicked(uri)
        }
    private var lastDetectedLink: String? = null
    private var speedDialVisible: Boolean by mutableStateOf(true)
    private var browserMenuExpanded: Boolean by mutableStateOf(false)
    private var clearBrowsingDataDialogOpen: Boolean by mutableStateOf(false)
    private var downloadPrompt: BrowserDownloadPrompt? by mutableStateOf(null)
    // Compose State (not just a plain var) so browserDialogHost's
    // setContent lambda recomposes when this changes -- non-null shows
    // SniffedMediaSheet with this exact snapshot, same one-shot
    // synchronized-map read showSniffedMediaSheet() always did; null
    // (initial, or after onDismiss) means "sheet not shown". `by` here
    // needs the getValue/setValue imports below (see the Phase 4 lesson
    // on extension functions in COMPOSE_MIGRATION.md) -- a fully-qualified
    // mutableStateOf() call alone wouldn't be enough.
    private var sniffedSheetStreams: List<com.invictus.xmd.core.MediaSniffer.Sniffed>?
            by mutableStateOf(null)
    // Drives browserDialogHost's LinkContextMenu branch -- non-null shows
    // the menu at that state's (touchX, touchY), null means "not shown".
    // See showLinkContextMenu() for how the coordinates get translated from
    // webView-local to browserDialogHost-local before landing here.
    private var linkContextMenuState: LinkContextMenuState? by mutableStateOf(null)
    // Drives browserDialogHost's AddBookmarkDialog branch -- non-null shows
    // the dialog prefilled with whatever showAddBookmarkDialog() was called
    // with (current tab's URL/title, or an explicit prefill).
    private data class AddBookmarkDialogState(val prefillUrl: String?, val prefillTitle: String?)
    private var addBookmarkDialogState: AddBookmarkDialogState? by mutableStateOf(null)
    // Drives suggestionsCard's Compose content -- see that field's comment.
    // Empty list == dropdown hidden, same meaning View.GONE used to carry.
    private var suggestionItems: List<Suggestion> by mutableStateOf(emptyList())
    private var suggestJob: Job? = null
    // Drives tabsListOverlay's visibility -- see showTabsOverlay()/
    // hideTabsOverlay() and TabsListOverlay.kt's doc comment.
    private var tabsOverlayVisible: Boolean by mutableStateOf(false)
    // One-shot snapshot of `tabs`, same reason sniffedSheetStreams is a
    // snapshot rather than reading BrowserViewModel.tabs live: that list
    // isn't Compose-observable. Refreshed explicitly in
    // refreshTabsOverlaySnapshot() after every mutation while the overlay
    // can be showing (currently just closeTab()).
    private var tabsOverlaySnapshot: List<TabOverlayItem> by mutableStateOf(emptyList())
    // Mirrors BookmarkRepository.bookmarks (URLs only) so the address-bar
    // star can flip filled/outline instantly without a DB round-trip on
    // every tab switch -- kept in sync by the observer in setupSpeedDial().
    private var bookmarkedUrls: Set<String> = emptySet()

    // ── Phase F: findInPageOverlay / navLoadingVeil / browserFabs state ───
    // Drives FindInPageBar's setContent lambda below -- see
    // setupFindInPage()/showFindInPage()/hideFindInPage(). Mirrors what
    // findInPageBar.visibility used to hold directly.
    private var findInPageVisible: Boolean by mutableStateOf(false)
    // Mirrors the old findInPageInput.text -- "field on the Fragment,
    // composable renders it" pattern, same as addressBarText.
    private var findInPageQuery: String by mutableStateOf("")
    // Mirrors the old findInPageMatchCount.text ("$current/$numberOfMatches").
    private var findInPageMatchText: String by mutableStateOf("0/0")
    // Bumped (never read for its value) each time showFindInPage() opens
    // the bar, so FindInPageBar's LaunchedEffect can request focus + show
    // the IME -- same "signal bump" pattern addressBarClearFocusSignal uses.
    private var findInPageFocusSignal: Int by mutableStateOf(0)
    // Drives NavLoadingVeil's visibility -- see showNavLoadingVeil()/
    // hideNavLoadingVeil(). Mirrors the old navLoadingVeil.visibility.
    private var navLoadingVeilVisible: Boolean by mutableStateOf(false)
    // Drives BrowserFabs' two FABs -- see checkPageForLinks()/
    // clearDetectedLink()/updateSniffedMediaFab(). Mirror the old
    // addLinkFab.visibility / sniffedMediaFab.visibility+text.
    private var detectedLinkVisible: Boolean by mutableStateOf(false)
    private var sniffedMediaFabVisible: Boolean by mutableStateOf(false)
    private var sniffedMediaFabText: String by mutableStateOf("")

    // ── Phase E: browserToolbar's state ──────────────────────────────────
    // Drives BrowserToolbarRow's setContent lambda below -- same "field on
    // the Fragment, composable reads it" pattern sniffedSheetStreams/
    // linkContextMenuState/suggestionItems above already use. Mirrors what
    // urlInput/pageProgress/siteSecurityIcon/bookmarkStarButton/tabsCount
    // used to hold directly on their Views.
    private var addressBarText: String by mutableStateOf("")
    // Read-only mirror of BrowserToolbarRow's TextField focus state,
    // reported up via its onAddressFocusChange callback -- same gate
    // urlInput.hasFocus() used to provide in the old TextWatcher, so a
    // programmatic addressBarText set (onPageStarted, applyTabUiState,
    // etc.) still doesn't trigger scheduleSuggest. Plain var, not Compose
    // state: nothing renders off this directly.
    private var addressBarFocused: Boolean = false
    // Bumped (never read for its value, just its change) to tell
    // BrowserToolbarRow's composition to clear focus + hide the IME --
    // replaces the old urlInput.clearFocus() + hideSoftInputFromWindow()
    // calls at the end of loadUrl(); see BrowserToolbar.kt's doc comment
    // for why this can't just be an imperative call from here anymore.
    private var addressBarClearFocusSignal: Int by mutableStateOf(0)
    private var securityIconVisible: Boolean by mutableStateOf(false)
    private var siteIsSecure: Boolean by mutableStateOf(true)
    private var bookmarkStarVisible: Boolean by mutableStateOf(false)
    private var bookmarkStarFilled: Boolean by mutableStateOf(false)
    private var toolbarProgress: Int by mutableStateOf(0)
    private var toolbarProgressVisible: Boolean by mutableStateOf(false)
    private var tabsCountValue: Int by mutableStateOf(1)

    private val browserViewModel: BrowserViewModel by viewModels()

    // Thin pass-through onto browserViewModel's fields -- keeps every
    // existing `tabs`/`currentTabIndex`/`nextTabId` reference below working
    // unchanged while the actual storage now lives in the ViewModel.
    private val tabs get() = browserViewModel.tabs
    private var currentTabIndex: Int
        get() = browserViewModel.currentTabIndex
        set(value) { browserViewModel.currentTabIndex = value }
    private var nextTabId: Long
        get() = browserViewModel.nextTabId
        set(value) { browserViewModel.nextTabId = value }

    // Live WebView pool, keyed by tab id -- Context-bound and
    // View-lifecycle-bound, so unlike the tab metadata above these stay
    // owned by the Fragment itself rather than the ViewModel (see
    // BrowserViewModel's class doc for why). [webViewStates] is the
    // WebView.saveState() snapshot taken whenever a tab's WebView gets
    // torn down (LRU eviction, or explicitly reset to blank), letting a
    // later visit restore instantly instead of reloading from the network.
    private val webViews = mutableMapOf<Long, WebView>()
    private val webViewStates = mutableMapOf<Long, android.os.Bundle>()

    private fun webViewFor(tab: BrowserTab?): WebView? = tab?.let { webViews[it.id] }

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

    // DoH client construction (currentDohClient) + prefetchDns now live on
    // browserViewModel -- see BrowserViewModel.kt. Both are pure
    // OkHttp/Settings logic with no View dependency, so they moved as-is;
    // call sites below now go through browserViewModel.currentDohClient()/
    // browserViewModel.prefetchDns() instead.

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = androidx.compose.ui.platform.ComposeView(requireContext()).apply {
        browserRoot = this
        setViewCompositionStrategy(
            androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        setContent {
            com.invictus.xmd.ui.theme.XmdTheme {
                BrowserScreen(
                    speedDialVisible = speedDialVisible,
                    toolbar = {
                        BrowserToolbarRow(
                            addressText = addressBarText,
                            onAddressTextChange = { text ->
                                addressBarText = text
                                if (addressBarFocused) scheduleSuggest(text)
                            },
                            onAddressFocusChange = { focused ->
                                addressBarFocused = focused
                                if (!focused) hideSuggestions()
                            },
                            onGo = { loadUrl(addressBarText) },
                            clearFocusSignal = addressBarClearFocusSignal,
                            securityIconVisible = securityIconVisible,
                            isSecure = siteIsSecure,
                            bookmarkVisible = bookmarkStarVisible,
                            bookmarkFilled = bookmarkStarFilled,
                            onBookmarkTap = ::onBookmarkStarTapped,
                            onHomeTap = ::goHome,
                            onNewTabTap = ::addNewTab,
                            onTabsTap = ::showTabsOverlay,
                            tabsCount = tabsCountValue,
                            onOverflowTap = { browserMenuExpanded = true },
                            overflowMenu = {
                                BrowserOverflowMenu(
                                    expanded = browserMenuExpanded,
                                    desktopSiteEnabled = isCurrentTabDesktopMode(),
                                    currentPageAvailable = currentPageUrl() != null,
                                    onDismiss = { browserMenuExpanded = false },
                                    onRefresh = ::reloadActiveTab,
                                    onFindInPage = ::showFindInPage,
                                    onToggleDesktopSite = ::toggleDesktopModeForCurrentTab,
                                    onCopyPage = { currentPageUrl()?.let(::copyLinkToClipboard) },
                                    onSharePage = { currentPageUrl()?.let(::shareLink) },
                                    onClearBrowsingData = { clearBrowsingDataDialogOpen = true },
                                    onAction = { action ->
                                        (activity as? Callbacks)?.onBrowserMenuAction(action)
                                    },
                                )
                            },
                            progress = toolbarProgress,
                            progressVisible = toolbarProgressVisible,
                        )
                    },
                    onWebViewHostReady = { swipeRefresh, containerView ->
                        webViewSwipeRefresh = swipeRefresh
                        webViewContainer = containerView
                        setupPullToRefresh()
                    },
                    speedDial = {
                        ShortcutsScreen(
                            viewModel = shortcutsViewModel,
                            onOpenUrl = { shortcut ->
                                addressBarText = shortcut.url
                                loadUrl(shortcut.url)
                            },
                            onPickIcon = { pickIconLauncher.launch("image/*") },
                        )
                    },
                    suggestions = {
                        AddressBarSuggestions(
                            suggestions = suggestionItems,
                            onTap = { item ->
                                when (item) {
                                    is Suggestion.History -> {
                                        addressBarText = item.url
                                        loadUrl(item.url)
                                    }
                                    is Suggestion.Search -> {
                                        addressBarText = item.text
                                        loadUrl(item.text)
                                    }
                                }
                            },
                            onAddTap = { phrase ->
                                val url = normalizeToUrl(phrase)
                                ShortcutRepository.add(title = phrase, url = url)
                                Toast.makeText(
                                    requireContext(),
                                    R.string.shortcut_added_toast,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                        )
                    },
                    findInPage = {
                        FindInPageBar(
                            visible = findInPageVisible,
                            query = findInPageQuery,
                            onQueryChange = ::onFindInPageQueryChange,
                            matchText = findInPageMatchText,
                            onPrev = { webViewFor(tabs.getOrNull(currentTabIndex))?.findNext(false) },
                            onNext = { webViewFor(tabs.getOrNull(currentTabIndex))?.findNext(true) },
                            onClose = ::hideFindInPage,
                            requestFocus = findInPageFocusSignal,
                        )
                    },
                    loadingVeil = { NavLoadingVeil(visible = navLoadingVeilVisible) },
                    floatingActions = {
                        BrowserFabs(
                            detectedLinkVisible = detectedLinkVisible,
                            onDetectedLinkTap = ::onAddLinkClicked,
                            sniffedMediaVisible = sniffedMediaFabVisible,
                            sniffedMediaText = sniffedMediaFabText,
                            onSniffedMediaTap = ::showSniffedMediaSheet,
                        )
                    },
                    dialogs = {
                        sniffedSheetStreams?.let { streams ->
                            SniffedMediaSheet(
                                streams = streams,
                                onStreamSelected = { stream ->
                                    val needsPicker = with(com.invictus.xmd.core.MediaSniffer) {
                                        stream.kind.needsQualityPicker()
                                    }
                                    (activity as? Callbacks)?.triggerSniffedMedia(stream.url, needsPicker)
                                },
                                onCopyLink = ::copyLinkToClipboard,
                                onDismiss = { sniffedSheetStreams = null },
                            )
                        }
                        linkContextMenuState?.let { menuState ->
                            LinkContextMenu(
                                state = menuState,
                                onDismiss = { linkContextMenuState = null },
                                onOpenNewTab = ::openUrlInNewTab,
                                onOpenImageNewTab = ::openUrlInNewTab,
                                onDownloadImage = { url -> onWebViewDownloadRequested(url, null, "image/*") },
                                onCopyLinkAddress = ::copyLinkToClipboard,
                                onShareLink = ::shareLink,
                            )
                        }
                        addBookmarkDialogState?.let { state ->
                            AddBookmarkDialog(
                                initialUrl = state.prefillUrl,
                                initialTitle = state.prefillTitle,
                                onDismiss = { addBookmarkDialogState = null },
                                onConfirm = { url, title, alsoAddShortcut ->
                                    if (url.isBlank()) {
                                        Toast.makeText(
                                            requireContext(),
                                            R.string.bookmark_needs_url,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                        return@AddBookmarkDialog
                                    }
                                    val normalized = normalizeToUrl(url.trim())
                                    val trimmedTitle = title.trim()
                                    BookmarkRepository.add(trimmedTitle, normalized)
                                    if (alsoAddShortcut) ShortcutRepository.add(trimmedTitle, normalized)
                                    Toast.makeText(
                                        requireContext(),
                                        R.string.bookmark_added_toast,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    addBookmarkDialogState = null
                                },
                            )
                        }
                        if (clearBrowsingDataDialogOpen) {
                            ClearBrowsingDataDialog(
                                onDismiss = { clearBrowsingDataDialogOpen = false },
                                onClear = { history, cookies, cache ->
                                    clearBrowsingData(history, cookies, cache)
                                    Toast.makeText(
                                        requireContext(),
                                        R.string.clear_data_cleared_toast,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                            )
                        }
                        downloadPrompt?.let { prompt ->
                            BrowserDownloadConfirmationDialog(
                                prompt = prompt,
                                onDismiss = { downloadPrompt = null },
                                onCopyLink = ::copyLinkToClipboard,
                                onAddToDownloads = { url ->
                                    (activity as? Callbacks)?.triggerPrepare(listOf(url))
                                },
                            )
                        }
                    },
                    tabsOverlay = {
                        TabsListOverlay(
                            visible = tabsOverlayVisible,
                            tabs = tabsOverlaySnapshot,
                            currentTabId = tabs.getOrNull(currentTabIndex)?.id,
                            onSwitch = { id ->
                                val index = tabs.indexOfFirst { it.id == id }
                                if (index != -1) switchToTab(index)
                                hideTabsOverlay()
                            },
                            onClose = { id ->
                                val index = tabs.indexOfFirst { it.id == id }
                                if (index != -1) closeTab(index)
                                refreshTabsOverlaySnapshot()
                            },
                            onAddNew = {
                                addNewTab()
                                hideTabsOverlay()
                            },
                            onDismiss = ::hideTabsOverlay,
                        )
                    },
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSpeedDial()
        updateTabsCount()
        view.post {
            val currentTab = tabs.getOrNull(currentTabIndex)
            if (currentTab?.url.isNullOrBlank()) {
                showSpeedDial()
            } else {
                activateTab(currentTabIndex, previousView = null)
            }
        }
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

    override fun onDestroyView() {
        suggestJob?.cancel()
        tabs.toList().forEach(::destroyTabWebView)
        fullscreenView?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
        }
        fullscreenCallback?.onCustomViewHidden()
        fullscreenView = null
        fullscreenCallback = null
        setImmersiveMode(false)
        super.onDestroyView()
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
        webViewSwipeRefresh.setColorSchemeColors(
            resolveCurrentXmdColorScheme(requireContext()).primary.toArgb()
        )
        webViewSwipeRefresh.setOnChildScrollUpCallback { _, _ ->
            webViewFor(tabs.getOrNull(currentTabIndex))?.canScrollVertically(-1) == true
        }
        webViewSwipeRefresh.setOnRefreshListener {
            val webView = webViewFor(tabs.getOrNull(currentTabIndex))
            if (webView == null) {
                webViewSwipeRefresh.isRefreshing = false
            } else {
                webView.reload()
            }
        }
    }

    private fun reloadActiveTab() {
        webViewFor(tabs.getOrNull(currentTabIndex))?.reload()
    }

    private fun toggleDesktopModeForCurrentTab() = toggleDesktopMode()

    /** Overflow menu's "Clear browsing data" dialog result. Cache/cookies
     *  are cleared through every currently-live WebView (any tab whose
     *  WebView has been torn down by LRU eviction has nothing left to
     *  clear anyway) since there's no single global handle for either --
     *  each WebView instance owns its own cache, though the cookie jar
     *  itself is shared, so clearing it once via any instance is enough. */
    private fun clearBrowsingData(clearHistory: Boolean, clearCookies: Boolean, clearCache: Boolean) {
        if (clearHistory) HistoryRepository.clearAll()
        if (clearCache) {
            tabs.mapNotNull { webViewFor(it) }.forEach { it.clearCache(true) }
        }
        if (clearCookies) {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            tabs.mapNotNull { webViewFor(it) }.forEach {
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
        webViews[tab.id]?.let { touchLru(tab.id); return it }

        val wv = WebView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            alpha = 0f
            visibility = View.GONE
        }
        configureWebView(wv, tab)
        webViewContainer.addView(wv)
        webViews[tab.id] = wv
        touchLru(tab.id)
        evictIfNeeded()
        return wv
    }

    /** Tears down [tab]'s WebView, snapshotting its state first so a later
     *  visit can restore instantly instead of reloading from the network. */
    private fun destroyTabWebView(tab: BrowserTab) {
        tabAccessOrder.remove(tab.id)
        val wv = webViews[tab.id] ?: return
        if (!tab.isPrivate) {
            val bundle = android.os.Bundle()
            if (wv.saveState(bundle) != null) webViewStates[tab.id] = bundle
        }
        webViewContainer.removeView(wv)
        wv.stopLoading()
        wv.destroy()
        webViews.remove(tab.id)
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
        webViewStates.remove(tab.id)
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
        // listener tracks the last down-point purely to anchor the menu
        // where the finger actually was; it never consumes the event
        // (always returns false) so normal scrolling/tapping/scrubbing is
        // completely untouched. Raw (screen) coordinates, not view-local --
        // see showLinkContextMenu() for why, now that the menu itself lives
        // in browserDialogHost (a different view in the hierarchy than
        // webView) instead of the old invisible-anchor-View-in-
        // webViewContainer trick.
        var lastTouchRawX = 0f
        var lastTouchRawY = 0f
        webView.setOnTouchListener { _, event ->
            if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                lastTouchRawX = event.rawX
                lastTouchRawY = event.rawY
            }
            false
        }
        webView.setOnLongClickListener {
            val result = webView.hitTestResult
            showLinkContextMenu(result, lastTouchRawX, lastTouchRawY)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                tab.url = url
                tab.isLoading = true
                tab.progress = 0
                tab.sniffedMedia.clear()
                if (isCurrentTab(tab)) {
                    toolbarProgress = 0
                    toolbarProgressVisible = true
                    addressBarText = url.orEmpty()
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
                    toolbarProgressVisible = false
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
             * is OFF, browserViewModel.currentDohClient() returns null and every request
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
                val client = browserViewModel.currentDohClient() ?: return null
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
                        toolbarProgressVisible = false
                    } else {
                        toolbarProgress = newProgress
                        toolbarProgressVisible = true
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
        webViewFor(tabs.getOrNull(currentTabIndex))?.webChromeClient?.onHideCustomView()
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
     * fall back to the Downloads tab instead of exiting.
     */
    fun onBackPressed(): Boolean {
        // The old BottomSheetDialog consumed back presses for free (Android's
        // Dialog window intercepts them); tabsListOverlay is a plain
        // full-bleed ComposeView, not a Dialog, so that has to be replicated
        // by hand here or back would fall through to the WebView underneath.
        if (tabsOverlayVisible) {
            hideTabsOverlay()
            return true
        }
        val tab = tabs.getOrNull(currentTabIndex) ?: return false
        val view = webViewFor(tab)
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
    // setupAddressBar() (urlInput's editor-action listener, TextWatcher,
    // focus-change listener) is gone -- that logic now lives in
    // BrowserToolbarRow's onGo/onAddressTextChange/onAddressFocusChange
    // lambdas, wired once in onViewCreated's browserToolbar.setContent.

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
        val historyMatches = HistoryRepository.entries.value
            .filter { it.title.contains(trimmed, ignoreCase = true) || it.url.contains(trimmed, ignoreCase = true) }
            .take(MAX_HISTORY_SUGGESTIONS)
            .map { Suggestion.History(text = it.title, url = it.url) }

        // History is already in memory, so it renders on this frame instead
        // of waiting on the debounce + network round-trip below -- only the
        // search half of the list is provisional at this point.
        if (historyMatches.isNotEmpty()) {
            suggestionItems = historyMatches
        }

        suggestJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(150)
            val searchResults = withContext(Dispatchers.IO) { SuggestApi.suggest(trimmed, suggestClient) }
            if (!isAdded) return@launch
            val merged = historyMatches + searchResults.map { Suggestion.Search(it) }
            if (merged.isEmpty()) {
                hideSuggestions()
            } else {
                suggestionItems = merged
            }
        }
    }

    private fun hideSuggestions() {
        suggestJob?.cancel()
        suggestionItems = emptyList()
    }

    // ── Find in page ──────────────────────────────────────────────────────
    // Phase F: was setupFindInPage() wiring a TextWatcher + 3 click
    // listeners onto real Views; FindInPageBar's setContent lambda
    // (onViewCreated) now wires the same callbacks directly, so there's no
    // separate setup function left to call -- onFindInPageQueryChange below
    // is the TextWatcher's afterTextChanged logic, unchanged.

    /** Same logic the old TextWatcher's afterTextChanged had -- updates
     *  findInPageQuery (so FindInPageBar's TextField reflects the edit)
     *  and re-runs the WebView's find-in-page search, or clears matches on
     *  an empty query. */
    private fun onFindInPageQueryChange(query: String) {
        findInPageQuery = query
        val webView = webViewFor(tabs.getOrNull(currentTabIndex)) ?: return
        if (query.isEmpty()) {
            webView.clearMatches()
            findInPageMatchText = "0/0"
        } else {
            webView.findAllAsync(query)
        }
    }

    /** Opened from the overflow menu's "Find in page" item. Wires the
     *  active tab's WebView.FindListener fresh each time (rather than once
     *  up front) since the active WebView instance can change between
     *  opens as tabs get created/switched/evicted. */
    fun showFindInPage() {
        val webView = webViewFor(tabs.getOrNull(currentTabIndex)) ?: return
        webView.setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
            if (!isDoneCounting) return@setFindListener
            val current = if (numberOfMatches == 0) 0 else activeMatchOrdinal + 1
            findInPageMatchText = "$current/$numberOfMatches"
        }
        findInPageVisible = true
        // FindInPageBar's LaunchedEffect(requestFocus) does the actual
        // focus-request + IME-show, same as the old requestFocus() +
        // showSoftInput(SHOW_IMPLICIT) pair -- see that composable's doc
        // comment in BrowserOverlays.kt for why this can't be an
        // imperative call from here anymore.
        findInPageFocusSignal++
    }

    private fun hideFindInPage() {
        webViewFor(tabs.getOrNull(currentTabIndex))?.let {
            it.clearMatches()
            it.setFindListener(null)
        }
        findInPageQuery = ""
        findInPageMatchText = "0/0"
        findInPageVisible = false
        val imm = requireContext().getSystemService(android.view.inputmethod.InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(view?.windowToken, 0)
    }

    private fun updateSecurityIcon(tab: BrowserTab) {
        val url = tab.url
        if (url.isNullOrBlank() || !url.startsWith("http")) {
            securityIconVisible = false
            return
        }
        securityIconVisible = true
        siteIsSecure = url.startsWith("https")
    }

    /** Filled star when the loaded page's URL is already saved as a
     *  bookmark, outline otherwise; hidden entirely on the speed dial (no
     *  page yet). */
    private fun updateBookmarkStar(tab: BrowserTab) {
        val url = tab.url
        if (url.isNullOrBlank() || !url.startsWith("http")) {
            bookmarkStarVisible = false
            return
        }
        bookmarkStarVisible = true
        bookmarkStarFilled = url in bookmarkedUrls
    }

    /** Star tapped: adds the current page as a bookmark (via the Add
     *  Bookmark dialog, prefilled -- with a checkbox to also add it as a
     *  speed-dial Shortcut) if it isn't one yet, or removes the bookmark
     *  in one tap if it already is -- Chrome-style toggle. Never touches
     *  Shortcuts on removal; those are independent once created. */
    private fun onBookmarkStarTapped() {
        val tab = tabs.getOrNull(currentTabIndex) ?: return
        val url = tab.url ?: return
        val existing = BookmarkRepository.bookmarks.value.firstOrNull { it.url == url }
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
        addressBarText = tab.url.orEmpty()
        updateSecurityIcon(tab)
        updateBookmarkStar(tab)
        toolbarProgress = tab.progress
        toolbarProgressVisible = tab.isLoading
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
        webViewStates.remove(tab.id)
        applyTabUiState(tab)
        showNavLoadingVeil()
        browserViewModel.prefetchDns(url)

        val view = ensureWebView(tab)
        view.alpha = 1f
        view.visibility = View.VISIBLE
        view.loadUrl(url)

        // Drop keyboard focus so the address bar doesn't stay expanded --
        // browserToolbar's TextField owns focus now, so this is a signal
        // bump its LaunchedEffect reacts to (see BrowserToolbar.kt) instead
        // of a direct View.clearFocus() + hideSoftInputFromWindow() call.
        addressBarClearFocusSignal++
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

    /** ShortcutsScreen's own content + grid + reorder + dialogs is wired up
     *  in onViewCreated when speedDialContainer's ComposeView is set up --
     *  this now just keeps the toolbar bookmark-star in sync, which was
     *  always a separate concern from the speed-dial tiles themselves. */
    private fun setupSpeedDial() {
        // Drives the star toggle in the toolbar (updateBookmarkStar), which
        // reflects real Bookmarks, not Shortcuts. BookmarkRepository.bookmarks
        // is a StateFlow (Bookmarks/History screens now collect it via
        // Compose), so this is a lifecycle-scoped collect instead of
        // LiveData.observe.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                BookmarkRepository.bookmarks.collect { list ->
                    bookmarkedUrls = list.map { it.url }.toSet()
                    tabs.getOrNull(currentTabIndex)?.let { updateBookmarkStar(it) }
                }
            }
        }
    }

    private fun showSpeedDial() {
        speedDialVisible = true
        addressBarText = ""
        securityIconVisible = false
        bookmarkStarVisible = false
        toolbarProgressVisible = false
        if (::webViewSwipeRefresh.isInitialized) webViewSwipeRefresh.isRefreshing = false
        hideSuggestions()
        clearDetectedLink()
        sniffedMediaFabVisible = false
        hideNavLoadingVeil()
    }

    private fun showWebView() {
        speedDialVisible = false
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
        navLoadingVeilVisible = true
    }

    private fun hideNavLoadingVeil() {
        navLoadingVeilVisible = false
    }

    // Add/edit/options dialogs for shortcuts are now Compose (see
    // ShortcutsScreen.kt's AddEditShortcutDialog/ShortcutOptionsDialog),
    // driven by ShortcutsViewModel -- showAddShortcutDialog/wireIconPicker/
    // showShortcutOptionsDialog/showEditShortcutDialog used to live here.

    /** Star-button flow: saves a real Bookmark for the current page. The
     *  checkbox additionally creates a matching speed-dial Shortcut in the
     *  same tap -- the two lists stay independent after that (removing the
     *  bookmark later never removes the shortcut, and vice versa).
     *
     *  Post-migration-audit conversion: was a MaterialAlertDialogBuilder +
     *  dialog_add_bookmark.xml inflate, now just sets Compose state read by
     *  browserDialogHost's AddBookmarkDialog branch (see onViewCreated).
     *  Validation/persist logic (empty-URL toast, normalizeToUrl,
     *  BookmarkRepository/ShortcutRepository.add, success toast) moved into
     *  that branch's onConfirm lambda -- this function now only computes
     *  the prefill. */
    private fun showAddBookmarkDialog(prefillUrl: String?, prefillTitle: String? = null) {
        addBookmarkDialogState = AddBookmarkDialogState(
            prefillUrl = prefillUrl ?: tabs.getOrNull(currentTabIndex)?.url,
            prefillTitle = prefillTitle,
        )
    }

    // ── Tabs ─────────────────────────────────────────────────────────────

    private fun updateTabsCount() {
        tabsCountValue = tabs.size
    }

    private fun addNewTab() {
        val previousView = webViewFor(tabs.getOrNull(currentTabIndex))
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
        previousView: WebView? = webViewFor(tabs.getOrNull(currentTabIndex))
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
        val hadLiveView = webViewFor(tab) != null
        val view = ensureWebView(tab)
        if (!hadLiveView) {
            showNavLoadingVeil()
            val state = webViewStates[tab.id]
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
     * Tabs tray. Phase 5 conversion -- see TabsListOverlay.kt's doc comment
     * for the full reasoning (was a BottomSheetDialog built in this
     * function; now tabsListOverlay, a Compose-native full-bleed overlay).
     * Every tab is still closable including the last one -- closeTab()
     * resets it to a fresh "New tab" (speed dial) in that case, same as
     * before.
     */
    private fun showTabsOverlay() {
        refreshTabsOverlaySnapshot()
        tabsOverlayVisible = true
    }

    private fun hideTabsOverlay() {
        tabsOverlayVisible = false
    }

    private fun refreshTabsOverlaySnapshot() {
        tabsOverlaySnapshot = tabs.map { tab ->
            TabOverlayItem(id = tab.id, title = tab.title, url = tab.url, isPrivate = tab.isPrivate)
        }
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
        downloadPrompt = BrowserDownloadPrompt(url = url, fileName = guessedName)

        viewLifecycleOwner.lifecycleScope.launch {
            val probed = withContext(Dispatchers.IO) {
                DownloadEngine.probeRealFilename(filenameClient, url)
            }
            val currentPrompt = downloadPrompt
            if (probed != null && currentPrompt?.url == url && probed != currentPrompt.fileName) {
                downloadPrompt = currentPrompt.copy(fileName = probed)
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
            detectedLinkVisible = true
        } else {
            clearDetectedLink()
        }
    }

    private fun clearDetectedLink() {
        lastDetectedLink = null
        detectedLinkVisible = false
    }

    /** Reflects [tab]'s current sniffedMedia count onto the chip -- called
     *  from onPageStarted (clears it), and from shouldInterceptRequest's
     *  sniff hook every time a genuinely new stream URL is found. No-op
     *  visually unless [tab] is the tab currently on screen. */
    private fun updateSniffedMediaFab(tab: BrowserTab) {
        if (!isCurrentTab(tab)) return
        val count = tab.sniffedMedia.size
        if (count == 0) {
            sniffedMediaFabVisible = false
            return
        }
        sniffedMediaFabText = if (count == 1) {
            getString(R.string.sniffed_media_chip_one)
        } else {
            getString(R.string.sniffed_media_chip_many, count)
        }
        sniffedMediaFabVisible = true
    }

    /** Opens the Compose SniffedMediaSheet (see browserDialogHost's
     *  setContent in onViewCreated) listing every stream in the current
     *  tab's sniffedMedia. Tapping a row hands it straight to
     *  Callbacks.triggerSniffedMedia; each row also carries a copy button
     *  to grab the raw URL without starting a download. Phase 5 conversion
     *  -- this used to hand-build a BottomSheetDialog + one LinearLayout
     *  row per stream here; all of that now lives in SniffedMediaSheet.kt,
     *  this function is just the snapshot-and-open trigger. */
    private fun showSniffedMediaSheet() {
        val tab = tabs.getOrNull(currentTabIndex) ?: return
        // Snapshot under the same lock shouldInterceptRequest writes under --
        // sniffedMedia is a synchronizedMap precisely so this read (main
        // thread) can't race a concurrent write (WebView background thread).
        val streams = synchronized(tab.sniffedMedia) { tab.sniffedMedia.values.toList() }
        if (streams.isEmpty()) return
        sniffedSheetStreams = streams
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
        val webView = webViewFor(tab) ?: return
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
     * Chrome-style long-press menu. hitTestResult only ever reports
     * SRC_ANCHOR_TYPE (plain link), SRC_IMAGE_ANCHOR_TYPE (an image wrapped
     * in a link, e.g. `<a href><img></a>`), or IMAGE_TYPE (a bare image, no
     * link) for what we care about here -- anything else (plain text,
     * unlinked page area) shows no menu at all, same as a real browser.
     * Returns true from the long-click listener only when a menu was
     * actually shown, so an unrecognized hit falls through to WebView's
     * own default long-press behavior (text selection) instead of
     * silently eating the gesture.
     *
    * The menu is anchored to the touch point inside the single Compose root.
     */
    private fun showLinkContextMenu(
        result: WebView.HitTestResult,
        rawTouchX: Float,
        rawTouchY: Float
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

        val hostLocation = IntArray(2)
        browserRoot.getLocationOnScreen(hostLocation)
        linkContextMenuState = LinkContextMenuState(
            touchX = rawTouchX - hostLocation[0],
            touchY = rawTouchY - hostLocation[1],
            linkUrl = linkUrl,
            imageUrl = imageUrl,
        )
        return true
    }

    /** Opens [url] in a brand-new background... actually foreground tab,
     *  Chrome-style: the new tab becomes current and is shown immediately. */
    private fun openUrlInNewTab(url: String) {
        val previousView = webViewFor(tabs.getOrNull(currentTabIndex))
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
        clipboard.setPrimaryClip(
            android.content.ClipData.newPlainText(getString(R.string.clipboard_link_label), url)
        )
        Toast.makeText(requireContext(), R.string.link_copied_toast, Toast.LENGTH_SHORT).show()
    }

    private fun currentPageUrl(): String? = tabs.getOrNull(currentTabIndex)?.url
        ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }

    private fun shareLink(url: String) {
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, url)
        }
        startActivity(android.content.Intent.createChooser(intent, getString(R.string.link_menu_share_link)))
    }

}
