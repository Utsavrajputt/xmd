package com.invictus.xmd.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.invictus.xmd.R
import com.invictus.xmd.core.HistoryEntry

/** History screen -- thin wrapper around [SavedPagesScreen] with the
 *  history-specific icon/strings. Filtering (title/URL substring) happens
 *  in MainActivity's overlayNavHost NavHost route (Phase D; previously in
 *  HistoryFragment, retired), this just renders. */
@Composable
fun HistoryScreen(
    entries: List<HistoryEntry>,
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onClearAll: () -> Unit,
    onTap: (HistoryEntry) -> Unit,
    onDelete: (HistoryEntry) -> Unit,
) {
    SavedPagesScreen(
        title = stringResource(R.string.history_title),
        clearAllLabel = stringResource(R.string.history_clear_all),
        searchHint = stringResource(R.string.history_search_hint),
        emptyLabel = stringResource(R.string.history_empty),
        searchEmptyLabel = stringResource(R.string.history_search_empty),
        rowIcon = painterResource(R.drawable.ic_link),
        entries = entries,
        entryKey = { it.id },
        entryTitle = { it.title },
        entryUrl = { it.url },
        query = query,
        onQueryChange = onQueryChange,
        onBack = onBack,
        onClearAll = onClearAll,
        onTap = onTap,
        onDelete = onDelete,
    )
}
