package com.invictus.xmd.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit
import com.invictus.xmd.Xmd

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

    // Hosts where every source failed (raw IPs, hash-named CDN hosts...) are
    // skipped for a while so long lists (History) don't re-run 3 slow requests
    // per row on every scroll.
    private const val FAILED_RETRY_MS = 10 * 60 * 1000L
    private val failedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    /** Call once from Xmd.onCreate; harmless if called again. */
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
    fun load(pageUrl: String, fast: Boolean = false): Bitmap? {
        val host = runCatching { URI(pageUrl).host }.getOrNull() ?: return null
        cache.get(host)?.let { return it }
        failedAt[host]?.let { t ->
            if (System.currentTimeMillis() - t < FAILED_RETRY_MS) return null
        }

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
        // fast = address-bar suggestion row: Google's service first (one quick,
        // reliable hit) and skip the often-404 apple-touch-icon probe.
        val candidates = if (fast) listOf(
            "https://www.google.com/s2/favicons?sz=$TARGET_PX&domain=$host",
            "$scheme://$host/favicon.ico"
        ) else declaredIconUrls("$scheme://$host/") + listOf(
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
        if (best == null) failedAt[host] = System.currentTimeMillis()
        return best
    }

    // Small icons handed over by the WebView itself (onReceivedIcon): shown
    // instantly while the sharper network fetch is still running.
    private val quick = object : LruCache<String, Bitmap>(MAX_CACHE_ENTRIES) {}

    /** Memory-only, non-blocking: best icon already in hand for this page, or null. */
    fun peek(pageUrl: String): Bitmap? {
        val host = runCatching { URI(pageUrl).host }.getOrNull() ?: return null
        return cache.get(host) ?: quick.get(host)
    }

    /**
     * Drops everything remembered for [pageUrl]'s host (memory, disk, "failed" marker) so the next
     * [load] fetches fresh. Used when a shortcut moves to a new domain.
     */
    fun invalidate(pageUrl: String) {
        val host = runCatching { URI(pageUrl).host }.getOrNull() ?: return
        cache.remove(host)
        failedAt.remove(host)
        diskCacheDir?.let { File(it, diskFileName(host)).delete() }
    }

    /** Keeps [bitmap] (e.g. the page's own WebView favicon) as the icon for [pageUrl]'s host, in memory and on disk. */
    fun save(pageUrl: String, bitmap: Bitmap) {
        val host = runCatching { URI(pageUrl).host }.getOrNull() ?: return
        cache.put(host, bitmap)
        failedAt.remove(host)
        diskCacheDir?.let { dir ->
            runCatching { File(dir, diskFileName(host)).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        }
    }

    /** Stores the WebView-provided favicon for instant display. */
    fun putQuick(pageUrl: String, bitmap: Bitmap) {
        val host = runCatching { URI(pageUrl).host }.getOrNull() ?: return
        quick.put(host, bitmap)
    }

    /** Filesystem-safe cache filename for a host, e.g. "www.example.com" -> "www.example.com.png". */
    private fun diskFileName(host: String): String =
        host.replace(Regex("[^a-zA-Z0-9.-]"), "_") + ".png"

    /**
     * Icons the site itself declares in its home page (`<link rel="apple-touch-icon|icon" ...>`),
     * best first: touch icons before plain icons, larger `sizes` first. These are usually far
     * sharper than a guessed /favicon.ico or a 16px third-party lookup. SVGs are skipped.
     */
    private fun declaredIconUrls(homeUrl: String): List<String> {
        return try {
            val html = client.newCall(Request.Builder().url(homeUrl).build()).execute().use { r ->
                if (!r.isSuccessful) return emptyList()
                r.body?.source()?.let { src ->
                    src.request(120_000)
                    src.buffer.clone().readUtf8(minOf(src.buffer.size, 120_000L))
                }.orEmpty()
            }
            val base = URI(homeUrl)
            data class Cand(val url: String, val score: Int)
            Regex("""<link\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(html).mapNotNull { m ->
                val tag = m.value
                val rel = Regex("""rel\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)?.lowercase() ?: return@mapNotNull null
                if (!rel.contains("icon") || rel.contains("mask-icon")) return@mapNotNull null
                val href = Regex("""href\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1) ?: return@mapNotNull null
                if (href.substringBefore('?').endsWith(".svg", ignoreCase = true) || href.startsWith("data:")) return@mapNotNull null
                val size = Regex("""sizes\s*=\s*["'](\d+)x\d+""", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val resolved = runCatching { base.resolve(href).toString() }.getOrNull() ?: return@mapNotNull null
                Cand(resolved, (if (rel.contains("apple-touch")) 1000 else 0) + size)
            }.sortedByDescending { it.score }.map { it.url }.distinct().take(3).toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

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
