package com.invictus.xmd.domain.browser

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Real title of a YouTube video for the "videos found" sheet, which otherwise only knows the id
 * ("YouTube video (VMTOwHKtu6E)"). Uses YouTube's public oEmbed endpoint (no key, same call the Add
 * Download dialog already makes) and remembers results for the session. Call off the main thread.
 */
object YoutubeTitleResolver {
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()
    }
    private val cache = ConcurrentHashMap<String, String>()
    private val misses = ConcurrentHashMap.newKeySet<String>()

    fun cached(url: String): String? = cache[url]

    fun title(url: String): String? {
        cache[url]?.let { return it }
        if (url in misses) return null
        val found = runCatching {
            val encoded = URLEncoder.encode(url.trim(), "UTF-8")
            val req = Request.Builder()
                .url("https://www.youtube.com/oembed?url=$encoded&format=json")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                JSONObject(resp.body?.string() ?: return@use null).optString("title").takeIf { it.isNotBlank() }
            }
        }.getOrNull()
        if (found != null) cache[url] = found else misses.add(url)
        return found
    }
}
