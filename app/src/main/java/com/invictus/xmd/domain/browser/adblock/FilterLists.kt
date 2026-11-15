package com.invictus.xmd.domain.browser.adblock

import android.content.Context
import com.invictus.xmd.preferences.Settings
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The filter-list catalog (what can be switched on in Settings), the user's
 * own lists, and the download / compile plumbing around them. Lists live as
 * raw text under `files/adblock/<id>.txt`; [buildEngine] compiles whichever
 * are enabled into a [FilterEngine].
 */
object FilterLists {

    enum class Format { ABP, HOSTS, DOMAINS }

    enum class Group { ADS, PRIVACY, BRAVE, ANNOYANCES, SECURITY, HOSTS, REGIONAL, CUSTOM }

    /** [format] null = detect from the downloaded file (user-added lists). */
    class Info(
        val id: String,
        val title: String,
        val description: String,
        val urls: List<String>,
        val format: Format?,
        val defaultOn: Boolean,
        val group: Group,
        val minLines: Int = 20,
        val custom: Boolean = false,
    )

    private const val UBO = "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/"
    private const val ADGUARD = "https://filters.adtidy.org/extension/ublock/filters/"
    private const val BRAVE = "https://raw.githubusercontent.com/brave/adblock-lists/master/brave-lists/"

