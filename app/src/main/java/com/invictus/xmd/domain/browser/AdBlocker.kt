package com.invictus.xmd.domain.browser

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Base64
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.invictus.xmd.domain.browser.adblock.FilterEngine
import com.invictus.xmd.domain.browser.adblock.FilterLists
import com.invictus.xmd.domain.browser.adblock.RType
import com.invictus.xmd.domain.browser.adblock.RedirectResources
import com.invictus.xmd.domain.browser.adblock.UserRules
import com.invictus.xmd.preferences.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Ad blocking for the in-app Browser. A Kotlin filter engine (see the
 * `adblock` package) reads standard uBlock/AdBlock Plus filter lists,
 * hosts files and plain domain lists -- the same formats 1DM, Brave and
 * uBlock Origin use -- and decides, per request, whether it is an ad or a
 * tracker.
 *
 *  - Lists: a catalog of well-known lists plus the user's own
 *    ([FilterLists]); each can be switched on or off in Settings. Enabled
 *    lists are downloaded, cached on disk and compiled into one
 *    [FilterEngine] in the background; until it is ready nothing is blocked.
 *  - Matching: sub-resource requests are matched on URL pattern, resource
 *    type ([inferType]), first/third-party and the page they came from.
 *    Exceptions (`@@`), `$important` and `$badfilter` follow uBlock semantics.
 *    Blocked requests get an empty stand-in response.
 *  - Updates: lists refresh in the background every few days (Settings: the
 *    interval and Wi-Fi only), or on demand.
 *  - Popups: [isPopupBlocked] refuses popup tabs / script redirects that the
 *    engine blocks.
 *
 * A site in the per-site allowlist ([Settings.isAdblockAllowlisted]) is never
 * filtered, nor a page that a list whitelists with `@@...$document`. Called
 * from WebView's own threads, so lookups only read an immutable snapshot and
 * never block.
 */
object AdBlocker {

    private val DAY_MS = TimeUnit.DAYS.toMillis(1)
    private val RETRY_INTERVAL_MS = TimeUnit.HOURS.toMillis(6)

    @Volatile private var engine: FilterEngine? = null
    private val started = AtomicBoolean(false)
    private val pageAllowCache = ConcurrentHashMap<String, Boolean>()

    /** Bumped whenever lists, counts or download state change; the Settings screen observes it. */
    val changes = MutableStateFlow(0)

    /** Ids of lists being downloaded right now. */
    @Volatile var downloading: Set<String> = emptySet()
        private set

    /** True while the engine is compiling. */
    @Volatile var building: Boolean = false
        private set

    private fun bump() {
        changes.value = changes.value + 1
    }

    /** Loads what is on disk off the main thread, then refreshes lists that are due. Safe to call repeatedly. */
    fun init(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        Thread {
            migrateLegacy(appContext)
            if (Settings.adblockEnabled()) {
                rebuildBlocking(appContext)
                updateIfDue(appContext)
            }
        }.apply { name = "adblock-init"; isDaemon = true }.start()
    }

    /** Switches blocking on or off; turning it on loads (and, for missing lists, downloads) the enabled lists. */
    fun setEnabled(context: Context, enabled: Boolean) {
        Settings.setAdblockEnabled(enabled)
        val appContext = context.applicationContext
        if (!enabled) {
            engine = null
            pageAllowCache.clear()
            cosmeticCache.clear()
            bump()
            return
        }
        Thread {
            downloadMissingBlocking(appContext)
            rebuildBlocking(appContext)
        }.apply { name = "adblock-enable"; isDaemon = true }.start()
    }

    /** Enables / disables one list; enabling fetches it first if needed. */
    fun setListEnabled(context: Context, id: String, enabled: Boolean) {
        Settings.setAdblockListEnabled(id, enabled)
        bump()
        if (!Settings.adblockEnabled()) return
        val appContext = context.applicationContext
        Thread {
            val info = FilterLists.find(id)
            if (enabled && info != null && !FilterLists.isDownloaded(appContext, info)) {
                downloadList(appContext, info, force = true)
            }
            rebuildBlocking(appContext)
        }.apply { name = "adblock-toggle"; isDaemon = true }.start()
    }

