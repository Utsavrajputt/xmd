package com.invictus.xmd.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.invictus.xmd.ui.icons.AppIcon
import com.invictus.xmd.ui.icons.Icon
import com.invictus.xmd.ui.icons.Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.invictus.xmd.R
import com.invictus.xmd.core.ItemStatus
import com.invictus.xmd.core.MediaPlatform
import com.invictus.xmd.core.QueueItem
import com.invictus.xmd.core.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full Downloads/Queue screen: summary chips + list-or-empty-state +
 * Cancel All/Retry All + Clear All. Mirrors fragment_downloads.xml.
 *
 * Unlike Bookmarks/History, this screen owns no header or search field of
 * its own -- the query comes from MainActivity's in-header search box via
 * DownloadsFragment.setFilterQuery(), same as before.
 */
@Composable
fun DownloadsScreen(
    items: List<QueueItem>,
    query: String,
    onPauseResume: (QueueItem) -> Unit,
    onCancel: (QueueItem) -> Unit,
    onRetry: (QueueItem) -> Unit,
    onClear: (QueueItem) -> Unit,
    onOpen: (QueueItem) -> Unit,
    onOpenWith: (QueueItem) -> Unit,
    onRename: (QueueItem, String) -> Unit,
    onCopyLink: (QueueItem) -> Unit,
    onShare: (QueueItem) -> Unit,
    onOpenFileLocation: (QueueItem) -> Unit,
    onDelete: (QueueItem) -> Unit,
    onCancelAll: () -> Unit,
    onRetryAll: () -> Unit,
    onClearAllFinished: () -> Unit,
    onPauseAll: (List<QueueItem>) -> Unit,
    onResumeAll: (List<QueueItem>) -> Unit,
    onStartAll: (List<QueueItem>) -> Unit = onResumeAll,
    onSelectionStateChanged: (DownloadsSelectionUiState?) -> Unit = {},
    onCopyLinks: (List<QueueItem>) -> Unit = { list -> list.firstOrNull()?.let(onCopyLink) },
    onShareItems: (List<QueueItem>) -> Unit = { list -> list.firstOrNull()?.let(onShare) },
    onDeleteItems: (List<QueueItem>) -> Unit = { list -> list.forEach(onDelete) },
) {
    // Same filter as DownloadsFragment.renderList: filename OR sourceUrl,
    // case-insensitive substring.
    val queryMatches = remember(items, query) {
        val q = query.trim()
        if (q.isEmpty()) {
            items
        } else {
            items.filter { item ->
                (item.fileName?.contains(q, ignoreCase = true) == true) ||
                    item.sourceUrl.contains(q, ignoreCase = true)
            }
        }
    }
    var selectedFilterName by rememberSaveable { mutableStateOf(DownloadFilter.All.name) }
    val selectedFilter = DownloadFilter.valueOf(selectedFilterName)
    val list = remember(queryMatches, selectedFilter) {
        queryMatches.filter(selectedFilter::matches)
    }

    // If the currently selected chip (e.g. "Active") loses its last item to
    // a cancel/delete, its count drops to 0 and the chip itself disappears
    // (see the `count == 0 -> return@forEach` skip below) -- leaving the
    // user stranded on a filter with no chip and an empty list. Snap back
    // to "All" whenever that happens.
    LaunchedEffect(selectedFilter, list.isEmpty()) {
        if (selectedFilter != DownloadFilter.All && list.isEmpty()) {
            selectedFilterName = DownloadFilter.All.name
        }
    }

    var selectedIds by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var renameTarget by remember { mutableStateOf<QueueItem?>(null) }
    var deleteTargets by remember { mutableStateOf<List<QueueItem>?>(null) }
    var overflowMenuExpanded by remember { mutableStateOf(false) }

    BackHandler(enabled = selectedIds.isNotEmpty()) {
        selectedIds = emptySet()
    }

    DisposableEffect(Unit) {
        onDispose {
            onSelectionStateChanged(null)
        }
    }

    val selectedItems = remember(items, selectedIds) {
        items.filter { it.id in selectedIds }
    }

    LaunchedEffect(items, selectedIds) {
        val validIds = items.map { it.id }.toSet()
        val pruned = selectedIds.intersect(validIds)
        if (pruned.size != selectedIds.size) {
            selectedIds = pruned
        }
    }

    val canPause = remember(selectedItems) {
        selectedItems.any {
            it.status == ItemStatus.DOWNLOADING || it.status == ItemStatus.RETRYING || it.status == ItemStatus.SAVING
        }
    }
    val canStart = remember(selectedItems) {
        selectedItems.any {
            it.status == ItemStatus.PAUSED || it.status == ItemStatus.READY || it.status == ItemStatus.PENDING
        }
    }
    val canRetry = remember(selectedItems) {
        selectedItems.any {
            it.status == ItemStatus.FAILED || it.status == ItemStatus.DONE
        }
    }

    val currentSelectionUiState = remember(selectedItems.size, list.size, canPause, canStart, canRetry) {
        if (selectedItems.isEmpty()) {
            null
        } else {
            DownloadsSelectionUiState(
                selectedCount = selectedItems.size,
                totalCount = list.size,
                canPause = canPause,
                canStart = canStart,
                canRetry = canRetry,
                canCopyLink = true,
                canShare = true,
                canDelete = true,
                onPause = {
                    val currentSelected = items.filter { it.id in selectedIds }
                    val toPause = currentSelected.filter {
                        it.status == ItemStatus.DOWNLOADING || it.status == ItemStatus.RETRYING || it.status == ItemStatus.SAVING
                    }
                    toPause.forEach { onPauseResume(it) }
                    selectedIds = emptySet()
                },
                onStart = {
                    val currentSelected = items.filter { it.id in selectedIds }
                    val toStart = currentSelected.filter {
                        it.status == ItemStatus.PAUSED || it.status == ItemStatus.READY || it.status == ItemStatus.PENDING
                    }
                    toStart.forEach { onPauseResume(it) }
                    selectedIds = emptySet()
                },
                onRetry = {
                    val currentSelected = items.filter { it.id in selectedIds }
                    val toRetry = currentSelected.filter {
                        it.status == ItemStatus.FAILED || it.status == ItemStatus.DONE
                    }
                    toRetry.forEach { onRetry(it) }
                    selectedIds = emptySet()
                },
                onCopyLink = {
                    val currentSelected = items.filter { it.id in selectedIds }
                    onCopyLinks(currentSelected)
                    selectedIds = emptySet()
                },
                onShare = {
                    val currentSelected = items.filter { it.id in selectedIds }
                    onShareItems(currentSelected)
                    selectedIds = emptySet()
                },
                onDelete = {
                    deleteTargets = items.filter { it.id in selectedIds }
                },
                onClose = {
                    selectedIds = emptySet()
                },
                onSelectAll = {
                    selectedIds = list.map { it.id }.toSet()
                },
                onInvertSelection = {
                    val allVisibleIds = list.map { it.id }.toSet()
                    selectedIds = allVisibleIds - selectedIds
                },
            )
        }
    }

    LaunchedEffect(currentSelectionUiState) {
        onSelectionStateChanged(currentSelectionUiState)
    }

    // Used by both the top-bar overflow menu (Cancel/Retry All + Clear) and
    // to decide whether that menu button shows at all.
    val hasActive = list.any {
        it.status == ItemStatus.DOWNLOADING || it.status == ItemStatus.PAUSED ||
            it.status == ItemStatus.RETRYING
    }
    val hasFailed = list.any { it.status == ItemStatus.FAILED }
    val hasClearable = list.any { it.status == ItemStatus.DONE || it.status == ItemStatus.FAILED }
    val showCancelOrRetry = hasActive || hasFailed

    // Start All / Pause All operate on visible `list`, or fallback to `items`
    // so they are fully functional regardless of the active filter tab.
    val pausableItems = list.filter {
        it.status == ItemStatus.DOWNLOADING || it.status == ItemStatus.RETRYING || it.status == ItemStatus.SAVING
    }.ifEmpty {
        items.filter {
            it.status == ItemStatus.DOWNLOADING || it.status == ItemStatus.RETRYING || it.status == ItemStatus.SAVING
        }
    }
    val startableItems = list.filter {
        it.status == ItemStatus.PAUSED || it.status == ItemStatus.READY || it.status == ItemStatus.PENDING
    }.ifEmpty {
        items.filter {
            it.status == ItemStatus.PAUSED || it.status == ItemStatus.READY || it.status == ItemStatus.PENDING
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (items.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DownloadFilter.entries.forEach { filter ->
                        val count = queryMatches.count(filter::matches)
                        // All always shows (it's the reset-to-everything
                        // chip); the rest only show once they'd actually
                        // have something in them, so a Waiting/Completed/
                        // Failed chip stuck at 0 doesn't just sit there.
                        if (filter != DownloadFilter.All && count == 0) return@forEach
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilterName = filter.name },
                            label = {
                                Text(
                                    text = "${stringResource(filter.labelRes)} $count",
                                    fontSize = 12.sp,
                                )
                            },
                            leadingIcon = if (selectedFilter == filter) {
                                {
                                    Icon(
                                        imageVector = Icons.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                                    )
                                }
                            } else {
                                null
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                        )
                    }
                }

                // Cancel All / Retry All + Clear, folded into a compact
                // overflow menu instead of a separate full-width bar at the
                // bottom of the screen (was visually disconnected from
                // everything else and left a lot of dead space below a
                // short list).
                if (list.isNotEmpty()) {
                    Box(modifier = Modifier.padding(end = 4.dp)) {
                        IconButton(onClick = { overflowMenuExpanded = true }) {
                            Icon(
                                imageVector = Icons.More,
                                contentDescription = stringResource(R.string.action_more),
                            )
                        }
                        DropdownMenu(
                            expanded = overflowMenuExpanded,
                            onDismissRequest = { overflowMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_start_all)) },
                                leadingIcon = { Icon(imageVector = Icons.Play, contentDescription = null) },
                                enabled = true,
                                onClick = {
                                    overflowMenuExpanded = false
                                    val targets = startableItems.ifEmpty {
                                        items.filter { it.status == ItemStatus.PAUSED || it.status == ItemStatus.READY || it.status == ItemStatus.PENDING }
                                    }
                                    onStartAll(targets.ifEmpty { items })
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_pause_all)) },
                                leadingIcon = { Icon(imageVector = Icons.Pause, contentDescription = null) },
                                enabled = true,
                                onClick = {
                                    overflowMenuExpanded = false
                                    val targets = pausableItems.ifEmpty {
                                        items.filter { it.status == ItemStatus.DOWNLOADING || it.status == ItemStatus.RETRYING || it.status == ItemStatus.SAVING }
                                    }
                                    if (targets.isNotEmpty()) {
                                        onPauseAll(targets)
                                    }
                                },
                            )
                            if (hasActive) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(R.string.action_cancel_all),
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Cancel,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    onClick = { overflowMenuExpanded = false; onCancelAll() },
                                )
                            }
                            if (hasFailed) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_retry_all)) },
                                    leadingIcon = { Icon(imageVector = Icons.Refresh, contentDescription = null) },
                                    onClick = { overflowMenuExpanded = false; onRetryAll() },
                                )
                            }
                            if (hasClearable) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_clear_all)) },
                                    leadingIcon = { Icon(imageVector = Icons.DeleteSweep, contentDescription = null) },
                                    onClick = { overflowMenuExpanded = false; onClearAllFinished() },
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Content: list or empty state ────────────────────────────────
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (list.isEmpty()) {
                EmptyState(showIcon = query.isBlank())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(list, key = { it.id }) { item ->
                        val isSelected = item.id in selectedIds
                        QueueItemRow(
                            item = item,
                            isSelected = isSelected,
                            isSelectionMode = selectedIds.isNotEmpty(),
                            onPauseResume = onPauseResume,
                            onCancel = onCancel,
                            onRetry = onRetry,
                            onClear = onClear,
                            onOpen = onOpen,
                            onToggleSelect = { toggled ->
                                selectedIds = if (toggled.id in selectedIds) {
                                    selectedIds - toggled.id
                                } else {
                                    selectedIds + toggled.id
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    // ── Rename dialog ─────────────────────────────────────────────────────
    renameTarget?.let { item ->
        val currentName = item.filePath?.let { java.io.File(it).name } ?: item.fileName.orEmpty()
        RenameDialog(
            currentName = currentName,
            onConfirm = { newName -> onRename(item, newName); renameTarget = null },
            onDismiss = { renameTarget = null },
        )
    }

    // ── Delete confirm dialog ─────────────────────────────────────────────
    deleteTargets?.let { targets ->
        val isSingle = targets.size == 1
        val firstItem = targets.first()
        AlertDialog(
            onDismissRequest = { deleteTargets = null },
            modifier = Modifier.wideDialogWidth(),
            properties = WideDialogProperties,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    if (isSingle) stringResource(R.string.delete_download_title)
                    else "Delete ${targets.size} downloads?"
                )
            },
            text = {
                Text(
                    if (isSingle) (firstItem.fileName ?: firstItem.sourceUrl)
                    else "Delete the selected files from device and remove them from the queue?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteItems(targets)
                        deleteTargets = null
                        selectedIds = emptySet()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargets = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

private enum class DownloadFilter(val labelRes: Int) {
    All(R.string.download_filter_all),
    Active(R.string.download_filter_active),
    Waiting(R.string.download_filter_waiting),
    Completed(R.string.download_filter_completed),
    Failed(R.string.download_filter_failed);

    fun matches(item: QueueItem): Boolean = when (this) {
        All -> true
        Active -> item.status in setOf(
            ItemStatus.DOWNLOADING,
            ItemStatus.PAUSED,
            ItemStatus.RETRYING,
            ItemStatus.SAVING,
        )
        Waiting -> item.status in setOf(
            ItemStatus.PENDING,
            ItemStatus.RESOLVING,
            ItemStatus.NEEDS_CHALLENGE,
            ItemStatus.READY,
        )
        Completed -> item.status == ItemStatus.DONE
        Failed -> item.status == ItemStatus.FAILED
    }
}

@Composable
private fun EmptyState(showIcon: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (showIcon) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Downloads,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(36.dp),
                )
            }
            Box(modifier = Modifier.padding(top = 18.dp))
        }
        Text(
            text = stringResource(if (showIcon) R.string.queue_empty_title else R.string.queue_search_empty),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
        )
    }
}

// ── Queue row ─────────────────────────────────────────────────────────────

/** Mirrors item_queue.xml + QueueAdapter's onBindViewHolder, 1:1. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun QueueItemRow(
    item: QueueItem,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onPauseResume: (QueueItem) -> Unit,
    onCancel: (QueueItem) -> Unit,
    onRetry: (QueueItem) -> Unit,
    onClear: (QueueItem) -> Unit,
    onOpen: (QueueItem) -> Unit,
    onToggleSelect: (QueueItem) -> Unit = {},
) {
    val haptics = LocalHapticFeedback.current
    val cardBorder = if (isSelected) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }
    val cardContainerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardContainerColor),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = cardBorder,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .combinedClickable(
                    onClick = {
                        if (isSelectionMode) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onToggleSelect(item)
                        } else {
                            when (item.status) {
                                ItemStatus.DOWNLOADING, ItemStatus.RETRYING, ItemStatus.SAVING,
                                ItemStatus.PAUSED, ItemStatus.READY -> {
                                    onPauseResume(item)
                                }
                                ItemStatus.FAILED -> {
                                    onRetry(item)
                                }
                                ItemStatus.DONE -> {
                                    if (item.filePath != null) {
                                        onOpen(item)
                                    }
                                }
                                else -> Unit
                            }
                        }
                    },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleSelect(item)
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .fillMaxHeight()
                    .background(colorForStatus(item.status)),
            )

            AnimatedVisibility(
                visible = isSelectionMode,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally(),
            ) {
                Box(
                    modifier = Modifier.padding(start = 12.dp, end = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = Color.Transparent,
                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.size(22.dp),
                        ) {}
                    }
                }
            }

            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 7.dp)) {
                // Title row: filename + file type bubble
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = item.fileName ?: item.sourceUrl,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(6.dp))
                    FileTypeBubble(item)
                }

                // Status row: icon + unified status text + inline action buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                ) {
                    val statusIcon = when (item.status) {
                        ItemStatus.PAUSED -> Icons.Pause
                        ItemStatus.DOWNLOADING -> Icons.Download
                        ItemStatus.DONE -> Icons.Check
                        ItemStatus.FAILED -> Icons.Close
                        ItemStatus.RETRYING -> Icons.Refresh
                        ItemStatus.SAVING -> Icons.Sync
                        else -> null
                    }
                    if (statusIcon != null) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = colorForStatus(item.status),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    val throttledSpeedEta = if (item.status == ItemStatus.DOWNLOADING) {
                        rememberThrottledSpeedEtaText(item)
                    } else {
                        null
                    }
                    Text(
                        text = statusText(item, throttledSpeedEta),
                        color = if (item.status == ItemStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )

                    if (!isSelectionMode) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(start = 6.dp),
                        ) {
                            when (item.status) {
                                ItemStatus.DOWNLOADING, ItemStatus.PAUSED -> {
                                    CompactIconButton(
                                        icon = if (item.status == ItemStatus.PAUSED) Icons.Play else Icons.Pause,
                                        contentDescription = stringResource(
                                            if (item.status == ItemStatus.PAUSED) R.string.action_resume else R.string.action_pause
                                        ),
                                        tint = MaterialTheme.colorScheme.primary,
                                        onClick = { onPauseResume(item) },
                                    )
                                    CompactIconButton(
                                        icon = Icons.Close,
                                        contentDescription = stringResource(R.string.action_cancel),
                                        tint = MaterialTheme.colorScheme.error,
                                        onClick = { onCancel(item) },
                                    )
                                }
                                ItemStatus.READY -> {
                                    CompactIconButton(
                                        icon = Icons.Play,
                                        contentDescription = stringResource(R.string.action_start),
                                        tint = MaterialTheme.colorScheme.primary,
                                        onClick = { onPauseResume(item) },
                                    )
                                    CompactIconButton(
                                        icon = Icons.Close,
                                        contentDescription = stringResource(R.string.action_cancel),
                                        tint = MaterialTheme.colorScheme.error,
                                        onClick = { onCancel(item) },
                                    )
                                }
                                ItemStatus.RETRYING, ItemStatus.PENDING, ItemStatus.RESOLVING, ItemStatus.NEEDS_CHALLENGE -> {
                                    CompactIconButton(
                                        icon = Icons.Close,
                                        contentDescription = stringResource(R.string.action_cancel),
                                        tint = MaterialTheme.colorScheme.error,
                                        onClick = { onCancel(item) },
                                    )
                                }
                                ItemStatus.FAILED -> {
                                    CompactIconButton(
                                        icon = Icons.Refresh,
                                        contentDescription = stringResource(R.string.action_retry),
                                        tint = MaterialTheme.colorScheme.primary,
                                        onClick = { onRetry(item) },
                                    )
                                    CompactIconButton(
                                        icon = Icons.DeleteSweep,
                                        contentDescription = stringResource(R.string.action_clear),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        onClick = { onClear(item) },
                                    )
                                }
                                ItemStatus.DONE -> {
                                    if (item.filePath != null) {
                                        CompactIconButton(
                                            icon = Icons.FileOpen,
                                            contentDescription = stringResource(R.string.action_open),
                                            tint = MaterialTheme.colorScheme.primary,
                                            onClick = { onOpen(item) },
                                        )
                                    }
                                    CompactIconButton(
                                        icon = Icons.DeleteSweep,
                                        contentDescription = stringResource(R.string.action_clear),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        onClick = { onClear(item) },
                                    )
                                }
                                else -> Unit
                            }
                        }
                    }
                }

                if (showsProgressBar(item.status)) {
                    val (progressValue, indeterminate) = progressFor(item)
                    DownloadProgressBar(
                        progress = progressValue,
                        isDownloading = item.status == ItemStatus.DOWNLOADING,
                        isIndeterminate = indeterminate,
                        modifier = Modifier.padding(top = 4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                }
            }
        }
    }
}

@Composable
private fun FileTypeBubble(item: QueueItem) {
    val type = remember(item.fileName, item.sourceUrl, item.platform, item.mediaFormatLabel, item.category) {
        fileTypeLabel(item)
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    ) {
        Text(
            text = type,
            fontWeight = FontWeight.Bold,
            fontSize = 9.5.sp,
            letterSpacing = 0.4.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private fun fileTypeLabel(item: QueueItem): String {
    if (item.platform == MediaPlatform.YOUTUBE) {
        val format = item.mediaFormatLabel?.trim()
        if (!format.isNullOrEmpty()) {
            val f = format.lowercase()
            if (f.contains("audio") || f.contains("mp3") || f.contains("m4a") || f.contains("opus")) return "AUDIO"
            if (f.contains("1080")) return "1080P"
            if (f.contains("720")) return "720P"
            if (f.contains("4k") || f.contains("2160")) return "4K"
            if (f.contains("480")) return "480P"
            if (f.contains("360")) return "360P"
            return "VIDEO"
        }
        return "YOUTUBE"
    }
    val name = item.fileName ?: item.sourceUrl
    val cleanName = name.substringBefore('?').substringBefore('#')
    if (item.sourceUrl.startsWith("magnet:", ignoreCase = true) || cleanName.endsWith(".torrent", ignoreCase = true)) {
        return "TORRENT"
    }
    val ext = cleanName.substringAfterLast('.', "").trim()
    if (ext.isNotEmpty() && ext.length in 2..5 && ext.all { it.isLetterOrDigit() }) {
        return ext.uppercase()
    }
    return item.category.label.uppercase()
}

@Composable
private fun DownloadProgressBar(
    progress: Float,
    isDownloading: Boolean,
    isIndeterminate: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "download_wave_transition")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wave_phase",
    )
    val indeterminateOffset by infiniteTransition.animateFloat(
        initialValue = -0.35f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "indeterminate_offset",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp),
    ) {
        val centerY = size.height / 2f
        val strokeWidth = 3.5.dp.toPx()
        val totalWidth = size.width

        // Background track
        drawLine(
            color = trackColor,
            start = Offset(0f, centerY),
            end = Offset(totalWidth, centerY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )

        if (isIndeterminate) {
            val barLength = totalWidth * 0.35f
            val startX = (indeterminateOffset * totalWidth).coerceIn(0f, totalWidth)
            val endX = ((indeterminateOffset * totalWidth) + barLength).coerceIn(0f, totalWidth)
            if (endX > startX) {
                drawLine(
                    color = color,
                    start = Offset(startX, centerY),
                    end = Offset(endX, centerY),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
        } else {
            val progressWidth = (totalWidth * progress.coerceIn(0f, 1f))
            if (progressWidth > 0f) {
                if (isDownloading && progress < 1f) {
                    // Animated wavy progress bar when downloading
                    val waveLength = 22.dp.toPx()
                    val amplitude = 2.2.dp.toPx()
                    val path = Path()
                    path.moveTo(0f, centerY)

                    var x = 0f
                    val step = 2f
                    while (x <= progressWidth) {
                        val angle = ((x / waveLength) + wavePhase) * 2f * Math.PI.toFloat()
                        val taper = ((progressWidth - x) / 12.dp.toPx()).coerceIn(0f, 1f) *
                            (x / 12.dp.toPx()).coerceIn(0f, 1f)
                        val y = centerY + (kotlin.math.sin(angle) * amplitude * taper)
                        path.lineTo(x, y)
                        x += step
                    }
                    path.lineTo(progressWidth, centerY)

                    drawPath(
                        path = path,
                        color = color,
                        style = Stroke(
                            width = strokeWidth,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                } else {
                    // Straight solid bar when completed (DONE) or paused
                    drawLine(
                        color = color,
                        start = Offset(0f, centerY),
                        end = Offset(progressWidth, centerY),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactIconButton(
    icon: AppIcon,
    contentDescription: String?,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 26.dp,
    iconSize: androidx.compose.ui.unit.Dp = 15.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, radius = size / 2),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

private fun showsProgressBar(status: ItemStatus) =
    status == ItemStatus.DOWNLOADING || status == ItemStatus.PAUSED || status == ItemStatus.SAVING

/** Returns (progressFraction 0f..1f, isIndeterminate). */
private fun progressFor(item: QueueItem): Pair<Float, Boolean> = when (item.status) {
    ItemStatus.DOWNLOADING, ItemStatus.PAUSED -> when {
        item.platform == MediaPlatform.YOUTUBE ->
            if (item.progressPercent >= 0) (item.progressPercent / 100f) to false else 0f to (item.status == ItemStatus.DOWNLOADING)
        item.bytesTotal > 0 -> ((item.bytesDone.toFloat() / item.bytesTotal.toFloat())).coerceIn(0f, 1f) to false
        else -> 0f to (item.status == ItemStatus.DOWNLOADING)
    }
    ItemStatus.DONE, ItemStatus.SAVING -> 1f to false
    else -> 0f to true
}

private fun statusText(item: QueueItem, speedEta: String?): String = when (item.status) {
    ItemStatus.PENDING -> "Queued"
    ItemStatus.RESOLVING -> "Resolving…"
    ItemStatus.NEEDS_CHALLENGE -> "Verifying — complete check in browser"
    ItemStatus.READY -> "Ready to download"
    ItemStatus.DOWNLOADING -> {
        val sizePart = when {
            item.platform == MediaPlatform.YOUTUBE && !item.mediaStatusText.isNullOrBlank() -> item.mediaStatusText
            item.bytesTotal > 0 -> {
                val pct = (item.bytesDone * 100 / item.bytesTotal).coerceIn(0, 100)
                "${formatBytes(item.bytesDone)} / ${formatBytes(item.bytesTotal)} ($pct%)"
            }
            item.bytesDone > 0 -> formatBytes(item.bytesDone)
            item.platform == MediaPlatform.YOUTUBE && item.progressPercent >= 0 -> "${item.progressPercent}%"
            else -> "Downloading…"
        }
        val label = if (item.platform == MediaPlatform.YOUTUBE) item.mediaFormatLabel?.let { " • $it" } else null
        buildString {
            append(sizePart)
            if (speedEta != null) {
                append(" • ")
                append(speedEta)
            }
            if (label != null) {
                append(label)
            }
        }
    }
    ItemStatus.PAUSED -> {
        val sizePart = when {
            item.bytesTotal > 0 -> {
                val pct = (item.bytesDone * 100 / item.bytesTotal).coerceIn(0, 100)
                "${formatBytes(item.bytesDone)} / ${formatBytes(item.bytesTotal)} ($pct%)"
            }
            item.bytesDone > 0 -> formatBytes(item.bytesDone)
            item.platform == MediaPlatform.YOUTUBE && item.progressPercent >= 0 -> "${item.progressPercent}%"
            else -> null
        }
        val label = when (item.error) {
            Settings.WIFI_WAIT_MARKER -> "Waiting for Wi-Fi"
            Settings.NETWORK_WAIT_MARKER -> "Waiting for network"
            else -> "Paused"
        }
        if (sizePart != null) "$sizePart • $label" else label
    }
    ItemStatus.RETRYING -> item.error ?: "Retrying…"
    ItemStatus.SAVING -> "Saving to storage…"
    ItemStatus.DONE -> {
        val bytes = when {
            item.bytesTotal > 0 -> item.bytesTotal
            item.bytesDone > 0 -> item.bytesDone
            else -> item.filePath?.let { java.io.File(it).takeIf { f -> f.exists() }?.length() } ?: 0L
        }
        val finishedAt = when {
            item.downloadFinishedAtMs > 0 -> item.downloadFinishedAtMs
            else -> item.filePath?.let { java.io.File(it).takeIf { f -> f.exists() }?.lastModified() } ?: 0L
        }
        val durationMs = if (item.downloadStartedAtMs > 0 && finishedAt > item.downloadStartedAtMs) {
            finishedAt - item.downloadStartedAtMs
        } else 0L
        val parts = buildList {
            if (bytes > 0) add(formatBytes(bytes))
            if (durationMs > 500) add("Took ${formatElapsedDuration(durationMs)}")
            if (finishedAt > 0) add(dateFormat.format(Date(finishedAt)))
        }
        if (parts.isNotEmpty()) parts.joinToString("  •  ") else "Completed"
    }
    ItemStatus.FAILED -> item.error ?: "Failed"
}

@Composable
private fun colorForStatus(status: ItemStatus): Color = when (status) {
    ItemStatus.PENDING, ItemStatus.RESOLVING, ItemStatus.NEEDS_CHALLENGE -> MaterialTheme.colorScheme.onSurfaceVariant
    ItemStatus.READY, ItemStatus.DOWNLOADING, ItemStatus.SAVING, ItemStatus.PAUSED -> MaterialTheme.colorScheme.primary
    ItemStatus.RETRYING -> MaterialTheme.colorScheme.secondary
    ItemStatus.DONE -> MaterialTheme.colorScheme.primary
    ItemStatus.FAILED -> MaterialTheme.colorScheme.error
}

private val dateFormat by lazy { SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()) }

private fun formatElapsedDuration(durationMs: Long): String {
    val totalSecs = (durationMs / 1000).coerceAtLeast(1)
    val hours = totalSecs / 3600
    val mins = (totalSecs % 3600) / 60
    val secs = totalSecs % 60
    return when {
        hours > 0 -> "${hours}h ${mins}m"
        mins > 0 -> "${mins}m ${secs}s"
        else -> "${secs}s"
    }
}

/**
 * Speed + time-left text, recomputed from whatever `item` snapshot is
 * passed in. Callers that want the Chrome-style once-a-second cadence
 * should go through [rememberThrottledSpeedEtaText] instead of calling
 * this directly on every recomposition.
 */
private fun buildSpeedEtaText(item: QueueItem): String {
    val bps = item.speedBps
    val speedStr = when {
        bps >= 1_048_576.0 -> "%.1f MB/s".format(bps / 1_048_576.0)
        bps >= 1_024.0 -> "%.0f KB/s".format(bps / 1_024.0)
        else -> "%.0f B/s".format(bps)
    }
    val remaining = (item.bytesTotal - item.bytesDone).coerceAtLeast(0)
    val etaSec = if (bps > 1.0 && item.bytesTotal > 0) (remaining / bps).toLong() else -1L
    return if (etaSec >= 0) "$speedStr  •  ${formatRemainingTimeChrome(etaSec)}" else speedStr
}

/**
 * Holds a download row's speed/ETA text steady for up to a second at a
 * time, then refreshes it from the latest [item] -- the same cadence
 * Chrome's own download UI updates its remaining-time estimate at.
 * Progress ticks arrive from the engine up to ~5x/sec (see
 * QueueRepository), which would otherwise make the ETA number flicker.
 *
 * The displayed value still reflects the most recent data at each tick;
 * only the *update frequency* is throttled, not the data itself.
 */
@Composable
private fun rememberThrottledSpeedEtaText(item: QueueItem): String? {
    val latestItem = rememberUpdatedState(item)
    var text by remember(item.id) {
        mutableStateOf(if (item.speedBps > 0) buildSpeedEtaText(item) else null)
    }
    LaunchedEffect(item.id) {
        while (true) {
            delay(1_000L)
            val current = latestItem.value
            text = if (current.speedBps > 0) buildSpeedEtaText(current) else null
        }
    }
    return text
}

/**
 * Formats remaining time using [formatRemainingTimeChrome] -- see that
 * function for details.
 */
private fun formatRemainingTimeChrome(totalSeconds: Long): String =
    com.invictus.xmd.core.formatRemainingTimeChrome(totalSeconds)

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.2f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024L -> "%.0f KB".format(bytes / 1_024.0)
    else -> "$bytes B"
}

// ── Dialogs ───────────────────────────────────────────────────────────────

@Composable
private fun RenameDialog(currentName: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember(currentName) { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.wideDialogWidth(),
        properties = WideDialogProperties,
        shape = RoundedCornerShape(20.dp),
        title = { Text(stringResource(R.string.action_rename)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val newName = text.trim()
                if (newName.isNotEmpty() && newName != currentName) onConfirm(newName) else onDismiss()
            }) { Text(stringResource(R.string.settings_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}