    val catalog: List<Info> = listOf(
        // Ads
        Info("easylist", "EasyList", "The standard ad-blocking list", listOf("https://easylist.to/easylist/easylist.txt"), Format.ABP, true, Group.ADS, 1000),
        Info("ublock-filters", "uBlock filters", "uBlock Origin's own ad and tracker rules", listOf(UBO + "filters.txt"), Format.ABP, true, Group.ADS, 200),
        Info("ublock-quick-fixes", "uBlock quick fixes", "Fast fixes for sites that changed overnight", listOf("https://ublockorigin.github.io/uAssets/filters/quick-fixes.txt"), Format.ABP, true, Group.ADS, 10),
        Info("ublock-unbreak", "uBlock unbreak", "Exceptions that stop filters from breaking sites", listOf(UBO + "unbreak.txt"), Format.ABP, true, Group.ADS, 50),
        Info("adguard-base", "AdGuard Base", "AdGuard's ad list (without EasyList)", listOf(ADGUARD + "2_without_easylist.txt"), Format.ABP, false, Group.ADS, 1000),
        Info("anti-adblock", "EasyList anti-adblock", "Stops \"disable your ad blocker\" walls", listOf("https://easylist-downloads.adblockplus.org/antiadblockfilters.txt"), Format.ABP, false, Group.ADS, 100),
        // Privacy
        Info("easyprivacy", "EasyPrivacy", "Trackers and analytics", listOf("https://easylist.to/easylist/easyprivacy.txt"), Format.ABP, true, Group.PRIVACY, 1000),
        Info("ublock-privacy", "uBlock privacy", "Extra tracker and privacy rules", listOf(UBO + "privacy.txt"), Format.ABP, true, Group.PRIVACY, 20),
        Info("ublock-resource-abuse", "uBlock resource abuse", "Crypto miners and CPU-hungry scripts", listOf(UBO + "resource-abuse.txt"), Format.ABP, true, Group.PRIVACY, 5),
        Info("adguard-tracking", "AdGuard Tracking Protection", "AdGuard's tracker list", listOf(ADGUARD + "3.txt"), Format.ABP, false, Group.PRIVACY, 500),
        // Brave
        Info("brave-specific", "Brave specific", "Rules maintained for Brave Shields", listOf(BRAVE + "brave-specific.txt"), Format.ABP, true, Group.BRAVE, 5),
        Info("brave-unbreak", "Brave unbreak", "Brave's site-breakage exceptions", listOf(BRAVE + "brave-unbreak.txt"), Format.ABP, true, Group.BRAVE, 5),
        Info("brave-android", "Brave Android specific", "Brave rules for mobile sites", listOf(BRAVE + "brave-android-specific.txt"), Format.ABP, false, Group.BRAVE, 1),
        Info(
            "bpc-paywall", "Bypass Paywalls Clean",
            "Paywall bypass filters (may need cosmetic/script support to fully work)",
            listOf(
                "https://gitlab.com/magnolia1234/bypass-paywalls-clean-filters/-/raw/main/bpc-paywall-filter.txt",
                "https://gitflic.ru/project/magnolia1234/bypass-paywalls-clean-filters/blob/raw?file=bpc-paywall-filter.txt",
            ),
            Format.ABP, false, Group.BRAVE, 20,
        ),
        // Annoyances
        Info("ublock-annoyances", "uBlock annoyances", "Popups, newsletter and notice banners", listOf(UBO + "annoyances.txt"), Format.ABP, false, Group.ANNOYANCES, 100),
        Info("ublock-annoyances-others", "uBlock annoyances (others)", "Other page annoyances", listOf(UBO + "annoyances-others.txt"), Format.ABP, false, Group.ANNOYANCES, 50),
        Info("fanboy-annoyance", "Fanboy Annoyances", "Social widgets, popups and in-page nags", listOf("https://secure.fanboy.co.nz/fanboy-annoyance.txt"), Format.ABP, false, Group.ANNOYANCES, 500),
        Info("fanboy-cookie", "Fanboy Cookie Monster", "Cookie-consent notices", listOf("https://secure.fanboy.co.nz/fanboy-cookiemonster.txt"), Format.ABP, false, Group.ANNOYANCES, 200),
        Info("adguard-annoyances", "AdGuard Annoyances", "AdGuard's annoyance list", listOf(ADGUARD + "14.txt"), Format.ABP, false, Group.ANNOYANCES, 500),
        Info("adguard-cookies", "AdGuard Cookie Notices", "Cookie-consent notices", listOf(ADGUARD + "18.txt"), Format.ABP, false, Group.ANNOYANCES, 100),
        // Security
        Info("ublock-badware", "uBlock badware risks", "Malware and scam domains", listOf(UBO + "badware.txt"), Format.ABP, true, Group.SECURITY, 50),
        Info("urlhaus", "URLhaus malware URLs", "Currently active malware URLs", listOf("https://malware-filter.gitlab.io/malware-filter/urlhaus-filter-online.txt", "https://curben.gitlab.io/malware-filter/urlhaus-filter-online.txt"), Format.ABP, false, Group.SECURITY, 20),
        // Hosts / domain lists
        Info("pgl", "Peter Lowe's list", "Ad server hostnames", listOf("https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext"), Format.HOSTS, true, Group.HOSTS, 500),
        Info("stevenblack", "StevenBlack unified hosts", "Large hosts list (ads, malware)", listOf("https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"), Format.HOSTS, false, Group.HOSTS, 5000),
        Info("1hosts-lite", "1Hosts Lite", "Compact hosts list", listOf("https://badmojr.github.io/1Hosts/Lite/hosts.txt"), Format.HOSTS, false, Group.HOSTS, 5000),
        Info("hagezi-pro", "HaGeZi Pro", "Broad domain blocklist", listOf("https://raw.githubusercontent.com/hagezi/dns-blocklists/main/adblock/pro.txt"), Format.ABP, false, Group.HOSTS, 10000),
        // Regional
        Info("fanboy-indian", "Fanboy Indian", "Indian sites", listOf("https://secure.fanboy.co.nz/fanboy-indian.txt"), Format.ABP, false, Group.REGIONAL, 20),
        Info("easylist-germany", "EasyList Germany", "German sites", listOf("https://easylist-downloads.adblockplus.org/easylistgermany.txt"), Format.ABP, false, Group.REGIONAL, 100),
    )

