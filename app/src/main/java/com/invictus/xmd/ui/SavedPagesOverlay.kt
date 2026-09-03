package com.invictus.xmd.ui

import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.invictus.xmd.R
import com.invictus.xmd.core.Bookmark
import com.invictus.xmd.core.BookmarkRepository
import com.invictus.xmd.core.HistoryEntry
import com.invictus.xmd.core.HistoryRepository

internal enum class SavedPagesDestination {
    History,
    Bookmarks,
}

@Composable
internal fun SavedPagesOverlay(
    destination: SavedPagesDestination,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    key(destination) {
        when (destination) {
            SavedPagesDestination.History -> HistoryOverlay(onBack, onOpenUrl)
            SavedPagesDestination.Bookmarks -> BookmarksOverlay(onBack, onOpenUrl)
        }
    }
}

@Composable
private fun HistoryOverlay(
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val context = LocalContext.current
    val allEntries by HistoryRepository.entries.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var confirmingClearAll by remember { mutableStateOf(false) }
    val visibleEntries = remember(allEntries, query) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) {
            allEntries
        } else {
            allEntries.filter { entry ->
                entry.title.contains(trimmedQuery, ignoreCase = true) ||
                    entry.url.contains(trimmedQuery, ignoreCase = true)
            }
        }
    }

    HistoryScreen(
        entries = visibleEntries,
        query = query,
        onQueryChange = { query = it },
        onBack = onBack,
        onClearAll = { confirmingClearAll = true },
        onTap = { entry: HistoryEntry -> onOpenUrl(entry.url) },
        onDelete = HistoryRepository::remove,
    )

    if (confirmingClearAll) {
        AlertDialog(
            onDismissRequest = { confirmingClearAll = false },
            title = { Text(stringResource(R.string.history_clear_all)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingClearAll = false
                        HistoryRepository.clearAll()
                        Toast.makeText(context, R.string.history_cleared_toast, Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Text(stringResource(R.string.history_clear_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingClearAll = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun BookmarksOverlay(
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val context = LocalContext.current
    val allBookmarks by BookmarkRepository.bookmarks.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var confirmingClearAll by remember { mutableStateOf(false) }
    val visibleBookmarks = remember(allBookmarks, query) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) {
            allBookmarks
        } else {
            allBookmarks.filter { entry ->
                entry.title.contains(trimmedQuery, ignoreCase = true) ||
                    entry.url.contains(trimmedQuery, ignoreCase = true)
            }
        }
    }

    BookmarkScreen(
        entries = visibleBookmarks,
        query = query,
        onQueryChange = { query = it },
        onBack = onBack,
        onClearAll = { confirmingClearAll = true },
        onTap = { bookmark: Bookmark -> onOpenUrl(bookmark.url) },
        onDelete = BookmarkRepository::remove,
    )

    if (confirmingClearAll) {
        AlertDialog(
            onDismissRequest = { confirmingClearAll = false },
            title = { Text(stringResource(R.string.bookmarks_clear_all)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingClearAll = false
                        BookmarkRepository.clearAll()
                        Toast.makeText(context, R.string.bookmarks_cleared_toast, Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Text(stringResource(R.string.bookmarks_clear_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingClearAll = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}