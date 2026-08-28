package com.invictus.xmd.core

import android.content.Context
import androidx.lifecycle.LiveData
import com.invictus.xmd.core.db.AppDatabase
import com.invictus.xmd.core.db.BookmarkDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Real bookmarks -- pages the user starred in the Browser toolbar, listed
 * on their own Bookmarks screen (most-recent first). Same simple
 * Room-backed shape as HistoryRepository. Not to be confused with
 * [ShortcutRepository], which backs the speed-dial tiles on the new-tab
 * page; adding a bookmark can optionally also add a shortcut, but the two
 * are stored and managed independently.
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

    fun add(title: String, url: String) {
        scope.launch {
            runCatching {
                dao.upsert(
                    Bookmark(
                        id = UUID.randomUUID().toString(),
                        title = title.ifBlank { hostOf(url) },
                        url = url
                    )
                )
            }
        }
    }

    fun remove(bookmark: Bookmark) {
        scope.launch { runCatching { dao.delete(bookmark) } }
    }

    fun clearAll() {
        scope.launch { runCatching { dao.clearAll() } }
    }

    private fun hostOf(url: String): String =
        runCatching { java.net.URI(url).host }.getOrNull() ?: url
}