    private const val MAX_RESPONSE_BYTES = 40L * 1024 * 1024

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // ── Catalog + custom lists ───────────────────────────────────────

    fun customLists(): List<Info> = Settings.adblockCustomLists().mapNotNull { line ->
        val parts = line.split('\t')
        if (parts.size < 3) return@mapNotNull null
        Info(parts[0], parts[1], parts[2], listOf(parts[2]), null, false, Group.CUSTOM, 1, custom = true)
    }.sortedBy { it.title.lowercase() }

    fun all(): List<Info> = catalog + customLists()

    fun find(id: String): Info? = all().firstOrNull { it.id == id }

    fun isEnabled(info: Info): Boolean = Settings.adblockListEnabled(info.id, info.defaultOn)

    /** Adds a user list; returns it, or null for an invalid / duplicate URL. */
    fun addCustom(title: String, url: String): Info? {
        val clean = url.trim()
        if (!(clean.startsWith("https://") || clean.startsWith("http://"))) return null
        if (clean.contains('\t') || clean.contains(' ')) return null
        val existing = Settings.adblockCustomLists()
        if (existing.any { it.substringAfterLast('\t') == clean }) return null
        val name = title.trim().replace('\t', ' ').ifEmpty { clean.substringAfter("://").substringBefore('/') }
        val id = "custom-" + Integer.toHexString(clean.hashCode()) + "-" + (System.currentTimeMillis() % 100000)
        val updated = HashSet(existing)
        updated.add("$id\t$name\t$clean")
        Settings.setAdblockCustomLists(updated)
        Settings.setAdblockListEnabled(id, true)
        return Info(id, name, clean, listOf(clean), null, false, Group.CUSTOM, 1, custom = true)
    }

    fun removeCustom(context: Context, id: String) {
        val updated = Settings.adblockCustomLists().filterNot { it.startsWith("$id\t") }.toSet()
        Settings.setAdblockCustomLists(updated)
        Settings.clearAdblockListState(id)
        runCatching { fileFor(context, id).delete() }
    }

    // ── Files ────────────────────────────────────────────────────────

    private fun dir(context: Context) = File(context.filesDir, "adblock").also { it.mkdirs() }

    fun fileFor(context: Context, id: String) = File(dir(context), "$id.txt")

    fun isDownloaded(context: Context, info: Info) = fileFor(context, info.id).let { it.exists() && it.length() > 0 }

    // ── Download ─────────────────────────────────────────────────────

    enum class Result { UPDATED, NOT_MODIFIED, FAILED }

    /** Downloads [info] (blocking network I/O -- call off the main thread). */
    fun download(context: Context, info: Info, force: Boolean): Result {
        val target = fileFor(context, info.id)
        val etag = if (!force && target.exists()) Settings.adblockListEtag(info.id) else null
        var lastError = "download failed"
        for (url in info.urls) {
            try {
                val builder = Request.Builder().url(url).header("User-Agent", "XMD-Adblock/2.0")
                if (etag != null) builder.header("If-None-Match", etag)
                client.newCall(builder.build()).execute().use { response ->
                    if (response.code == 304) {
                        Settings.setAdblockListDownloaded(info.id, System.currentTimeMillis(), etag)
                        return Result.NOT_MODIFIED
                    }
                    if (!response.isSuccessful) {
                        lastError = "HTTP ${response.code}"
                        return@use
                    }
                    val body = response.body ?: run { lastError = "empty response"; return@use }
                    val declared = response.header("Content-Length")?.toLongOrNull()
                    if (declared != null && declared > MAX_RESPONSE_BYTES) {
                        lastError = "list too large"
                        return@use
                    }
                    val tmp = File(target.parentFile, target.name + ".tmp")
                    var lines = 0
                    var total = 0L
                    var head: String? = null
                    var tooBig = false
                    body.byteStream().use { input ->
                        tmp.outputStream().buffered().use { out ->
                            val buf = ByteArray(32 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                if (head == null) head = String(buf, 0, minOf(n, 512), Charsets.UTF_8)
                                total += n
                                if (total > MAX_RESPONSE_BYTES) { tooBig = true; break }
                                for (i in 0 until n) if (buf[i] == '\n'.code.toByte()) lines++
                                out.write(buf, 0, n)
                            }
                        }
                    }
                    val h = head?.lowercase().orEmpty()
                    when {
                        tooBig -> lastError = "list too large"
                        h.contains("<html") || h.contains("<!doctype") -> lastError = "not a filter list"
                        lines < info.minLines -> lastError = "list looks incomplete"
                        else -> {
                            if (!tmp.renameTo(target)) {
                                target.delete()
                                if (!tmp.renameTo(target)) {
                                    lastError = "could not save list"
                                    tmp.delete()
                                    return@use
                                }
                            }
                            Settings.setAdblockListDownloaded(info.id, System.currentTimeMillis(), response.header("ETag"))
                            return Result.UPDATED
                        }
                    }
                    tmp.delete()
                    Unit
                }
            } catch (e: Exception) {
                lastError = e.javaClass.simpleName
            }
        }
        Settings.setAdblockListError(info.id, lastError)
        return Result.FAILED
    }

