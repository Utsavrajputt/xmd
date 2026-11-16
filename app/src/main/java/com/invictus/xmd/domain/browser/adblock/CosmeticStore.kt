package com.invictus.xmd.domain.browser.adblock

/** One cosmetic rule, as the page agent will receive it. */
internal class CosEntry(
    @JvmField val kind: Int,
    /** HIDE: selector. PROC: base selector. SCRIPTLET: name. CSS: a complete CSS rule. */
    @JvmField val a: String,
    /** PROC: operator/argument pairs. SCRIPTLET: arguments. */
    @JvmField val b: Array<String>?,
    /** Hosts the rule must NOT apply to (`~example.com##...`). */
    @JvmField val exclude: Array<String>?,
    /** Text a `#@#` exception uses to cancel this rule. */
    @JvmField val key: String,
    /** From the user's own rules: not cancelled by list exceptions, not switched off by `$elemhide`. */
    @JvmField val user: Boolean = false,
)

/** What a page on one host gets: CSS to inject, procedural rules, scriptlets. */
class CosmeticResult internal constructor(
    val css: String,
    internal val proc: List<Array<String>>,
    internal val scriptlets: List<Array<String>>,
    /** True if the page should ask for generic class/id selectors too. */
    val generic: Boolean,
) {
    val isEmpty: Boolean get() = css.isEmpty() && proc.isEmpty() && scriptlets.isEmpty() && !generic

    /** `{"c":"...","g":1,"p":[[base,op,arg,...]],"s":[[name,arg,...]]}`; empty parts are left out. */
    fun toJson(): String {
        val sb = StringBuilder(css.length + 64)
        sb.append('{')
        var first = true
        fun sep() { if (!first) sb.append(','); first = false }
        if (css.isNotEmpty()) { sep(); sb.append("\"c\":"); quote(css, sb) }
        if (generic) { sep(); sb.append("\"g\":1") }
        if (proc.isNotEmpty()) { sep(); sb.append("\"p\":"); arrays(proc, sb) }
        if (scriptlets.isNotEmpty()) { sep(); sb.append("\"s\":"); arrays(scriptlets, sb) }
        sb.append('}')
        return sb.toString()
    }

    private fun arrays(list: List<Array<String>>, sb: StringBuilder) {
        sb.append('[')
        list.forEachIndexed { i, arr ->
            if (i > 0) sb.append(',')
            sb.append('[')
            arr.forEachIndexed { j, s ->
                if (j > 0) sb.append(',')
                quote(s, sb)
            }
            sb.append(']')
        }
        sb.append(']')
    }

    companion object {
        val EMPTY = CosmeticResult("", emptyList(), emptyList(), false)

        fun quote(s: String, sb: StringBuilder) {
            sb.append('"')
            for (ch in s) {
                when {
                    ch == '"' -> sb.append("\\\"")
                    ch == '\\' -> sb.append("\\\\")
                    ch == '\n' -> sb.append("\\n")
                    ch == '\r' -> sb.append("\\r")
                    ch == '\t' -> sb.append("\\t")
                    ch == '\u2028' -> sb.append("\\u2028")
                    ch == '\u2029' -> sb.append("\\u2029")
                    ch < ' ' -> sb.append("\\u").append(String.format("%04x", ch.code))
                    else -> sb.append(ch)
                }
            }
            sb.append('"')
        }
    }
}

/**
 * Element-hiding (`##.ad`), procedural (`:has-text`, `:upward`, `:remove`),
 * style-injection (`#$#`, `:style()`) and scriptlet (`##+js(...)`,
 * `#%#//scriptlet(...)`) rules, with their `#@#` exceptions and the
 * `$elemhide` / `$generichide` switches. Generic class/id selectors are
 * indexed so a page only receives the ones its own DOM can match.
 */
internal class CosmeticStore {

