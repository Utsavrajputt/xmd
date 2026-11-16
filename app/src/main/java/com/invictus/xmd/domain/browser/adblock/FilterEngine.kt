package com.invictus.xmd.domain.browser.adblock

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/** Request-type bits used by filter options (`$script`, `$image`, ...). */
internal object RType {
    const val SCRIPT = 1
    const val IMAGE = 2
    const val STYLE = 4
    const val FONT = 8
    const val XHR = 16
    const val SUBDOC = 32
    const val MEDIA = 64
    const val OBJECT = 128
    const val PING = 256
    const val WEBSOCKET = 512
    const val OTHER = 1024
    const val DOCUMENT = 2048
    const val POPUP = 4096

    /** What a rule without any type option applies to (never the page itself or popups). */
    const val DEFAULT = SCRIPT or IMAGE or STYLE or FONT or XHR or SUBDOC or MEDIA or OBJECT or PING or WEBSOCKET or OTHER
    const val ALL = DEFAULT or DOCUMENT or POPUP
}

/**
 * One parsed network filter. Patterns are stored lower-cased; [anchor] is
 * 0 (floating), 1 (`|` start of URL) or 2 (`||` start of a host label).
 * [domainOnly] marks a plain `||domain^` rule (with options) that is fully
 * decided by the host index it lives in.
 */
internal class NetRule(
    @JvmField val pattern: String,
    @JvmField val anchor: Int,
    @JvmField val endAnchor: Boolean,
    @JvmField val types: Int,
    @JvmField val party: Int,
    @JvmField val important: Boolean,
    @JvmField val include: Array<String>?,
    @JvmField val exclude: Array<String>?,
    @JvmField val isRegex: Boolean,
    @JvmField val domainOnly: Boolean,
    /** `$redirect=` resource to serve instead of an empty response, if any. */
    @JvmField val redirect: String? = null,
) {
    @Volatile private var compiled: Pattern? = null
    @Volatile private var compileFailed = false

    fun regex(): Pattern? {
        compiled?.let { return it }
        if (compileFailed) return null
        return try {
            Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).also { compiled = it }
        } catch (e: PatternSyntaxException) {
            compileFailed = true
            null
        }
    }
}

/** What the engine knows about a request while matching it. */
internal class Req(
    @JvmField val url: String,
    @JvmField val host: String,
    @JvmField val pageHost: String?,
    @JvmField val thirdParty: Boolean,
    @JvmField val type: Int,
) {
    @JvmField val lower: String = url.lowercase()
    @JvmField val hostStart: Int
    @JvmField val hostEnd: Int

    init {
        val schemeEnd = lower.indexOf("://")
        val from = if (schemeEnd >= 0) schemeEnd + 3 else 0
        val at = lower.indexOf(host, from)
        hostStart = if (at >= 0) at else from
        hostEnd = hostStart + host.length
    }
}

/** A set of rules behind the three lookups that make matching cheap: host index, token index and a short fallback list. */
internal class RuleSet {
    private val hostRules = HashMap<String, Any>()
    private val tokenRules = HashMap<String, Any>()
    private val fallback = ArrayList<NetRule>()
    private val regexRules = ArrayList<NetRule>()
    val domainSet = HashSet<String>()
    var count = 0
        private set

    fun addPlainDomain(domain: String) {
        if (domainSet.add(domain)) count++
    }

    fun addHostRule(domain: String, rule: NetRule) {
        put(hostRules, domain, rule)
        count++
    }

    fun addTokenRule(token: String, rule: NetRule) {
        put(tokenRules, token, rule)
        count++
    }

    fun addFallback(rule: NetRule) {
        fallback.add(rule)
        count++
    }

    fun addRegex(rule: NetRule) {
        regexRules.add(rule)
        count++
    }

    @Suppress("UNCHECKED_CAST")
    private fun put(map: HashMap<String, Any>, key: String, rule: NetRule) {
        when (val cur = map[key]) {
            null -> map[key] = rule
            is NetRule -> map[key] = arrayListOf(cur, rule)
            else -> (cur as ArrayList<NetRule>).add(rule)
        }
    }

    fun domainListed(host: String): Boolean = Domains.matchesDomainOrParent(host, domainSet)

    @Suppress("UNCHECKED_CAST")
    private fun scan(entry: Any?, req: Req, importantOnly: Boolean): NetRule? {
        when (entry) {
            null -> return null
            is NetRule -> return if (matches(entry, req, importantOnly)) entry else null
            else -> {
                val list = entry as ArrayList<NetRule>
                for (i in 0 until list.size) {
                    val r = list[i]
                    if (matches(r, req, importantOnly)) return r
                }
                return null
            }
        }
    }

