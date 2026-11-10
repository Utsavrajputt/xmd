package com.invictus.xmd.ui.browser

import com.invictus.xmd.database.entities.HistoryEntry
import com.invictus.xmd.network.SuggestItem
import kotlin.math.ln
import kotlin.math.min

/**
 * One ranking for the whole address-bar list, the way Chrome's omnibox does it: every
 * candidate -- pages you visited, things you searched for before, Google's phrases and
 * entities -- gets a single relevance number on the same scale, then the list is just
 * sorted by it (duplicates merged first).
 *
 *  - Google rows keep Google's own relevance (`google:suggestrelevance`), roughly 600-1300.
 *  - History rows score 400-1399 from two parts, like Chrome's ScoredHistoryMatch:
 *    topicality (how well the typed words match the address/title: address prefix beats a
 *    word start, which beats "somewhere inside") and frequency (how recent, how many visits
 *    up to 10, bookmark bonus).
 *
 * Pure functions on purpose: no Android state, easy to reason about and to unit test.
 */
object SuggestionRanker {

    private const val MAX_ROWS = 24
    // Pages from history pile up fast (every video/article of one site is its own row), so only a
    // few show, and at most two from the same site.
    private const val MAX_PAGE_ROWS = 4
    private const val MAX_PAGE_ROWS_PER_HOST = 2
    private const val MAX_PAST_SEARCH_ROWS = 4
    private const val PAST_SEARCH_CAP = 1250
    private const val DAY_MS = 86_400_000.0

    private class Candidate(val suggestion: Suggestion, val relevance: Int, val key: String)

    fun rank(
        typed: String,
        history: List<HistoryEntry>,
        isBookmarked: (String) -> Boolean,
        google: List<SuggestItem>,
        nowMs: Long = System.currentTimeMillis(),
    ): List<Suggestion> {
        val query = typed.trim()
        val tokens = query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val candidates = ArrayList<Candidate>()

        for (e in history) {
            val pastQuery = searchQueryOf(e.url)
            if (pastQuery != null) {
                // A search-results page in history = "you searched for this" (clock-icon row).
                if (!tokens.all { pastQuery.lowercase().contains(it) }) continue
                val rel = historyRelevance(tokens, query, pastQuery, "", e, false, nowMs)
                    .coerceAtMost(PAST_SEARCH_CAP)
                candidates += Candidate(
                    Suggestion.Search(pastQuery, history = true, inHistory = true), rel, "q:" + pastQuery.lowercase(),
                )
            } else {
                val rel = historyRelevance(tokens, query, e.title, e.url, e, isBookmarked(e.url), nowMs)
                candidates += Candidate(Suggestion.History(e.title, e.url), rel, "u:" + addressKey(e.url))
            }
        }

        google.forEachIndexed { i, g ->
            if (g.text.equals(query, ignoreCase = true)) return@forEachIndexed // the typed row covers it
            val rel = if (g.relevance > 0) g.relevance else 1000 - 40 * i
            candidates += Candidate(
                Suggestion.Search(g.text, g.subtitle, g.imageUrl), rel, "q:" + g.text.lowercase(),
            )
        }

        // Merge duplicates (same page, or the same phrase from history and from Google).
        val merged = LinkedHashMap<String, Candidate>()
        for (c in candidates) {
            val old = merged[c.key]
            merged[c.key] = if (old == null) c else merge(old, c)
        }

        val out = ArrayList<Suggestion>()
        out += Suggestion.Search(query, typed = true)
        var pages = 0
        var past = 0
        val perHost = HashMap<String, Int>()
        for (c in merged.values.sortedByDescending { it.relevance }) { // stable: ties keep source order
            if (out.size >= MAX_ROWS) break
            val s = c.suggestion
            if (s is Suggestion.History) {
                if (pages >= MAX_PAGE_ROWS) continue
                val host = addressKey(s.url).substringBefore('/')
                val sameHost = perHost[host] ?: 0
                if (sameHost >= MAX_PAGE_ROWS_PER_HOST) continue
                perHost[host] = sameHost + 1
                pages++
            }
            if (s is Suggestion.Search && s.history) { if (past >= MAX_PAST_SEARCH_ROWS) continue; past++ }
            out += s
        }
        return out
    }

