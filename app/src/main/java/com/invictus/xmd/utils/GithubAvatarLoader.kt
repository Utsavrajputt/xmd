package com.invictus.xmd.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Fetches a GitHub user's avatar for the About screen's Developers list.
 * Same no-third-party-library approach as [FaviconLoader] -- a direct
 * OkHttp GET + BitmapFactory decode, backed by two cache layers so
 * re-composing (or relaunching) the Developers list doesn't refetch the
 * same avatar:
 *  - an in-memory LRU for the current process
 *  - a 72-hour-old disk cache (see [init]) so a fresh process still hits
 *    disk instead of the network -- this used to be memory-only, so every
 *    cold app start (LruCache wiped with the process) redownloaded every
 *    developer's avatar from scratch, even right after it was last shown.
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
    private const val DISK_CACHE_MAX_AGE_MS = 72 * 60 * 60 * 1000L

    private val cache = object : LruCache<String, Bitmap>(MAX_CACHE_ENTRIES) {}

    private var diskCacheDir: File? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    /** Call once from FfApp.onCreate; harmless if called again. */
    fun init(context: Context) {
        if (diskCacheDir != null) return
        diskCacheDir = File(context.applicationContext.cacheDir, "github_avatars").apply { mkdirs() }
    }

    /** Blocking; call from a background thread/coroutine, never the main thread. */
    fun load(githubUsername: String): Bitmap? {
        val username = githubUsername.trim().removePrefix("@")
        if (username.isEmpty()) return null
        cache.get(username)?.let { return it }

        val diskFile = diskCacheDir?.let { File(it, diskFileName(username)) }
        if (diskFile != null && diskFile.exists() &&
            System.currentTimeMillis() - diskFile.lastModified() < DISK_CACHE_MAX_AGE_MS
        ) {
            BitmapFactory.decodeFile(diskFile.path)?.let { bitmap ->
                cache.put(username, bitmap)
                return bitmap
            }
        }

        val bitmap = fetch("https://github.com/$username.png?size=$TARGET_PX") ?: return null
        cache.put(username, bitmap)
        // File.lastModified() doubles as the "fetched at" timestamp --
        // overwriting it here is what makes the next cold start's
        // staleness check above work with no separate timestamp store.
        diskFile?.let { file ->
            runCatching {
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        return bitmap
    }

    /** Filesystem-safe cache filename for a GitHub username, e.g. "Arnab11" -> "Arnab11.png". */
    private fun diskFileName(username: String): String =
        username.replace(Regex("[^a-zA-Z0-9.-]"), "_") + ".png"

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