    private val specific = HashMap<String, ArrayList<CosEntry>>()
    private val specificExc = HashMap<String, HashSet<String>>()
    private val genericExc = HashSet<String>()
    private val genericClass = HashMap<String, Any>()
    private val genericId = HashMap<String, Any>()
    private val genericComplex = ArrayList<String>()
    private val genericExcluded = ArrayList<CosEntry>()
    private val genericScriptlets = ArrayList<CosEntry>()
    private val hideOff = HashMap<String, Int>()

    /** True while the user's own rules are being added: they take priority over lists. */
    var userMode = false

    // Exceptions the user wrote themselves (list exceptions never cancel user rules).
    private val userSpecificExc = HashMap<String, HashSet<String>>()
    private val userGenericExc = HashSet<String>()
    // User rules without a host: always sent, never indexed (so list exceptions can't drop them).
    private val userGeneric = ArrayList<String>()

    var count = 0
        private set

    fun addHideOff(site: String, mask: Int) {
        hideOff[site] = (hideOff[site] ?: 0) or mask
    }

    // ── Parsing ──────────────────────────────────────────────────────

    /** Parses one cosmetic line; true if it was understood and stored. */
    fun addLine(line: String): Boolean {
        var at = -1
        var op = ""
        var i = line.indexOf('#')
        while (i >= 0) {
            val found = OPS.firstOrNull { line.startsWith(it, i) }
            if (found != null) { at = i; op = found; break }
            i = line.indexOf('#', i + 1)
        }
        if (at < 0) return false
        val domains = line.substring(0, at)
        val body = line.substring(at + op.length).trim()
        if (body.isEmpty()) return false

        val include = ArrayList<String>()
        val exclude = ArrayList<String>()
        if (domains.isNotEmpty()) {
            for (raw in domains.split(',')) {
                val e = raw.trim().lowercase()
                if (e.isEmpty()) continue
                if (e.startsWith("/")) return false // regex domains are not supported
                if (e.startsWith("~")) exclude.add(e.substring(1)) else include.add(e)
            }
        }
        val excl = if (exclude.isEmpty()) null else exclude.toTypedArray()

        when (op) {
            "##", "#?#" -> return addRule(body, include, excl)
            "#$#" -> return addCssInjection(body, include, excl)
            "#%#" -> {
                val args = adguardScriptlet(body) ?: return false
                return addScriptlet(args, include, excl)
            }
            "#@#", "#@?#" -> return addException(exceptionKey(body), include)
            "#@$#" -> return addException(body.trim(), include)
            "#@%#" -> {
                val args = adguardScriptlet(body) ?: return false
                return addException(scriptletKey(args), include)
            }
        }
        return false
    }

    private fun exceptionKey(body: String): String {
        if (body.startsWith("+js(") && body.endsWith(")")) {
            val args = parseJsArgs(body.substring(4, body.length - 1))
            return if (args.isEmpty()) "+js:*" else scriptletKey(args)
        }
        return body
    }

    private fun scriptletKey(args: List<String>): String =
        "+js:" + normalizeScriptlet(args[0]) + "," + args.drop(1).joinToString(",")

    private fun addException(key: String, include: List<String>): Boolean {
        if (key.isEmpty()) return false
        if (include.isEmpty()) genericExc.add(key)
        else for (d in include) specificExc.getOrPut(d) { HashSet() }.add(key)
        if (userMode) {
            if (include.isEmpty()) userGenericExc.add(key)
            else for (d in include) userSpecificExc.getOrPut(d) { HashSet() }.add(key)
        }
        count++
        return true
    }

