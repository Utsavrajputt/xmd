package com.invictus.xmd.core

import android.content.Context
import android.os.Environment
import androidx.lifecycle.LiveData
import com.invictus.xmd.core.db.AppDatabase
import com.invictus.xmd.core.db.BookmarkDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.util.UUID

/**
 * Speed-dial tiles shown on the Browser tab's new-tab page. Unlike
 * QueueRepository, there's no in-flight/background-writer state to race
 * against here -- reads/writes are just simple CRUD against Room, so the
 * DAO's own LiveData query is exposed directly instead of hand-rolling a
 * synchronized master list.
 *
 * The app no longer ships with any preloaded shortcuts -- a fresh install
 * starts at zero, and every entry comes from the user either adding one by
 * hand (add()) or importing an xmdweb source pack (importWebsites()) via
 * Settings -> Import Websites, which scans Downloads for matching files
 * and lets the user pick one.
 */
object BookmarkRepository {

    private lateinit var dao: BookmarkDao
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var bookmarks: LiveData<List<Bookmark>>
        private set

    fun init(context: Context) {
        if (::dao.isInitialized) return
        dao = AppDatabase.get(context).bookmarkDao()
        bookmarks = dao.observeAll()
    }

    // ── Website source pack import ──────────────────────────────────────

    data class WebImportResult(val imported: Int, val skipped: Int, val total: Int)

    private data class WebSiteEntry(
        val name: String,
        val url: String,
        val icon: String?,
        val category: String?
    )

    /**
     * Recursively scans a fixed set of likely folders (and their
     * subfolders) for any xmdweb source-pack file -- matched by filename
     * only (case/separator-insensitive: "xmd_web.json", "XmdWeb (1).json",
     * "xmdweb-movies.json" all match). Scanned folders: Downloads, this
     * app's own "Xmd" download folder, and the WhatsApp/WhatsApp Business
     * "Documents" subfolder specifically (not Images/Video/Audio/Statuses
     * -- a JSON pack only ever lands there), covering both the legacy and
     * scoped-storage paths since which one exists depends on Android
     * version. Newest first. The caller (Settings -> Import Websites) lists results and lets the user pick
     * one -- no auto-popup, no system file picker.
     */
    fun findImportCandidates(): List<File> {
        val results = mutableListOf<File>()
        importScanRoots().forEach { root -> scanForCandidates(root, results, depth = 0) }
        return results.distinctBy { it.canonicalPath }.sortedByDescending { it.lastModified() }
    }

    private fun importScanRoots(): List<File> {
        val storageRoot = Environment.getExternalStorageDirectory()
        return listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            File(storageRoot, "Xmd"),
            File(storageRoot, "WhatsApp/Media/WhatsApp Documents"),
            File(storageRoot, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents"),
            File(storageRoot, "WhatsApp Business/Media/WhatsApp Business Documents"),
            File(storageRoot, "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Documents")
        ).filter { it.isDirectory }
    }

    private const val MAX_SCAN_DEPTH = 15

    private fun scanForCandidates(dir: File, out: MutableList<File>, depth: Int) {
        if (depth > MAX_SCAN_DEPTH) return
        val entries = runCatching { dir.listFiles() }.getOrNull() ?: return
        for (entry in entries) {
            when {
                entry.isDirectory -> {
                    if (entry.name.startsWith(".")) continue
                    scanForCandidates(entry, out, depth + 1)
                }
                entry.isFile && matchesImportFileName(entry.name) -> out += entry
            }
        }
    }

    private fun matchesImportFileName(name: String): Boolean {
        if (!name.endsWith(".json", ignoreCase = true)) return false
        val normalized = name.lowercase().replace(Regex("[_\\-\\s]"), "")
        return normalized.contains("xmdweb")
    }

    /**
     * Parses and merges websites from an xmd_web.json source pack.
     * Duplicates are skipped by exact URL match against what's already
     * saved -- an existing bookmark's title/icon/sortOrder is left
     * untouched even if the pack lists a different name for the same URL.
     *
     * Accepts either `{"websites": [...]}` or a bare `[...]` array at the
     * root. Each entry needs "name" and "url" at minimum; "icon" maps to
     * the favicon shown on the tile. "category" (and any other field) is
     * accepted for forward-compatibility but not persisted yet -- Bookmark
     * has no category column today.
     */
    suspend fun importWebsites(file: File): WebImportResult = withContext(Dispatchers.IO) {
        val entries = runCatching { parseWebJson(file.readText()) }.getOrDefault(emptyList())
        if (entries.isEmpty()) return@withContext WebImportResult(0, 0, 0)

        val existing = runCatching { dao.getAll() }.getOrDefault(emptyList())
        val existingUrls = existing.map { it.url }.toMutableSet()
        var nextOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1

        var imported = 0
        entries.forEach { entry ->
            if (entry.url in existingUrls) return@forEach
            runCatching {
                dao.upsert(
                    Bookmark(
                        id = UUID.randomUUID().toString(),
                        title = entry.name.ifBlank { hostOf(entry.url) },
                        url = entry.url,
                        faviconUrl = entry.icon,
                        sortOrder = nextOrder
                    )
                )
            }.onSuccess {
                existingUrls += entry.url
                nextOrder++
                imported++
            }
        }
        WebImportResult(imported = imported, skipped = entries.size - imported, total = entries.size)
    }

    private fun parseWebJson(json: String): List<WebSiteEntry> {
        val root = JSONTokener(json).nextValue()
        val array: JSONArray = when (root) {
            is JSONArray -> root
            is JSONObject -> root.optJSONArray("websites") ?: JSONArray()
            else -> JSONArray()
        }
        return (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            val url = obj.optString("url").trim()
            if (url.isBlank()) return@mapNotNull null
            WebSiteEntry(
                name = obj.optStringOrNull("name", "title") ?: "",
                url = url,
                icon = obj.optStringOrNull("icon", "faviconUrl"),
                category = obj.optStringOrNull("category")
            )
        }
    }

    private fun JSONObject.optStringOrNull(vararg keys: String): String? {
        for (key in keys) {
            val value = optString(key)
            if (value.isNotBlank()) return value
        }
        return null
    }

    fun add(title: String, url: String) {
        scope.launch {
            val nextOrder = (runCatching { dao.getAll() }.getOrDefault(emptyList())
                .maxOfOrNull { it.sortOrder } ?: -1) + 1
            runCatching {
                dao.upsert(
                    Bookmark(
                        id = UUID.randomUUID().toString(),
                        title = title.ifBlank { hostOf(url) },
                        url = url,
                        sortOrder = nextOrder
                    )
                )
            }
        }
    }

    fun remove(bookmark: Bookmark) {
        scope.launch { runCatching { dao.delete(bookmark) } }
    }

    fun rename(bookmark: Bookmark, newTitle: String) {
        scope.launch {
            runCatching { dao.upsert(bookmark.copy(title = newTitle.ifBlank { hostOf(bookmark.url) })) }
        }
    }

    private fun hostOf(url: String): String =
        runCatching { java.net.URI(url).host }.getOrNull() ?: url
}
