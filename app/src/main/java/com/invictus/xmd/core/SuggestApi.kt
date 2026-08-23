package com.invictus.xmd.core

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

/**
 * Address-bar autocomplete. Backed by Google's public suggest endpoint
 * (the same one Chrome's omnibox uses) rather than any list bundled in
 * this app -- we don't ship or maintain a list of sites of any kind
 * (movie, download, or otherwise). Whatever the user types is sent to
 * Google and results come back tagged by type; only "QUERY" (a search
 * phrase) results are kept, "NAVIGATION" (a website/URL guess) ones are
 * dropped, so the dropdown always reads as search suggestions rather than
 * site suggestions.
 */
object SuggestApi {

    private const val ENDPOINT = "https://www.google.com/complete/search"

    /** Empty list on any failure (network error, malformed response, blank query). */
    fun suggest(query: String, client: OkHttpClient): List<String> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val url = ENDPOINT.toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("client", "chrome")
            ?.addQueryParameter("q", trimmed)
            ?.build() ?: return emptyList()

        val request = Request.Builder().url(url).build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                // A couple of Google's suggest variants prefix the body with
                // a )]}' XSSI-protection line before the actual JSON -- strip
                // it if present so JSONArray(...) below doesn't choke on it.
                val raw = response.body?.string().orEmpty()
                val body = if (raw.startsWith(")]}'")) raw.substringAfter("\n") else raw

                // Shape: ["query", ["phrase one", "phrase two", ...], [], {"google:suggesttype": ["QUERY", "NAVIGATION", ...]}]
                val outer = JSONArray(body)
                if (outer.length() < 2) return emptyList()
                val phrases = outer.getJSONArray(1)
                val types = outer.optJSONObject(3)?.optJSONArray("google:suggesttype")

                (0 until phrases.length()).mapNotNull { i ->
                    val phrase = phrases.getString(i)
                    val type = types?.optString(i)
                    // No type info at all (older/plain response shape) -- keep
                    // everything rather than dropping results outright.
                    if (type != null && type != "QUERY") null else phrase
                }
            }
        }.getOrDefault(emptyList())
    }
}
