package com.invictus.xmd.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Fetches a GitHub user's avatar for the About screen's Developers list.
 * Same no-third-party-library approach as [FaviconLoader] -- a direct
 * OkHttp GET + BitmapFactory decode, backed by an in-memory LRU so
 * re-composing the Developers list doesn't refetch the same avatar.
 *
 * GitHub serves a user's current avatar directly at
 * `github.com/<username>.png`, no API token needed; `?size=` requests a
 * specific resolution so we're not decoding a full-size upload just to
 * shrink it into a 42dp circle.
 *
 * Returns null (never throws) on failure, so callers just keep showing
 * the generic person icon already in the layout.
 */
object GithubAvatarLoader {

    private const val MAX_CACHE_ENTRIES = 40
    private const val TARGET_PX = 128 // 2x a 42dp avatar circle on a xxhdpi-ish screen

    private val cache = object : LruCache<String, Bitmap>(MAX_CACHE_ENTRIES) {}

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    /** Blocking; call from a background thread/coroutine, never the main thread. */
    fun load(githubUsername: String): Bitmap? {
        val username = githubUsername.trim().removePrefix("@")
        if (username.isEmpty()) return null
        cache.get(username)?.let { return it }

        val bitmap = fetch("https://github.com/$username.png?size=$TARGET_PX") ?: return null
        cache.put(username, bitmap)
        return bitmap
    }

    private fun fetch(url: String): Bitmap? {
        return try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bytes = response.body?.bytes() ?: return null
                if (bytes.isEmpty()) return null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (e: Exception) {
            null
        }
    }
}