    /** First rule in this set matching [req], or null. With [importantOnly], `$important` rules only. */
    fun find(req: Req, importantOnly: Boolean): NetRule? {
        if (hostRules.isNotEmpty()) {
            var h = req.host
            while (true) {
                scan(hostRules[h], req, importantOnly)?.let { return it }
                val dot = h.indexOf('.')
                if (dot < 0) break
                h = h.substring(dot + 1)
            }
        }
        if (tokenRules.isNotEmpty()) {
            val u = req.lower
            val n = minOf(u.length, MAX_URL_SCAN)
            var i = 0
            while (i < n) {
                if (!isTokenChar(u[i])) { i++; continue }
                var j = i + 1
                while (j < n && isTokenChar(u[j])) j++
                if (j - i >= MIN_TOKEN) {
                    scan(tokenRules[u.substring(i, j)], req, importantOnly)?.let { return it }
                }
                i = j
            }
        }
        for (i in 0 until fallback.size) {
            val r = fallback[i]
            if (matches(r, req, importantOnly)) return r
        }
        if (regexRules.isNotEmpty() && req.url.length <= MAX_REGEX_URL) {
            for (i in 0 until regexRules.size) {
                val r = regexRules[i]
                if (matches(r, req, importantOnly)) return r
            }
        }
        return null
    }

    private fun matches(r: NetRule, req: Req, importantOnly: Boolean): Boolean {
        if (importantOnly && !r.important) return false
        if (r.types and req.type == 0) return false
        when (r.party) {
            1 -> if (!req.thirdParty) return false
            2 -> if (req.thirdParty) return false
        }
        if ((r.include != null || r.exclude != null) && !domainOk(r, req.pageHost)) return false
        if (r.domainOnly) return true
        if (r.isRegex) {
            val p = r.regex() ?: return false
            return p.matcher(req.url).find()
        }
        return PatternMatcher.matches(r, req)
    }

    private fun domainOk(r: NetRule, pageHost: String?): Boolean {
        val include = r.include
        if (include != null) {
            if (pageHost == null) return false
            if (include.none { Domains.entryMatches(pageHost, it) }) return false
        }
        val exclude = r.exclude
        if (exclude != null && pageHost != null && exclude.any { Domains.entryMatches(pageHost, it) }) return false
        return true
    }

    companion object {
        const val MIN_TOKEN = 2
        const val MAX_URL_SCAN = 1500
        const val MAX_REGEX_URL = 2000

        fun isTokenChar(c: Char): Boolean = (c in 'a'..'z') || (c in '0'..'9')
    }
}

/** `*` / `^` / anchor aware matcher for the non-regex rule patterns. */
internal object PatternMatcher {

    fun matches(r: NetRule, req: Req): Boolean {
        val u = req.lower
        val p = r.pattern
        when (r.anchor) {
            2 -> {
                var start = req.hostStart
                while (start <= req.hostEnd) {
                    if (matchHere(u, start, p, r.endAnchor)) return true
                    val dot = u.indexOf('.', start)
                    if (dot < 0 || dot >= req.hostEnd) break
                    start = dot + 1
                }
                return false
            }
            1 -> return matchHere(u, 0, p, r.endAnchor)
            else -> {
                if (p.isEmpty()) return true
                val first = p[0]
                var i = 0
                while (i <= u.length) {
                    if (first == '^' || (i < u.length && u[i] == first)) {
                        if (matchHere(u, i, p, r.endAnchor)) return true
                    }
                    i++
                }
                return false
            }
        }
    }

    private fun isSeparator(c: Char): Boolean =
        !((c in 'a'..'z') || (c in 'A'..'Z') || (c in '0'..'9') || c == '_' || c == '-' || c == '.' || c == '%')

    /** Does [p] match [u] starting exactly at [ui0]? `*` is any run, `^` a separator or the end. */
    private fun matchHere(u: String, ui0: Int, p: String, endAnchor: Boolean): Boolean {
        var ui = ui0
        var pi = 0
        var starPi = -1
        var starUi = -1
        val un = u.length
        val pn = p.length
        while (true) {
            var ok = false
            if (pi == pn) {
                if (!endAnchor || ui == un) return true
            } else {
                val c = p[pi]
                if (c == '*') {
                    starPi = pi
                    starUi = ui
                    pi++
                    continue
                }
                if (c == '^') {
                    if (ui < un && isSeparator(u[ui])) { ui++; pi++; ok = true }
                    else if (ui == un) { pi++; ok = true }
                } else if (ui < un && u[ui] == c) {
                    ui++; pi++; ok = true
                }
            }
            if (ok) continue
            if (starPi < 0) return false
            starUi++
            if (starUi > un) return false
            ui = starUi
            pi = starPi + 1
        }
    }
}

