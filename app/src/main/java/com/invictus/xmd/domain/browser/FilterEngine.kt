package com.invictus.xmd.domain.browser

/**
 * A compact Adblock-Plus / uBlock-style filter engine (EasyList, EasyPrivacy,
 * uBlock filters, AdGuard Base/Mobile/Tracking all share this syntax).
 *
 * Supported
 *  - Network rules: `||domain^`, `|anchors|`, `*` wildcards, `^` separators,
 *    `@@` exceptions, and the options `third-party`/`~third-party`/`1p`/`3p`,
 *    resource types (`script`, `image`, `stylesheet`, `xmlhttprequest`,
 *    `subdocument`, `media`, `font`, `ping`, `other`, `document`, `popup`,
 *    with `~` negation), `domain=a.com|~b.com`, `important`.
 *  - Cosmetic rules: generic `##selector` and per-domain `site.com##selector`,
 *    plus `#@#` exceptions.
 *
 * Deliberately skipped (rule is ignored, never half-applied): regex rules,
 * `removeparam`, `csp`, `replace`, `:has-text()`, `:xpath()` and the
 * `#?#`/`#$#`/`#%#` procedural rules. Skipping is safer than guessing -- a
 * mis-parsed rule becomes a false-positive block.
 *
 * Also handled: `$redirect=` (serve a neutral stub instead of a bare block, so
 * players don't hang), `##+js(...)` scriptlets (exposed via [scriptletsFor]
 * and executed by [AdblockScripts]), and generic cosmetic rules indexed by the
 * `#id`/`.class` they target ([genericCssFor]) so only rules relevant to the
 * page's actual DOM are ever injected.
 *
 * Lookup is indexed: host-anchored rules by domain (walking the request
 * host's label suffixes) and everything else by the longest safe token in the
 * pattern, so a request is tested against a handful of rules rather than the
 * full 100k.
 */