    // ── Compile ──────────────────────────────────────────────────────

    /** Compiles the bundled domains plus every enabled, downloaded list. Blocking; off the main thread. */
    fun buildEngine(context: Context): FilterEngine {
        val builder = EngineBuilder()

        builder.startList("bundled")
        runCatching {
            context.assets.open("adblock_hosts.txt").bufferedReader().useLines { lines ->
                lines.forEach { builder.addPlainDomain(it) }
            }
        }

        val active = all().filter { isEnabled(it) && fileFor(context, it.id).exists() }
        val formats = HashMap<String, Format>()
        for (info in active) {
            formats[info.id] = info.format ?: detectFormat(fileFor(context, info.id))
        }

        // Pass 1: `$badfilter` lines (they can target rules in another list).
        for (info in active) {
            if (formats[info.id] != Format.ABP) continue
            runCatching { fileFor(context, info.id).bufferedReader().useLines { it.forEach(builder::collectBad) } }
        }
        // Pass 2: the rules themselves.
        for (info in active) {
            builder.startList(info.id)
            val format = formats[info.id] ?: Format.ABP
            runCatching {
                fileFor(context, info.id).bufferedReader().useLines { lines ->
                    when (format) {
                        Format.ABP -> lines.forEach(builder::addAbpLine)
                        Format.HOSTS -> lines.forEach(builder::addHostsLine)
                        Format.DOMAINS -> lines.forEach(builder::addDomainLine)
                    }
                }
            }
        }
        return builder.build()
    }

    private val HOSTS_LINE = Regex("^(0\\.0\\.0\\.0|127\\.0\\.0\\.1)\\s+\\S+")
    private val DOMAIN_LINE = Regex("^[a-z0-9][a-z0-9._-]*\\.[a-z]{2,}$")

    /** Looks at the first lines of a user-added list to tell hosts / domain / ABP syntax apart. */
    fun detectFormat(file: File): Format {
        var hosts = 0
        var domains = 0
        var total = 0
        runCatching {
            file.bufferedReader().useLines { lines ->
                for (raw in lines) {
                    val line = raw.trim()
                    if (line.isEmpty() || line.startsWith("#") || line.startsWith("!") || line.startsWith("[")) continue
                    total++
                    if (HOSTS_LINE.containsMatchIn(line)) hosts++
                    else if (DOMAIN_LINE.matches(line.lowercase())) domains++
                    if (total >= 200) break
                }
            }
        }
        if (total == 0) return Format.ABP
        return when {
            hosts * 10 >= total * 7 -> Format.HOSTS
            domains * 10 >= total * 7 -> Format.DOMAINS
            else -> Format.ABP
        }
    }
}
