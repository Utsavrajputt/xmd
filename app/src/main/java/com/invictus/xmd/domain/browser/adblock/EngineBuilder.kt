package com.invictus.xmd.domain.browser.adblock

/**
 * Turns filter-list lines into a [FilterEngine]. Feed every list through
 * [collectBad] first (pass 1: `$badfilter` targets), then through the
 * `add*Line` functions (pass 2), then call [build].
 *
 * Supported: ABP / uBlock network syntax (`||`, `|`, `*`, `^`, `/regex/`,
 * `@@`, `$third-party`, `$1p`, `$domain=`, resource types, `$important`,
 * `$popup`, `$document`, `$badfilter`), hosts-file lines and bare-domain
 * lines. Cosmetic filters, scriptlets and options the engine can't honour
 * (`$csp`, `$removeparam`, `$replace`, `$redirect`, ...) are skipped, never
 * guessed at -- a skipped rule only means "not blocked", never a broken page.
 */
internal class EngineBuilder {

    private val block = RuleSet()
    private val allow = RuleSet()
    private val docAllow = RuleSet()
    private val hostExact = HashSet<String>()
    private val bad = HashSet<String>()
    private val counts = HashMap<String, Int>()
    private var listId: String = ""

    /** Marks which list the following lines belong to (for per-list counts). */
    fun startList(id: String) {
        listId = id
        counts[id] = counts[id] ?: 0
    }

    private fun counted() {
        counts[listId] = (counts[listId] ?: 0) + 1
    }

    // ── Pass 1 ───────────────────────────────────────────────────────

    /** Records the rule a `$badfilter` line disables. */
    fun collectBad(raw: String) {
        if (!raw.contains("badfilter")) return
        val line = raw.trim()
        val i = line.lastIndexOf("badfilter")
        if (i < 0) return
        var t = line.substring(0, i) + line.substring(i + "badfilter".length)
        t = t.replace("$,", "$").replace(",,", ",").trimEnd(',')
        if (t.endsWith("$")) t = t.dropLast(1)
        bad.add(t)
    }

    // ── Pass 2 ───────────────────────────────────────────────────────

    /** hosts-file line: `0.0.0.0 ads.example.com`. */
    fun addHostsLine(raw: String) {
        val line = raw.substringBefore('#').trim()
        if (line.isEmpty()) return
        val parts = line.split(' ', '\t').filter { it.isNotEmpty() }
        if (parts.size < 2) return
        if (parts[0] != "0.0.0.0" && parts[0] != "127.0.0.1") return
        for (i in 1 until parts.size) addExactHost(parts[i])
    }

    /** One bare hostname per line. */
    fun addDomainLine(raw: String) {
        val line = raw.substringBefore('#').trim()
        if (line.isEmpty() || line.startsWith("!")) return
        addExactHost(line)
    }

    private fun addExactHost(raw: String) {
        val host = raw.lowercase().trimEnd('.')
        if (host in IGNORED_HOSTS || !host.contains('.')) return
        if (!isHostName(host) || host.all { it.isDigit() || it == '.' }) return
        if (hostExact.add(host)) counted()
    }

    /** Adds the bundled/own plain domains (matched with their subdomains). */
    fun addPlainDomain(raw: String) {
        val d = raw.trim().lowercase()
        if (d.isEmpty() || d.startsWith("#")) return
        if (block.domainSet.contains(d)) return
        block.addPlainDomain(d)
        counted()
    }