internal object Domains {
    private val registrableCache = ConcurrentHashMap<String, String>()

    /** eTLD+1 of [host] (public-suffix aware via OkHttp), or the host itself for IPs / odd names. */
    fun registrable(host: String): String {
        registrableCache[host]?.let { return it }
        val r = runCatching { "https://$host/".toHttpUrlOrNull()?.topPrivateDomain() }.getOrNull() ?: host
        if (registrableCache.size > 4000) registrableCache.clear()
        registrableCache[host] = r
        return r
    }

    fun isThirdParty(host: String, pageHost: String?): Boolean {
        if (pageHost.isNullOrEmpty()) return false
        if (host == pageHost) return false
        return registrable(host) != registrable(pageHost)
    }

    fun matchesDomainOrParent(host: String, set: Set<String>): Boolean {
        if (set.isEmpty() || host.isEmpty()) return false
        if (set.contains(host)) return true
        var i = host.indexOf('.')
        while (i >= 0) {
            if (set.contains(host.substring(i + 1))) return true
            i = host.indexOf('.', i + 1)
        }
        return false
    }

    /** `domain=` entry vs the page host: `example.com` covers subdomains, `example.*` any TLD. */
    fun entryMatches(host: String, entry: String): Boolean {
        if (entry.endsWith(".*")) {
            val base = entry.substring(0, entry.length - 1)
            return host.startsWith(base) || host.contains(".$base")
        }
        return host == entry || host.endsWith(".$entry")
    }
}

/**
 * The compiled filter set. Immutable once built, so WebView's threads can
 * query it concurrently while a newer one is being built.
 */
class FilterEngine internal constructor(
    private val block: RuleSet,
    private val allow: RuleSet,
    private val docAllow: RuleSet,
    private val redirects: RuleSet,
    /** The user's own `@@` rules: they beat everything, `$important` blocks included. */
    private val userAllow: RuleSet,
    private val hostExact: Set<String>,
    internal val cosmetic: CosmeticStore,
    /** Rules per list id, for the manager UI. */
    val countsByList: Map<String, Int>,
) {
    val ruleCount: Int = block.count + allow.count + docAllow.count + userAllow.count + hostExact.size + cosmetic.count

    /** True if [host] is listed as a whole (hosts-format entry or plain `||domain^` rule). */
    fun isHostListed(host: String): Boolean =
        hostExact.contains(host) || block.domainListed(host)

    /** True if a request for [url] (to [host]) from a page on [pageHost] should be blocked. */
    fun shouldBlock(url: String, host: String, pageHost: String?, type: Int): Boolean =
        check(url, host, pageHost, type) != null

    /**
     * Verdict for a request: null = let it through, "" = block, any other
     * value = block and answer with that `$redirect` resource instead.
     */
    fun check(url: String, host: String, pageHost: String?, type: Int): String? {
        val req = Req(url, host, pageHost, Domains.isThirdParty(host, pageHost), type)
        if (userAllow.count > 0 && (userAllow.domainListed(host) || userAllow.find(req, false) != null)) return null
        var hit = hostExact.contains(host) || block.domainListed(host)
        var rule: NetRule? = null
        if (!hit) {
            rule = block.find(req, false)
            hit = rule != null
        }
        if (!hit) return null
        if (rule == null || !rule.important) {
            val excepted = allow.domainListed(host) || allow.find(req, false) != null
            if (excepted) {
                // `$important` blocks beat exception rules.
                rule = block.find(req, true) ?: return null
            }
        }
        val named = rule?.redirect ?: redirects.find(req, false)?.redirect
        return named ?: ""
    }

    /** Cosmetic rules / scriptlets for a page (or frame) on [host]. */
    fun cosmeticFor(host: String, hiding: Boolean, scripts: Boolean): CosmeticResult =
        cosmetic.lookup(host, hiding, scripts)

    /** Hide rules for the generic class / id selectors a page on [host] reports. */
    fun cosmeticNamesCss(host: String, classes: List<String>, ids: List<String>): String =
        cosmetic.namesCss(host, classes, ids)

    /** True if a page URL is whitelisted as a whole by an `@@...$document` rule. */
    fun isPageAllowed(pageUrl: String, pageHost: String): Boolean {
        val req = Req(pageUrl, pageHost, pageHost, false, RType.DOCUMENT)
        return docAllow.find(req, false) != null
    }
}
