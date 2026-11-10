package com.invictus.xmd.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Thumbnails for entity rows in the address-bar suggestions (the small
 * picture Chrome shows next to e.g. a show or a person). Same no-library
 * approach as FaviconLoader: OkHttp GET + BitmapFactory, memory LRU.
 */
object SuggestImageLoader {

    private val cache = object : LruCache<String, Bitmap>(80) {}

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    fun peek(url: String): Bitmap? = cache.get(url)

    /** Blocking; call from a background thread/coroutine. Null on any failure. */
    fun load(url: String): Bitmap? {
        cache.get(url)?.let { return it }
        return try {
            client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val bytes = r.body?.bytes() ?: return@use null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }?.also { cache.put(url, it) }
        } catch (e: Exception) {
            null
        }
    }
}
