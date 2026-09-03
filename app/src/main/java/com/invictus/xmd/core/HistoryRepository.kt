package com.invictus.xmd.core

import android.content.Context
import com.invictus.xmd.core.db.AppDatabase
import com.invictus.xmd.core.db.HistoryDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

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
        entries = dao.observeAll()
            .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun record(url: String, title: String) {
        if (url.isBlank()) return
        scope.launch {
            runCatching {
                dao.insert(HistoryEntry(id = UUID.randomUUID().toString(), url = url, title = title.ifBlank { url }))
            }
        }
    }

    fun remove(entry: HistoryEntry) {
        scope.launch { runCatching { dao.delete(entry) } }
    }

    fun clearAll() {
        scope.launch { runCatching { dao.clearAll() } }
    }
}
