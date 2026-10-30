package com.invictus.xmd.repository

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import com.invictus.xmd.database.AppDatabase
import com.invictus.xmd.database.dao.HistoryDao
import com.invictus.xmd.database.entities.HistoryEntry

/** Browser tab visited-page history. Same simple Room-backed shape as BookmarkRepository. */
object HistoryRepository {

    private lateinit var dao: HistoryDao
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var entries: StateFlow<List<HistoryEntry>>
        private set

    fun init(context: Context) {
        if (::dao.isInitialized) return
        dao = AppDatabase.get(context).historyDao()
        // See BookmarkRepository for why WhileSubscribed(5000) here.
        // Collapse repeat visits (same page title on the same site, e.g. one
        // video opened many times with a different token URL each time) into
        // one row -- the newest. Also cleans up duplicates already stored.
        entries = dao.observeAll()
            .map { list -> list.distinctBy { dedupKey(it) } }
            .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    private fun hostOf(url: String): String =
        runCatching { android.net.Uri.parse(url).host }.getOrNull()
            ?.removePrefix("www.")
            .orEmpty()
            .ifBlank { url }

    /** Two entries with the same title on the same host count as the same page. */
    private fun dedupKey(e: HistoryEntry): String = hostOf(e.url) + "\u0000" + e.title

    fun record(url: String, title: String) {
        if (url.isBlank()) return
        scope.launch {
            runCatching {
                dao.deleteByUrl(url)
                dao.insert(HistoryEntry(id = UUID.randomUUID().toString(), url = url, title = title.ifBlank { url }))
            }
        }
    }

    fun remove(entry: HistoryEntry) {
        scope.launch {
            runCatching {
                // Delete every stored duplicate of this page too, otherwise an
                // older copy would pop back into the list after the delete.
                val key = dedupKey(entry)
                dao.getAll().filter { dedupKey(it) == key }.forEach { dao.delete(it) }
            }
        }
    }

    fun clearAll() {
        scope.launch { runCatching { dao.clearAll() } }
    }
}