class FilterEngine private constructor(
    private val blockDomain: HashMap<String, ArrayList<NetRule>>,
    private val blockToken: HashMap<String, ArrayList<NetRule>>,
    private val blockLoose: ArrayList<NetRule>,
    private val allowDomain: HashMap<String, ArrayList<NetRule>>,
    private val allowToken: HashMap<String, ArrayList<NetRule>>,
    private val allowLoose: ArrayList<NetRule>,
    private val pageAllowHosts: HashSet<String>,
    private val complexGenericCss: String,
    private val genericIndex: HashMap<String, ArrayList<String>>,
    private val genericExceptions: HashSet<String>,
    private val domainCss: HashMap<String, ArrayList<String>>,
    private val domainCssExceptions: HashMap<String, HashSet<String>>,
    private val scriptlets: HashMap<String, ArrayList<String>>,
    private val scriptletExceptions: HashMap<String, HashSet<String>>,
    val networkRuleCount: Int,
    val cosmeticRuleCount: Int,
) {

    internal class NetRule(
        val pattern: String,
        val startAnchor: Boolean,
        val endAnchor: Boolean,
        val typeMask: Int,
        val thirdParty: Int, // 0 = any, 1 = only third-party, 2 = only first-party
        val includeDomains: Array<String>?,
        val excludeDomains: Array<String>?,
        val important: Boolean,
        val redirect: String? = null,
    )

    /** True if the page itself is exempted by an `@@||site^$document` rule. */
    fun isPageAllowed(pageHost: String?): Boolean {
        if (pageHost.isNullOrEmpty() || pageAllowHosts.isEmpty()) return false
        return hostSuffixes(pageHost.lowercase()).any { it in pageAllowHosts }
    }

    /**
     * @param url full request URL
     * @param host request host (lowercase)
     * @param pageHost host of the page that made the request, if known
     * @param type bitmask of TYPE_* describing the request
     * @return null = allow, "" = block, anything else = block and serve that
     *         redirect resource (uBlock `$redirect=name`).
     */
    fun check(url: String, host: String, pageHost: String?, type: Int): String? {
        if (host.isEmpty()) return null
        val lowerUrl = url.lowercase()
        val page = pageHost?.lowercase()
        if (isPageAllowed(page)) return null
        val thirdParty = page != null && registrableDomain(host) != registrableDomain(page)
        val hostEnd = hostEndIndex(lowerUrl)

        val block = find(blockDomain, blockToken, blockLoose, lowerUrl, host, hostEnd, page, type, thirdParty)
            ?: return null
        if (!block.important &&
            find(allowDomain, allowToken, allowLoose, lowerUrl, host, hostEnd, page, type, thirdParty) != null
        ) return null
        return block.redirect ?: ""
    }

    fun shouldBlock(url: String, host: String, pageHost: String?, type: Int): Boolean =
        check(url, host, pageHost, type) != null

    /** Scriptlet invocations ("name, arg1, arg2") that apply to [pageHost]. */
    fun scriptletsFor(pageHost: String?): List<String> {
        val host = pageHost?.lowercase().orEmpty()
        if (host.isEmpty() || isPageAllowed(host) || scriptlets.isEmpty()) return emptyList()
        val suffixes = hostSuffixes(host)
        val excepted = HashSet<String>()
        for (s in suffixes) scriptletExceptions[s]?.let { excepted.addAll(it) }
        scriptletExceptions["*"]?.let { excepted.addAll(it) }
        if ("*" in excepted) return emptyList()
        val out = ArrayList<String>()
        for (s in suffixes) scriptlets[s]?.forEach { if (it !in excepted) out.add(it) }
        scriptlets["*"]?.forEach { if (it !in excepted) out.add(it) }
        return out
    }

    /** Generic cosmetic rules whose key `#id` / `.class` is present on the page. */
    fun genericCssFor(pageHost: String?, ids: List<String>, classes: List<String>): String {
        if (genericIndex.isEmpty()) return ""
        val host = pageHost?.lowercase().orEmpty()
        if (host.isNotEmpty() && isPageAllowed(host)) return ""
        val excepted = HashSet<String>()
        if (host.isNotEmpty()) for (s in hostSuffixes(host)) domainCssExceptions[s]?.let { excepted.addAll(it) }
        val sb = StringBuilder()
        fun emit(key: String) {
            val list = genericIndex[key] ?: return
            for (sel in list) {
                if (sel in genericExceptions || sel in excepted) continue
                sb.append(sel).append("{display:none!important}")
            }
        }
        for (id in ids) emit("#$id")
        for (c in classes) emit(".$c")
        return sb.toString()
    }

    /** CSS (one `display:none` rule per selector, so one bad selector can't
     *  invalidate the rest) for a page on [pageHost]: the generic set plus
     *  rules scoped to that host or its parent domains. */
    fun cosmeticCss(pageHost: String?): String {
        val host = pageHost?.lowercase().orEmpty()
        if (host.isEmpty()) return complexGenericCss
        if (isPageAllowed(host)) return ""
        val suffixes = hostSuffixes(host)
        val excepted = HashSet<String>()
        for (s in suffixes) domainCssExceptions[s]?.let { excepted.addAll(it) }
        val sb = StringBuilder(complexGenericCss)
        for (s in suffixes) {
            val list = domainCss[s] ?: continue
            for (sel in list) {
                if (sel in excepted) continue
                sb.append(sel).append("{display:none!important}")
            }
        }
        return sb.toString()
    }

    private fun find(
        domainMap: HashMap<String, ArrayList<NetRule>>,
        tokenMap: HashMap<String, ArrayList<NetRule>>,
        loose: ArrayList<NetRule>,
        url: String, host: String, hostEnd: Int,
        page: String?, type: Int, thirdParty: Boolean,
    ): NetRule? {
        // 1. Host-anchored rules: bucketed by domain, remainder anchored at the host's end.
        if (domainMap.isNotEmpty()) {
            for (suffix in hostSuffixes(host)) {
                val bucket = domainMap[suffix] ?: continue
                for (r in bucket) {
                    if (!applies(r, page, type, thirdParty)) continue
                    if (r.pattern.isEmpty() || matches(r, url, hostEnd, true)) return r
                }
            }
        }
        // 2. Token-indexed rules.
        if (tokenMap.isNotEmpty()) {
            var i = 0
            var checked = 0
            val n = url.length
            while (i < n && checked < 96) {
                if (!isAlnum(url[i])) { i++; continue }
                var j = i
                while (j < n && isAlnum(url[j])) j++
                if (j - i >= 3) {
                    checked++
                    val bucket = tokenMap[url.substring(i, j)]
                    if (bucket != null) {
                        for (r in bucket) {
                            if (!applies(r, page, type, thirdParty)) continue
                            if (matches(r, url, 0, r.startAnchor)) return r
                        }
                    }
                }
                i = j
            }
        }
        // 3. Rules without a usable token.
        for (r in loose) {
            if (!applies(r, page, type, thirdParty)) continue
            if (r.pattern.isEmpty() || matches(r, url, 0, r.startAnchor)) return r
        }
        return null
    }

    private fun applies(r: NetRule, page: String?, type: Int, thirdParty: Boolean): Boolean {
        if (r.typeMask and type == 0) return false
        if (r.thirdParty == 1 && !thirdParty) return false
        if (r.thirdParty == 2 && thirdParty) return false
        val inc = r.includeDomains
        if (inc != null) {
            if (page == null || inc.none { hostMatches(page, it) }) return false
        }
        val exc = r.excludeDomains
        if (exc != null && page != null && exc.any { hostMatches(page, it) }) return false
        return true
    }

    /**
     * Matches [r]'s wildcard pattern against [url] starting at [from].
     * [anchored] = the first segment must begin exactly at [from].
     */
    private fun matches(r: NetRule, url: String, from: Int, anchored: Boolean): Boolean {
        val pattern = r.pattern
        if (pattern.isEmpty()) return true
        var pos = from
        var segStart = 0
        var first = true
        while (true) {
            var segEnd = pattern.indexOf('*', segStart)
            val last = segEnd < 0
            if (last) segEnd = pattern.length
            val seg = pattern.substring(segStart, segEnd)
            if (seg.isNotEmpty()) {
                if (first && anchored) {
                    val end = segMatchAt(url, pos, seg)
                    if (end < 0) return false
                    if (last && r.endAnchor && end != url.length) return false
                    pos = end
                } else if (last && r.endAnchor) {
                    // Last segment pinned to the end of the URL.
                    var s = maxOf(pos, url.length - seg.length)
                    var ok = false
                    while (s <= url.length) {
                        if (segMatchAt(url, s, seg) == url.length) { ok = true; break }
                        s++
                    }
                    return ok
                } else {
                    val idx = findSeg(url, seg, pos)
                    if (idx < 0) return false
                    pos = segMatchAt(url, idx, seg)
                }
            } else if (last && r.endAnchor) {
                // Pattern ended in '*' + '|' -> always satisfied.
                return true
            }
            first = false
            if (last) return true
            segStart = segEnd + 1
        }
    }

    private fun findSeg(url: String, seg: String, from: Int): Int {
        var i = from
        val limit = url.length
        while (i <= limit) {
            if (segMatchAt(url, i, seg) >= 0) return i
            i++
        }
        return -1
    }

    /** Returns the end index if [seg] (with `^` separators) matches at [i], else -1. */
    private fun segMatchAt(url: String, i: Int, seg: String): Int {
        var u = i
        for (c in seg) {
            if (c == '^') {
                if (u == url.length) continue
                if (isSeparator(url[u])) u++ else return -1
            } else {
                if (u >= url.length || url[u] != c) return -1
                u++
            }
        }
        return u
    }

    // ───────────────────────── Builder ─────────────────────────

    class Builder {
        private val blockDomain = HashMap<String, ArrayList<NetRule>>()
        private val blockToken = HashMap<String, ArrayList<NetRule>>()
        private val blockLoose = ArrayList<NetRule>()
        private val allowDomain = HashMap<String, ArrayList<NetRule>>()
        private val allowToken = HashMap<String, ArrayList<NetRule>>()
        private val allowLoose = ArrayList<NetRule>()
        private val pageAllow = HashSet<String>()
        private val complexGeneric = LinkedHashSet<String>()
        private val genericIndex = HashMap<String, ArrayList<String>>()
        private val genericExceptions = HashSet<String>()
        private val scriptlets = HashMap<String, ArrayList<String>>()
        private val scriptletExceptions = HashMap<String, HashSet<String>>()
        private val domainCss = HashMap<String, ArrayList<String>>()
        private val domainCssExceptions = HashMap<String, HashSet<String>>()
        private var networkCount = 0
        private var cosmeticCount = 0

        fun addLine(raw: String) {
            val line = raw.trim()
            if (line.isEmpty()) return
            val c = line[0]
            if (c == '!' || c == '[') return
            if (line.contains("\$\$")) return // AdGuard HTML-filtering rules
            if (!tryCosmetic(line)) parseNetwork(line)
        }

        // ── cosmetic ──
        private fun tryCosmetic(line: String): Boolean {
            val idx = line.indexOf('#')
            if (idx < 0) return false
            // Domain prefix may only contain host-list characters.
            for (k in 0 until idx) {
                val ch = line[k]
                if (!(ch.isLetterOrDigit() || ch == '.' || ch == ',' || ch == '~' || ch == '*' || ch == '-' || ch == '_')) {
                    return false
                }
            }
            val isException: Boolean
            val selectorStart: Int
            when {
                line.startsWith("##", idx) -> { isException = false; selectorStart = idx + 2 }
                line.startsWith("#@#", idx) -> { isException = true; selectorStart = idx + 3 }
                line.startsWith("#?#", idx) || line.startsWith("#\$#", idx) ||
                    line.startsWith("#%#", idx) || line.startsWith("#@?#", idx) ||
                    line.startsWith("#@\$#", idx) || line.startsWith("#@%#", idx) -> return true // procedural: skip
                else -> return false
            }
            val selector = line.substring(selectorStart).trim()
            if (selector.isEmpty() || selector.length > 400) return true

            val domains = if (idx == 0) emptyList() else line.substring(0, idx).lowercase().split(',')
            val positive = domains.filter { it.isNotEmpty() && !it.startsWith("~") && !it.contains('*') }
            if (domains.isNotEmpty() && positive.isEmpty()) return true

            // uBlock scriptlet: domain##+js(name, arg, ...)
            if (selector.startsWith("+js(") && selector.endsWith(")")) {
                val inner = selector.substring(4, selector.length - 1).trim()
                if (inner.startsWith("trusted-")) return true
                val keys = if (positive.isEmpty()) listOf("*") else positive
                if (isException) {
                    for (k in keys) scriptletExceptions.getOrPut(k) { HashSet() }.add(if (inner.isEmpty()) "*" else inner)
                } else if (inner.isNotEmpty()) {
                    for (k in keys) scriptlets.getOrPut(k) { ArrayList(2) }.add(inner)
                }
                return true
            }

            if (selector.contains('{') || selector.contains('}')) return true
            for (bad in UNSUPPORTED_PSEUDO) if (selector.contains(bad)) return true

            if (isException) {
                if (positive.isEmpty()) genericExceptions.add(selector)
                else for (d in positive) domainCssExceptions.getOrPut(d) { HashSet() }.add(selector)
                return true
            }
            cosmeticCount++
            if (positive.isEmpty()) {
                val key = indexKey(selector)
                if (key == null) {
                    if (complexGeneric.size < MAX_COMPLEX_GENERIC) complexGeneric.add(selector)
                } else {
                    genericIndex.getOrPut(key) { ArrayList(1) }.add(selector)
                }
            } else {
                for (d in positive) domainCss.getOrPut(d) { ArrayList() }.add(selector)
            }
            return true
        }

        /** `#id` / `.class` token from the selector's rightmost compound -- the
         *  element it hides must carry it, so the rule only matters on pages
         *  that contain such an element. Null = no safe key (always emitted). */
        private fun indexKey(selector: String): String? {
            var depth = 0
            var lastComb = -1
            for (i in selector.indices) {
                when (selector[i]) {
                    '[', '(' -> depth++
                    ']', ')' -> if (depth > 0) depth--
                    ',' -> if (depth == 0) return null
                    ' ', '>', '+', '~' -> if (depth == 0) lastComb = i
                }
            }
            val last = selector.substring(lastComb + 1)
            val flat = StringBuilder()
            var d = 0
            for (c in last) {
                if (c == '[' || c == '(') d++
                else if ((c == ']' || c == ')') && d > 0) d--
                else if (d == 0) flat.append(c)
            }
            val f = flat.toString()
            var i = 0
            while (i < f.length) {
                val c = f[i]
                if (c == '.' || c == '#') {
                    var j = i + 1
                    while (j < f.length && (f[j].isLetterOrDigit() || f[j] == '_' || f[j] == '-')) j++
                    if (j > i + 1) return f.substring(i, j)
                    i = j
                } else i++
            }
            return null
        }

        // ── network ──
        private fun parseNetwork(rawLine: String) {
            var line = rawLine
            var exception = false
            if (line.startsWith("@@")) { exception = true; line = line.substring(2) }

            var typeInc = 0
            var typeExc = 0
            var thirdParty = 0
            var important = false
            var redirect: String? = null
            var include: ArrayList<String>? = null
            var exclude: ArrayList<String>? = null

            val dollar = line.lastIndexOf('$')
            if (dollar >= 0) {
                val opts = line.substring(dollar + 1)
                if (looksLikeOptions(opts)) {
                    for (opt in opts.lowercase().split(',')) {
                        if (opt.isEmpty()) continue
                        val neg = opt.startsWith("~")
                        val name = if (neg) opt.substring(1) else opt
                        when {
                            name == "third-party" || name == "3p" -> thirdParty = if (neg) 2 else 1
                            name == "first-party" || name == "1p" -> thirdParty = if (neg) 1 else 2
                            name == "important" -> important = true
                            name == "match-case" -> {}
                            name.startsWith("redirect=") || name.startsWith("redirect-rule=") -> {
                                redirect = name.substringAfter('=').trim().ifEmpty { null }
                            }
                            name.startsWith("domain=") -> {
                                for (d in name.substring(7).split('|')) {
                                    if (d.isEmpty() || d.contains('*')) continue
                                    if (d.startsWith("~")) {
                                        if (exclude == null) exclude = ArrayList()
                                        exclude.add(d.substring(1))
                                    } else {
                                        if (include == null) include = ArrayList()
                                        include.add(d)
                                    }
                                }
                            }
                            name == "all" -> typeInc = typeInc or ALL_TYPES
                            else -> {
                                val bit = typeBit(name)
                                if (bit == 0) return // unsupported option: skip the whole rule
                                if (neg) typeExc = typeExc or bit else typeInc = typeInc or bit
                            }
                        }
                    }
                    line = line.substring(0, dollar)
                }
            }
            if (line.length >= 2 && line.startsWith("/") && line.endsWith("/")) return // regex rule

            var hostAnchored = false
            var startAnchor = false
            var endAnchor = false
            if (line.startsWith("||")) { hostAnchored = true; line = line.substring(2) }
            else if (line.startsWith("|")) { startAnchor = true; line = line.substring(1) }
            if (line.endsWith("|")) { endAnchor = true; line = line.substring(0, line.length - 1) }
            line = line.lowercase()

            var mask = if (typeInc != 0) typeInc and typeExc.inv() else DEFAULT_TYPES and typeExc.inv()
            if (mask == 0) return

            val inc = include?.toTypedArray()
            val exc = exclude?.toTypedArray()

            // Exemption of the page itself: @@||site^$document
            if (exception && hostAnchored && (mask and TYPE_DOCUMENT) != 0 && typeInc and TYPE_DOCUMENT != 0) {
                val h = hostPart(line)
                if (h.isNotEmpty() && !h.contains('*')) { pageAllow.add(h); return }
            }

            if (line.isEmpty() || line == "*") {
                // Option-only rule: only worth keeping when scoped to specific pages.
                if (inc == null) return
                add(NetRule("", false, false, mask, thirdParty, inc, exc, important, redirect), exception, null, null)
                return
            }

            if (hostAnchored) {
                val host = hostPart(line)
                if (host.isEmpty() || host.contains('*')) return
                var rest = line.substring(host.length)
                // "||host^" == plain domain rule; strip the trailing caret so it matches trivially.
                if (rest == "^") rest = ""
                val rule = NetRule(rest, true, endAnchor, mask, thirdParty, inc, exc, important, redirect)
                add(rule, exception, host, null)
                return
            }

            val rule = NetRule(line, startAnchor, endAnchor, mask, thirdParty, inc, exc, important, redirect)
            add(rule, exception, null, bestToken(line, startAnchor, endAnchor))
        }

        private fun add(rule: NetRule, exception: Boolean, domainKey: String?, token: String?) {
            networkCount++
            val domainMap = if (exception) allowDomain else blockDomain
            val tokenMap = if (exception) allowToken else blockToken
            val loose = if (exception) allowLoose else blockLoose
            when {
                domainKey != null -> domainMap.getOrPut(domainKey) { ArrayList(2) }.add(rule)
                token != null -> tokenMap.getOrPut(token) { ArrayList(2) }.add(rule)
                else -> loose.add(rule)
            }
        }

        fun build(): FilterEngine {
            val sb = StringBuilder()
            for (sel in complexGeneric) {
                if (sel in genericExceptions) continue
                sb.append(sel).append("{display:none!important}")
            }
            return FilterEngine(
                blockDomain, blockToken, blockLoose,
                allowDomain, allowToken, allowLoose,
                pageAllow, sb.toString(), genericIndex, genericExceptions,
                domainCss, domainCssExceptions, scriptlets, scriptletExceptions,
                networkCount, cosmeticCount,
            )
        }

        private fun hostPart(s: String): String {
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '^' || c == '/' || c == ':' || c == '?' || c == '|') break
                i++
            }
            return s.substring(0, i)
        }

        private fun looksLikeOptions(s: String): Boolean {
            if (s.isEmpty()) return false
            for (c in s) {
                if (!(c.isLetterOrDigit() || c == ',' || c == '=' || c == '~' || c == '|' || c == '.' || c == '-' || c == '_')) {
                    return false
                }
            }
            return true
        }

        /** Longest alphanumeric run (>=3) that a URL tokenizer is guaranteed to
         *  see as a whole token -- i.e. not touching a `*` and not floating at
         *  an unanchored edge. */
        private fun bestToken(p: String, startAnchor: Boolean, endAnchor: Boolean): String? {
            var best: String? = null
            var i = 0
            while (i < p.length) {
                if (!isAlnum(p[i])) { i++; continue }
                var j = i
                while (j < p.length && isAlnum(p[j])) j++
                val okStart = if (i == 0) startAnchor else p[i - 1] != '*'
                val okEnd = if (j == p.length) endAnchor else p[j] != '*'
                if (j - i >= 3 && okStart && okEnd && (best == null || j - i > best.length)) {
                    best = p.substring(i, j)
                }
                i = j
            }
            return best
        }
    }

    companion object {
        const val TYPE_SCRIPT = 1
        const val TYPE_IMAGE = 2
        const val TYPE_STYLESHEET = 4
        const val TYPE_XHR = 8
        const val TYPE_SUBDOC = 16
        const val TYPE_MEDIA = 32
        const val TYPE_FONT = 64
        const val TYPE_PING = 128
        const val TYPE_OTHER = 256
        const val TYPE_DOCUMENT = 512
        const val TYPE_POPUP = 1024

        /** ABP semantics: a rule with no type option covers everything except document/popup. */
        const val DEFAULT_TYPES = TYPE_SCRIPT or TYPE_IMAGE or TYPE_STYLESHEET or TYPE_XHR or
            TYPE_SUBDOC or TYPE_MEDIA or TYPE_FONT or TYPE_PING or TYPE_OTHER
        const val ALL_TYPES = DEFAULT_TYPES or TYPE_DOCUMENT or TYPE_POPUP

        private const val MAX_COMPLEX_GENERIC = 3000

        private val UNSUPPORTED_PSEUDO = listOf(
            ":has-text(", ":xpath(", ":matches-css", ":-abp-", ":contains(", ":upward(",
            ":remove(", ":style(", ":matches-path(", ":min-text-length(", ":watch-attr(",
            ":others(", ":nth-ancestor(", ":matches-media(", ":matches-attr(", ":matches-property(",
        )

        private fun typeBit(name: String): Int = when (name) {
            "script" -> TYPE_SCRIPT
            "image" -> TYPE_IMAGE
            "stylesheet", "css" -> TYPE_STYLESHEET
            "xmlhttprequest", "xhr" -> TYPE_XHR
            "subdocument", "frame" -> TYPE_SUBDOC
            "media" -> TYPE_MEDIA
            "font" -> TYPE_FONT
            "ping" -> TYPE_PING
            "other", "object", "object-subrequest", "websocket" -> TYPE_OTHER
            "document", "doc" -> TYPE_DOCUMENT
            "popup" -> TYPE_POPUP
            else -> 0
        }

        private fun isAlnum(c: Char) = (c in 'a'..'z') || (c in '0'..'9')

        private fun isSeparator(c: Char): Boolean =
            !((c in 'a'..'z') || (c in 'A'..'Z') || (c in '0'..'9') || c == '_' || c == '-' || c == '.' || c == '%')

        /** "a.b.example.com" -> ["a.b.example.com", "b.example.com", "example.com", "com"] */
        internal fun hostSuffixes(host: String): List<String> {
            val out = ArrayList<String>(4)
            var i = 0
            out.add(host)
            while (true) {
                i = host.indexOf('.', i)
                if (i < 0) break
                i++
                if (i < host.length) out.add(host.substring(i))
            }
            return out
        }

        private fun hostMatches(host: String, domain: String): Boolean =
            host == domain || host.endsWith(".$domain")

        /** Index just past the host in "scheme://host[:port]/...". */
        private fun hostEndIndex(url: String): Int {
            val s = url.indexOf("://")
            var i = if (s >= 0) s + 3 else 0
            // skip userinfo
            val at = url.indexOf('@', i)
            val slash = url.indexOf('/', i)
            if (at >= 0 && (slash < 0 || at < slash)) i = at + 1
            while (i < url.length) {
                val c = url[i]
                if (c == '/' || c == ':' || c == '?' || c == '#') break
                i++
            }
            return i
        }

        private val SECOND_LEVEL = setOf("co", "com", "org", "net", "gov", "edu", "ac", "or", "ne", "go")

        /** Approximate registrable domain (eTLD+1) without shipping the public-suffix list. */
        internal fun registrableDomain(host: String): String {
            val parts = host.split('.')
            if (parts.size <= 2) return host
            val tld = parts[parts.size - 1]
            val sld = parts[parts.size - 2]
            val take = if (tld.length == 2 && sld in SECOND_LEVEL) 3 else 2
            return parts.takeLast(take).joinToString(".")
        }
    }
}
