package com.invictus.xmd.ui;

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
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0084\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\"\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0000\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\t\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010!\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u000b\n\u0002\b%\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u001d\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0007\n\u0002\b\u0018\u0018\u0000 \u00b9\u00012\u00020\u0001:\b\u00b7\u0001\u00b8\u0001\u00b9\u0001\u00ba\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\u001c\u0010L\u001a\u00020M2\u0006\u0010N\u001a\u00020\r2\n\b\u0002\u0010O\u001a\u0004\u0018\u00010PH\u0002J\b\u0010Q\u001a\u00020MH\u0002J\u0018\u0010R\u001a\u00020M2\u0006\u0010S\u001a\u00020P2\u0006\u0010T\u001a\u00020UH\u0002J\u0010\u0010V\u001a\u00020M2\u0006\u0010W\u001a\u00020DH\u0002J\u0010\u0010X\u001a\u00020M2\u0006\u0010Y\u001a\u00020\u000bH\u0002J\u001e\u0010Z\u001a\u00020M2\u0006\u0010[\u001a\u00020U2\u0006\u0010\\\u001a\u00020U2\u0006\u0010]\u001a\u00020UJ\b\u0010^\u001a\u00020MH\u0002J\u0010\u0010_\u001a\u00020M2\u0006\u0010N\u001a\u00020\rH\u0002J\u0018\u0010`\u001a\u00020M2\u0006\u0010S\u001a\u00020P2\u0006\u0010W\u001a\u00020DH\u0003J\u0010\u0010a\u001a\u00020M2\u0006\u0010Y\u001a\u00020\u000bH\u0002J\u001a\u0010b\u001a\u00020M2\u0006\u0010c\u001a\u00020P2\b\u0010d\u001a\u0004\u0018\u00010PH\u0002J\n\u0010e\u001a\u0004\u0018\u00010\u000fH\u0002J\u0010\u0010f\u001a\u00020M2\u0006\u0010W\u001a\u00020DH\u0002J\u0010\u0010g\u001a\u00020P2\u0006\u0010W\u001a\u00020DH\u0002J\b\u0010h\u001a\u00020MH\u0002J\u0006\u0010i\u001a\u00020MJ\b\u0010j\u001a\u00020MH\u0002J\b\u0010k\u001a\u00020MH\u0002J\b\u0010l\u001a\u00020MH\u0002J\b\u0010m\u001a\u00020MH\u0002J\u0010\u0010n\u001a\u00020U2\u0006\u0010W\u001a\u00020DH\u0002J\b\u0010o\u001a\u00020UH\u0002J\u0006\u0010p\u001a\u00020UJ\u0006\u0010q\u001a\u00020UJ\u0010\u0010r\u001a\u00020M2\u0006\u0010s\u001a\u00020\u000bH\u0002J\u0010\u0010t\u001a\u00020\u000b2\u0006\u0010u\u001a\u00020\u000bH\u0002J\b\u0010v\u001a\u00020MH\u0002J\u0006\u0010w\u001a\u00020UJ\b\u0010x\u001a\u00020MH\u0002J$\u0010y\u001a\u00020 2\u0006\u0010z\u001a\u00020{2\b\u0010|\u001a\u0004\u0018\u00010}2\b\u0010~\u001a\u0004\u0018\u00010\u007fH\u0016J\t\u0010\u0080\u0001\u001a\u00020MH\u0016J\u001c\u0010\u0081\u0001\u001a\u00020M2\u0007\u0010\u0082\u0001\u001a\u00020 2\b\u0010~\u001a\u0004\u0018\u00010\u007fH\u0016J'\u0010\u0083\u0001\u001a\u00020M2\u0006\u0010Y\u001a\u00020\u000b2\t\u0010\u0084\u0001\u001a\u0004\u0018\u00010\u000b2\t\u0010\u0085\u0001\u001a\u0004\u0018\u00010\u000bH\u0002J\u000f\u0010\u0086\u0001\u001a\u00020M2\u0006\u0010Y\u001a\u00020\u000bJ\u0011\u0010\u0087\u0001\u001a\u00020M2\u0006\u0010Y\u001a\u00020\u000bH\u0002J\u0011\u0010\u0088\u0001\u001a\u00020M2\u0006\u0010Y\u001a\u00020\u000bH\u0002J\u0007\u0010\u0089\u0001\u001a\u00020MJ\u0011\u0010\u008a\u0001\u001a\u00020M2\u0006\u0010W\u001a\u00020DH\u0002J\u0012\u0010\u008b\u0001\u001a\u00020\r2\u0007\u0010\u008c\u0001\u001a\u00020\rH\u0002J\u0012\u0010\u008d\u0001\u001a\u00020M2\u0007\u0010\u008e\u0001\u001a\u00020\u000bH\u0002J\u0012\u0010\u008f\u0001\u001a\u00020M2\u0007\u0010\u0090\u0001\u001a\u00020UH\u0002J\t\u0010\u0091\u0001\u001a\u00020MH\u0002J\t\u0010\u0092\u0001\u001a\u00020MH\u0002J\t\u0010\u0093\u0001\u001a\u00020MH\u0002J\t\u0010\u0094\u0001\u001a\u00020MH\u0002J\t\u0010\u0095\u0001\u001a\u00020MH\u0002J\u0011\u0010\u0096\u0001\u001a\u00020M2\u0006\u0010Y\u001a\u00020\u000bH\u0002J!\u0010\u0097\u0001\u001a\u00020M2\t\u0010\u0098\u0001\u001a\u0004\u0018\u00010\u000b2\u000b\b\u0002\u0010\u0099\u0001\u001a\u0004\u0018\u00010\u000bH\u0002J!\u0010\u009a\u0001\u001a\u00020M2\t\u0010\u0098\u0001\u001a\u0004\u0018\u00010\u000b2\u000b\b\u0002\u0010\u0099\u0001\u001a\u0004\u0018\u00010\u000bH\u0002J\u0013\u0010\u009b\u0001\u001a\u00020M2\b\u0010\u009c\u0001\u001a\u00030\u009d\u0001H\u0002J\u0007\u0010\u009e\u0001\u001a\u00020MJ/\u0010\u009f\u0001\u001a\u00020U2\u0006\u0010S\u001a\u00020P2\b\u0010\u00a0\u0001\u001a\u00030\u00a1\u00012\b\u0010\u00a2\u0001\u001a\u00030\u00a3\u00012\b\u0010\u00a4\u0001\u001a\u00030\u00a3\u0001H\u0002J\t\u0010\u00a5\u0001\u001a\u00020MH\u0002J\u0013\u0010\u00a6\u0001\u001a\u00020M2\b\u0010\u009c\u0001\u001a\u00030\u009d\u0001H\u0002J\t\u0010\u00a7\u0001\u001a\u00020MH\u0002J\t\u0010\u00a8\u0001\u001a\u00020MH\u0002J\t\u0010\u00a9\u0001\u001a\u00020MH\u0002J\t\u0010\u00aa\u0001\u001a\u00020MH\u0002J\u0011\u0010\u00ab\u0001\u001a\u00020M2\u0006\u0010N\u001a\u00020\rH\u0002J\t\u0010\u00ac\u0001\u001a\u00020MH\u0002J\u0007\u0010\u00ad\u0001\u001a\u00020MJ\u0012\u0010\u00ae\u0001\u001a\u00020M2\u0007\u0010\u00af\u0001\u001a\u00020&H\u0002J\u0011\u0010\u00b0\u0001\u001a\u00020M2\u0006\u0010W\u001a\u00020DH\u0002J\u0011\u0010\u00b1\u0001\u001a\u00020M2\u0006\u0010W\u001a\u00020DH\u0002J\u0011\u0010\u00b2\u0001\u001a\u00020M2\u0006\u0010W\u001a\u00020DH\u0002J\t\u0010\u00b3\u0001\u001a\u00020MH\u0002J \u0010\u00b4\u0001\u001a\u00020M2\u0007\u0010\u00b5\u0001\u001a\u00020 2\f\b\u0002\u0010\u00b6\u0001\u001a\u0005\u0018\u00010\u009d\u0001H\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\bX\u0082.\u00a2\u0006\u0002\n\u0000R\u0014\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u000b0\nX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\f\u001a\u00020\rX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u000e\u001a\u0004\u0018\u00010\u000fX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0010\u001a\u00020\u0011X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0012\u001a\u0004\u0018\u00010\u000bX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0013\u001a\u00020\u000fX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0014\u001a\u00020\u0015X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0016\u001a\u00020\bX\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0017\u001a\u00020\u0018X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0019\u001a\u00020\u001aX\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u001b\u001a\u00020\bX\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u001c\u001a\u00020\bX\u0082.\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u001d\u001a\u0004\u0018\u00010\u001eX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u001f\u001a\u0004\u0018\u00010 X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010!\u001a\u00020\bX\u0082.\u00a2\u0006\u0002\n\u0000R\u0010\u0010\"\u001a\u0004\u0018\u00010\u000bX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010#\u001a\u00020 X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010$\u001a\u00020\bX\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010%\u001a\u00020&X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010'\u001a\u00020\bX\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010(\u001a\u00020)X\u0082.\u00a2\u0006\u0002\n\u0000R\u0010\u0010*\u001a\u0004\u0018\u00010+X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010,\u001a\u0004\u0018\u00010-X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u001c\u0010.\u001a\u0010\u0012\f\u0012\n 0*\u0004\u0018\u00010\u000b0\u000b0/X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u00101\u001a\u00020\u001aX\u0082.\u00a2\u0006\u0002\n\u0000R\u0010\u00102\u001a\u0004\u0018\u000103X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u00104\u001a\u00020+X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u00105\u001a\u000206X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u00107\u001a\u00020 X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u00108\u001a\u000209X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010:\u001a\u00020\u000fX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010;\u001a\u0004\u0018\u00010<X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010=\u001a\u00020>X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010?\u001a\u00020\u0015X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010@\u001a\u000209X\u0082.\u00a2\u0006\u0002\n\u0000R\u0014\u0010A\u001a\b\u0012\u0004\u0012\u00020&0BX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010C\u001a\b\u0012\u0004\u0012\u00020D0BX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010E\u001a\u00020FX\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010G\u001a\u00020\u001aX\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010H\u001a\u00020\u0018X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010I\u001a\u00020FX\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010J\u001a\u00020KX\u0082.\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u00bb\u0001"}, d2 = {"Lcom/invictus/xmd/ui/BrowserFragment;", "Landroidx/fragment/app/Fragment;", "()V", "adapter", "Lcom/invictus/xmd/ui/ShortcutAdapter;", "addLinkFab", "Lcom/google/android/material/floatingactionbutton/FloatingActionButton;", "bookmarkStarButton", "Landroid/widget/ImageButton;", "bookmarkedUrls", "", "", "currentTabIndex", "", "dohClient", "Lokhttp3/OkHttpClient;", "dohClientLock", "", "dohClientSignature", "filenameClient", "findInPageBar", "Lcom/google/android/material/card/MaterialCardView;", "findInPageClose", "findInPageInput", "Landroid/widget/EditText;", "findInPageMatchCount", "Landroid/widget/TextView;", "findInPageNext", "findInPagePrev", "fullscreenCallback", "Landroid/webkit/WebChromeClient$CustomViewCallback;", "fullscreenView", "Landroid/view/View;", "homeButton", "lastDetectedLink", "navLoadingVeil", "newTabButton", "nextTabId", "", "overflowButton", "pageProgress", "Lcom/google/android/material/progressindicator/LinearProgressIndicator;", "pendingIconPreview", "Landroid/widget/ImageView;", "pendingIconUri", "Landroid/net/Uri;", "pickIconLauncher", "Landroidx/activity/result/ActivityResultLauncher;", "kotlin.jvm.PlatformType", "shortcutReorderToggle", "shortcutTouchHelper", "Landroidx/recyclerview/widget/ItemTouchHelper;", "siteSecurityIcon", "sniffedMediaFab", "Lcom/google/android/material/floatingactionbutton/ExtendedFloatingActionButton;", "speedDialContainer", "speedDialGrid", "Landroidx/recyclerview/widget/RecyclerView;", "suggestClient", "suggestJob", "Lkotlinx/coroutines/Job;", "suggestionAdapter", "Lcom/invictus/xmd/ui/SuggestionAdapter;", "suggestionsCard", "suggestionsList", "tabAccessOrder", "", "tabs", "Lcom/invictus/xmd/ui/BrowserFragment$BrowserTab;", "tabsButton", "Landroid/widget/FrameLayout;", "tabsCount", "urlInput", "webViewContainer", "webViewSwipeRefresh", "Landroidx/swiperefreshlayout/widget/SwipeRefreshLayout;", "activateTab", "", "index", "previousView", "Landroid/webkit/WebView;", "addNewTab", "applyDesktopMode", "webView", "desktop", "", "applyTabUiState", "tab", "checkPageForLinks", "url", "clearBrowsingData", "clearHistory", "clearCookies", "clearCache", "clearDetectedLink", "closeTab", "configureWebView", "copyLinkToClipboard", "crossfadeSwap", "newView", "oldView", "currentDohClient", "destroyTabWebView", "ensureWebView", "evictIfNeeded", "exitFullscreenVideo", "goHome", "hideFindInPage", "hideNavLoadingVeil", "hideSuggestions", "isCurrentTab", "isCurrentTabDesktopMode", "isDesktopModeOn", "isInFullscreenVideo", "loadUrl", "raw", "normalizeToUrl", "input", "onAddLinkClicked", "onBackPressed", "onBookmarkStarTapped", "onCreateView", "inflater", "Landroid/view/LayoutInflater;", "container", "Landroid/view/ViewGroup;", "savedInstanceState", "Landroid/os/Bundle;", "onPause", "onViewCreated", "view", "onWebViewDownloadRequested", "contentDisposition", "mimeType", "openUrl", "openUrlInNewTab", "prefetchDns", "reloadActiveTab", "resetTabToBlank", "resolveThemeColor", "attrResId", "scheduleSuggest", "query", "setImmersiveMode", "enabled", "setupAddressBar", "setupFindInPage", "setupPullToRefresh", "setupSpeedDial", "setupSuggestions", "shareLink", "showAddBookmarkDialog", "prefillUrl", "prefillTitle", "showAddShortcutDialog", "showEditShortcutDialog", "shortcut", "Lcom/invictus/xmd/core/Shortcut;", "showFindInPage", "showLinkContextMenu", "result", "Landroid/webkit/WebView$HitTestResult;", "touchX", "", "touchY", "showNavLoadingVeil", "showShortcutOptionsDialog", "showSniffedMediaSheet", "showSpeedDial", "showTabsDialog", "showWebView", "switchToTab", "toggleDesktopMode", "toggleDesktopModeForCurrentTab", "touchLru", "id", "updateBookmarkStar", "updateSecurityIcon", "updateSniffedMediaFab", "updateTabsCount", "wireIconPicker", "dialogView", "existing", "BrowserTab", "Callbacks", "Companion", "ShortcutDragCallback", "app_fullDebug"})
public final class BrowserFragment extends androidx.fragment.app.Fragment {
    