    private fun addRule(body: String, include: List<String>, exclude: Array<String>?): Boolean {
        if (body.startsWith("+js(") && body.endsWith(")")) {
            val args = parseJsArgs(body.substring(4, body.length - 1))
            if (args.isEmpty()) return false
            return addScriptlet(args, include, exclude)
        }
        var sel = body.replace(":-abp-has(", ":has(")
        if (!safeSelector(sel)) return false

        // `sel:style(color:red)` -> injected CSS rule
        if (sel.endsWith(")") && sel.contains(":style(")) {
            val idx = sel.lastIndexOf(":style(")
            val base = sel.substring(0, idx).trim()
            val css = sel.substring(idx + ":style(".length, sel.length - 1).trim()
            if (base.isEmpty() || css.isEmpty() || !safeCssBody(css) || !balanced(base)) return false
            return addEntry(CosEntry(KIND_CSS, "$base{$css}", null, exclude, body, userMode), include, exclude, generic = false)
        }

        val proc = parseProcedural(sel)
        if (proc === UNSUPPORTED) return false
        if (proc != null) {
            // Procedural rules need a host (a generic one would run on every page).
            if (include.isEmpty()) return false
            return addEntry(CosEntry(KIND_PROC, proc.base, proc.ops, exclude, body, userMode), include, exclude, generic = false)
        }
        sel = sel.trim()
        return addEntry(CosEntry(KIND_HIDE, sel, null, exclude, body, userMode), include, exclude, generic = true)
    }

    private fun addCssInjection(body: String, include: List<String>, exclude: Array<String>?): Boolean {
        // AdGuard: `#$#selector { declarations }`
        val open = body.indexOf('{')
        if (open <= 0 || !body.endsWith("}")) return false
        val sel = body.substring(0, open).trim()
        var css = body.substring(open + 1, body.length - 1).trim()
        if (sel.isEmpty() || css.isEmpty()) return false
        if (css.replace(" ", "") == "remove:true;") css = "display:none!important"
        if (!safeSelector(sel) || !safeCssBody(css)) return false
        return addEntry(CosEntry(KIND_CSS, "$sel{$css}", null, exclude, body.trim(), userMode), include, exclude, generic = false)
    }

    private fun addScriptlet(args: List<String>, include: List<String>, exclude: Array<String>?): Boolean {
        val name = normalizeScriptlet(args[0])
        if (name.isEmpty()) return false
        val entry = CosEntry(
            KIND_SCRIPTLET, name, args.drop(1).toTypedArray(), exclude,
            scriptletKey(args), userMode,
        )
        if (include.isEmpty()) {
            genericScriptlets.add(entry)
            count++
            return true
        }
        return addEntry(entry, include, exclude, generic = false)
    }

    private fun addEntry(entry: CosEntry, include: List<String>, exclude: Array<String>?, generic: Boolean): Boolean {
        if (include.isNotEmpty()) {
            for (d in include) specific.getOrPut(d) { ArrayList() }.add(entry)
            count++
            return true
        }
        if (!generic) {
            // A style/procedural rule without a host: keep only when negated hosts exist.
            if (entry.kind == KIND_CSS) {
                genericExcluded.add(entry)
                count++
                return true
            }
            return false
        }
        if (exclude != null) {
            genericExcluded.add(entry)
            count++
            return true
        }
        if (userMode) {
            userGeneric.add(entry.a)
            count++
            return true
        }
        val m = SIMPLE.matchEntire(entry.a)
        if (m != null) {
            val cls = m.groupValues[1]
            val id = m.groupValues[2]
            if (cls.isNotEmpty()) put(genericClass, cls, entry.a) else put(genericId, id, entry.a)
        } else {
            genericComplex.add(entry.a)
        }
        count++
        return true
    }

    @Suppress("UNCHECKED_CAST")
    private fun put(map: HashMap<String, Any>, key: String, selector: String) {
        when (val cur = map[key]) {
            null -> map[key] = selector
            is String -> map[key] = arrayListOf(cur, selector)
            else -> (cur as ArrayList<String>).add(selector)
        }
    }

    // ── Lookup ───────────────────────────────────────────────────────

    private fun keysFor(host: String): List<String> {
        val keys = ArrayList<String>(6)
        var h = host
        while (true) {
            keys.add(h)
            val dot = h.indexOf('.')
            if (dot < 0) break
            h = h.substring(dot + 1)
        }
        // `example.*` entities: host without its public suffix.
        val reg = Domains.registrable(host)
        val tld = reg.substringAfter('.', "")
        if (tld.isNotEmpty() && host.endsWith(".$tld")) {
            var base = host.removeSuffix(".$tld")
            while (true) {
                keys.add("$base.*")
                val dot = base.indexOf('.')
                if (dot < 0) break
                base = base.substring(dot + 1)
            }
        }
        return keys
    }

