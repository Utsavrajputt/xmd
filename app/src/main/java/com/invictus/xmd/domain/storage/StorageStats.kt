package com.invictus.xmd.domain.storage

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.invictus.xmd.domain.status.StatusSaverRepository
import com.invictus.xmd.preferences.Settings
import com.invictus.xmd.domain.download.PartialFileCleaner
import com.invictus.xmd.repository.QueueRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Where XMD's storage goes, in bytes. [Empty] = "not read yet" (a real reading
 * always has a non-zero [deviceTotalBytes]).
 */
data class StorageStats(
    val deviceTotalBytes: Long = 0L,
    val deviceFreeBytes: Long = 0L,
    val videosBytes: Long = 0L,
    val audioBytes: Long = 0L,
    val archivesAppsBytes: Long = 0L,
    val otherDownloadsBytes: Long = 0L,
    val statusSavedBytes: Long = 0L,
    val appDataBytes: Long = 0L,
    val networkCacheBytes: Long = 0L,
    val browserCacheBytes: Long = 0L,
    val temporaryBytes: Long = 0L,
    val leftoverPartialBytes: Long = 0L,
) {
    val downloadsBytes: Long get() = videosBytes + audioBytes + archivesAppsBytes + otherDownloadsBytes
    val totalCacheBytes: Long get() = networkCacheBytes + browserCacheBytes + temporaryBytes
    val appUsedBytes: Long get() = downloadsBytes + leftoverPartialBytes + statusSavedBytes + appDataBytes + totalCacheBytes

    companion object {
        val Empty = StorageStats()
    }
}

/** Reads and clears XMD's storage. Everything here is blocking IO, called off the main thread. */
object StorageStatsRepository {
    private const val NETWORK_CACHE_DIR = "browser_doh_cache"
    private const val TEMP_DIR = "torrent_meta"

    private val VIDEO = setOf("mp4", "mkv", "webm", "avi", "mov", "ts", "m4v", "3gp", "flv", "wmv", "m3u8")
    private val AUDIO = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "wma")
    private val ARCHIVE_APP = setOf("zip", "rar", "7z", "tar", "gz", "iso", "apk", "apks", "xapk", "apkm")

    /** Device-wide internal storage as (free, total); (0, 0) if the platform won't say. */
    fun deviceSpace(): Pair<Long, Long> = runCatching {
        val fs = StatFs(Environment.getExternalStorageDirectory().absolutePath)
        fs.availableBytes to fs.totalBytes
    }.getOrDefault(0L to 0L)

    suspend fun read(context: Context): StorageStats = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val (free, total) = deviceSpace()

        var videos = 0L
        var audio = 0L
        var archives = 0L
        var other = 0L
        val saveRoot = runCatching { File(Settings.defaultSaveLocation()) }.getOrNull()
        // Finished/abandoned downloads' leftovers (orphan .xmd.part + resume sidecars) are
        // reported on their own line instead of being lumped into "other downloads".
        val orphans = saveRoot?.let { findLeftovers(it) }.orEmpty()
        val orphanPaths = orphans.mapTo(HashSet()) { it.absolutePath }
        val leftoverBytes = orphans.sumOf { it.length() }
        saveRoot
            ?.takeIf { it.isDirectory }
            ?.walkTopDown()
            ?.filter { it.isFile && it.absolutePath !in orphanPaths }
            ?.forEach { file ->
                val len = file.length()
                when (file.extension.lowercase()) {
                    in VIDEO -> videos += len
                    in AUDIO -> audio += len
                    in ARCHIVE_APP -> archives += len
                    else -> other += len
                }
            }

        val statusSaved = runCatching {
            StatusSaverRepository.listSaved().sumOf { File(it.path).length() }
        }.getOrDefault(0L)

        val cacheRoot = app.cacheDir
        val network = dirSize(File(cacheRoot, NETWORK_CACHE_DIR))
        val temp = dirSize(File(cacheRoot, TEMP_DIR))
        val cacheTotal = dirSize(cacheRoot)
        val browser = (cacheTotal - network - temp).coerceAtLeast(0L)

        // Everything private to the app (DB, prefs, profile/wallpaper/shortcut files, WebView
        // storage...) except the caches counted above.
        val appData = (dirSize(app.dataDir) - cacheTotal).coerceAtLeast(0L)

        StorageStats(
            deviceTotalBytes = total.coerceAtLeast(1L),
            deviceFreeBytes = free,
            videosBytes = videos,
            audioBytes = audio,
            archivesAppsBytes = archives,
            otherDownloadsBytes = other,
            statusSavedBytes = statusSaved,
            appDataBytes = appData,
            networkCacheBytes = network,
            browserCacheBytes = browser,
            temporaryBytes = temp,
            leftoverPartialBytes = leftoverBytes,
        )
    }

    private fun findLeftovers(root: File): List<File> =
        runCatching { PartialFileCleaner.findOrphans(root, QueueRepository.current().map { it.id }) }
            .getOrDefault(emptyList())

    /** Deletes orphan partial-download files; returns the bytes freed. */
    suspend fun clearLeftoverPartials(): Long = withContext(Dispatchers.IO) {
        val root = runCatching { File(Settings.defaultSaveLocation()) }.getOrNull() ?: return@withContext 0L
        PartialFileCleaner.delete(findLeftovers(root))
    }

    /** Each returns the bytes freed. */
    suspend fun clearNetworkCache(context: Context): Long = withContext(Dispatchers.IO) {
        clearDir(File(context.cacheDir, NETWORK_CACHE_DIR))
    }

    suspend fun clearTemporary(context: Context): Long = withContext(Dispatchers.IO) {
        clearDir(File(context.cacheDir, TEMP_DIR))
    }

    suspend fun clearBrowserCache(context: Context): Long = withContext(Dispatchers.IO) {
        var freed = 0L
        context.cacheDir.listFiles()?.forEach { child ->
            if (child.name != NETWORK_CACHE_DIR && child.name != TEMP_DIR) {
                freed += dirSize(child)
                child.deleteRecursively()
            }
        }
        freed
    }

    suspend fun clearAllCaches(context: Context): Long =
        clearNetworkCache(context) + clearTemporary(context) + clearBrowserCache(context)

    private fun dirSize(file: File): Long = when {
        !file.exists() -> 0L
        file.isFile -> file.length()
        else -> file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /** Empties [dir] but keeps the directory itself (callers re-create caches lazily anyway). */
    private fun clearDir(dir: File): Long {
        val size = dirSize(dir)
        dir.listFiles()?.forEach { it.deleteRecursively() }
        return size
    }
}