    /**
     * Max WebView instances kept alive across all tabs at once. Beyond
     * this, the least-recently-used *non-current* tab's WebView is
     * torn down (state saved first) to keep memory bounded, same
     * general idea as Chrome's background tab discarding.
     */
    private static final int MAX_LIVE_WEBVIEWS = 5;
    private static final long TAB_SWITCH_ANIM_MS = 130L;
    
    /**
     * Cap on local history matches shown in the address-bar dropdown --
     * Chrome-style: a handful of your own visited pages, not a full list,
     * since the remaining rows are Google's live search suggestions.
     */
    private static final int MAX_HISTORY_SUGGESTIONS = 5;
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String DESKTOP_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String MOBILE_USER_AGENT = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
    private android.widget.ImageButton newTabButton;
    private android.widget.ImageButton homeButton;
    private android.widget.EditText urlInput;
    private android.widget.FrameLayout tabsButton;
    private android.widget.TextView tabsCount;
    private android.widget.ImageButton overflowButton;
    private com.google.android.material.progressindicator.LinearProgressIndicator pageProgress;
    private android.widget.ImageView siteSecurityIcon;
    private android.widget.ImageButton bookmarkStarButton;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout webViewSwipeRefresh;
    private android.widget.FrameLayout webViewContainer;
    private android.view.View navLoadingVeil;
    private android.view.View speedDialContainer;
    private androidx.recyclerview.widget.RecyclerView speedDialGrid;
    private android.widget.TextView shortcutReorderToggle;
    @org.jetbrains.annotations.Nullable()
    private androidx.recyclerview.widget.ItemTouchHelper shortcutTouchHelper;
    private com.google.android.material.floatingactionbutton.FloatingActionButton addLinkFab;
    private com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton sniffedMediaFab;
    private com.google.android.material.card.MaterialCardView suggestionsCard;
    private androidx.recyclerview.widget.RecyclerView suggestionsList;
    private com.google.android.material.card.MaterialCardView findInPageBar;
    private android.widget.EditText findInPageInput;
    private android.widget.TextView findInPageMatchCount;
    private android.widget.ImageButton findInPagePrev;
    private android.widget.ImageButton findInPageNext;
    private android.widget.ImageButton findInPageClose;
    private com.invictus.xmd.ui.ShortcutAdapter adapter;
    private com.invictus.xmd.ui.SuggestionAdapter suggestionAdapter;
    @org.jetbrains.annotations.Nullable()
    private android.net.Uri pendingIconUri;
    @org.jetbrains.annotations.Nullable()
    private android.widget.ImageView pendingIconPreview;
    @org.jetbrains.annotations.NotNull()
    private final androidx.activity.result.ActivityResultLauncher<java.lang.String> pickIconLauncher = null;
    @org.jetbrains.annotations.Nullable()
    private java.lang.String lastDetectedLink;
    @org.jetbrains.annotations.Nullable()
    private kotlinx.coroutines.Job suggestJob;
    @org.jetbrains.annotations.NotNull()
    private java.util.Set<java.lang.String> bookmarkedUrls;
    @org.jetbrains.annotations.NotNull()
    private final java.util.List<com.invictus.xmd.ui.BrowserFragment.BrowserTab> tabs = null;
    private int currentTabIndex = 0;
    private long nextTabId = 1L;
    @org.jetbrains.annotations.NotNull()
    private final java.util.List<java.lang.Long> tabAccessOrder = null;
    @org.jetbrains.annotations.NotNull()
    private final okhttp3.OkHttpClient suggestClient = null;
    @org.jetbrains.annotations.NotNull()
    private final okhttp3.OkHttpClient filenameClient = null;
    @kotlin.jvm.Volatile()
    @org.jetbrains.annotations.Nullable()
    private volatile okhttp3.OkHttpClient dohClient;
    @kotlin.jvm.Volatile()
    @org.jetbrains.annotations.Nullable()
    private volatile java.lang.String dohClientSignature;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.Object dohClientLock = null;
    @org.jetbrains.annotations.Nullable()
    private android.view.View fullscreenView;
    @org.jetbrains.annotations.Nullable()
    private android.webkit.WebChromeClient.CustomViewCallback fullscreenCallback;
    @org.jetbrains.annotations.NotNull()
    public static final com.invictus.xmd.ui.BrowserFragment.Companion Companion = null;
    