    /** One ABP / uBlock line. */
    fun addAbpLine(raw: String) {
        var line = raw.trim()
        if (line.isEmpty()) return
        val c0 = line[0]
        if (c0 == '!' || c0 == '[') return
        if (isCosmetic(line)) return
        if (bad.isNotEmpty() && bad.contains(line)) return

        var exception = false
        if (line.startsWith("@@")) {
            exception = true
            line = line.substring(2)
        }

        var types = 0
        var negTypes = 0
        var party = 0
        var important = false
        var include: ArrayList<String>? = null
        var exclude: ArrayList<String>? = null
        var pattern = line

        val d = line.lastIndexOf('$')
        if (d >= 0) {
            val opts = line.substring(d + 1)
            if (looksLikeOptions(opts)) {
                pattern = line.substring(0, d)
                for (token in opts.split(',')) {
                    var name = token.trim().lowercase()
                    if (name.isEmpty()) continue
                    val neg = name.startsWith("~")
                    if (neg) name = name.substring(1)
                    val eq = name.indexOf('=')
                    val value = if (eq >= 0) token.trim().substring(token.trim().indexOf('=') + 1) else ""
                    val key = if (eq >= 0) name.substring(0, eq) else name
                    when (key) {
                        "third-party", "3p" -> party = if (neg) 2 else 1
                        "first-party", "1p" -> party = if (neg) 1 else 2
                        "important" -> important = true
                        "match-case" -> {}
                        "domain", "from" -> {
                            if (value.isEmpty()) return
                            for (entry in value.lowercase().split('|')) {
                                val e = entry.trim()
                                if (e.isEmpty()) continue
                                if (e.startsWith("~")) {
                                    if (exclude == null) exclude = ArrayList()
                                    exclude.add(e.substring(1))
                                } else {
                                    if (include == null) include = ArrayList()
                                    include.add(e)
                                }
                            }
                        }
                        "all" -> {
                            if (neg) return
                            types = types or RType.ALL
                        }
                        else -> {
                            val bit = typeBit(key)
                            if (bit == 0) return // unsupported option: skip the rule
                            if (neg) negTypes = negTypes or bit else types = types or bit
                        }
                    }
                }
            }
        }

        var t = if (types == 0) RType.DEFAULT else types
        if (negTypes != 0) t = t and negTypes.inv()
        if (t == 0) return

        // Page-level exceptions (`@@||site^$document`): whole page is not filtered.
        if (exception && (t and RType.DOCUMENT) != 0) {
            val rule = buildRule(pattern, t and RType.DOCUMENT, party, important, include, exclude) ?: return
            addRule(docAllow, rule, indexHost = false)
            counted()
            t = t and RType.DOCUMENT.inv()
            if (t == 0) return
        }
        // Blocking the page itself is never done here (navigations are untouched).
        if (!exception) {
            t = t and RType.DOCUMENT.inv()
            if (t == 0) return
        }

        val plainOk = types == 0 && negTypes == 0 && party == 0 && !important && include == null && exclude == null
        if (plainOk && !pattern.startsWith("/")) {
            val p = pattern.lowercase()
            if (p.startsWith("||") && p.endsWith("^") && !p.endsWith("|^")) {
                val dom = p.substring(2, p.length - 1)
                if (isPlainHost(dom)) {
                    val set = if (exception) allow else block
                    if (!set.domainSet.contains(dom)) {
                        set.addPlainDomain(dom)
                        counted()
                    }
                    return
                }
            }
        }

        val rule = buildRule(pattern, t, party, important, include, exclude) ?: return
        addRule(if (exception) allow else block, rule, indexHost = true)
        counted()
    }

    private fun buildRule(
        patternIn: String,
        types: Int,
        party: Int,
        important: Boolean,
        include: List<String>?,
        exclude: List<String>?,
    ): NetRule? {
        var p = patternIn
        var anchor = 0
        var endAnchor = false
        val inc = include?.toTypedArray()
        val exc = exclude?.toTypedArray()

        if (p.length >= 2 && p.startsWith("/") && p.endsWith("/")) {
            val body = p.substring(1, p.length - 1)
            if (body.isEmpty()) return null
            return NetRule(body, 0, false, types, party, important, inc, exc, isRegex = true, domainOnly = false)
        }
        if (p.startsWith("||")) { anchor = 2; p = p.substring(2) }
        else if (p.startsWith("|")) { anchor = 1; p = p.substring(1) }
        if (p.endsWith("|")) { endAnchor = true; p = p.dropLast(1) }
        p = p.lowercase()
        if (p.startsWith("*")) {
            p = p.trimStart('*')
            anchor = 0
        }
        if (p.endsWith("*")) {
            p = p.trimEnd('*')
            endAnchor = false
        }
        // A rule that would match every URL without any restriction is never intended.
        if (p.isEmpty() && types == RType.DEFAULT && party == 0 && inc == null) return null

        var domainOnly = false
        if (anchor == 2) {
            val key = hostKey(p)
            if (key != null) {
                val rest = p.substring(key.length)
                domainOnly = rest == "^"
                return NetRule(p, anchor, endAnchor, types, party, important, inc, exc, false, domainOnly).also {
                    hostKeyed = key
                }
            }
        }
        hostKeyed = null
        return NetRule(p, anchor, endAnchor, types, party, important, inc, exc, false, false)
    }

    // buildRule reports the host-index key it chose through this field (single-threaded builder).
    private var hostKeyed: String? = null

