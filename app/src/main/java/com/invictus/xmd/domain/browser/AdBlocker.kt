package com.invictus.xmd.domain.browser

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.webkit.WebResourceResponse
import com.invictus.xmd.preferences.Settings
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Ad blocking for the in-app Browser, download-manager style: one ON/OFF
 * switch ([Settings.adblockEnabled]), an online host list and a popup
 * blocker -- no levels, no cosmetic/script injection.
 *
 *  - Host-list blocking: sub-resource requests to a listed host get an empty
 *    response, see [interceptResponse]. Two lists feed it:
 *     - the bundled `assets/adblock_hosts.txt`: a short curated list of ad
 *       *domains*, matched against the host and every parent domain, so it
 *       works offline from the first launch;
 *     - two hosts-format lists downloaded from the internet (see [SOURCES]):
 *       Peter Lowe's ad server list and StevenBlack's unified hosts. These
 *       list individual *hostnames* (that is what the hosts-file format
 *       means), so they are matched exactly -- never as "any subdomain" --
 *       which keeps a listed `ads.example.com` from taking `example.com` or
 *       its siblings down with it.
 *  - Online updates: [init] loads whatever was downloaded last time, then
 *    refreshes in the background when the lists are over a week old (only on
 *    an unmetered connection); Settings can also refresh on demand
 *    ([updateNowBlocking]). Each source is replaced only by a download that
 *    parsed to a plausible size, so a bad response never wipes a good list.
 *  - Popup blocking: [isPopupBlocked] refuses popup tabs / script redirects
 *    that land on a listed host. (Gesture-less `window.open` is refused by
 *    the browser's onCreateWindow.)
 *
 * A site in the per-site allowlist ([Settings.isAdblockAllowlisted]) is
 * never filtered. Called from WebView's own background threads, so lookups
 * read plain @Volatile sets and never block; until a list has loaded
 * nothing from it is blocked.
 */
object AdBlocker {

    private const val ASSET_PATH = "adblock_hosts.txt"

    private class Source(val id: String, val url: String, val minEntries: Int)

    private val SOURCES = listOf(
        Source(
            "pgl",
            "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext",
            minEntries = 500,
        ),
        Source(
            "stevenblack",
            "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
            minEntries = 5_000,
        ),
    )

    private val REFRESH_INTERVAL_MS = TimeUnit.DAYS.toMillis(7)
    private val RETRY_INTERVAL_MS = TimeUnit.HOURS.toMillis(6)

    // Guards against a misbehaving source handing back something absurd.
    private const val MAX_RESPONSE_BYTES = 30L * 1024 * 1024

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** Curated ad domains from the asset: matched against the host and its parent domains. */
    @Volatile private var bundledDomains: Set<String> = emptySet()
    /** Downloaded hostnames: matched exactly. */
    @Volatile private var remoteHosts: Set<String> = emptySet()
    private val started = AtomicBoolean(false)

    /**
     * Loads the lists off the main thread, then refreshes the online ones if
     * they're due. Safe to call repeatedly.
     */
    fun init(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        Thread {
            removeLegacyFiles(appContext)
            reload(appContext)
            updateIfDue(appContext)
        }.apply { name = "adblock-init"; isDaemon = true }.start()
    }

    /** Number of hosts/domains currently loaded (0 until the first load finishes). */
    fun listedHostCount(): Int = bundledDomains.size + remoteHosts.size

    // ── Lists on disk ────────────────────────────────────────────────

    private fun cacheFile(context: Context, source: Source) =
        File(context.filesDir, "adblock_remote_${source.id}.txt")

    /** Older builds cached their downloaded filter lists under other names. */
    private fun removeLegacyFiles(context: Context) {
        runCatching {
            context.filesDir.listFiles()
                ?.filter { it.name == "adblock_hosts_cache.txt" || it.name.startsWith("adblock_list_") }
                ?.forEach { it.delete() }
        }
    }

    private fun reload(context: Context) {
        bundledDomains = runCatching {
            context.assets.open(ASSET_PATH).bufferedReader().useLines { lines ->
                lines.map { it.trim().lowercase() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .toHashSet()
            }
        }.getOrDefault(emptySet())

        val remote = HashSet<String>()
        for (source in SOURCES) {
            val file = cacheFile(context, source)
            if (!file.exists()) continue
            runCatching { file.bufferedReader().useLines { lines -> lines.forEach { if (it.isNotBlank()) remote.add(it) } } }
        }
        remoteHosts = remote
    }

    // ── Online updates ───────────────────────────────────────────────

    /** Background refresh, throttled to once a week (retrying a failed one after 6 hours). */
    private fun updateIfDue(context: Context) {
        if (!Settings.adblockEnabled()) return
        val now = System.currentTimeMillis()
        if (now - Settings.adblockListUpdatedAt() < REFRESH_INTERVAL_MS) return
        if (now - Settings.adblockListAttemptAt() < RETRY_INTERVAL_MS) return
        // ~5 MB of lists: leave it to Wi-Fi unless the user asks from Settings.
        if (!isUnmetered(context)) return
        updateNowBlocking(context)
    }

    private fun isUnmetered(context: Context): Boolean = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }.getOrDefault(false)

    /**
     * Downloads every list now (blocking network I/O -- call off the main
     * thread). Returns true if at least one list was refreshed; lists that
     * fail keep their previous contents.
     */
    @Synchronized
    fun updateNowBlocking(context: Context): Boolean {
        val appContext = context.applicationContext
        Settings.setAdblockListAttemptAt(System.currentTimeMillis())
        var refreshed = 0
        for (source in SOURCES) {
            if (runCatching { download(appContext, source) }.getOrDefault(false)) refreshed++
        }
        if (refreshed > 0) {
            reload(appContext)
            Settings.setAdblockListUpdatedAt(System.currentTimeMillis())
        }
        return refreshed > 0
    }

    private fun download(context: Context, source: Source): Boolean {
        val request = Request.Builder()
            .url(source.url)
            .header("User-Agent", "XMD-Adblock/1.0")
            .build()
        val hosts = HashSet<String>()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return false
            val body = response.body ?: return false
            val declared = response.header("Content-Length")?.toLongOrNull()
            if (declared != null && declared > MAX_RESPONSE_BYTES) return false
            var bytesRead = 0L
            for (line in body.byteStream().bufferedReader().lineSequence()) {
                bytesRead += line.length + 1
                if (bytesRead > MAX_RESPONSE_BYTES) return false
                parseHostsLine(line, hosts)
            }
        }
        // An error page or truncated download parses to (almost) nothing:
        // keep the list we already have instead of replacing it.
        if (hosts.size < source.minEntries) return false

        val target = cacheFile(context, source)
        val tmp = File(context.filesDir, target.name + ".tmp")
        tmp.bufferedWriter().use { out -> hosts.forEach { out.write(it); out.write("\n") } }
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) { tmp.delete(); return false }
        }
        return true
    }

    private val IGNORED_HOSTS = setOf(
        "localhost", "localhost.localdomain", "local", "broadcasthost",
        "ip6-localhost", "ip6-loopback", "ip6-localnet", "ip6-mcastprefix",
        "ip6-allnodes", "ip6-allrouters", "ip6-allhosts", "0.0.0.0",
    )
    private val HOST_PATTERN = Regex("^[a-z0-9]([a-z0-9._-]*[a-z0-9])?$")

    /**
     * One line of a hosts file ("0.0.0.0 ads.example.com", optional trailing
     * comment, possibly several names). Only entries pointing at a null /
     * loopback address are blocklist entries; everything else is skipped.
     */
    private fun parseHostsLine(rawLine: String, out: MutableSet<String>) {
        val line = rawLine.substringBefore('#').trim()
        if (line.isEmpty()) return
        val parts = line.split(' ', '\t').filter { it.isNotEmpty() }
        if (parts.size < 2) return
        if (parts[0] != "0.0.0.0" && parts[0] != "127.0.0.1") return
        for (i in 1 until parts.size) {
            val host = parts[i].lowercase().trimEnd('.')
            if (host in IGNORED_HOSTS || !host.contains('.')) continue
            if (!HOST_PATTERN.matches(host)) continue
            // A bare IP address is not a hostname.
            if (host.all { it.isDigit() || it == '.' }) continue
            out.add(host)
        }
    }

    // ── Blocking ─────────────────────────────────────────────────────

    /** True if a sub-resource request to [uri], made by a page on [pageHost], should be blocked. */
    fun isBlocked(uri: Uri?, pageHost: String?): Boolean {
        if (uri == null || !Settings.adblockEnabled()) return false
        if (Settings.isAdblockAllowlisted(pageHost)) return false
        val host = uri.host
        if (isAuthHost(host)) return false
        return isHostListed(host)
    }

    /** Empty stand-in response for a blocked sub-resource, or null to let it through. */
    fun interceptResponse(uri: Uri?, pageHost: String?, isMainFrame: Boolean): WebResourceResponse? {
        if (isMainFrame || !isBlocked(uri, pageHost)) return null
        val mime = when (uri?.path.orEmpty().substringAfterLast('.', "").lowercase()) {
            "js", "mjs" -> "application/javascript"
            "css" -> "text/css"
            "html", "htm" -> "text/html"
            "gif" -> "image/gif"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            else -> "text/plain"
        }
        return WebResourceResponse(mime, "UTF-8", ByteArrayInputStream(ByteArray(0)))
    }

    /** True if opening [url] as a popup / script redirect from a page on [openerHost] should be refused. */
    fun isPopupBlocked(url: String, openerHost: String?): Boolean {
        if (!Settings.adblockEnabled() || Settings.isAdblockAllowlisted(openerHost)) return false
        val host = runCatching { Uri.parse(url).host }.getOrNull() ?: return false
        if (isAuthHost(host)) return false
        return isHostListed(host)
    }

    // Sign-in infrastructure that must never be filtered (and that the
    // browser's DoH proxy must leave to WebView's own network stack).
    private val authHosts = setOf(
        "accounts.google.com", "accounts.youtube.com", "apis.google.com",
        "ssl.gstatic.com", "www.gstatic.com", "oauth2.googleapis.com",
        "appleid.apple.com", "login.microsoftonline.com", "login.live.com",
        "github.com", "githubassets.com", "api.github.com",
    )

    fun isAuthHost(host: String?): Boolean = matchesDomainOrParent(host, authHosts)

    private fun isHostListed(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val lower = host.lowercase().trimEnd('.')
        return matchesDomainOrParent(lower, bundledDomains) || remoteHosts.contains(lower)
    }

    private fun matchesDomainOrParent(host: String?, set: Set<String>): Boolean {
        if (host.isNullOrBlank() || set.isEmpty()) return false
        val lower = host.lowercase()
        if (set.contains(lower)) return true
        var i = lower.indexOf('.')
        while (i >= 0) {
            if (set.contains(lower.substring(i + 1))) return true
            i = lower.indexOf('.', i + 1)
        }
        return false
    }
}
