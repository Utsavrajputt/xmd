package com.invictus.xmd.domain.browser

import android.content.Context
import com.invictus.xmd.preferences.Settings
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Downloads, caches and parses the filter lists behind [FilterEngine].
 *
 * The user picks a list set in Settings ([Settings.AdblockFilterSet]):
 *  - EASYLIST: EasyList + EasyPrivacy + uBlock filters/unbreak/quick-fixes/badware + Peter Lowe
 *  - ADGUARD:  AdGuard Base + Mobile + Tracking Protection + Popups
 *  - BOTH:     the union of the two
 * Every list is cached under filesDir, refreshed at most weekly, and a failed
 * download never discards a working cached copy.
 */
object FilterListManager {

    data class ListDef(val id: String, val url: String)

    private val EASYLIST_SET = listOf(
        ListDef("easylist", "https://easylist.to/easylist/easylist.txt"),
        ListDef("easyprivacy", "https://easylist.to/easylist/easyprivacy.txt"),
        ListDef("ublock-filters", "https://ublockorigin.github.io/uAssets/filters/filters.txt"),
        ListDef("ublock-unbreak", "https://ublockorigin.github.io/uAssets/filters/unbreak.txt"),
        // quick-fixes carries the scriptlet rules uBlock ships for sites whose ads
        // can't be stopped at the network level; badware blocks malicious ad hosts.
        ListDef("ublock-quick-fixes", "https://ublockorigin.github.io/uAssets/filters/quick-fixes.txt"),
        ListDef("ublock-badware", "https://ublockorigin.github.io/uAssets/filters/badware.txt"),
        ListDef("peter-lowe", "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=adblockplus&showintro=0&mimetype=plaintext"),
    )

    private val ADGUARD_SET = listOf(
        ListDef("adguard-base", "https://filters.adtidy.org/extension/ublock/filters/2.txt"),
        ListDef("adguard-mobile", "https://filters.adtidy.org/extension/ublock/filters/11.txt"),
        ListDef("adguard-tracking", "https://filters.adtidy.org/extension/ublock/filters/3.txt"),
        ListDef("adguard-popups", "https://filters.adtidy.org/extension/ublock/filters/19.txt"),
    )

    private val REFRESH_INTERVAL_MS = TimeUnit.DAYS.toMillis(7)
    private const val MAX_LIST_BYTES = 25L * 1024 * 1024

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun listsFor(set: Settings.AdblockFilterSet): List<ListDef> = when (set) {
        Settings.AdblockFilterSet.EASYLIST -> EASYLIST_SET
        Settings.AdblockFilterSet.ADGUARD -> ADGUARD_SET
        Settings.AdblockFilterSet.BOTH -> EASYLIST_SET + ADGUARD_SET
    }

    private fun cacheFile(context: Context, def: ListDef) =
        File(context.filesDir, "adblock_list_${def.id}.txt")

    /** Builds an engine from whatever is cached for [set]; null if nothing is cached yet. */
    fun loadEngine(context: Context, set: Settings.AdblockFilterSet): FilterEngine? {
        val files = listsFor(set).map { cacheFile(context, it) }.filter { it.exists() && it.length() > 0L }
        if (files.isEmpty()) return null
        val builder = FilterEngine.Builder()
        for (f in files) {
            runCatching { f.bufferedReader().useLines { lines -> lines.forEach(builder::addLine) } }
        }
        return builder.build()
    }

    /** True if any list for [set] is missing or older than a week. */
    fun needsRefresh(context: Context, set: Settings.AdblockFilterSet): Boolean {
        val now = System.currentTimeMillis()
        return listsFor(set).any {
            val f = cacheFile(context, it)
            !f.exists() || f.length() == 0L || now - f.lastModified() > REFRESH_INTERVAL_MS
        }
    }

    /**
     * Downloads every stale (or, with [force], every) list for [set]. Blocking
     * network I/O -- call off the main thread. Returns true if at least one
     * list was updated.
     */
    fun refresh(context: Context, set: Settings.AdblockFilterSet, force: Boolean = false): Boolean {
        var updated = false
        val now = System.currentTimeMillis()
        for (def in listsFor(set)) {
            val target = cacheFile(context, def)
            val fresh = target.exists() && target.length() > 0L && now - target.lastModified() <= REFRESH_INTERVAL_MS
            if (fresh && !force) continue
            if (runCatching { download(def, target, context) }.getOrDefault(false)) updated = true
        }
        return updated
    }

    private fun download(def: ListDef, target: File, context: Context): Boolean {
        val request = Request.Builder().url(def.url).header("User-Agent", "XMD-Adblock/2.0").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return false
            val body = response.body ?: return false
            val declared = response.header("Content-Length")?.toLongOrNull()
            if (declared != null && declared > MAX_LIST_BYTES) return false
            val tmp = File(context.filesDir, target.name + ".tmp")
            var written = 0L
            tmp.outputStream().buffered().use { out ->
                body.byteStream().use { input ->
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        written += n
                        if (written > MAX_LIST_BYTES) { tmp.delete(); return false }
                        out.write(buf, 0, n)
                    }
                }
            }
            // A real list is far bigger than this; guards against error pages / empty bodies.
            if (written < 2_000L) { tmp.delete(); return false }
            if (!tmp.renameTo(target)) { tmp.delete(); return false }
            return true
        }
    }
}
