package com.invictus.xmd.core

import android.content.Context

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
object AdblockFilter {

    private const val ASSET_PATH = "adblock_hosts.txt"

    @Volatile private var blockedHosts: Set<String>? = null

    fun init(context: Context) {
        if (blockedHosts != null) return
        Thread {
            val hosts = runCatching {
                context.assets.open(ASSET_PATH).bufferedReader().useLines { lines ->
                    lines
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && !it.startsWith("#") }
                        .toHashSet()
                }
            }.getOrDefault(emptySet())
            blockedHosts = hosts
        }.start()
    }

    /** True if [host] is a known ad/tracker domain or a subdomain of one
     *  (e.g. "ads.doubleclick.net" matches the "doubleclick.net" entry).
     *  Returns false while the list is still loading or if adblock is off
     *  (callers should check [Settings.adblockEnabled] themselves before
     *  bothering to call this -- kept as a separate check rather than
     *  folded in here so callers can skip the whole path cheaply). */
    fun isBlocked(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val hosts = blockedHosts ?: return false
        val lower = host.lowercase()
        if (hosts.contains(lower)) return true
        var i = lower.indexOf('.')
        while (i >= 0) {
            val parent = lower.substring(i + 1)
            if (hosts.contains(parent)) return true
            i = lower.indexOf('.', i + 1)
        }
        return false
    }
}
