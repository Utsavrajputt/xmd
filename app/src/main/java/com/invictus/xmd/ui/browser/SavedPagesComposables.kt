@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.invictus.xmd.ui.browser

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.invictus.xmd.R
import com.invictus.xmd.ui.icons.AppIcon
import com.invictus.xmd.ui.icons.Icon
import com.invictus.xmd.ui.icons.Icons

private val GroupOuterCorner = 24.dp
private val GroupInnerCorner = 6.dp

/** Expressive "grouped list" shape: big radius on the outer edges of the first/last row, tight radius between rows. */
private fun groupedRowShape(index: Int, count: Int): RoundedCornerShape {
    val top = if (index == 0) GroupOuterCorner else GroupInnerCorner
    val bottom = if (index == count - 1) GroupOuterCorner else GroupInnerCorner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

private fun hostOf(url: String): String =
    runCatching { android.net.Uri.parse(url).host }.getOrNull()
        ?.removePrefix("www.")
        ?.takeIf { it.isNotBlank() }
        ?: url

/**
 * One row in a saved-pages list (Bookmarks or History): a rounded tonal card
 * (shape depends on its position in the group), icon badge, title + host,
 * delete button, and swipe-to-delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SavedPageRow(
    entry: T,
    title: String,
    url: String,
    icon: AppIcon,
    onTap: (T) -> Unit,
    onDelete: (T) -> Unit,
    shape: RoundedCornerShape = RoundedCornerShape(GroupOuterCorner),
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd || value == SwipeToDismissBoxValue.EndToStart) {
                onDelete(entry)
                true
            } else {
                false
            }
        },
    )

    val badgeShape = MaterialShapes.Cookie9Sided.toShape()
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.clip(shape),
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = when (dismissState.dismissDirection) {
                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                    else -> Alignment.CenterEnd
                },
            ) {
                Icon(
                    imageVector = Icons.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        Surface(
            onClick = { onTap(entry) },
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, badgeShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 14.dp, end = 4.dp),
                ) {
                    Text(
                        text = title.ifBlank { hostOf(url) },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = hostOf(url),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                IconButton(shapes = androidx.compose.material3.IconButtonDefaults.shapes(), onClick = { onDelete(entry) }) {
                    Icon(
                        imageVector = Icons.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Full screen shared by Bookmarks and History: rounded header (back + title,
 * with Search and Clear all on the right -- Search sits to the left of Clear
 * all and expands in place of the title), grouped list, empty state.
 * [T] is [com.invictus.xmd.database.entities.Bookmark] or
 * [com.invictus.xmd.database.entities.HistoryEntry].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SavedPagesScreen(
    title: String,
    clearAllLabel: String,
    searchHint: String,
    emptyLabel: String,
    searchEmptyLabel: String,
    rowIcon: AppIcon,
    entries: List<T>,
    entryKey: (T) -> Any,
    entryTitle: (T) -> String,
    entryUrl: (T) -> String,
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onClearAll: () -> Unit,
    onTap: (T) -> Unit,
    onDelete: (T) -> Unit,
) {
    var searching by rememberSaveable { mutableStateOf(query.isNotEmpty()) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(searching) {
        if (searching) runCatching { focusRequester.requestFocus() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(com.invictus.xmd.ui.theme.wallpaperAwareBackgroundColor()),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
        ) {
            TopAppBar(
                title = {
                    AnimatedContent(
                        targetState = searching,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "savedPagesTitle",
                    ) { isSearching ->
                        if (isSearching) {
                            TextField(
                                value = query,
                                onValueChange = onQueryChange,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                                placeholder = { Text(searchHint, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                singleLine = true,
                                shape = CircleShape,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent,
                                ),
                            )
                        } else {
                            Text(text = title, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                },
                // This screen already sits inside MainShell's Scaffold content,
                // which reserves the status-bar inset for us.
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    IconButton(shapes = androidx.compose.material3.IconButtonDefaults.shapes(), onClick = onBack) {
                        Icon(
                            imageVector = Icons.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    // Search sits immediately to the LEFT of Clear all.
                    IconButton(shapes = androidx.compose.material3.IconButtonDefaults.shapes(), 
                        onClick = {
                            if (searching) {
                                onQueryChange("")
                                searching = false
                            } else {
                                searching = true
                            }
                        },
                    ) {
                        Icon(
                            imageVector = if (searching) Icons.Close else Icons.Search,
                            contentDescription = searchHint,
                        )
                    }
                    TextButton(shapes = androidx.compose.material3.ButtonDefaults.shapes(), onClick = onClearAll) {
                        Text(clearAllLabel)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }

        if (entries.isEmpty()) {
            val emptyShape = MaterialShapes.Cookie12Sided.toShape()
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, emptyShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (query.isEmpty()) rowIcon else Icons.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(44.dp),
                    )
                }
                Text(
                    text = if (query.isEmpty()) emptyLabel else searchEmptyLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                itemsIndexed(entries, key = { _, e -> entryKey(e) }) { index, entry ->
                    SavedPageRow(
                        entry = entry,
                        title = entryTitle(entry),
                        url = entryUrl(entry),
                        icon = rowIcon,
                        onTap = onTap,
                        onDelete = onDelete,
                        shape = groupedRowShape(index, entries.size),
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}