    private fun excluded(host: String, exclude: Array<String>?): Boolean {
        if (exclude == null) return false
        for (e in exclude) if (Domains.entryMatches(host, e)) return true
        return false
    }

    private fun offMask(keys: List<String>): Int {
        var m = 0
        for (k in keys) m = m or (hideOff[k] ?: 0)
        return m
    }

    private fun userExceptionsFor(keys: List<String>): Set<String> {
        if (userGenericExc.isEmpty() && userSpecificExc.isEmpty()) return emptySet()
        val out = HashSet<String>(userGenericExc)
        for (k in keys) userSpecificExc[k]?.let { out.addAll(it) }
        return out
    }

    private fun exceptionsFor(keys: List<String>): Set<String> {
        val out = HashSet<String>(genericExc)
        for (k in keys) specificExc[k]?.let { out.addAll(it) }
        return out
    }

    /** Everything that applies to a page on [host]. */
    fun lookup(host: String, hiding: Boolean, scripts: Boolean): CosmeticResult {
        if (host.isEmpty() || (!hiding && !scripts)) return CosmeticResult.EMPTY
        val keys = keysFor(host)
        val off = offMask(keys)
        val exc = exceptionsFor(keys)
        val uexc = userExceptionsFor(keys)
        val noScriptlets = exc.contains("+js:*")
        val userNoScriptlets = uexc.contains("+js:*")
        val hide = LinkedHashSet<String>()
        val css = LinkedHashSet<String>()
        val proc = ArrayList<Array<String>>()
        val scriptlets = ArrayList<Array<String>>()
        val allOff = (off and OFF_ALL) != 0
        val specificOff = allOff || (off and OFF_SPECIFIC) != 0
        val genericOff = allOff || (off and OFF_GENERIC) != 0

        fun take(e: CosEntry, fromSpecific: Boolean) {
            if (excluded(host, e.exclude) || (if (e.user) uexc else exc).contains(e.key)) return
            // The user's own rules ignore the lists' per-site `$elemhide` switches.
            val sOff = specificOff && !e.user
            val gOff = genericOff && !e.user
            when (e.kind) {
                KIND_HIDE -> if (hiding && !(fromSpecific && sOff) && !(!fromSpecific && gOff)) hide.add(e.a)
                KIND_CSS -> if (hiding && !(fromSpecific && sOff) && !(!fromSpecific && gOff)) css.add(e.a)
                KIND_PROC -> if (hiding && !sOff) {
                    val arr = arrayOfNulls<String>(1 + (e.b?.size ?: 0))
                    arr[0] = e.a
                    e.b?.forEachIndexed { i, s -> arr[i + 1] = s }
                    @Suppress("UNCHECKED_CAST")
                    proc.add(arr as Array<String>)
                }
                KIND_SCRIPTLET -> if (scripts && !(if (e.user) userNoScriptlets else noScriptlets)) {
                    val args = e.b ?: emptyArray()
                    val arr = arrayOfNulls<String>(1 + args.size)
                    arr[0] = e.a
                    args.forEachIndexed { i, s -> arr[i + 1] = s }
                    @Suppress("UNCHECKED_CAST")
                    scriptlets.add(arr as Array<String>)
                }
            }
        }

        for (k in keys) specific[k]?.forEach { take(it, true) }
        if (hiding && !genericOff) {
            for (sel in genericComplex) if (!exc.contains(sel)) hide.add(sel)
        }
        // Style rules with negated hosts only: lists' ones respect `$generichide`, the user's don't.
        genericExcluded.forEach { if (it.user || (hiding && !genericOff)) take(it, false) }
        if (hiding) for (sel in userGeneric) if (!uexc.contains(sel)) hide.add(sel)
        if (scripts) genericScriptlets.forEach { take(it, false) }

        val sb = StringBuilder()
        for (sel in hide) sb.append(sel).append("{display:none!important}\n")
        for (rule in css) sb.append(rule).append('\n')
        return CosmeticResult(sb.toString(), proc, scriptlets, hiding && !genericOff && (genericClass.isNotEmpty() || genericId.isNotEmpty()))
    }

