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
        // Eagerly (not WhileSubscribed): the address bar reads entries.value
        // directly for history suggestions without collecting the flow, and
        // a WhileSubscribed StateFlow keeps returning its empty initial value
        // until something collects it -- which made history suggestions
        // randomly missing.
        // Collapse repeat visits (same page title on the same site, e.g. one
        // video opened many times with a different token URL each time) into
        // one row -- the newest. Also cleans up duplicates already stored.
        entries = dao.observeAll()
            .map { list ->
                // newest-first list: the first row of each group is the newest; visit counts add up.
                list.groupBy { dedupKey(it) }.values
                    .map { g -> g.first().copy(visitCount = g.sumOf { it.visitCount }) }
            }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())
    }

    private fun hostOf(url: String): String =
        runCatching { android.net.Uri.parse(url).host }.getOrNull()
            ?.removePrefix("www.")
            .orEmpty()
            .ifBlank { url }

    /** Two entries with the same title on the same host count as the same page. */
    private fun dedupKey(e: HistoryEntry): String = hostOf(e.url) + "\u0000" + e.title

    /**
     * Address-bar history matching. Every whitespace-separated word of
     * [query] must appear in the title or URL (so "avengers 4k" still finds
     * "Avengers Endgame 4K - site"). Pages whose address starts with the
     * query are ranked first, the rest keep newest-first order.
     */
    fun search(query: String, limit: Int): List<HistoryEntry> {
        if (!::entries.isInitialized) return emptyList()
        val q = query.trim().lowercase()
        val tokens = q.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        return entries.value.asSequence()
            .filter { e ->
                val t = e.title.lowercase()
                val u = e.url.lowercase()
                tokens.all { t.contains(it) || u.contains(it) }
            }
            .distinctBy { it.url }
            .sortedBy { e -> if (stripScheme(e.url.lowercase()).startsWith(q)) 0 else 1 }
            .take(limit)
            .toList()
    }

    private fun stripScheme(url: String): String =
        url.removePrefix("https://").removePrefix("http://").removePrefix("www.")

    fun record(url: String, title: String) {
        if (url.isBlank()) return
        scope.launch {
            runCatching {
                val visits = dao.visitCountFor(url) + 1
                dao.deleteByUrl(url)
                dao.insert(
                    HistoryEntry(
                        id = UUID.randomUUID().toString(),
                        url = url,
                        title = title.ifBlank { url },
                        visitCount = visits,
                    )
                )
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