    /** Adds a user list from a URL (downloads it right away). Returns false for an invalid or duplicate URL. */
    fun addCustomList(context: Context, title: String, url: String): Boolean {
        val info = FilterLists.addCustom(title, url) ?: return false
        bump()
        if (Settings.adblockEnabled()) {
            val appContext = context.applicationContext
            Thread {
                downloadList(appContext, info, force = true)
                rebuildBlocking(appContext)
            }.apply { name = "adblock-add"; isDaemon = true }.start()
        }
        return true
    }

    fun removeCustomList(context: Context, id: String) {
        FilterLists.removeCustom(context.applicationContext, id)
        bump()
        if (Settings.adblockEnabled()) {
            val appContext = context.applicationContext
            Thread { rebuildBlocking(appContext) }.apply { name = "adblock-remove"; isDaemon = true }.start()
        }
    }

    /** Number of rules currently loaded (0 until the first build finishes). */
    fun ruleCount(): Int = engine?.ruleCount ?: 0

    // ── My rules (user-written rules + the element picker) ───────────

    private val rebuildExecutor: ScheduledExecutorService by lazy {
        Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "adblock-rules").apply { isDaemon = true } }
    }
    private var pendingRebuild: ScheduledFuture<*>? = null

    /** Recompiles soon; several quick edits (add, undo, add) share one rebuild. */
    @Synchronized
    private fun scheduleRebuild(context: Context, delayMs: Long) {
        val appContext = context.applicationContext
        pendingRebuild?.cancel(false)
        pendingRebuild = rebuildExecutor.schedule({
            if (Settings.adblockEnabled()) rebuildBlocking(appContext)
        }, delayMs, TimeUnit.MILLISECONDS)
        bump()
    }

    fun userRules(context: Context): String = UserRules.read(context.applicationContext)

    fun userRuleCount(context: Context): Int = UserRules.count(userRules(context))

    /** Replaces the whole "My rules" text (the editor's Save) and applies it. */
    fun saveUserRules(context: Context, text: String) {
        UserRules.write(context.applicationContext, text)
        scheduleRebuild(context, 300)
    }

    /** One-time secret handed to the element picker running in a page; see [pickerRule]. */
    @Volatile private var pickerToken: String? = null
    @Volatile private var pickerExpiresAt = 0L

    fun newPickerToken(): String {
        val t = java.util.UUID.randomUUID().toString()
        pickerToken = t
        pickerExpiresAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(10)
        return t
    }

    /**
     * The picker's way to add (or, for Undo, remove) a `host##selector` rule. A page's
     * own scripts can reach the bridge too, so it needs the token the app gave the
     * picker, the host must be the tab's own site, and the selector can't smuggle in
     * a scriptlet or style block.
     */
    fun pickerRule(context: Context, token: String?, tabHost: String?, host: String?, selector: String?, add: Boolean): Boolean {
        val t = pickerToken
        if (t == null || token != t || System.currentTimeMillis() > pickerExpiresAt) return false
        val h = host?.lowercase()?.trim().orEmpty()
        val th = tabHost?.lowercase()?.trim().orEmpty()
        if (h.isEmpty() || !h.matches(Regex("[a-z0-9.-]+")) || !(th == h || th.endsWith(".$h"))) return false
        val sel = selector?.trim().orEmpty()
        if (sel.isEmpty() || sel.length > 600 || sel.startsWith("+js") || sel.contains(":style(") || sel.contains("##")) return false
        val rule = "$h##$sel"
        if (add) {
            if (!UserRules.add(context.applicationContext, rule)) return false
        } else {
            UserRules.remove(context.applicationContext, rule)
        }
        scheduleRebuild(context, 1500)
        return true
    }

    // ── Build / update ───────────────────────────────────────────────

    @Synchronized
    private fun rebuildBlocking(context: Context) {
        building = true
        bump()
        try {
            val built = FilterLists.buildEngine(context)
            for ((id, count) in built.countsByList) {
                if (id != "bundled") Settings.setAdblockListRuleCount(id, count)
            }
            pageAllowCache.clear()
            cosmeticCache.clear()
            if (Settings.adblockEnabled()) engine = built
        } catch (e: Throwable) {
            // A bad build keeps the previous engine.
        } finally {
            building = false
            bump()
        }
    }

    private fun downloadList(context: Context, info: FilterLists.Info, force: Boolean): FilterLists.Result {
        downloading = downloading + info.id
        bump()
        try {
            return FilterLists.download(context, info, force)
        } finally {
            downloading = downloading - info.id
            bump()
        }
    }

    /** Fetches enabled lists that have never been downloaded (an explicit user action, so any network). */
    private fun downloadMissingBlocking(context: Context) {
        for (info in FilterLists.all()) {
            if (FilterLists.isEnabled(info) && !FilterLists.isDownloaded(context, info)) {
                downloadList(context, info, force = true)
            }
        }
    }

    /** Background refresh of lists older than the user's interval (Wi-Fi only if so configured). */
    private fun updateIfDue(context: Context) {
        val days = Settings.adblockUpdateDays()
        if (days <= 0) return
        val now = System.currentTimeMillis()
        if (now - Settings.adblockListAttemptAt() < RETRY_INTERVAL_MS) return
        if (Settings.adblockUpdateWifiOnly() && !isUnmetered(context)) return
        val due = FilterLists.all().filter {
            FilterLists.isEnabled(it) &&
                (!FilterLists.isDownloaded(context, it) || now - Settings.adblockListUpdatedAt(it.id) >= days * DAY_MS)
        }
        if (due.isEmpty()) return
        Settings.setAdblockListAttemptAt(now)
        var changed = false
        for (info in due) {
            if (downloadList(context, info, force = false) == FilterLists.Result.UPDATED) changed = true
        }
        if (changed) rebuildBlocking(context)
    }

    private fun isUnmetered(context: Context): Boolean = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }.getOrDefault(false)

    /**
     * "Update now": re-downloads every enabled list (blocking network I/O --
     * call off the main thread). Returns true if at least one list was
     * refreshed or confirmed current; lists that fail keep their old copy.
     */
    @Synchronized
    fun updateNowBlocking(context: Context): Boolean {
        val appContext = context.applicationContext
        Settings.setAdblockListAttemptAt(System.currentTimeMillis())
        var ok = 0
        var changed = false
        for (info in FilterLists.all()) {
            if (!FilterLists.isEnabled(info)) continue
            when (downloadList(appContext, info, force = false)) {
                FilterLists.Result.UPDATED -> { ok++; changed = true }
                FilterLists.Result.NOT_MODIFIED -> ok++
                FilterLists.Result.FAILED -> {}
            }
        }
        if (changed || engine == null) rebuildBlocking(appContext)
        if (ok > 0) Settings.setAdblockListUpdatedAt(System.currentTimeMillis())
        return ok > 0
    }

    /** Older builds kept hosts lists under other names; keep their choice and drop the files. */
    private fun migrateLegacy(context: Context) {
        runCatching {
            val files = context.filesDir.listFiles().orEmpty()
            if (files.any { it.name == "adblock_remote_stevenblack.txt" }) {
                Settings.setAdblockListEnabled("stevenblack", true)
            }
            files.filter {
                it.name == "adblock_hosts_cache.txt" || it.name.startsWith("adblock_list_") ||
                    it.name.startsWith("adblock_remote_")
            }.forEach(File::delete)
        }
    }

    // ── Blocking ─────────────────────────────────────────────────────

    /**
     * Stand-in response for a blocked sub-resource of a page on [pageUrl],
     * or null to let the request through.
     */
    fun interceptResponse(request: WebResourceRequest, pageUrl: String?): WebResourceResponse? {
        if (request.isForMainFrame) return null
        val eng = engine ?: return null
        if (!Settings.adblockEnabled()) return null
        val uri = request.url ?: return null
        val scheme = uri.scheme ?: return null
        if (scheme != "http" && scheme != "https" && scheme != "ws" && scheme != "wss") return null
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return null
        if (isAuthHost(host)) return null
        val pageHost = pageUrl?.let { hostOf(it) }
        if (Settings.isAdblockAllowlisted(pageHost)) return null
        if (pageUrl != null && pageHost != null && pageAllowed(eng, pageUrl, pageHost)) return null
        val type = inferType(request)
        val verdict = eng.check(uri.toString(), host, pageHost, type) ?: return null
        if (verdict.isNotEmpty()) {
            // `$redirect=` rule: answer with the named stand-in so the page's own code keeps working.
            val res = RedirectResources.get(verdict)
            if (res != null) {
                return WebResourceResponse(res.mime, "UTF-8", ByteArrayInputStream(res.bytes))
            }
        }
        return stub(type, uri)
    }

    // ── Cosmetic filtering / scriptlets (see assets/adblock_agent.js) ─

    private val cosmeticCache = ConcurrentHashMap<String, String>()
    @Volatile private var agentSource: String? = null

    /** The page agent, read once from assets; null if missing. */
    fun agentScript(context: Context): String? {
        agentSource?.let { return it }
        val text = runCatching {
            context.applicationContext.assets.open("adblock_agent.js").bufferedReader().use { it.readText() }
        }.getOrNull()
        agentSource = text
        return text
    }

    /** The element picker for the page, with a fresh one-time token baked in; null if the asset is missing. */
    fun pickerScript(context: Context): String? {
        val src = runCatching {
            context.applicationContext.assets.open("adblock_picker.js").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return null
        return src.replace("__XMD_TOKEN__", newPickerToken())
    }

    /** Whether the long-press "Hide element" item makes sense for a page on [host]. */
    fun canPickElements(host: String?): Boolean =
        Settings.adblockEnabled() && Settings.adblockCosmetic() && !host.isNullOrEmpty() &&
            !Settings.isAdblockAllowlisted(host.lowercase())

    /**
     * JSON for the page agent: CSS to inject, procedural rules, scriptlets
     * (see [com.invictus.xmd.domain.browser.adblock.CosmeticResult.toJson]).
     * Empty string when blocking is off, the site is allowlisted, or nothing applies.
     */
    fun cosmeticJson(host: String?): String {
        val eng = engine ?: return ""
        if (!Settings.adblockEnabled() || host.isNullOrEmpty() || host.length > 253) return ""
        val h = host.lowercase().trimEnd('.')
        if (Settings.isAdblockAllowlisted(h) || isAuthHost(h)) return ""
        val hiding = Settings.adblockCosmetic()
        val scripts = Settings.adblockScriptlets()
        if (!hiding && !scripts) return ""
        if (pageAllowed(eng, "https://$h/", h)) return ""
        val key = "$h|${if (hiding) 1 else 0}${if (scripts) 1 else 0}"
        cosmeticCache[key]?.let { return it }
        val res = eng.cosmeticFor(h, hiding, scripts)
        val json = if (res.isEmpty) "" else res.toJson()
        if (cosmeticCache.size > 300) cosmeticCache.clear()
        cosmeticCache[key] = json
        return json
    }

    /** Hide-CSS for the generic class / id names a page reports (JSON arrays of strings). */
    fun cosmeticNamesCss(host: String?, classesJson: String?, idsJson: String?): String {
        val eng = engine ?: return ""
        if (!Settings.adblockEnabled() || !Settings.adblockCosmetic() || host.isNullOrEmpty()) return ""
        val h = host.lowercase().trimEnd('.')
        if (Settings.isAdblockAllowlisted(h) || isAuthHost(h)) return ""
        val classes = names(classesJson)
        val ids = names(idsJson)
        if (classes.isEmpty() && ids.isEmpty()) return ""
        return eng.cosmeticNamesCss(h, classes, ids)
    }

    private fun names(json: String?): List<String> {
        if (json.isNullOrEmpty() || json.length > 60_000) return emptyList()
        return runCatching {
            val arr = org.json.JSONArray(json)
            val out = ArrayList<String>(arr.length())
            for (i in 0 until minOf(arr.length(), 800)) {
                val s = arr.optString(i, "")
                if (s.isNotEmpty() && s.length <= 100) out.add(s)
            }
            out
        }.getOrDefault(emptyList())
    }

    /** Drops cached per-host cosmetic results (after a rebuild or a settings change). */
    fun clearCosmeticCache() {
        cosmeticCache.clear()
    }

    /** True if opening [url] as a popup / script redirect from a page on [openerHost] should be refused. */
    fun isPopupBlocked(url: String, openerHost: String?): Boolean {
        if (!Settings.adblockEnabled() || Settings.isAdblockAllowlisted(openerHost)) return false
        val eng = engine ?: return false
        val host = hostOf(url) ?: return false
        if (isAuthHost(host)) return false
        return eng.shouldBlock(url, host, openerHost?.lowercase(), RType.POPUP)
    }

    private fun hostOf(url: String): String? =
        runCatching { Uri.parse(url).host }.getOrNull()?.lowercase()?.trimEnd('.')

    private fun pageAllowed(eng: FilterEngine, pageUrl: String, pageHost: String): Boolean {
        pageAllowCache[pageHost]?.let { return it }
        val allowed = eng.isPageAllowed(pageUrl, pageHost)
        if (pageAllowCache.size > 500) pageAllowCache.clear()
        pageAllowCache[pageHost] = allowed
        return allowed
    }

    // WebView doesn't say what kind of request this is, so work it out from
    // the headers Chromium sends and the file extension. Anything ambiguous
    // gets several type bits at once ("some kind of script/fetch"), which
    // rules then match by overlap.
    private fun inferType(request: WebResourceRequest): Int {
        val headers = request.requestHeaders
        when (header(headers, "Sec-Fetch-Dest")?.lowercase()) {
            "script", "worker", "sharedworker", "serviceworker" -> return RType.SCRIPT
            "image" -> return RType.IMAGE
            "style" -> return RType.STYLE
            "font" -> return RType.FONT
            "iframe", "frame", "fencedframe" -> return RType.SUBDOC
            "audio", "video", "track" -> return RType.MEDIA
            "object", "embed" -> return RType.OBJECT
        }
        if (header(headers, "X-Requested-With").equals("XMLHttpRequest", ignoreCase = true)) return RType.XHR
        when (request.url.path.orEmpty().substringAfterLast('.', "").lowercase()) {
            "js", "mjs" -> return RType.SCRIPT
            "css" -> return RType.STYLE
            "png", "jpg", "jpeg", "gif", "webp", "svg", "avif", "ico", "bmp" -> return RType.IMAGE
            "woff", "woff2", "ttf", "otf", "eot" -> return RType.FONT
            "mp4", "webm", "m3u8", "mp3", "ogg", "m4a", "m4s", "ts" -> return RType.MEDIA
            "html", "htm" -> return RType.SUBDOC
        }
        val accept = header(headers, "Accept")?.lowercase().orEmpty()
        return when {
            accept.startsWith("image/") || accept.contains("image/webp") || accept.contains("image/avif") -> RType.IMAGE
            accept.contains("text/css") -> RType.STYLE
            accept.contains("text/html") -> RType.SUBDOC
            else -> RType.SCRIPT or RType.XHR or RType.OTHER or RType.PING
        }
    }

    private fun header(headers: Map<String, String>?, name: String): String? {
        if (headers == null) return null
        for ((k, v) in headers) if (k.equals(name, ignoreCase = true)) return v
        return null
    }

    private val GIF_1X1: ByteArray by lazy {
        Base64.decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7", Base64.DEFAULT)
    }

    private fun stub(type: Int, uri: Uri): WebResourceResponse {
        if (type == RType.IMAGE) return WebResourceResponse("image/gif", null, ByteArrayInputStream(GIF_1X1))
        val mime = when (type) {
            RType.SCRIPT -> "application/javascript"
            RType.STYLE -> "text/css"
            RType.SUBDOC -> "text/html"
            else -> when (uri.path.orEmpty().substringAfterLast('.', "").lowercase()) {
                "js", "mjs" -> "application/javascript"
                "css" -> "text/css"
                "html", "htm" -> "text/html"
                "gif" -> "image/gif"
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                else -> "text/plain"
            }
        }
        return WebResourceResponse(mime, "UTF-8", ByteArrayInputStream(ByteArray(0)))
    }

    // Sign-in infrastructure that must never be filtered (and that the
    // browser's DoH proxy must leave to WebView's own network stack).
    private val authHosts = setOf(
        "accounts.google.com", "accounts.youtube.com", "apis.google.com",
        "ssl.gstatic.com", "www.gstatic.com", "oauth2.googleapis.com",
        "appleid.apple.com", "login.microsoftonline.com", "login.live.com",
        "github.com", "githubassets.com", "api.github.com",
    )

    fun isAuthHost(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val lower = host.lowercase()
        if (authHosts.contains(lower)) return true
        var i = lower.indexOf('.')
        while (i >= 0) {
            if (authHosts.contains(lower.substring(i + 1))) return true
            i = lower.indexOf('.', i + 1)
        }
        return false
    }
}
