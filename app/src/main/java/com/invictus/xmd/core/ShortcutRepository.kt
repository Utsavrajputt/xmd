package com.invictus.xmd.core

import android.content.Context
import android.os.Environment
import androidx.lifecycle.LiveData
import com.invictus.xmd.core.db.AppDatabase
import com.invictus.xmd.core.db.ShortcutDao
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
 * Speed-dial tiles ("Shortcuts") shown on the Browser tab's new-tab page.
 * Unlike QueueRepository, there's no in-flight/background-writer state to race
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
object ShortcutRepository {

    private lateinit var dao: ShortcutDao
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var shortcuts: LiveData<List<Shortcut>>
        private set

    fun init(context: Context) {
        if (::dao.isInitialized) return
        dao = AppDatabase.get(context).shortcutDao()
        shortcuts = dao.observeAll()
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
     * saved -- an existing shortcut's title/icon/sortOrder is left
     * untouched even if the pack lists a different name for the same URL.
     *
     * Accepts either `{"websites": [...]}` or a bare `[...]` array at the
     * root. Each entry needs "name" and "url" at minimum; "icon" maps to
     * the favicon shown on the tile. "category" (and any other field) is
     * accepted for forward-compatibility but not persisted yet -- Shortcut
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
                    Shortcut(
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

    /**
     * Serializes all saved shortcuts back into the same xmdweb source-pack
     * shape importWebsites() reads (`{"websites": [...]}`) -- so a file
     * exported here can be re-imported on this device or shared to
     * another one via Settings -> Import Websites, sortOrder preserved.
     */
    suspend fun exportWebsitesJson(): String = withContext(Dispatchers.IO) {
        val all = runCatching { dao.getAll() }.getOrDefault(emptyList()).sortedBy { it.sortOrder }
        val array = JSONArray()
        all.forEach { shortcut ->
            val obj = JSONObject()
            obj.put("name", shortcut.title)
            obj.put("url", shortcut.url)
            shortcut.faviconUrl?.let { obj.put("icon", it) }
            array.put(obj)
        }
        JSONObject().put("websites", array).toString(2)
    }

    suspend fun count(): Int = withContext(Dispatchers.IO) {
        runCatching { dao.getAll() }.getOrDefault(emptyList()).size
    }

    fun add(title: String, url: String) {
        scope.launch {
            val nextOrder = (runCatching { dao.getAll() }.getOrDefault(emptyList())
                .maxOfOrNull { it.sortOrder } ?: -1) + 1
            runCatching {
                dao.upsert(
                    Shortcut(
                        id = UUID.randomUUID().toString(),
                        title = title.ifBlank { hostOf(url) },
                        url = url,
                        sortOrder = nextOrder
                    )
                )
            }
        }
    }

    /**
     * Like [add], but for the case where the user also picked a custom icon
     * in the add-shortcut dialog: generates the id up front so the picked
     * image can be copied into place and attached to the very same insert,
     * instead of racing the async [add] + a follow-up lookup by URL.
     */
    suspend fun addWithIcon(context: Context, title: String, url: String, iconUri: android.net.Uri?) {
        withContext(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            val iconPath = iconUri?.let { copyIconToInternalStorage(context, it, id) }
            val nextOrder = (runCatching { dao.getAll() }.getOrDefault(emptyList())
                .maxOfOrNull { it.sortOrder } ?: -1) + 1
            runCatching {
                dao.upsert(
                    Shortcut(
                        id = id,
                        title = title.ifBlank { hostOf(url) },
                        url = url,
                        sortOrder = nextOrder,
                        customIconPath = iconPath
                    )
                )
            }
        }
    }

    fun remove(shortcut: Shortcut) {
        scope.launch { runCatching { dao.delete(shortcut) } }
    }

    fun rename(shortcut: Shortcut, newTitle: String) {
        scope.launch {
            runCatching { dao.upsert(shortcut.copy(title = newTitle.ifBlank { hostOf(shortcut.url) })) }
        }
    }

    /**
     * Saves edits to an existing shortcut in place -- preserves [Shortcut.id]
     * and [Shortcut.sortOrder] (and anything else the caller didn't touch),
     * unlike the old edit flow which did remove()+add() and silently reset
     * the tile to the end of the grid every time you edited it.
     */
    fun update(shortcut: Shortcut) {
        scope.launch { runCatching { dao.upsert(shortcut) } }
    }

    /**
     * Persists a new tile order after a drag-reorder session. [orderedIds]
     * is the full, final top-to-bottom/left-to-right id sequence; only
     * called once, when the user taps "Done" -- dragging itself just
     * reorders the adapter's in-memory list.
     */
    fun reorder(orderedIds: List<String>) {
        scope.launch {
            runCatching {
                val byId = dao.getAll().associateBy { it.id }
                orderedIds.forEachIndexed { index, id ->
                    val existing = byId[id] ?: return@forEachIndexed
                    if (existing.sortOrder != index) {
                        dao.upsert(existing.copy(sortOrder = index))
                    }
                }
            }
        }
    }

    /**
     * Copies a user-picked icon image (from a content:// Uri, e.g. the
     * system photo picker) into this app's private files dir so it survives
     * independent of the source app/gallery. Returns the new file's absolute
     * path, or null if the copy failed. Old custom icon files aren't
     * auto-deleted here -- callers that replace/clear a shortcut's icon
     * should remove the previous file themselves if they track it.
     */
    suspend fun copyIconToInternalStorage(context: Context, sourceUri: android.net.Uri, shortcutId: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.filesDir, "shortcut_icons").apply { mkdirs() }
                val dest = File(dir, "$shortcutId.png")
                val input = context.contentResolver.openInputStream(sourceUri) ?: return@runCatching null
                val bitmap = input.use { android.graphics.BitmapFactory.decodeStream(it) }
                    ?: return@runCatching null
                dest.outputStream().use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                }
                dest.absolutePath
            }.getOrNull()
        }

    private fun hostOf(url: String): String =
        runCatching { java.net.URI(url).host }.getOrNull() ?: url
}
