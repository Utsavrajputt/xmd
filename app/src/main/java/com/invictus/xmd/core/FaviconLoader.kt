package com.invictus.xmd.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Fetches a site's favicon for the Browser tab's speed-dial tiles.
 * No third-party image library -- a direct OkHttp GET + BitmapFactory
 * decode, backed by two cache layers so re-showing the speed dial (or
 * relaunching the app) doesn't refetch the same host repeatedly:
 *  - an in-memory LRU for the current process
 *  - a one-day-old disk cache (see [init]) so a fresh process still hits
 *    disk instead of the network -- this used to be memory-only, so every
 *    cold app start (LruCache wiped with the process) redownloaded every
 *    tile's icon from scratch, even ones fetched minutes earlier.
 *
 * Tries, in order, apple-touch-icon.png (usually the sharpest, 120-180px),
 * Google's public favicon service at a higher requested size, then the
 * site's raw favicon.ico (often only 16-32px and the main source of the
 * blurry/pixelated tiles this used to produce) -- keeping whichever
 * candidate actually decoded to the largest bitmap rather than stopping
 * at the first one that merely succeeded.
 * Returns null (never throws) if all fail, so callers just keep showing
 * the generic link icon already in the layout.
 */
object FaviconLoader {

    private const val MAX_CACHE_ENTRIES = 60
    private const val TARGET_PX = 128 // 2x a 52dp tile's ~64dp icon area on a xxhdpi-ish screen
    private const val MIN_ACCEPTABLE_PX = 48 // below this, keep trying other sources for a sharper icon
    private const val DISK_CACHE_MAX_AGE_MS = 24 * 60 * 60 * 1000L

    private val cache = object : LruCache<String, Bitmap>(MAX_CACHE_ENTRIES) {}

    private var diskCacheDir: File? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    /** Call once from FfApp.onCreate; harmless if called again. */
    fun init(context: Context) {
        if (diskCacheDir != null) return
        diskCacheDir = File(context.applicationContext.cacheDir, "favicons").apply { mkdirs() }
    }

    /**
     * Blocking; call from a background thread/coroutine, never the main
     * thread. Tries several sources in order of typical quality (a site's
     * own apple-touch-icon is almost always a crisp 120-180px PNG, whereas
     * favicon.ico is frequently a stretched 16x16 that reads as blurry once
     * it fills a 52dp tile) and keeps the sharpest bitmap actually found
     * rather than stopping at the first response that merely succeeds.
     */
    fun load(pageUrl: String): Bitmap? {
        val host = runCatching { URI(pageUrl).host }.getOrNull() ?: return null
        cache.get(host)?.let { return it }

        val diskFile = diskCacheDir?.let { File(it, diskFileName(host)) }
        if (diskFile != null && diskFile.exists() &&
            System.currentTimeMillis() - diskFile.lastModified() < DISK_CACHE_MAX_AGE_MS
        ) {
            BitmapFactory.decodeFile(diskFile.path)?.let { bitmap ->
                cache.put(host, bitmap)
                return bitmap
            }
        }

        val scheme = runCatching { URI(pageUrl).scheme }.getOrNull().takeUnless { it.isNullOrBlank() } ?: "https"

        var best: Bitmap? = null
        val candidates = listOf(
            "$scheme://$host/apple-touch-icon.png",
            "https://www.google.com/s2/favicons?sz=$TARGET_PX&domain=$host",
            "$scheme://$host/favicon.ico"
        )
        for (url in candidates) {
            val bitmap = fetch(url) ?: continue
            if (best == null || bitmap.width > best!!.width) best = bitmap
            if (bitmap.width >= MIN_ACCEPTABLE_PX) break
        }

        best?.let { bitmap ->
            cache.put(host, bitmap)
            // File.lastModified() doubles as the "fetched at" timestamp --
            // overwriting it here is what makes the next cold start's
            // staleness check above work with no separate timestamp store.
            diskFile?.let { file ->
                runCatching {
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
        }
        return best
    }

    /** Filesystem-safe cache filename for a host, e.g. "www.example.com" -> "www.example.com.png". */
    private fun diskFileName(host: String): String =
        host.replace(Regex("[^a-zA-Z0-9.-]"), "_") + ".png"

    private fun fetch(url: String): Bitmap? {
        return try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bytes = response.body?.bytes() ?: return null
                if (bytes.isEmpty()) return null
                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
                // Downscale oversized icons (some hosts serve large PNGs at
                // /favicon.ico) so tiles don't hold huge bitmaps in memory.
                if (decoded.width > TARGET_PX * 2 || decoded.height > TARGET_PX * 2) {
                    Bitmap.createScaledBitmap(decoded, TARGET_PX, TARGET_PX, true)
                } else {
                    decoded
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