    public BrowserFragment() {
        super();
    }
    
    /**
     * (Re)builds dohClient only if the effective DNS setting actually changed.
     */
    private final okhttp3.OkHttpClient currentDohClient() {
        return null;
    }
    
    /**
     * Warms the DoH resolver's host cache for [url] in the background right
     * as navigation starts, so by the time shouldInterceptRequest actually
     * needs the address it's often already resolved instead of paying a
     * DNS round-trip on the critical path of the very first request.
     */
    private final void prefetchDns(java.lang.String url) {
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public android.view.View onCreateView(@org.jetbrains.annotations.NotNull()
    android.view.LayoutInflater inflater, @org.jetbrains.annotations.Nullable()
    android.view.ViewGroup container, @org.jetbrains.annotations.Nullable()
    android.os.Bundle savedInstanceState) {
        return null;
    }
    
    @java.lang.Override()
    public void onViewCreated(@org.jetbrains.annotations.NotNull()
    android.view.View view, @org.jetbrains.annotations.Nullable()
    android.os.Bundle savedInstanceState) {
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
    @java.lang.Override()
    public void onPause() {
    }
    
    /**
     * Chrome-style pull-to-refresh: only fires when the active WebView is
     * already scrolled to the top (setOnChildScrollUpCallback), same as
     * Chrome -- otherwise a downward scroll mid-page would trigger a
     * refresh instead of just scrolling. Replaces the old dedicated reload
     * button; manual reload also still available via the overflow menu's
     * "Refresh" item (see MainActivity.openBrowserMenu -> reloadActiveTab()).
     */
    private final void setupPullToRefresh() {
    }
    
    /**
     * Called from MainActivity's overflow menu "Refresh" item.
     */
    public final void reloadActiveTab() {
    }
    
    /**
     * Called from MainActivity's overflow menu "Desktop site" checkbox.
     */
    public final void toggleDesktopModeForCurrentTab() {
    }
    
    /**
     * Called from MainActivity to set the checkbox's checked state before showing the menu.
     */
    public final boolean isDesktopModeOn() {
        return false;
    }
    
    /**
     * Overflow menu's "Clear browsing data" dialog result. Cache/cookies
     * are cleared through every currently-live WebView (any tab whose
     * WebView has been torn down by LRU eviction has nothing left to
     * clear anyway) since there's no single global handle for either --
     * each WebView instance owns its own cache, though the cookie jar
     * itself is shared, so clearing it once via any instance is enough.
     */
    public final void clearBrowsingData(boolean clearHistory, boolean clearCookies, boolean clearCache) {
    }
    
    /**
     * Home button: returns the *current* tab to the speed dial (unlike New
     * Tab, which opens an additional tab) -- reuses the existing tab slot
     * instead of growing the tab count.
     */
    private final void goHome() {
    }
    
    private final boolean isCurrentTab(com.invictus.xmd.ui.BrowserFragment.BrowserTab tab) {
        return false;
    }
    
    private final void touchLru(long id) {
    }
    
    /**
     * Returns [tab]'s live WebView, creating (or restoring) it if needed.
     */
    private final android.webkit.WebView ensureWebView(com.invictus.xmd.ui.BrowserFragment.BrowserTab tab) {
        return null;
    }
    
    /**
     * Tears down [tab]'s WebView, snapshotting its state first so a later
     * visit can restore instantly instead of reloading from the network.
     */
    private final void destroyTabWebView(com.invictus.xmd.ui.BrowserFragment.BrowserTab tab) {
    }
    
    /**
     * Never evicts the currently active tab, even if it's the oldest entry.
     */
    private final void evictIfNeeded() {
    }
    
    /**
     * Fully resets [tab] to a blank "New tab" state, tearing down its WebView.
     */
    private final void resetTabToBlank(com.invictus.xmd.ui.BrowserFragment.BrowserTab tab) {
    }
    
    @android.annotation.SuppressLint(value = {"SetJavaScriptEnabled"})
    private final void configureWebView(android.webkit.WebView webView, com.invictus.xmd.ui.BrowserFragment.BrowserTab tab) {
    }
    
    /**
     * True while a fullscreen <video> is up -- MainActivity's back handler
     * checks this first so back exits fullscreen instead of navigating
     * the page underneath it.
     */
    public final boolean isInFullscreenVideo() {
        return false;
    }
    
    /**
     * Called by MainActivity's back handler when [isInFullscreenVideo] is
     * true, and directly by onHideCustomView's own decor cleanup path --
     * webView.webChromeClient?.onHideCustomView() is the documented way to
     * ask WebView to exit fullscreen from the app side (it then calls our
     * onHideCustomView override above to actually tear the view down).
     */
    public final void exitFullscreenVideo() {
    }
    
    private final void setImmersiveMode(boolean enabled) {
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
    public final boolean onBackPressed() {
        return false;
    }
    
    private final void setupAddressBar() {
    }
    
    private final void setupSuggestions() {
    }
    
    /**
     * 2-3 letters is enough to start querying, debounced ~150ms so we're not
     * firing a network request on every keystroke. Merges two sources,
     * history first then search (Chrome-style):
     * - local visited-page history (HistoryRepository's already-cached
     *   LiveData value -- no DB round-trip needed here), matched by
     *   title/URL substring, capped at [MAX_HISTORY_SUGGESTIONS]
     * - Google's public suggest endpoint, filtered to search-phrase
     *   results only (see SuggestApi) -- no bundled/bare-URL "website"
     *   suggestions of any kind
     */
    private final void scheduleSuggest(java.lang.String query) {
    }
    
    private final void hideSuggestions() {
    }
    
    private final void setupFindInPage() {
    }
    
    /**
     * Opened from the overflow menu's "Find in page" item. Wires the
     * active tab's WebView.FindListener fresh each time (rather than once
     * up front) since the active WebView instance can change between
     * opens as tabs get created/switched/evicted.
     */
    public final void showFindInPage() {
    }
    
    private final void hideFindInPage() {
    }
    
    private final void updateSecurityIcon(com.invictus.xmd.ui.BrowserFragment.BrowserTab tab) {
    }
    
    /**
     * Filled star when the loaded page's URL is already saved as a
     * bookmark, outline otherwise; hidden entirely on the speed dial (no
     * page yet).
     */
    private final void updateBookmarkStar(com.invictus.xmd.ui.BrowserFragment.BrowserTab tab) {
    }
    
    /**
     * Star tapped: adds the current page as a bookmark (via the Add
     * Bookmark dialog, prefilled -- with a checkbox to also add it as a
     * speed-dial Shortcut) if it isn't one yet, or removes the bookmark
     * in one tap if it already is -- Chrome-style toggle. Never touches
     * Shortcuts on removal; those are independent once created.
     */
    private final void onBookmarkStarTapped() {
    }
    
    /**
     * Syncs the shared toolbar (address text, lock icon, progress, reload/
     * stop icon, download-link FAB) from [tab]'s own state. Call whenever
     * [tab] becomes the active one.
     */
    private final void applyTabUiState(com.invictus.xmd.ui.BrowserFragment.BrowserTab tab) {
    }
    
    /**
     * Called from MainActivity (e.g. reopening a History entry) to load a
     * URL in the current tab, same as typing it into the address bar.
     */
    public final void openUrl(@org.jetbrains.annotations.NotNull()
    java.lang.String url) {
    }
    
    private final void loadUrl(java.lang.String raw) {
    }
    
    /**
     * Bare host/search text -> https URL; anything already URL-shaped is passed through.
     */
    private final java.lang.String normalizeToUrl(java.lang.String input) {
        return null;
    }
    
    private final void setupSpeedDial() {
    }
    
    private final void showSpeedDial() {
    }
    
    private final void showWebView() {
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
    private final void showNavLoadingVeil() {
    }
    
    private final void hideNavLoadingVeil() {
    }
    
    private final void showAddShortcutDialog(java.lang.String prefillUrl, java.lang.String prefillTitle) {
    }
    
    /**
     * Wires the icon-preview tile in dialog_add_shortcut.xml to launch the
     * system photo picker, and (for edits) shows the shortcut's current
     * icon -- custom if it has one, else its live favicon.
     */
    private final void wireIconPicker(android.view.View dialogView, com.invictus.xmd.core.Shortcut existing) {
    }
    
    private final void showShortcutOptionsDialog(com.invictus.xmd.core.Shortcut shortcut) {
    }
    
    private final void showEditShortcutDialog(com.invictus.xmd.core.Shortcut shortcut) {
    }
    
    /**
     * Star-button flow: saves a real Bookmark for the current page. The
     * checkbox additionally creates a matching speed-dial Shortcut in the
     * same tap -- the two lists stay independent after that (removing the
     * bookmark later never removes the shortcut, and vice versa).
     */
    private final void showAddBookmarkDialog(java.lang.String prefillUrl, java.lang.String prefillTitle) {
    }
    
    private final void updateTabsCount() {
    }
    
    private final void addNewTab() {
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
    private final void activateTab(int index, android.webkit.WebView previousView) {
    }
    
    /**
     * Crossfades [newView] in over [oldView] (if any, and if different).
     */
    private final void crossfadeSwap(android.webkit.WebView newView, android.webkit.WebView oldView) {
    }
    
    /**
     * Closes a tab. Never drops below one tab -- closing the last remaining
     * one just resets it to a fresh "New tab" instead of removing it, same
     * as closing the last tab in a normal browser (a new tab effectively
     * "opens" automatically since the speed dial is shown right away).
     */
    private final void closeTab(int index) {
    }
    
    private final void switchToTab(int index) {
    }
    
    /**
     * Tabs tray: a bottom sheet (not a modal dialog) listing every open tab
     * as a compact pill -- round icon, title, close X -- with a floating
     * "+" beneath the list instead of a dialog footer button.
     */
    private final void showTabsDialog() {
    }
    
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
    private final void onWebViewDownloadRequested(java.lang.String url, java.lang.String contentDisposition, java.lang.String mimeType) {
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
    private final void checkPageForLinks(java.lang.String url) {
    }
    
    private final void clearDetectedLink() {
    }
    
    /**
     * Reflects [tab]'s current sniffedMedia count onto the chip -- called
     * from onPageStarted (clears it), and from shouldInterceptRequest's
     * sniff hook every time a genuinely new stream URL is found. No-op
     * visually unless [tab] is the tab currently on screen.
     */
    private final void updateSniffedMediaFab(com.invictus.xmd.ui.BrowserFragment.BrowserTab tab) {
    }
    
    /**
     * Bottom sheet listing every stream in the current tab's sniffedMedia,
     * tapping a row hands it straight to Callbacks.triggerSniffedMedia; each
     * row also carries a copy button to grab the raw URL without starting
     * a download.
     */
    private final void showSniffedMediaSheet() {
    }
    
    /**
     * Applies (or reverts) desktop-site emulation on [webView]: a desktop
     * Chrome UA string plus wide-viewport rendering, same two settings a
     * real browser's "Desktop site" toggle flips. Doesn't reload itself --
     * callers that change this on an already-loaded page (the overflow
     * menu toggle) are responsible for reloading afterwards so the new UA
     * actually takes effect.
     */
    private final void applyDesktopMode(android.webkit.WebView webView, boolean desktop) {
    }
    
    /**
     * Overflow menu's "Desktop site" checkbox -- flips the current tab
     * only. Uses loadUrl() (a real fresh network request) instead of
     * reload(), which was the actual bug: WebView's reload() can be
     * served straight from its own HTTP cache, so a page fetched under
     * the old UA string just came back byte-for-byte identical from
     * cache -- the new UA never even reached the server on some sites.
     * loadUrl() with the exact current URL forces a genuine new request.
     * Cache mode is also forced to LOAD_NO_CACHE for just this one
     * navigation (restored to the normal LOAD_DEFAULT right after
     * starting it) so even a cached response under the *new* UA from an
     * earlier visit can't mask a real mismatch -- guarantees this one
     * load actually hits the server fresh.
     */
    private final void toggleDesktopMode() {
    }
    
    private final boolean isCurrentTabDesktopMode() {
        return false;
    }
    
    private final void onAddLinkClicked() {
    }
    
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
    private final boolean showLinkContextMenu(android.webkit.WebView webView, android.webkit.WebView.HitTestResult result, float touchX, float touchY) {
        return false;
    }
    
    /**
     * Opens [url] in a brand-new background... actually foreground tab,
     * Chrome-style: the new tab becomes current and is shown immediately.
     */
    private final void openUrlInNewTab(java.lang.String url) {
    }
    
    private final void copyLinkToClipboard(java.lang.String url) {
    }
    
    private final void shareLink(java.lang.String url) {
    }
    
    /**
     * Resolves a color from the current active theme (Theme.Xmd.*) instead
     * of a static @color resource, so tab-switcher rows, the favicon tint,
     * and the pull-to-refresh spinner all follow the selected app theme.
     */
    private final int resolveThemeColor(int attrResId) {
        return 0;
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
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000>\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\t\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0010%\n\u0002\u0018\u0002\n\u0002\b+\b\u0082\b\u0018\u00002\u00020\u0001By\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\n\b\u0002\u0010\u0004\u001a\u0004\u0018\u00010\u0005\u0012\b\b\u0002\u0010\u0006\u001a\u00020\u0005\u0012\n\b\u0002\u0010\u0007\u001a\u0004\u0018\u00010\b\u0012\n\b\u0002\u0010\t\u001a\u0004\u0018\u00010\n\u0012\b\b\u0002\u0010\u000b\u001a\u00020\f\u0012\b\b\u0002\u0010\r\u001a\u00020\u000e\u0012\b\b\u0002\u0010\u000f\u001a\u00020\f\u0012\b\b\u0002\u0010\u0010\u001a\u00020\f\u0012\u0014\b\u0002\u0010\u0011\u001a\u000e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020\u00130\u0012\u00a2\u0006\u0002\u0010\u0014J\t\u0010/\u001a\u00020\u0003H\u00c6\u0003J\u0015\u00100\u001a\u000e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020\u00130\u0012H\u00c6\u0003J\u000b\u00101\u001a\u0004\u0018\u00010\u0005H\u00c6\u0003J\t\u00102\u001a\u00020\u0005H\u00c6\u0003J\u000b\u00103\u001a\u0004\u0018\u00010\bH\u00c6\u0003J\u000b\u00104\u001a\u0004\u0018\u00010\nH\u00c6\u0003J\t\u00105\u001a\u00020\fH\u00c6\u0003J\t\u00106\u001a\u00020\u000eH\u00c6\u0003J\t\u00107\u001a\u00020\fH\u00c6\u0003J\t\u00108\u001a\u00020\fH\u00c6\u0003J\u007f\u00109\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\n\b\u0002\u0010\u0004\u001a\u0004\u0018\u00010\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u00052\n\b\u0002\u0010\u0007\u001a\u0004\u0018\u00010\b2\n\b\u0002\u0010\t\u001a\u0004\u0018\u00010\n2\b\b\u0002\u0010\u000b\u001a\u00020\f2\b\b\u0002\u0010\r\u001a\u00020\u000e2\b\b\u0002\u0010\u000f\u001a\u00020\f2\b\b\u0002\u0010\u0010\u001a\u00020\f2\u0014\b\u0002\u0010\u0011\u001a\u000e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020\u00130\u0012H\u00c6\u0001J\u0013\u0010:\u001a\u00020\f2\b\u0010;\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010<\u001a\u00020\u000eH\u00d6\u0001J\t\u0010=\u001a\u00020\u0005H\u00d6\u0001R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0015\u0010\u0016R\u001a\u0010\u000f\u001a\u00020\fX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u000f\u0010\u0017\"\u0004\b\u0018\u0010\u0019R\u001a\u0010\u000b\u001a\u00020\fX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u000b\u0010\u0017\"\u0004\b\u001a\u0010\u0019R\u0011\u0010\u0010\u001a\u00020\f\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0010\u0010\u0017R\u001a\u0010\r\u001a\u00020\u000eX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u001b\u0010\u001c\"\u0004\b\u001d\u0010\u001eR\u001d\u0010\u0011\u001a\u000e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020\u00130\u0012\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001f\u0010 R\u001a\u0010\u0006\u001a\u00020\u0005X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b!\u0010\"\"\u0004\b#\u0010$R\u001c\u0010\u0004\u001a\u0004\u0018\u00010\u0005X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b%\u0010\"\"\u0004\b&\u0010$R\u001c\u0010\u0007\u001a\u0004\u0018\u00010\bX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b'\u0010(\"\u0004\b)\u0010*R\u001c\u0010\t\u001a\u0004\u0018\u00010\nX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b+\u0010,\"\u0004\b-\u0010.\u00a8\u0006>"}, d2 = {"Lcom/invictus/xmd/ui/BrowserFragment$BrowserTab;", "", "id", "", "url", "", "title", "webView", "Landroid/webkit/WebView;", "webViewState", "Landroid/os/Bundle;", "isLoading", "", "progress", "", "isDesktopMode", "isPrivate", "sniffedMedia", "", "Lcom/invictus/xmd/core/MediaSniffer$Sniffed;", "(JLjava/lang/String;Ljava/lang/String;Landroid/webkit/WebView;Landroid/os/Bundle;ZIZZLjava/util/Map;)V", "getId", "()J", "()Z", "setDesktopMode", "(Z)V", "setLoading", "getProgress", "()I", "setProgress", "(I)V", "getSniffedMedia", "()Ljava/util/Map;", "getTitle", "()Ljava/lang/String;", "setTitle", "(Ljava/lang/String;)V", "getUrl", "setUrl", "getWebView", "()Landroid/webkit/WebView;", "setWebView", "(Landroid/webkit/WebView;)V", "getWebViewState", "()Landroid/os/Bundle;", "setWebViewState", "(Landroid/os/Bundle;)V", "component1", "component10", "component2", "component3", "component4", "component5", "component6", "component7", "component8", "component9", "copy", "equals", "other", "hashCode", "toString", "app_fullDebug"})
    static final class BrowserTab {
        private final long id = 0L;
        @org.jetbrains.annotations.Nullable()
        private java.lang.String url;
        @org.jetbrains.annotations.NotNull()
        private java.lang.String title;
        @org.jetbrains.annotations.Nullable()
        private android.webkit.WebView webView;
        @org.jetbrains.annotations.Nullable()
        private android.os.Bundle webViewState;
        private boolean isLoading;
        private int progress;
        private boolean isDesktopMode;
        private final boolean isPrivate = false;
        @org.jetbrains.annotations.NotNull()
        private final java.util.Map<java.lang.String, com.invictus.xmd.core.MediaSniffer.Sniffed> sniffedMedia = null;
        
        public BrowserTab(long id, @org.jetbrains.annotations.Nullable()
        java.lang.String url, @org.jetbrains.annotations.NotNull()
        java.lang.String title, @org.jetbrains.annotations.Nullable()
        android.webkit.WebView webView, @org.jetbrains.annotations.Nullable()
        android.os.Bundle webViewState, boolean isLoading, int progress, boolean isDesktopMode, boolean isPrivate, @org.jetbrains.annotations.NotNull()
        java.util.Map<java.lang.String, com.invictus.xmd.core.MediaSniffer.Sniffed> sniffedMedia) {
            super();
        }
        
        public final long getId() {
            return 0L;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String getUrl() {
            return null;
        }
        
        public final void setUrl(@org.jetbrains.annotations.Nullable()
        java.lang.String p0) {
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getTitle() {
            return null;
        }
        
        public final void setTitle(@org.jetbrains.annotations.NotNull()
        java.lang.String p0) {
        }
        
        @org.jetbrains.annotations.Nullable()
        public final android.webkit.WebView getWebView() {
            return null;
        }
        
        public final void setWebView(@org.jetbrains.annotations.Nullable()
        android.webkit.WebView p0) {
        }
        
        @org.jetbrains.annotations.Nullable()
        public final android.os.Bundle getWebViewState() {
            return null;
        }
        
        public final void setWebViewState(@org.jetbrains.annotations.Nullable()
        android.os.Bundle p0) {
        }
        
        public final boolean isLoading() {
            return false;
        }
        
        public final void setLoading(boolean p0) {
        }
        
        public final int getProgress() {
            return 0;
        }
        
        public final void setProgress(int p0) {
        }
        
        public final boolean isDesktopMode() {
            return false;
        }
        
        public final void setDesktopMode(boolean p0) {
        }
        
        public final boolean isPrivate() {
            return false;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.util.Map<java.lang.String, com.invictus.xmd.core.MediaSniffer.Sniffed> getSniffedMedia() {
            return null;
        }
        
        public final long component1() {
            return 0L;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.util.Map<java.lang.String, com.invictus.xmd.core.MediaSniffer.Sniffed> component10() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String component2() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component3() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final android.webkit.WebView component4() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final android.os.Bundle component5() {
            return null;
        }
        
        public final boolean component6() {
            return false;
        }
        
        public final int component7() {
            return 0;
        }
        
        public final boolean component8() {
            return false;
        }
        
        public final boolean component9() {
            return false;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.invictus.xmd.ui.BrowserFragment.BrowserTab copy(long id, @org.jetbrains.annotations.Nullable()
        java.lang.String url, @org.jetbrains.annotations.NotNull()
        java.lang.String title, @org.jetbrains.annotations.Nullable()
        android.webkit.WebView webView, @org.jetbrains.annotations.Nullable()
        android.os.Bundle webViewState, boolean isLoading, int progress, boolean isDesktopMode, boolean isPrivate, @org.jetbrains.annotations.NotNull()
        java.util.Map<java.lang.String, com.invictus.xmd.core.MediaSniffer.Sniffed> sniffedMedia) {
            return null;
        }
        
        @java.lang.Override()
        public boolean equals(@org.jetbrains.annotations.Nullable()
        java.lang.Object other) {
            return false;
        }
        
        @java.lang.Override()
        public int hashCode() {
            return 0;
        }
        
        @java.lang.Override()
        @org.jetbrains.annotations.NotNull()
        public java.lang.String toString() {
            return null;
        }
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000*\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0000\bf\u0018\u00002\u00020\u0001J\u0010\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u0005H&J\u0016\u0010\u0006\u001a\u00020\u00032\f\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\t0\bH&J\u0018\u0010\n\u001a\u00020\u00032\u0006\u0010\u000b\u001a\u00020\t2\u0006\u0010\f\u001a\u00020\rH&\u00a8\u0006\u000e"}, d2 = {"Lcom/invictus/xmd/ui/BrowserFragment$Callbacks;", "", "openBrowserMenu", "", "anchor", "Landroid/view/View;", "triggerPrepare", "lines", "", "", "triggerSniffedMedia", "url", "needsPicker", "", "app_fullDebug"})
    public static abstract interface Callbacks {
        
        /**
         * Same handoff HomeFragment uses for pasted links -- expands + queues + resolves.
         */
        public abstract void triggerPrepare(@org.jetbrains.annotations.NotNull()
        java.util.List<java.lang.String> lines);
        
        /**
         * Opens the Browser's own overflow menu (Private DNS, History) --
         * deliberately separate from the app-wide download Settings dialog,
         * which the Browser's overflow no longer opens. [anchor] is the
         * 3-dot button itself, so the menu can be anchored/dropped down
         * from it Chrome-style instead of popping up as a centered dialog.
         */
        public abstract void openBrowserMenu(@org.jetbrains.annotations.NotNull()
        android.view.View anchor);
        
        /**
         * A stream MediaSniffer picked up was tapped in the "videos found"
         * sheet. HLS/DASH ([needsPicker] true) routes through the same
         * quality-picker flow as a YouTube link (resolveYoutube reused
         * as-is -- yt-dlp's generic extractor handles a raw manifest URL
         * the same way); direct video/audio goes straight to READY like
         * any other direct-download link.
         */
        public abstract void triggerSniffedMedia(@org.jetbrains.annotations.NotNull()
        java.lang.String url, boolean needsPicker);
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0010\t\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0006X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\nX\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u000b"}, d2 = {"Lcom/invictus/xmd/ui/BrowserFragment$Companion;", "", "()V", "DESKTOP_USER_AGENT", "", "MAX_HISTORY_SUGGESTIONS", "", "MAX_LIVE_WEBVIEWS", "MOBILE_USER_AGENT", "TAB_SWITCH_ANIM_MS", "", "app_fullDebug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
    
    /**
     * Drag-only (no swipe-to-dismiss) ItemTouchHelper callback for the
     * speed-dial grid. Only active while [ShortcutAdapter.reorderMode] is
     * on -- the fragment starts a drag itself via onStartDrag when a tile
     * is long-pressed in that mode, so this doesn't need to detect
     * long-press starts on its own. The trailing "+" add tile is never a
     * drag target in either direction.
     */
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\b\n\u0000\b\u0002\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J \u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\nH\u0016J\b\u0010\f\u001a\u00020\u0006H\u0016J \u0010\r\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\b2\u0006\u0010\u000e\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\nH\u0016J\u0018\u0010\u000f\u001a\u00020\u00102\u0006\u0010\u000e\u001a\u00020\n2\u0006\u0010\u0011\u001a\u00020\u0012H\u0016R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0013"}, d2 = {"Lcom/invictus/xmd/ui/BrowserFragment$ShortcutDragCallback;", "Landroidx/recyclerview/widget/ItemTouchHelper$SimpleCallback;", "adapter", "Lcom/invictus/xmd/ui/ShortcutAdapter;", "(Lcom/invictus/xmd/ui/ShortcutAdapter;)V", "canDropOver", "", "recyclerView", "Landroidx/recyclerview/widget/RecyclerView;", "current", "Landroidx/recyclerview/widget/RecyclerView$ViewHolder;", "target", "isLongPressDragEnabled", "onMove", "viewHolder", "onSwiped", "", "direction", "", "app_fullDebug"})
    static final class ShortcutDragCallback extends androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback {
        @org.jetbrains.annotations.NotNull()
        private final com.invictus.xmd.ui.ShortcutAdapter adapter = null;
        
        public ShortcutDragCallback(@org.jetbrains.annotations.NotNull()
        com.invictus.xmd.ui.ShortcutAdapter adapter) {
            super(0, 0);
        }
        
        @java.lang.Override()
        public boolean isLongPressDragEnabled() {
            return false;
        }
        
        @java.lang.Override()
        public boolean onMove(@org.jetbrains.annotations.NotNull()
        androidx.recyclerview.widget.RecyclerView recyclerView, @org.jetbrains.annotations.NotNull()
        androidx.recyclerview.widget.RecyclerView.ViewHolder viewHolder, @org.jetbrains.annotations.NotNull()
        androidx.recyclerview.widget.RecyclerView.ViewHolder target) {
            return false;
        }
        
        @java.lang.Override()
        public void onSwiped(@org.jetbrains.annotations.NotNull()
        androidx.recyclerview.widget.RecyclerView.ViewHolder viewHolder, int direction) {
        }
        
        @java.lang.Override()
        public boolean canDropOver(@org.jetbrains.annotations.NotNull()
        androidx.recyclerview.widget.RecyclerView recyclerView, @org.jetbrains.annotations.NotNull()
        androidx.recyclerview.widget.RecyclerView.ViewHolder current, @org.jetbrains.annotations.NotNull()
        androidx.recyclerview.widget.RecyclerView.ViewHolder target) {
            return false;
        }
    }
}