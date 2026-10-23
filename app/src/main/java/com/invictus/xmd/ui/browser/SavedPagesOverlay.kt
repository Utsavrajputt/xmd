@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.invictus.xmd.ui.browser

import android.widget.Toast
import com.invictus.xmd.ui.components.XmdAlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.invictus.xmd.R
import com.invictus.xmd.database.entities.Bookmark
import com.invictus.xmd.database.entities.HistoryEntry
import com.invictus.xmd.repository.BookmarkRepository
import com.invictus.xmd.repository.HistoryRepository
import com.invictus.xmd.ui.bookmarks.BookmarkScreen
import com.invictus.xmd.ui.components.WideDialogProperties
import com.invictus.xmd.ui.components.compactDialogWidth
import com.invictus.xmd.ui.history.HistoryScreen

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
            SavedPagesDestination.History -> SavedPagesListOverlay(
                entriesFlow = HistoryRepository.entries,
                titleRes = R.string.history_clear_all,
                clearAllToastRes = R.string.history_cleared_toast,
                getTitle = { it.title },
                getUrl = { it.url },
                onBack = onBack,
                onOpenUrl = onOpenUrl,
                onDelete = HistoryRepository::remove,
                onClearAll = HistoryRepository::clearAll,
                screen = { entries, query, onQueryChange, back, clearAll, tap, delete ->
                    HistoryScreen(entries, query, onQueryChange, back, clearAll, tap, delete)
                },
            )
            SavedPagesDestination.Bookmarks -> SavedPagesListOverlay(
                entriesFlow = BookmarkRepository.bookmarks,
                titleRes = R.string.bookmarks_clear_all,
                clearAllToastRes = R.string.bookmarks_cleared_toast,
                getTitle = { it.title },
                getUrl = { it.url },
                onBack = onBack,
                onOpenUrl = onOpenUrl,
                onDelete = BookmarkRepository::remove,
                onClearAll = BookmarkRepository::clearAll,
                screen = { entries, query, onQueryChange, back, clearAll, tap, delete ->
                    BookmarkScreen(entries, query, onQueryChange, back, clearAll, tap, delete)
                },
            )
        }
    }
}

@Composable
private fun <T> SavedPagesListOverlay(
    entriesFlow: kotlinx.coroutines.flow.StateFlow<List<T>>,
    titleRes: Int,
    clearAllToastRes: Int,
    getTitle: (T) -> String,
    getUrl: (T) -> String,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onDelete: (T) -> Unit,
    onClearAll: () -> Unit,
    screen: @Composable (
        entries: List<T>,
        query: String,
        onQueryChange: (String) -> Unit,
        onBack: () -> Unit,
        onClearAll: () -> Unit,
        onTap: (T) -> Unit,
        onDelete: (T) -> Unit,
    ) -> Unit,
) {
    val context = LocalContext.current
    val allEntries by entriesFlow.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var confirmingClearAll by remember { mutableStateOf(false) }
    val visibleEntries = remember(allEntries, query) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) {
            allEntries
        } else {
            allEntries.filter { entry ->
                getTitle(entry).contains(trimmedQuery, ignoreCase = true) ||
                    getUrl(entry).contains(trimmedQuery, ignoreCase = true)
            }
        }
    }

    screen(
        visibleEntries,
        query,
        { query = it },
        onBack,
        { confirmingClearAll = true },
        { entry -> onOpenUrl(getUrl(entry)) },
        onDelete,
    )

    if (confirmingClearAll) {
        XmdAlertDialog(
            onDismissRequest = { confirmingClearAll = false },
            modifier = Modifier.compactDialogWidth(),
            properties = WideDialogProperties,
            compact = true,
            title = { Text(stringResource(titleRes)) },
            confirmButton = {
                TextButton(shapes = androidx.compose.material3.ButtonDefaults.shapes(), 
                    onClick = {
                        confirmingClearAll = false
                        onClearAll()
                        Toast.makeText(context, clearAllToastRes, Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Text(stringResource(titleRes))
                }
            },
            dismissButton = {
                TextButton(shapes = androidx.compose.material3.ButtonDefaults.shapes(), onClick = { confirmingClearAll = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}