    /** Same phrase from history and Google: keep Google's picture/description, add a "you searched this" boost. */
    private fun merge(a: Candidate, b: Candidate): Candidate {
        val sa = a.suggestion
        val sb = b.suggestion
        if (sa is Suggestion.Search && sb is Suggestion.Search) {
            val rich = listOf(sa, sb).firstOrNull { it.imageUrl != null || it.subtitle != null }
            val shown = rich ?: listOf(sa, sb).firstOrNull { it.history } ?: sa
            val both = sa.history != sb.history
            return Candidate(
                shown.copy(inHistory = sa.inHistory || sb.inHistory),
                maxOf(a.relevance, b.relevance) + if (both) 40 else 0,
                a.key,
            )
        }
        return if (a.relevance >= b.relevance) a else b
    }

    // ---- history scoring (Chrome's ScoredHistoryMatch, simplified) ---------------------------

    private fun historyRelevance(
        tokens: List<String>,
        query: String,
        title: String,
        url: String,
        entry: HistoryEntry,
        bookmarked: Boolean,
        nowMs: Long,
    ): Int {
        val topical = topicality(tokens, query.lowercase(), title.lowercase(), addressKey(url))
        val freq = frequency(entry.visitedAtMs, entry.visitCount, bookmarked, nowMs)
        val blend = (topical * 0.55 + freq * 0.45).coerceIn(0.0, 1.0)
        return (400 + blend * 999).toInt() // Chrome's history range: 400..1399
    }

    internal fun topicality(tokens: List<String>, query: String, title: String, address: String): Double {
        if (tokens.isEmpty()) return 0.0
        var sum = 0.0
        for (t in tokens) {
            sum += when {
                address.startsWith(t) -> 1.0
                hasWordStart(address, t) -> 0.85
                hasWordStart(title, t) -> 0.75
                address.contains(t) -> 0.45
                title.contains(t) -> 0.4
                else -> 0.2
            }
        }
        var score = sum / tokens.size
        // What you typed is the start of the whole address / title: Chrome could inline-complete it.
        if (query.isNotEmpty()) {
            if (address.startsWith(query)) score = maxOf(score, 1.0)
            else if (title.startsWith(query)) score = maxOf(score, 0.9)
        }
        return score
    }

    internal fun frequency(lastVisitMs: Long, visitCount: Int, bookmarked: Boolean, nowMs: Long): Double {
        val days = (nowMs - lastVisitMs).coerceAtLeast(0L) / DAY_MS
        // Chrome's recency curve: flat for 4 days, then a gentle decline to about a year.
        val recency = when {
            days <= 4 -> 1.0
            days <= 14 -> lerp(1.0, 0.7, (days - 4) / 10)
            days <= 90 -> lerp(0.7, 0.3, (days - 14) / 76)
            days <= 365 -> lerp(0.3, 0.1, (days - 90) / 275)
            else -> 0.05
        }
        val visits = visitCount.coerceIn(1, 10) // Chrome only scores the last 10 visits too
        val volume = ln(1.0 + visits) / ln(11.0) // ~0.29 for one visit .. 1.0 for ten
        var f = recency * (0.35 + 0.65 * volume)
        if (bookmarked) f = min(1.0, f + 0.25)
        return f
    }

    private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t.coerceIn(0.0, 1.0)

    /** True when [token] begins a word in [text] (start of text or right after a non-letter/digit). */
    private fun hasWordStart(text: String, token: String): Boolean {
        var i = text.indexOf(token)
        while (i >= 0) {
            if (i == 0 || !text[i - 1].isLetterOrDigit()) return true
            i = text.indexOf(token, i + 1)
        }
        return false
    }

    /** "https://www.github.com/a/b/" -> "github.com/a/b" (lowercase), the form matched against typed text. */
    private fun addressKey(url: String): String =
        url.trim().lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')

    /** The search phrase inside a search-engine results URL (google/bing/ddg/...), else null. */
    fun searchQueryOf(url: String): String? {
        val uri = runCatching { android.net.Uri.parse(url) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val key = when {
            host.contains("google.") || host.contains("bing.com") || host.contains("duckduckgo.com") ||
                host.contains("search.brave.com") || host.contains("ecosia.org") || host.contains("startpage.com") -> "q"
            host.contains("yahoo.") -> "p"
            else -> return null
        }
        return uri.getQueryParameter(key)?.trim()?.takeIf { it.isNotEmpty() }
    }
}
