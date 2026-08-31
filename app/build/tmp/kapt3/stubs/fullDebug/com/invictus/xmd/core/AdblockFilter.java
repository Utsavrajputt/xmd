package com.invictus.xmd.core;

/**
 * Domain-blocklist ad/tracker blocking (1DM-style: plain host matching
 * against a bundled list, not full EasyList filter syntax -- no cosmetic
 * rules, no element hiding, just "is this request's host a known ad/tracker
 * domain"). Backed by [Settings.adblockEnabled] as a single global toggle.
 *
 * The list ships in assets/adblock_hosts.txt and is bundled with the app
 * (no remote fetch, no auto-update) -- see that file's header comment.
 *
 * [init] parses the asset into an in-memory Set once, off the main thread,
 * the first time the Browser is opened (see BrowserFragment.onViewCreated)
 * rather than at app startup, since most sessions never touch the browser.
 * [isBlocked] is called from shouldInterceptRequest -- WebView's own
 * background thread(s), potentially concurrently across tabs/sub-resources
 * -- so it's a plain read-only Set lookup with no locking needed once
 * loaded; while still loading it just returns false (fail open, same as
 * any other "nothing to block yet" state) rather than blocking that thread
 * on I/O.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000,\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\"\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0002\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\nJ\u0010\u0010\u000b\u001a\u00020\f2\b\u0010\r\u001a\u0004\u0018\u00010\u0004R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u0016\u0010\u0005\u001a\n\u0012\u0004\u0012\u00020\u0004\u0018\u00010\u0006X\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u000e"}, d2 = {"Lcom/invictus/xmd/core/AdblockFilter;", "", "()V", "ASSET_PATH", "", "blockedHosts", "", "init", "", "context", "Landroid/content/Context;", "isBlocked", "", "host", "app_fullDebug"})
public final class AdblockFilter {
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String ASSET_PATH = "adblock_hosts.txt";
    @kotlin.jvm.Volatile()
    @org.jetbrains.annotations.Nullable()
    private static volatile java.util.Set<java.lang.String> blockedHosts;
    @org.jetbrains.annotations.NotNull()
    public static final com.invictus.xmd.core.AdblockFilter INSTANCE = null;
    
    private AdblockFilter() {
        super();
    }
    
    public final void init(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
    }
    
    /**
     * True if [host] is a known ad/tracker domain or a subdomain of one
     * (e.g. "ads.doubleclick.net" matches the "doubleclick.net" entry).
     * Returns false while the list is still loading or if adblock is off
     * (callers should check [Settings.adblockEnabled] themselves before
     * bothering to call this -- kept as a separate check rather than
     * folded in here so callers can skip the whole path cheaply).
     */
    public final boolean isBlocked(@org.jetbrains.annotations.Nullable()
    java.lang.String host) {
        return false;
    }
}