    /** Hide rules for the generic class / id selectors a page reports having. */
    fun namesCss(host: String, classes: List<String>, ids: List<String>): String {
        if (host.isEmpty()) return ""
        val keys = keysFor(host)
        if ((offMask(keys) and (OFF_ALL or OFF_GENERIC)) != 0) return ""
        val exc = exceptionsFor(keys)
        val out = LinkedHashSet<String>()
        fun add(entry: Any?) {
            when (entry) {
                null -> {}
                is String -> if (!exc.contains(entry)) out.add(entry)
                else -> {
                    @Suppress("UNCHECKED_CAST")
                    for (s in entry as ArrayList<String>) if (!exc.contains(s)) out.add(s)
                }
            }
        }
        for (c in classes.take(MAX_NAMES)) add(genericClass[c])
        for (i in ids.take(MAX_NAMES)) add(genericId[i])
        val sb = StringBuilder()
        for (sel in out) sb.append(sel).append("{display:none!important}\n")
        return sb.toString()
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private class Proc(val base: String, val ops: Array<String>)

    /** Selector parts that are never accepted: they would break out of the CSS rule we build. */
    private fun safeSelector(s: String): Boolean {
        if (s.isEmpty() || s.length > 2000) return false
        if (s.contains('{') || s.contains('}') || s.contains(';') || s.contains("/*") || s.contains('\u0000')) return false
        if (s.contains("@import") || s.contains("url(") || s.contains("\\0")) return false
        return true
    }

    private fun safeCssBody(s: String): Boolean {
        if (s.length > 1000) return false
        val l = s.lowercase()
        if (l.contains("url(") || l.contains("@") || l.contains("image-set") || l.contains("expression(")) return false
        if (l.contains('{') || l.contains('}') || l.contains('\\') || l.contains("/*")) return false
        return true
    }

    private fun balanced(s: String): Boolean {
        var depth = 0
        for (c in s) {
            if (c == '(') depth++ else if (c == ')') { depth--; if (depth < 0) return false }
        }
        return depth == 0
    }

    /**
     * Splits `base:has-text(x):upward(2)` into the CSS base and its
     * operators. Returns null for a plain selector, [UNSUPPORTED] for
     * operators the page agent can't run.
     */
    private fun parseProcedural(sel: String): Proc? {
        for (u in UNSUPPORTED_OPS) if (sel.contains(u)) return UNSUPPORTED
        var first = -1
        for (m in SUPPORTED_OPS) {
            val idx = sel.indexOf(m)
            if (idx >= 0 && (first < 0 || idx < first)) first = idx
        }
        if (first < 0) return null
        val base = sel.substring(0, first).trim()
        if (base.isEmpty() || !balanced(base)) return UNSUPPORTED
        val ops = ArrayList<String>()
        var pos = first
        while (pos < sel.length) {
            val marker = SUPPORTED_OPS.firstOrNull { sel.startsWith(it, pos) } ?: return UNSUPPORTED
            val open = pos + marker.length
            var depth = 1
            var j = open
            while (j < sel.length && depth > 0) {
                val c = sel[j]
                if (c == '(') depth++ else if (c == ')') depth--
                j++
            }
            if (depth != 0) return UNSUPPORTED
            val arg = sel.substring(open, j - 1)
            when (marker) {
                ":has-text(", ":contains(", ":-abp-contains(" -> { ops.add("text"); ops.add(arg) }
                ":upward(" -> { ops.add("up"); ops.add(arg) }
                ":remove(" -> { ops.add("rm"); ops.add("") }
            }
            pos = j
        }
        return Proc(base, ops.toTypedArray())
    }

    companion object {
        const val KIND_HIDE = 0
        const val KIND_PROC = 1
        const val KIND_SCRIPTLET = 2
        const val KIND_CSS = 3

        const val OFF_ALL = 1
        const val OFF_GENERIC = 2
        const val OFF_SPECIFIC = 4
        const val MAX_NAMES = 800

        // Longest first so `#@$?#`-style variants are never cut short.
        private val OPS = listOf("#@$?#", "#$?#", "#@?#", "#@$#", "#@%#", "#?#", "#$#", "#%#", "#@#", "##")
        private val SIMPLE = Regex("^(?:[a-z][a-z0-9]*)?(?:\\.([A-Za-z0-9_-]+)|#([A-Za-z0-9_-]+))$")
        private val SUPPORTED_OPS = listOf(":has-text(", ":contains(", ":-abp-contains(", ":upward(", ":remove(")
        private val UNSUPPORTED_OPS = listOf(
            ":xpath(", ":matches-css(", ":matches-css-before(", ":matches-css-after(", ":matches-media(",
            ":matches-path(", ":matches-attr(", ":matches-property(", ":min-text-length(", ":others(",
            ":watch-attr(", ":-abp-properties(", ":nth-ancestor(", ":if(", ":if-not(", ":not(:has-text(",
            ":not(:contains(", ":has(:has-text(", ":has(:contains(", ":spath(", ":-abp-has(:",
        )
        private val UNSUPPORTED = Proc("", emptyArray())

        private val SCRIPTLET_PREFIXES = listOf("ubo-", "ubol-", "abp-")

        fun normalizeScriptlet(raw: String): String {
            var n = raw.trim().lowercase()
            for (p in SCRIPTLET_PREFIXES) if (n.startsWith(p)) { n = n.substring(p.length); break }
            if (n.endsWith(".js")) n = n.dropLast(3)
            return n
        }

        /** `name, arg1, arg2` with `\,` escapes and optional quotes. */
        fun parseJsArgs(inner: String): List<String> {
            val out = ArrayList<String>()
            val cur = StringBuilder()
            var i = 0
            while (i < inner.length) {
                val c = inner[i]
                if (c == '\\' && i + 1 < inner.length && inner[i + 1] == ',') {
                    cur.append(',')
                    i += 2
                    continue
                }
                if (c == ',') {
                    out.add(unquote(cur.toString().trim()))
                    cur.setLength(0)
                } else {
                    cur.append(c)
                }
                i++
            }
            if (cur.isNotBlank() || out.isNotEmpty()) out.add(unquote(cur.toString().trim()))
            return out
        }

        private fun unquote(s: String): String {
            if (s.length >= 2 && ((s.first() == '\'' && s.last() == '\'') || (s.first() == '"' && s.last() == '"'))) {
                return s.substring(1, s.length - 1)
            }
            return s
        }

        /** AdGuard `//scriptlet('name', 'arg', ...)` -> [name, args...]. */
        fun adguardScriptlet(body: String): List<String>? {
            val b = body.trim()
            val head = "//scriptlet("
            if (!b.startsWith(head) || !b.endsWith(")")) return null
            val inner = b.substring(head.length, b.length - 1)
            val out = ArrayList<String>()
            var i = 0
            while (i < inner.length) {
                while (i < inner.length && (inner[i] == ' ' || inner[i] == ',')) i++
                if (i >= inner.length) break
                val q = inner[i]
                if (q == '\'' || q == '"') {
                    val sb = StringBuilder()
                    i++
                    while (i < inner.length && inner[i] != q) {
                        if (inner[i] == '\\' && i + 1 < inner.length) { sb.append(inner[i + 1]); i += 2; continue }
                        sb.append(inner[i]); i++
                    }
                    i++
                    out.add(sb.toString())
                } else {
                    val start = i
                    while (i < inner.length && inner[i] != ',') i++
                    out.add(inner.substring(start, i).trim())
                }
            }
            return if (out.isEmpty()) null else out
        }
    }
}
