package com.invictus.xmd.network

import android.util.LruCache
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import java.io.IOException
import kotlin.coroutines.resume

/**
 * Address-bar autocomplete. Backed by Google's public suggest endpoint
 * (the same one Chrome's omnibox uses) rather than any list bundled in
 * this app -- we don't ship or maintain a list of sites of any kind
 * (movie, download, or otherwise). Whatever the user types is sent to
 * Google and results come back tagged by type; only "QUERY" (a search
 * phrase) results are kept, "NAVIGATION" (a website/URL guess) ones are
 * dropped, so the dropdown always reads as search suggestions rather than
 * site suggestions.
 *
 * Speed: requests are truly cancellable (typing the next letter aborts the
 * in-flight call instead of letting it finish), results are cached per
 * query, and [provisional] lets the UI paint something on the very same
 * frame as the keystroke while the real request is still on its way.
 */
/** One autocomplete row: the phrase, plus -- for Google "entity" results
 *  (a person, place, show...) -- a one-line description and a thumbnail URL. */
data class SuggestItem(
    val text: String,
    val subtitle: String? = null,
    val imageUrl: String? = null,
    /** Google's own score for this row (Chrome sorts by it); 0 = not provided. */
    val relevance: Int = 0,
)

object SuggestApi {

    private const val ENDPOINT = "https://www.google.com/complete/search"

    private val cache = object : LruCache<String, List<SuggestItem>>(150) {}

    private fun key(query: String) = query.trim().lowercase()

    /** Exact cached result for [query], or null if never fetched. */
    fun cached(query: String): List<SuggestItem>? = cache.get(key(query))

    /**
     * Instant placeholder list: the exact cached result if we have one,
     * otherwise the longest cached shorter prefix filtered down to phrases
     * that still start with what's typed now ("goo" -> filter of "go").
     */
    fun provisional(query: String): List<SuggestItem> {
        val q = key(query)
        if (q.isEmpty()) return emptyList()
        cache.get(q)?.let { return it }
        for (len in q.length - 1 downTo 1) {
            val prefix = cache.get(q.substring(0, len)) ?: continue
            return prefix.filter { it.text.lowercase().startsWith(q) }
        }
        return emptyList()
    }

    /**
     * Empty list on any failure (network error, malformed response, blank
     * query). Cancelling the calling coroutine cancels the HTTP call.
     */
    suspend fun suggest(query: String, client: OkHttpClient): List<SuggestItem> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        cache.get(key(trimmed))?.let { return it }

        val url = ENDPOINT.toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("client", "chrome")
            ?.addQueryParameter("q", trimmed)
            ?.build() ?: return emptyList()
        val request = Request.Builder().url(url).build()

        val result = suspendCancellableCoroutine<List<SuggestItem>?> { cont ->
            val call = client.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resume(null)
                }

                override fun onResponse(call: Call, response: Response) {
                    val parsed = response.use { r ->
                        if (!r.isSuccessful) null
                        else runCatching { parse(r.body?.string().orEmpty()) }.getOrNull()
                    }
                    if (cont.isActive) cont.resume(parsed)
                }
            })
        }
        if (result == null) return emptyList()
        cache.put(key(trimmed), result)
        return result
    }

    private fun parse(raw: String): List<SuggestItem> {
        // A couple of Google's suggest variants prefix the body with
        // a )]}' XSSI-protection line before the actual JSON -- strip
        // it if present so JSONArray(...) below doesn't choke on it.
        val body = if (raw.startsWith(")]}'")) raw.substringAfter("\n") else raw

        // Shape: ["query", ["phrase one", ...], [], {"google:suggesttype": ["QUERY", "ENTITY", ...],
        //         "google:suggestdetail": [{}, {"a": "description", "i": "thumbnail url"}, ...]}]
        val outer = JSONArray(body)
        if (outer.length() < 2) return emptyList()
        val phrases = outer.getJSONArray(1)
        val meta = outer.optJSONObject(3)
        val types = meta?.optJSONArray("google:suggesttype")
        val details = meta?.optJSONArray("google:suggestdetail")
        val relevances = meta?.optJSONArray("google:suggestrelevance")

        return (0 until phrases.length()).mapNotNull { i ->
            val phrase = phrases.getString(i)
            val type = types?.optString(i)
            // No type info at all (older/plain response shape) -- keep
            // everything rather than dropping results outright. Search
            // phrases and entities (the rows Chrome shows with a picture)
            // are kept; NAVIGATION (website guesses) stay dropped.
            if (type != null && type != "QUERY" && type != "ENTITY") return@mapNotNull null
            val detail = details?.optJSONObject(i)
            SuggestItem(
                text = phrase,
                subtitle = detail?.optString("a")?.takeIf { it.isNotBlank() },
                imageUrl = detail?.optString("i")?.takeIf { it.startsWith("http") },
                relevance = relevances?.optInt(i, 0) ?: 0,
            )
        }
    }
}