    private fun addRule(set: RuleSet, rule: NetRule, indexHost: Boolean) {
        if (rule.isRegex) {
            set.addRegex(rule)
            return
        }
        val key = hostKeyed
        if (indexHost && key != null && rule.anchor == 2) {
            set.addHostRule(key, rule)
            return
        }
        val token = chooseToken(rule.pattern, rule.anchor, rule.endAnchor)
        if (token != null) set.addTokenRule(token, rule) else set.addFallback(rule)
    }

    fun build(): FilterEngine = FilterEngine(block, allow, docAllow, hostExact, HashMap(counts))

    // ── Helpers ──────────────────────────────────────────────────────

    /** Domain part of a `||host...` pattern, if the rule can live in the host index. */
    private fun hostKey(p: String): String? {
        var i = 0
        while (i < p.length && p[i] !in "/^*?:|") i++
        if (i == 0 || i >= p.length) return null // `||host` with nothing after: stay in the token path
        val key = p.substring(0, i)
        if (key.endsWith(".") || !isPlainHost(key)) return null
        return key
    }

    private fun chooseToken(p: String, anchor: Int, endAnchor: Boolean): String? {
        var best: String? = null
        var i = 0
        while (i < p.length) {
            if (!RuleSet.isTokenChar(p[i])) { i++; continue }
            var j = i + 1
            while (j < p.length && RuleSet.isTokenChar(p[j])) j++
            val leftOk = if (i > 0) p[i - 1] != '*' else anchor != 0
            val rightOk = if (j < p.length) p[j] != '*' else endAnchor
            if (leftOk && rightOk && j - i >= RuleSet.MIN_TOKEN) {
                val tok = p.substring(i, j)
                if (tok !in STOP_TOKENS && (best == null || tok.length > best.length)) best = tok
            }
            i = j
        }
        return best
    }

    private fun isCosmetic(line: String): Boolean {
        val h = line.indexOf('#')
        if (h < 0) return false
        var j = h + 1
        // ##, #@#, #?#, #$#, #%#, #@$#, #@?#, #@%#
        while (j < line.length && (line[j] == '@' || line[j] == '?' || line[j] == '$' || line[j] == '%')) j++
        if (j >= line.length || line[j] != '#') return false
        for (k in 0 until h) {
            val c = line[k]
            if (!(c.isLetterOrDigit() || c == '.' || c == ',' || c == '~' || c == '*' || c == '-' || c == '_' || c == '|')) return false
        }
        return true
    }

    private fun looksLikeOptions(s: String): Boolean {
        if (s.isEmpty() || s.contains('/') && !s.contains('=')) return false
        for (token in s.split(',')) {
            val t = token.trim()
            if (t.isEmpty()) return false
            val name = t.removePrefix("~").substringBefore('=')
            if (name.isEmpty() || !name.all { it.isLetterOrDigit() || it == '-' || it == '_' }) return false
        }
        return true
    }

    private fun typeBit(name: String): Int = when (name) {
        "script" -> RType.SCRIPT
        "image" -> RType.IMAGE
        "stylesheet", "css" -> RType.STYLE
        "font" -> RType.FONT
        "xmlhttprequest", "xhr" -> RType.XHR
        "subdocument", "frame" -> RType.SUBDOC
        "media" -> RType.MEDIA
        "object", "object-subrequest" -> RType.OBJECT
        "ping" -> RType.PING
        "websocket" -> RType.WEBSOCKET
        "other" -> RType.OTHER
        "document", "doc" -> RType.DOCUMENT
        "popup" -> RType.POPUP
        else -> 0
    }

    private fun isHostName(s: String): Boolean {
        if (s.isEmpty() || s.first() == '.' || s.first() == '-') return false
        for (c in s) {
            if (!((c in 'a'..'z') || (c in '0'..'9') || c == '.' || c == '-' || c == '_')) return false
        }
        return true
    }

    private fun isPlainHost(s: String): Boolean = s.contains('.') && isHostName(s)

    companion object {
        private val STOP_TOKENS = setOf("http", "https", "www", "com", "net", "org", "html", "htm", "php")
        private val IGNORED_HOSTS = setOf(
            "localhost", "localhost.localdomain", "local", "broadcasthost",
            "ip6-localhost", "ip6-loopback", "ip6-localnet", "ip6-mcastprefix",
            "ip6-allnodes", "ip6-allrouters", "ip6-allhosts", "0.0.0.0",
        )
    }
}
