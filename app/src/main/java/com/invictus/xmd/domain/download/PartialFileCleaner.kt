package com.invictus.xmd.domain.download

import android.content.Context
import com.invictus.xmd.database.entities.QueueItem
import com.invictus.xmd.domain.torrent.TorrentPaths
import com.invictus.xmd.utils.LinkParser
import com.invictus.xmd.utils.storage.FileNameUtils
import java.io.File

/**
 * Removes the working files a direct download leaves next to its target:
 * the incomplete part file (`<name>.dl-<id>.xmd.part`) and the multi-connection resume
 * sidecars written beside it (`.xmdparts`, its `.bak` backup and the `.tmp` used while
 * writing). Everything here is blocking IO -- call it off the main thread.
 */
object PartialFileCleaner {
    private val SIDECAR_SUFFIXES = listOf(".xmdparts", ".xmdparts.bak", ".xmdparts.tmp")
    private val PART_ID = Regex("""\.dl-(\d+)\.xmd\.part$""")

    /** Files younger than this are never treated as leftovers (a download may just have created them). */
    private const val MIN_AGE_MS = 2 * 60 * 1000L

    private var cacheDir: File? = null

    /** Needed to find yt-dlp's temp folders (they live in the app cache). Called from Application.onCreate. */
    fun init(context: Context) {
        cacheDir = context.applicationContext.cacheDir
    }

    /** Same derivation DownloadService uses to name an item's part file. */
    fun partIdFor(itemId: String): Long =
        (itemId.hashCode().toLong() and 0xffffffffL).let { if (it == 0L) 1L else it }

    /** Deletes every resume sidecar that belongs to [part] (the part file itself is untouched). */
    fun deleteSidecars(part: File) {
        SIDECAR_SUFFIXES.forEach { suffix ->
            runCatching { File(part.parentFile, part.name + suffix).delete() }
        }
    }

    /** Deletes [part] and all of its sidecars. */
    fun deletePartWithSidecars(part: File) {
        runCatching { part.delete() }
        deleteSidecars(part)
    }

    /**
     * Cleans up after an item that is being abandoned (cancelled with no live engine,
     * or its row removed from the list). Completed items are left alone.
     */
    fun cleanupItem(item: QueueItem) {
        if (item.status == ItemStatus.DONE) return
        when {
            item.platform == MediaPlatform.YOUTUBE -> cleanupYtDlp(item)
            LinkParser.isTorrentLink(item.sourceUrl) -> cleanupTorrent(item)
            else -> cleanupDirect(item)
        }
    }

    /** yt-dlp downloads into its own per-item temp folder in the app cache (see YtDlpManager). */
    private fun cleanupYtDlp(item: QueueItem) {
        val root = cacheDir ?: return
        runCatching { File(root, "ytdlp/${item.id}").deleteRecursively() }
    }

    /**
     * A cancelled/failed torrent leaves its (partly filled) content at `<save dir>/<torrent name>`.
     * That is only deleted when it is certain to belong to this item: the download must have started,
     * the entry must have been written to since (so a complete copy from an earlier download of the
     * same torrent is never touched), and it must sit strictly inside the save dir.
     */
    private fun cleanupTorrent(item: QueueItem) {
        val name = item.fileName?.takeIf { it.isNotBlank() } ?: return
        if (item.downloadStartedAtMs <= 0L) return
        runCatching {
            val baseDir = TorrentPaths.resolveBaseDir(item.customSaveDirPath, name).canonicalFile
            val target = File(baseDir, name).canonicalFile
            if (target.parentFile?.canonicalPath != baseDir.canonicalPath) return // name had path parts / escaped
            if (!target.exists()) return
            if (target.lastModified() < item.downloadStartedAtMs - 1_000L) return // older than this download
            target.deleteRecursively()
        }
    }

    private fun cleanupDirect(item: QueueItem) {
        val name = item.fileName?.takeIf { it.isNotBlank() } ?: return
        runCatching {
            val dir = FileNameUtils.resolveDestinationFolder(item.customSaveDirPath, item.category, name, item.sourceUrl)
            val part = IncompleteFileUtil.addIncompleteIndicator(File(dir, name), partIdFor(item.id))
            deletePartWithSidecars(part)
        }
    }

    /**
     * Leftovers under [root] that no queue item can ever resume:
     *  - a part file whose id matches no item in [liveItemIds] (only when the queue is known, i.e. non-empty);
     *  - a sidecar whose part file is gone (finished/cancelled download) or orphaned as above.
     */
    fun findOrphans(root: File, liveItemIds: Collection<String>): List<File> {
        if (!root.isDirectory) return emptyList()
        val queueKnown = liveItemIds.isNotEmpty()
        val liveIds = liveItemIds.mapTo(HashSet()) { partIdFor(it) }
        val now = System.currentTimeMillis()
        val orphans = ArrayList<File>()
        val orphanParts = HashSet<String>()

        val all = root.walkTopDown().filter { it.isFile }.toList()
        for (f in all) {
            if (now - f.lastModified() < MIN_AGE_MS) continue
            val m = PART_ID.find(f.name) ?: continue
            val id = m.groupValues[1].toLongOrNull() ?: continue
            if (queueKnown && id !in liveIds) {
                orphans += f
                orphanParts += f.absolutePath
            }
        }
        for (f in all) {
            if (now - f.lastModified() < MIN_AGE_MS) continue
            val suffix = SIDECAR_SUFFIXES.firstOrNull { f.name.endsWith(it) } ?: continue
            val companion = File(f.parentFile, f.name.removeSuffix(suffix))
            if (!companion.isFile || companion.absolutePath in orphanParts) orphans += f
        }
        return orphans
    }

    /** Deletes [files] (as returned by [findOrphans]); returns the bytes freed. */
    fun delete(files: List<File>): Long {
        var freed = 0L
        files.forEach { f ->
            val len = f.length()
            if (runCatching { f.delete() }.getOrDefault(false)) freed += len
        }
        return freed
    }
}
