package com.invictus.xmd.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.invictus.xmd.R
import com.invictus.xmd.core.Bookmark

/** Bookmarks screen -- thin wrapper around [SavedPagesScreen] with the
 *  bookmark-specific icon/strings. Filtering (title/URL substring) happens
 *  in MainActivity's overlayNavHost NavHost route (Phase D; previously in
 *  BookmarkFragment, retired), this just renders. */
@Composable
fun BookmarkScreen(
    entries: List<Bookmark>,
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onClearAll: () -> Unit,
    onTap: (Bookmark) -> Unit,
    onDelete: (Bookmark) -> Unit,
) {
    SavedPagesScreen(
        title = stringResource(R.string.bookmarks_title),
        clearAllLabel = stringResource(R.string.bookmarks_clear_all),
        searchHint = stringResource(R.string.bookmarks_search_hint),
        emptyLabel = stringResource(R.string.bookmarks_empty),
        searchEmptyLabel = stringResource(R.string.bookmarks_search_empty),
        rowIcon = painterResource(XmdIcons.Bookmark),
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
