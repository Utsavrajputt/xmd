@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.invictus.xmd.ui.browser

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.invictus.xmd.ui.components.xmdTopBarColors
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import com.invictus.xmd.ui.components.XmdTopBar
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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
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
import androidx.compose.foundation.Image
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import com.invictus.xmd.utils.FaviconLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val GroupOuterCorner = 20.dp
private val GroupInnerCorner = 4.dp

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

private sealed interface DayListItem {
    data class Header(val dayStartMs: Long, val label: String) : DayListItem
    data class Row<T>(val entry: T, val indexInDay: Int, val countInDay: Int) : DayListItem
}

private fun dayStartOf(ms: Long): Long =
    java.util.Calendar.getInstance().apply {
        timeInMillis = ms
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis

/** Groups [entries] (already newest-first) by local calendar day, Chrome-style. */
private fun <T> buildDayGroupedItems(
    entries: List<T>,
    timeOf: (T) -> Long,
    todayLabel: String,
    yesterdayLabel: String,
): List<DayListItem> {
    val todayStart = dayStartOf(System.currentTimeMillis())
    val yesterdayStart = dayStartOf(todayStart - 1)
    val dateFmt = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault())
    val dayFmt = java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault())
    val out = ArrayList<DayListItem>()
    entries.groupBy { dayStartOf(timeOf(it)) }.forEach { (dayStart, group) ->
        val date = java.util.Date(dayStart)
        val label = when (dayStart) {
            todayStart -> "$todayLabel - ${dateFmt.format(date)}"
            yesterdayStart -> "$yesterdayLabel - ${dateFmt.format(date)}"
            else -> "${dayFmt.format(date)} - ${dateFmt.format(date)}"
        }
        out.add(DayListItem.Header(dayStart, label))
        group.forEachIndexed { i, e -> out.add(DayListItem.Row(e, i, group.size)) }
    }
    return out
}

private val LetterTileColors = listOf(
    Color(0xFFC62828), Color(0xFF6A1B9A), Color(0xFF283593), Color(0xFF00695C),
    Color(0xFF2E7D32), Color(0xFFEF6C00), Color(0xFF4E342E), Color(0xFF757575),
)

/** Stable colour per host, so the same site always gets the same tile. */
private fun letterTileColor(host: String): Color =
    LetterTileColors[Math.floorMod(host.lowercase().hashCode(), LetterTileColors.size)]

/**
 * One row in a saved-pages list (Bookmarks or History): a rounded tonal card
 (Material 3 Expressive SegmentedListItem, shape depends on its position in the group), icon badge, title + host,
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
    showFavicon: Boolean = false,
    index: Int = 0,
    count: Int = 1,
    modifier: Modifier = Modifier,
) {
    val shape = groupedRowShape(index, count)
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
                    .background(
                        if (dismissState.dismissDirection == SwipeToDismissBoxValue.Settled) {
                            Color.Transparent
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                    )
                    .padding(horizontal = 24.dp),
                contentAlignment = when (dismissState.dismissDirection) {
                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                    else -> Alignment.CenterEnd
                },
            ) {
                if (dismissState.dismissDirection != SwipeToDismissBoxValue.Settled) {
                    Icon(
                        imageVector = Icons.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        },
    ) {
        SegmentedListItem(
            onClick = { onTap(entry) },
            shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
            colors = ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            leadingContent = {
                val favicon = if (showFavicon) {
                    produceState<android.graphics.Bitmap?>(initialValue = null, key1 = url) {
                        value = withContext(Dispatchers.IO) { FaviconLoader.load(url) }
                    }.value
                } else null
                if (favicon != null) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color.White, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            bitmap = favicon.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                } else if (showFavicon) {
                    // No favicon for this site: Chrome-style letter tile (first
                    // letter of the host on a colour picked from the host name).
                    val host = hostOf(url)
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(letterTileColor(host), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = host.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?",
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, badgeShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            },
            trailingContent = {
                IconButton(modifier = Modifier.size(40.dp), onClick = { onDelete(entry) }) {
                    Icon(
                        imageVector = Icons.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
        ) {
            val host = hostOf(url)
            val shownTitle = title.ifBlank { host }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = shownTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (shownTitle != host) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = host,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 96.dp),
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
    showFavicons: Boolean = false,
    // When set, the list gets Chrome-style day headers ("Today - 30 Oct 2026").
    entryTimeMs: ((T) -> Long)? = null,
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
            // Opaque + touch-blocking: the browser / speed dial underneath must not
            // show through (or receive taps) behind the History/Bookmarks header.
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        XmdTopBar(
            colors = xmdTopBarColors(MaterialTheme.colorScheme.surfaceContainerLow),
            title = {
                val titleFade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                AnimatedContent(
                    targetState = searching,
                    transitionSpec = { fadeIn(titleFade) togetherWith fadeOut(titleFade) },
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
                            textStyle = MaterialTheme.typography.bodyLarge,
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
                        Text(text = title)
                    }
                }
            },
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
        )


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
            val todayLabel = stringResource(R.string.history_today)
            val yesterdayLabel = stringResource(R.string.history_yesterday)
            val listItems = remember(entries, entryTimeMs, todayLabel, yesterdayLabel) {
                if (entryTimeMs == null) emptyList()
                else buildDayGroupedItems(entries, entryTimeMs, todayLabel, yesterdayLabel)
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = 8.dp,
                    bottom = 12.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (entryTimeMs == null) {
                    itemsIndexed(entries, key = { _, e -> entryKey(e) }) { index, entry ->
                        SavedPageRow(
                            entry = entry,
                            title = entryTitle(entry),
                            url = entryUrl(entry),
                            icon = rowIcon,
                            showFavicon = showFavicons,
                            onTap = onTap,
                            onDelete = onDelete,
                            index = index,
                            count = entries.size,
                            modifier = Modifier.animateItem(),
                        )
                    }
                } else {
                    items(
                        listItems,
                        key = { item ->
                            when (item) {
                                is DayListItem.Header -> "day_" + item.dayStartMs
                                is DayListItem.Row<*> -> entryKey(@Suppress("UNCHECKED_CAST") (item.entry as T))
                            }
                        },
                    ) { item ->
                        when (item) {
                            is DayListItem.Header -> Text(
                                text = item.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)
                                    .animateItem(),
                            )
                            is DayListItem.Row<*> -> {
                                @Suppress("UNCHECKED_CAST")
                                val rowEntry = item.entry as T
                                SavedPageRow(
                                    entry = rowEntry,
                                    title = entryTitle(rowEntry),
                                    url = entryUrl(rowEntry),
                                    icon = rowIcon,
                                    showFavicon = showFavicons,
                                    onTap = onTap,
                                    onDelete = onDelete,
                                    index = item.indexInDay,
                                    count = item.countInDay,
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
