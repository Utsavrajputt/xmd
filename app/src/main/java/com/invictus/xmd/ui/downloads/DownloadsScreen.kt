@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.invictus.xmd.ui.downloads

import android.os.Build
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.Dp
import com.invictus.xmd.ui.theme.XmdShapes
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
import androidx.compose.ui.graphics.vector.ImageVector
import com.invictus.xmd.domain.download.DownloadCategory
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import android.widget.Toast
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.selection.SelectionContainer
import com.invictus.xmd.ui.components.XmdAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
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
import com.invictus.xmd.utils.media.MediaProbe
import com.invictus.xmd.utils.media.MediaInfoProbe
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.invictus.xmd.R
import java.util.Date
import com.invictus.xmd.database.entities.QueueItem
import com.invictus.xmd.domain.download.DownloadScheduler
import com.invictus.xmd.domain.download.ItemStatus
import com.invictus.xmd.domain.download.MediaPlatform
import com.invictus.xmd.domain.download.ScheduleMode
import com.invictus.xmd.utils.formatBytes
import com.invictus.xmd.utils.formatSpeed
import com.invictus.xmd.utils.formatRemainingTimeChrome
import com.invictus.xmd.preferences.Settings
import com.invictus.xmd.repository.QueueRepository
import com.invictus.xmd.ui.MainActivity
import com.invictus.xmd.ui.components.WideDialogProperties
import com.invictus.xmd.ui.components.compactDialogWidth
import com.invictus.xmd.ui.components.wideDialogWidth
import com.invictus.xmd.utils.ErrorUtils
import com.invictus.xmd.utils.formatRemainingTimeChrome

/**
 * Full Downloads/Queue screen: summary chips + list-or-empty-state +
 * Cancel All/Retry All + Clear All. Mirrors fragment_downloads.xml.
 *
 * Unlike Bookmarks/History, this screen owns no header or search field of
 * its own -- the query comes from MainActivity's saved-state owner through
 * DownloadsFragment.
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
    onDelete: (QueueItem, Boolean) -> Unit = { _, _ -> },
    onCancelAll: () -> Unit,
    onRetryAll: () -> Unit,
    onClearAllFinished: () -> Unit,
    onPauseAll: (List<QueueItem>) -> Unit,
    onResumeAll: (List<QueueItem>) -> Unit,
    onStartAll: (List<QueueItem>) -> Unit = onResumeAll,
    onSelectionStateChanged: (DownloadsSelectionUiState?) -> Unit = {},
    onCopyLinks: (List<QueueItem>) -> Unit = { list -> list.firstOrNull()?.let(onCopyLink) },
    onShareItems: (List<QueueItem>) -> Unit = { list -> list.firstOrNull()?.let(onShare) },
    onDeleteItems: (List<QueueItem>, Boolean) -> Unit = { list, deleteFiles -> list.forEach { onDelete(it, deleteFiles) } },
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
    // One flat chip row: [Active] [All] [Video] [Audio] [Documents] [Others].
    // Active = everything not finished (downloading, paused, waiting, failed),
    // whatever its type. All + the type chips list COMPLETED items only
    // (All = every type together). Exactly one
    // chip is selected at a time; empty chips are hidden.
    var chosenChipName by rememberSaveable { mutableStateOf<String?>(null) }
    var sortName by rememberSaveable { mutableStateOf(DownloadSort.Newest.name) }
    val sort = DownloadSort.entries.firstOrNull { it.name == sortName } ?: DownloadSort.Newest

    val activeItems = remember(queryMatches) { queryMatches.filter { it.status != ItemStatus.DONE } }
    val finishedItems = remember(queryMatches) { queryMatches.filter { it.status == ItemStatus.DONE } }
    val typeCounts = remember(finishedItems) {
        DownloadTypeFilter.entries.associateWith { t -> finishedItems.count { t.matches(it) } }
    }
    val activeCount = activeItems.size

    // The user's pick if it still has items; otherwise Active, else All.
    // showAll / selectedType both false/null with showActive false => nothing.
    val chosenType = DownloadTypeFilter.entries.firstOrNull { it.name == chosenChipName }
    val finishedCount = finishedItems.size
    val showActive: Boolean
    val showAll: Boolean
    val selectedType: DownloadTypeFilter?
    if (chosenChipName == ACTIVE_CHIP && activeCount > 0) {
        showActive = true; showAll = false; selectedType = null
    } else if (chosenChipName == ALL_CHIP && finishedCount > 0) {
        showActive = false; showAll = true; selectedType = null
    } else if (chosenType != null && (typeCounts[chosenType] ?: 0) > 0) {
        showActive = false; showAll = false; selectedType = chosenType
    } else if (activeCount > 0) {
        showActive = true; showAll = false; selectedType = null
    } else {
        showActive = false; showAll = finishedCount > 0; selectedType = null
    }
    // In-flight downloads stay on top of the Active list; the rest keep their order.
    val list = remember(activeItems, finishedItems, showActive, showAll, selectedType, sort) {
        if (showActive) {
            val (inFlight, others) = activeItems.partition { isInFlight(it) }
            sort.apply(inFlight) + sort.apply(others)
        } else if (showAll) {
            sort.apply(finishedItems)
        } else if (selectedType != null) {
            sort.apply(finishedItems.filter { selectedType.matches(it) })
        } else {
            emptyList()
        }
    }

    var selectedIds by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var renameTarget by remember { mutableStateOf<QueueItem?>(null) }
    var deleteTargets by remember { mutableStateOf<List<QueueItem>?>(null) }
    var errorDetailsTarget by remember { mutableStateOf<QueueItem?>(null) }
    var overflowMenuExpanded by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var pendingCancelItem by remember { mutableStateOf<QueueItem?>(null) }
    var showCancelAllDialog by remember { mutableStateOf(false) }

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
            .background(com.invictus.xmd.ui.theme.wallpaperAwareBackgroundColor()),
    ) {
        if (items.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(com.invictus.xmd.ui.theme.wallpaperAwareBackgroundColor())
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (activeCount > 0) {
                        DownloadFilterChip(
                            label = stringResource(R.string.download_filter_active),
                            selected = showActive,
                            onClick = { chosenChipName = ACTIVE_CHIP },
                        )
                    }
                    if (activeCount > 0 && finishedCount > 0) {
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(20.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant),
                        )
                    }
                    if (finishedCount > 0) {
                        DownloadFilterChip(
                            label = stringResource(R.string.download_filter_all),
                            selected = showAll,
                            onClick = { chosenChipName = ALL_CHIP },
                        )
                    }
                    DownloadTypeFilter.entries.forEach { type ->
                        if ((typeCounts[type] ?: 0) == 0) return@forEach
                        DownloadFilterChip(
                            label = stringResource(type.labelRes),
                            selected = selectedType == type,
                            onClick = { chosenChipName = type.name },
                        )
                    }
                }

                // Cancel All / Retry All + Clear, folded into a compact
                // overflow menu instead of a separate full-width bar at the
                // bottom of the screen (was visually disconnected from
                // everything else and left a lot of dead space below a
                // short list).
                if (list.isNotEmpty()) {
                    // Compact sort button, just left of the 3-dot menu.
                    Box {
                        IconButton(
                            shapes = androidx.compose.material3.IconButtonDefaults.shapes(),
                            onClick = { sortMenuExpanded = true },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Sort,
                                contentDescription = stringResource(R.string.action_sort),
                                tint = if (sort != DownloadSort.Newest) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        DropdownMenu(
                            shape = com.invictus.xmd.ui.theme.XmdShapes.M,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false },
                        ) {
                            DownloadSort.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(option.labelRes)) },
                                    trailingIcon = if (option == sort) {
                                        { Icon(imageVector = Icons.Check, contentDescription = null) }
                                    } else {
                                        null
                                    },
                                    onClick = {
                                        sortName = option.name
                                        sortMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    Box(modifier = Modifier.padding(end = 4.dp)) {
                        IconButton(shapes = androidx.compose.material3.IconButtonDefaults.shapes(), onClick = { overflowMenuExpanded = true }) {
                            Icon(
                                imageVector = Icons.More,
                                contentDescription = stringResource(R.string.action_more),
                            )
                        }
                        DropdownMenu(
 shape = com.invictus.xmd.ui.theme.XmdShapes.M, containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh,
                            expanded = overflowMenuExpanded,
                            onDismissRequest = { overflowMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_start_all)) },
                                leadingIcon = { Icon(imageVector = Icons.Play, contentDescription = null) },
                                enabled = startableItems.isNotEmpty(),
                                onClick = {
                                    overflowMenuExpanded = false
                                    onStartAll(startableItems)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_pause_all)) },
                                leadingIcon = { Icon(imageVector = Icons.Pause, contentDescription = null) },
                                enabled = pausableItems.isNotEmpty(),
                                onClick = {
                                    overflowMenuExpanded = false
                                    onPauseAll(pausableItems)
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
                                    onClick = { overflowMenuExpanded = false; showCancelAllDialog = true },
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
                val onSwipeClearItem: (QueueItem) -> Unit = { swipedItem ->
                    if (swipedItem.status == ItemStatus.DOWNLOADING ||
                        swipedItem.status == ItemStatus.RETRYING ||
                        swipedItem.status == ItemStatus.SAVING
                    ) {
                        onCancel(swipedItem)
                    }
                    onClear(swipedItem)
                    if (swipedItem.id in selectedIds) {
                        selectedIds = selectedIds - swipedItem.id
                    }
                }

                // Content scrolling under the floating navbar gets blurred
                // (Android 12+) with a fading scrim; older devices get the scrim only.
                val navBarInset = with(LocalDensity.current) {
                    WindowInsets.navigationBars.getBottom(this).toDp()
                }
                val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .bottomNavBlur(
                            enabled = !imeOpen,
                            stripHeight = NAV_BLUR_STRIP_HEIGHT + navBarInset,
                            scrimColor = MaterialTheme.colorScheme.background,
                        ),
                    contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(list, key = { it.id }) { item ->
                        val isSelected = item.id in selectedIds
                        QueueItemRow(
                            item = item,
                            isSelected = isSelected,
                            isSelectionMode = selectedIds.isNotEmpty(),
                            modifier = Modifier.animateItem(),
                            onPauseResume = onPauseResume,
                            onCancel = { pendingCancelItem = it },
                            onRetry = onRetry,
                            onClear = { deleteTargets = listOf(it) },
                            onOpen = onOpen,
                            onOpenFileLocation = onOpenFileLocation,
                            onErrorDetails = { errorDetailsTarget = it },
                            onSwipeClear = onSwipeClearItem,
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
        var deleteFilesFromDevice by remember(targets) { mutableStateOf(true) }
        val hasAnyFiles = remember(targets) {
            targets.any { !it.filePath.isNullOrBlank() }
        }

        XmdAlertDialog(
            onDismissRequest = { deleteTargets = null },
            modifier = Modifier.compactDialogWidth(),
            properties = WideDialogProperties,
            compact = true,
            title = {
                Text(
                    if (isSingle) stringResource(R.string.delete_download_title)
                    else stringResource(R.string.delete_downloads_title)
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = if (isSingle) (firstItem.fileName ?: firstItem.sourceUrl)
                        else stringResource(R.string.delete_downloads_message, targets.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (hasAnyFiles) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(XmdShapes.Xs)
                                .clickable { deleteFilesFromDevice = !deleteFilesFromDevice }
                                .padding(vertical = 4.dp),
                        ) {
                            Checkbox(
                                checked = deleteFilesFromDevice,
                                onCheckedChange = null,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(
                                    if (isSingle) R.string.delete_file_from_device
                                    else R.string.delete_files_from_device
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(shapes = androidx.compose.material3.ButtonDefaults.shapes(), 
                    onClick = {
                        onDeleteItems(targets, deleteFilesFromDevice)
                        deleteTargets = null
                        selectedIds = emptySet()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(shapes = androidx.compose.material3.ButtonDefaults.shapes(), onClick = { deleteTargets = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    // ── Cancel confirm dialog (single download) ───────────────────────────
    pendingCancelItem?.let { item ->
        XmdAlertDialog(
            onDismissRequest = { pendingCancelItem = null },
            modifier = Modifier.compactDialogWidth(),
            properties = WideDialogProperties,
            compact = true,
            title = { Text(stringResource(R.string.cancel_download_title)) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = item.fileName ?: item.sourceUrl,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.cancel_download_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(shapes = androidx.compose.material3.ButtonDefaults.shapes(), 
                    onClick = {
                        onCancel(item)
                        pendingCancelItem = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.action_cancel)) }
            },
            dismissButton = {
                TextButton(shapes = androidx.compose.material3.ButtonDefaults.shapes(), onClick = { pendingCancelItem = null }) {
                    Text(stringResource(R.string.action_keep_downloading))
                }
            },
        )
    }

    // ── Cancel All confirm dialog ─────────────────────────────────────────
    if (showCancelAllDialog) {
        val activeCount = remember(items) {
            items.count {
                it.status == ItemStatus.DOWNLOADING || it.status == ItemStatus.PAUSED ||
                    it.status == ItemStatus.RETRYING || it.status == ItemStatus.SAVING ||
                    it.status == ItemStatus.PENDING || it.status == ItemStatus.RESOLVING ||
                    it.status == ItemStatus.READY || it.status == ItemStatus.NEEDS_CHALLENGE
            }
        }
        XmdAlertDialog(
            onDismissRequest = { showCancelAllDialog = false },
            modifier = Modifier.compactDialogWidth(),
            properties = WideDialogProperties,
            compact = true,
            title = { Text(stringResource(R.string.cancel_downloads_title)) },
            text = {
                Text(
                    text = stringResource(R.string.cancel_downloads_message, activeCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(shapes = androidx.compose.material3.ButtonDefaults.shapes(), 
                    onClick = {
                        showCancelAllDialog = false
                        onCancelAll()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.action_cancel_all)) }
            },
            dismissButton = {
                TextButton(shapes = androidx.compose.material3.ButtonDefaults.shapes(), onClick = { showCancelAllDialog = false }) {
                    Text(stringResource(R.string.action_keep_downloading))
                }
            },
        )
    }

    // ── Error / Warning Details dialog ────────────────────────────────────
    errorDetailsTarget?.let { item ->
        ErrorDetailsDialog(
            item = item,
            onDismiss = { errorDetailsTarget = null },
            onRetry = { retryItem ->
                errorDetailsTarget = null
                onRetry(retryItem)
            },
        )
    }
}

private const val ACTIVE_CHIP = "ACTIVE"
private const val ALL_CHIP = "ALL"

/** Currently running (not paused/waiting/failed) -- kept on top of the Active list. */
private fun isInFlight(item: QueueItem): Boolean = item.status in setOf(
    ItemStatus.DOWNLOADING,
    ItemStatus.PAUSED,
    ItemStatus.RETRYING,
    ItemStatus.SAVING,
)

private enum class DownloadTypeFilter(val labelRes: Int) {
    Video(R.string.download_type_video),
    Audio(R.string.download_type_audio),
    Documents(R.string.download_type_documents),
    Others(R.string.download_type_others);

    fun matches(item: QueueItem): Boolean = downloadType(item) == this
}

/** Same extension/category rules the row icon uses, folded into the four filter buckets. */
private fun downloadType(item: QueueItem): DownloadTypeFilter {
    if (item.sourceUrl.startsWith("magnet:", ignoreCase = true)) return DownloadTypeFilter.Others
    if (item.platform == MediaPlatform.YOUTUBE) {
        val format = item.mediaFormatLabel?.trim()?.lowercase()
        val audio = format != null &&
            (format.contains("audio") || format.contains("mp3") || format.contains("m4a") || format.contains("opus"))
        return if (audio) DownloadTypeFilter.Audio else DownloadTypeFilter.Video
    }
    val name = (item.fileName ?: item.sourceUrl).substringBefore('?').substringBefore('#')
    val ext = name.substringAfterLast('.', "").trim().lowercase()
    return when {
        ext in listOf("mp4", "mkv", "webm", "avi", "mov", "flv", "ts", "wmv", "m4v", "3gp", "mpg", "mpeg") -> DownloadTypeFilter.Video
        ext in listOf("mp3", "m4a", "flac", "opus", "wav", "ogg", "aac", "wma") -> DownloadTypeFilter.Audio
        ext in listOf(
            "pdf", "epub", "mobi", "cbz", "cbr", "doc", "docx", "txt", "md", "rtf", "odt",
            "xls", "xlsx", "ods", "csv", "ppt", "pptx", "odp",
        ) -> DownloadTypeFilter.Documents
        ext.isNotEmpty() && (ext in listOf("apk", "xapk", "apks", "zip", "rar", "7z", "tar", "gz", "iso", "torrent")) -> DownloadTypeFilter.Others
        item.category == DownloadCategory.VIDEOS || item.category == DownloadCategory.MOVIES ||
            item.category == DownloadCategory.SHOWS -> DownloadTypeFilter.Video
        item.category == DownloadCategory.MUSIC -> DownloadTypeFilter.Audio
        item.category == DownloadCategory.DOCUMENTS -> DownloadTypeFilter.Documents
        else -> DownloadTypeFilter.Others
    }
}


/** Height of the blurred strip above the screen bottom, excluding the system nav-bar inset. */
private val NAV_BLUR_STRIP_HEIGHT = 104.dp

/**
 * Progressive blur for content scrolling beneath the floating bottom navbar.
 * The content is recorded into a layer a second time, blurred with a
 * [BlurEffect] (Android 12+), masked with a transparent->opaque vertical
 * gradient and drawn over the bottom [stripHeight]. A background-coloured
 * scrim fades in on top; below Android 12 only the scrim is drawn.
 */
@Composable
private fun Modifier.bottomNavBlur(
    enabled: Boolean,
    stripHeight: Dp,
    scrimColor: Color,
): Modifier {
    val blurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val density = LocalDensity.current
    val stripPx = with(density) { stripHeight.toPx() }
    val blurPx = with(density) { 18.dp.toPx() }
    val contentLayer = rememberGraphicsLayer()
    val maskLayer = rememberGraphicsLayer()
    remember(contentLayer, maskLayer, blurPx, blurSupported) {
        contentLayer.renderEffect = if (blurSupported) {
            BlurEffect(blurPx, blurPx, TileMode.Clamp)
        } else {
            null
        }
        maskLayer.compositingStrategy = CompositingStrategy.Offscreen
        true
    }
    val scrimAlpha = if (blurSupported) 0.45f else 0.85f
    return this.drawWithContent {
        drawContent()
        if (!enabled || stripPx <= 0f) return@drawWithContent
        val top = (size.height - stripPx).coerceAtLeast(0f)
        if (blurSupported) {
            contentLayer.record { this@drawWithContent.drawContent() }
            maskLayer.record {
                clipRect(top = top) { drawLayer(contentLayer) }
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black),
                        startY = top,
                        endY = size.height,
                    ),
                    topLeft = Offset(0f, top),
                    size = Size(size.width, size.height - top),
                    blendMode = BlendMode.DstIn,
                )
            }
            drawLayer(maskLayer)
        }
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, scrimColor.copy(alpha = scrimAlpha)),
                startY = top,
                endY = size.height,
            ),
            topLeft = Offset(0f, top),
            size = Size(size.width, size.height - top),
        )
    }
}

private enum class DownloadSort(val labelRes: Int) {
    Newest(R.string.download_sort_newest),
    Oldest(R.string.download_sort_oldest),
    NameAsc(R.string.download_sort_name_asc),
    NameDesc(R.string.download_sort_name_desc),
    SizeDesc(R.string.download_sort_size_desc),
    SizeAsc(R.string.download_sort_size_asc);

    fun apply(list: List<QueueItem>): List<QueueItem> = when (this) {
        Newest -> list.sortedByDescending { timeKey(it) }
        Oldest -> list.sortedBy { timeKey(it) }
        NameAsc -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.fileName ?: it.sourceUrl })
        NameDesc -> list.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.fileName ?: it.sourceUrl })
        SizeDesc -> list.sortedByDescending { sizeKey(it) }
        SizeAsc -> list.sortedBy { sizeKey(it) }
    }
}

// Finished time, else start time; items that haven't started yet were queued most recently.
private fun timeKey(item: QueueItem): Long {
    val t = maxOf(item.downloadFinishedAtMs, item.downloadStartedAtMs)
    return if (t == 0L) Long.MAX_VALUE else t
}

private fun sizeKey(item: QueueItem): Long = maxOf(item.bytesTotal, item.bytesDone)

@Composable
private fun DownloadFilterChip(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(text = label, fontSize = 12.sp) },
        leadingIcon = if (selected) {
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
                    .clip(XmdShapes.M)
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
    modifier: Modifier = Modifier,
    onPauseResume: (QueueItem) -> Unit,
    onCancel: (QueueItem) -> Unit,
    onRetry: (QueueItem) -> Unit,
    onClear: (QueueItem) -> Unit,
    onOpen: (QueueItem) -> Unit,
    onOpenFileLocation: (QueueItem) -> Unit = {},
    onErrorDetails: (QueueItem) -> Unit = {},
    onSwipeClear: (QueueItem) -> Unit = onClear,
    onToggleSelect: (QueueItem) -> Unit = {},
) {
    val haptics = LocalHapticFeedback.current
    val currentOnSwipeClear by rememberUpdatedState(onSwipeClear)
    val currentItem by rememberUpdatedState(item)
    var showInfo by remember { mutableStateOf(false) }
    if (showInfo) {
        DownloadInfoDialog(item = item, onDismiss = { showInfo = false })
    }

    // ── Swipe-to-reveal delete: swipe left stops at the delete action
    // instead of auto-deleting. Tapping Delete performs the delete.
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val actionWidthPx = with(density) { 88.dp.toPx() }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var revealed by remember { mutableStateOf(false) }

    LaunchedEffect(item.id, isSelectionMode) {
        offsetX = 0f
        revealed = false
    }

    val snapSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    fun snapTo(target: Float) {
        scope.launch {
            val start = offsetX
            val diff = target - start
            if (diff == 0f) return@launch
            androidx.compose.animation.core.Animatable(start).animateTo(
                target,
                animationSpec = snapSpec,
            ) { offsetX = value }
            revealed = target != 0f
        }
    }

    val dragState = rememberDraggableState { delta ->
        val next = (offsetX + delta).coerceIn(-actionWidthPx, 0f)
        offsetX = next
        val nowRevealed = next <= -actionWidthPx + 1f
        if (nowRevealed != revealed) {
            revealed = nowRevealed
            if (nowRevealed) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val cardBorder = if (isSelected) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }
    val cardContainerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            .compositeOver(MaterialTheme.colorScheme.surfaceContainer)
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    Box(
        modifier = modifier.fillMaxWidth(),
    ) {
        // Delete action revealed behind the card -- drawn only while the card
        // is actually swiped, so its red never peeks out past the card's corners.
        if (!isSelectionMode && offsetX < 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(XmdShapes.S)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(end = 8.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .fillMaxHeight()
                        .clip(XmdShapes.Xs)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true),
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                snapTo(0f)
                                currentOnSwipeClear(currentItem)
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(
                            imageVector = Icons.DeleteSweep,
                            contentDescription = stringResource(R.string.action_clear),
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            text = stringResource(R.string.action_clear),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.toInt(), 0) }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    enabled = !isSelectionMode,
                    onDragStopped = { velocity ->
                        val shouldReveal =
                            offsetX < -actionWidthPx / 2f || velocity < -800f
                        snapTo(if (shouldReveal) -actionWidthPx else 0f)
                    },
                )
                .defaultMinSize(minHeight = 72.dp),
            colors = CardDefaults.cardColors(containerColor = cardContainerColor),
            shape = XmdShapes.M,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = cardBorder,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .combinedClickable(
                    onClick = {
                        if (revealed) {
                            snapTo(0f)
                            return@combinedClickable
                        }
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
                                    if (!item.error.isNullOrBlank()) {
                                        onErrorDetails(item)
                                    } else {
                                        onRetry(item)
                                    }
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
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else colorForStatus(item.status)),
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

            Column(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 72.dp)
                    .padding(
                        start = if (isSelectionMode) 8.dp else 14.dp,
                        end = 14.dp,
                        top = 11.dp,
                        bottom = 11.dp,
                    ),
                verticalArrangement = Arrangement.Center,
            ) {
                // Title row: file type icon + filename + file type bubble
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val icon = remember(item.fileName, item.mediaFormatLabel, item.category) {
                        fileTypeIcon(item)
                    }
                    val iconColor = remember(item.fileName, item.platform, item.category) {
                        fileTypeColor(item)
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                        tint = iconColor,
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = item.fileName ?: item.sourceUrl,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(6.dp))
                    FileTypeBubble(item)
                }

                // Progress bar above progress details
                if (showsProgressBar(item.status)) {
                    val (progressValue, indeterminate) = progressFor(item)
                    DownloadProgressBar(
                        progress = progressValue,
                        isDownloading = item.status == ItemStatus.DOWNLOADING,
                        isIndeterminate = indeterminate,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                } else {
                    Spacer(Modifier.height(7.dp))
                }

                // Status row: icon + unified status text + inline action buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
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
                    val warningOrError = item.error?.takeIf { it.isNotBlank() }
                        ?: item.mediaStatusText?.takeIf { it.startsWith("Warning", ignoreCase = true) }
                    val hasErrorOrWarning = !warningOrError.isNullOrBlank() &&
                        (item.status == ItemStatus.FAILED || item.status == ItemStatus.RETRYING || item.status == ItemStatus.DOWNLOADING)
                    // Auto-wrap: when "size • speed • ETA" doesn't fit on one line
                    // (large font/display scale), move "speed • ETA" to a second line.
                    // NOTE: no BoxWithConstraints/SubcomposeLayout here -- the card row
                    // above uses IntrinsicSize.Min, which subcompose layouts can't answer.
                    // The text's width comes from the Row weight, so reading it via
                    // onSizeChanged can't feed back into the text content.
                    val textMeasurer = rememberTextMeasurer()
                    val statusStyle = LocalTextStyle.current.merge(
                        TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    )
                    var statusWidthPx by remember { mutableIntStateOf(0) }
                    val singleLine = statusText(item, throttledSpeedEta)
                    val needsWrap = throttledSpeedEta != null && statusWidthPx > 0 &&
                        remember(singleLine, statusWidthPx, statusStyle) {
                            textMeasurer.measure(
                                text = AnnotatedString(singleLine),
                                style = statusStyle,
                                maxLines = 1,
                                softWrap = false,
                            ).size.width > statusWidthPx
                        }
                    val shownText = if (needsWrap) {
                        statusText(item, throttledSpeedEta, splitSpeedEta = true)
                    } else singleLine
                    Text(
                        text = shownText,
                        color = if (item.status == ItemStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = if (needsWrap) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .onSizeChanged { statusWidthPx = it.width }
                            .then(
                                if (hasErrorOrWarning && !isSelectionMode) {
                                    Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable { onErrorDetails(item) }
                                } else Modifier
                            ),
                    )

                    if (!isSelectionMode) {
                        val clipboardManager = LocalClipboardManager.current
                        val context = LocalContext.current
                        val copyableWarningOrError = warningOrError

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(start = 6.dp),
                        ) {
                            when (item.status) {
                                ItemStatus.DOWNLOADING, ItemStatus.PAUSED -> {
                                    if (item.status == ItemStatus.DOWNLOADING && copyableWarningOrError != null) {
                                        CompactIconButton(
                                            icon = Icons.Copy,
                                            contentDescription = stringResource(R.string.action_copy_error),
                                            tint = MaterialTheme.colorScheme.error,
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(cleanErrorText(copyableWarningOrError)))
                                                Toast.makeText(context, R.string.error_copied_toast, Toast.LENGTH_SHORT).show()
                                            },
                                        )
                                    }
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
                                    // A configured YouTube item stuck in Queued has nothing left to
                                    // resolve -- let the user start it.
                                    if (item.status == ItemStatus.PENDING &&
                                        item.platform == MediaPlatform.YOUTUBE &&
                                        item.mediaFormatSelector != null
                                    ) {
                                        CompactIconButton(
                                            icon = Icons.Play,
                                            contentDescription = stringResource(R.string.action_start),
                                            tint = MaterialTheme.colorScheme.primary,
                                            onClick = { onRetry(item) },
                                        )
                                    }
                                    if (copyableWarningOrError != null) {
                                        CompactIconButton(
                                            icon = Icons.Copy,
                                            contentDescription = stringResource(R.string.action_copy_error),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(cleanErrorText(copyableWarningOrError)))
                                                Toast.makeText(context, R.string.error_copied_toast, Toast.LENGTH_SHORT).show()
                                            },
                                        )
                                    }
                                    CompactIconButton(
                                        icon = Icons.Close,
                                        contentDescription = stringResource(R.string.action_cancel),
                                        tint = MaterialTheme.colorScheme.error,
                                        onClick = { onCancel(item) },
                                    )
                                }
                                ItemStatus.FAILED -> {
                                    if (copyableWarningOrError != null) {
                                        CompactIconButton(
                                            icon = Icons.Copy,
                                            contentDescription = stringResource(R.string.action_copy_error),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(cleanErrorText(copyableWarningOrError)))
                                                Toast.makeText(context, R.string.error_copied_toast, Toast.LENGTH_SHORT).show()
                                            },
                                        )
                                    }
                                    CompactIconButton(
                                        icon = Icons.Refresh,
                                        contentDescription = stringResource(R.string.action_retry),
                                        tint = MaterialTheme.colorScheme.primary,
                                        onClick = { onRetry(item) },
                                    )
                                }
                                ItemStatus.DONE -> {
                                    CompactIconButton(
                                        icon = Icons.Folder,
                                        contentDescription = stringResource(R.string.action_open_file_location),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        onClick = { onOpenFileLocation(item) },
                                    )
                                    CompactIconButton(
                                        icon = Icons.Info,
                                        contentDescription = stringResource(R.string.action_details),
                                        tint = MaterialTheme.colorScheme.primary,
                                        onClick = { showInfo = true },
                                    )
                                }
                                else -> Unit
                            }
                        }
                    }
                }
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

private fun fileTypeIcon(item: QueueItem): AppIcon {
    if (item.sourceUrl.startsWith("magnet:", ignoreCase = true)) {
        return Icons.Torrent
    }
    if (item.platform == MediaPlatform.YOUTUBE) {
        val format = item.mediaFormatLabel?.trim()?.lowercase()
        if (format != null && (format.contains("audio") || format.contains("mp3") || format.contains("m4a") || format.contains("opus"))) {
            return Icons.Music
        }
        return Icons.Video
    }
    val name = item.fileName ?: item.sourceUrl
    val cleanName = name.substringBefore('?').substringBefore('#')
    if (cleanName.endsWith(".torrent", ignoreCase = true)) return Icons.Torrent

    val ext = cleanName.substringAfterLast('.', "").trim().lowercase()
    return when {
        ext in listOf("apk", "xapk", "apks") || item.category == DownloadCategory.APPS -> Icons.Android
        ext in listOf("mp4", "mkv", "webm", "avi", "mov", "flv", "ts", "wmv", "m4v") || item.category == DownloadCategory.VIDEOS || item.category == DownloadCategory.MOVIES || item.category == DownloadCategory.SHOWS -> Icons.Video
        ext in listOf("mp3", "m4a", "flac", "opus", "wav", "ogg", "aac", "wma") || item.category == DownloadCategory.MUSIC -> Icons.Music
        ext in listOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso", "zst", "7zip") -> Icons.Archive
        ext == "pdf" -> Icons.Pdf
        ext in listOf("epub", "mobi", "cbz", "cbr", "doc", "docx", "txt", "md", "xlsx", "pptx", "rtf") || item.category == DownloadCategory.DOCUMENTS -> Icons.Document
        ext in listOf("jpg", "jpeg", "png", "webp", "gif", "svg", "bmp", "ico") -> Icons.Image
        ext in listOf("json", "xml", "html", "css", "js", "ts", "py", "kt", "java", "cpp", "c", "sh") -> Icons.Code
        else -> Icons.Document
    }
}

private fun fileTypeColor(item: QueueItem): Color {
    if (item.sourceUrl.startsWith("magnet:", ignoreCase = true)) {
        return Color(0xFF9575CD)
    }
    if (item.platform == MediaPlatform.YOUTUBE) {
        val format = item.mediaFormatLabel?.trim()?.lowercase()
        if (format != null && (format.contains("audio") || format.contains("mp3") || format.contains("m4a") || format.contains("opus"))) {
            return Color(0xFFBA68C8)
        }
        return Color(0xFFEF5350)
    }
    val name = item.fileName ?: item.sourceUrl
    val cleanName = name.substringBefore('?').substringBefore('#')
    val ext = cleanName.substringAfterLast('.', "").trim().lowercase()
    return when {
        ext in listOf("apk", "xapk", "apks") || item.category == DownloadCategory.APPS -> Color(0xFF66BB6A)
        ext in listOf("mp4", "mkv", "webm", "avi", "mov", "flv", "ts", "wmv", "m4v") || item.category == DownloadCategory.VIDEOS || item.category == DownloadCategory.MOVIES || item.category == DownloadCategory.SHOWS -> Color(0xFFEF5350)
        ext in listOf("mp3", "m4a", "flac", "opus", "wav", "ogg", "aac", "wma") || item.category == DownloadCategory.MUSIC -> Color(0xFFBA68C8)
        ext in listOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso", "zst", "7zip") -> Color(0xFFFFA726)
        ext == "pdf" -> Color(0xFF42A5F5)
        ext in listOf("epub", "mobi", "cbz", "cbr", "doc", "docx", "txt", "md", "xlsx", "pptx", "rtf") || item.category == DownloadCategory.DOCUMENTS -> Color(0xFF29B6F6)
        ext in listOf("jpg", "jpeg", "png", "webp", "gif", "svg", "bmp", "ico") -> Color(0xFF26C6DA)
        ext in listOf("json", "xml", "html", "css", "js", "ts", "py", "kt", "java", "cpp", "c", "sh") -> Color(0xFF26A69A)
        else -> Color(0xFF78909C)
    }
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
    size: androidx.compose.ui.unit.Dp = 28.dp,
    iconSize: androidx.compose.ui.unit.Dp = 20.dp,
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

@Composable
private fun statusText(item: QueueItem, speedEta: String?, splitSpeedEta: Boolean = false): String {
    val context = LocalContext.current
    return when (item.status) {
    ItemStatus.PENDING -> "Queued"
    ItemStatus.RESOLVING -> "Resolving…"
    ItemStatus.NEEDS_CHALLENGE -> "Verifying — complete check in browser"
    ItemStatus.READY -> if (item.scheduleMode != ScheduleMode.NONE && !DownloadScheduler.isAllowedNow(item)) {
        when (item.scheduleMode) {
            ScheduleMode.ONE_TIME -> "Scheduled"
            else -> "Waiting for quiet hours"
        }
    } else {
        "Ready to download"
    }
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
        buildString {
            append(sizePart)
            // A YouTube stage label ("Merging…") replaces the size, so no speed/ETA next to it.
            val ytStage = item.platform == MediaPlatform.YOUTUBE && !item.mediaStatusText.isNullOrBlank()
            if (speedEta != null && !ytStage) {
                append(if (splitSpeedEta) "\n" else " • ")
                append(speedEta)
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
            Settings.DATA_LIMIT_WAIT_MARKER -> "Daily data limit reached"
            Settings.SCHEDULE_WAIT_MARKER -> "Waiting for scheduled time"
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
            !item.filePath.isNullOrBlank() -> {
                runCatching { java.io.File(item.filePath!!).takeIf { f -> f.exists() }?.length() }.getOrNull() ?: 0L
            }
            else -> 0L
        }
        val finishedAt = when {
            item.downloadFinishedAtMs > 0 -> item.downloadFinishedAtMs
            !item.filePath.isNullOrBlank() -> {
                runCatching { java.io.File(item.filePath!!).takeIf { f -> f.exists() }?.lastModified() }.getOrNull() ?: 0L
            }
            else -> 0L
        }
        val durationMs = if (item.downloadStartedAtMs > 0 && finishedAt > item.downloadStartedAtMs) {
            finishedAt - item.downloadStartedAtMs
        } else 0L
        val parts = buildList {
            if (bytes > 0) add(formatBytes(bytes))
            if (durationMs > 500) add("Took ${formatElapsedDuration(durationMs)}")
            if (finishedAt > 0) {
                val finishedDate = Date(finishedAt)
                val date = android.text.format.DateFormat.getMediumDateFormat(context).format(finishedDate)
                val time = android.text.format.DateFormat.getTimeFormat(context).format(finishedDate)
                add("$date \u00b7 $time")
            }
        }
        if (parts.isNotEmpty()) parts.joinToString("  •  ") else "Completed"
    }
    ItemStatus.FAILED -> item.error ?: "Failed"
}
}

@Composable
private fun colorForStatus(status: ItemStatus): Color = when (status) {
    ItemStatus.PENDING, ItemStatus.RESOLVING, ItemStatus.NEEDS_CHALLENGE -> MaterialTheme.colorScheme.onSurfaceVariant
    ItemStatus.READY, ItemStatus.DOWNLOADING, ItemStatus.SAVING, ItemStatus.PAUSED -> MaterialTheme.colorScheme.primary
    ItemStatus.RETRYING -> MaterialTheme.colorScheme.secondary
    ItemStatus.DONE -> MaterialTheme.colorScheme.primary
    ItemStatus.FAILED -> MaterialTheme.colorScheme.error
}

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
    val speedStr = formatSpeed(item.speedBps)
    val remaining = (item.bytesTotal - item.bytesDone).coerceAtLeast(0)
    val etaSec = if (item.speedBps > 1.0 && item.bytesTotal > 0) (remaining / item.speedBps).toLong() else -1L
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

// ── Dialogs ───────────────────────────────────────────────────────────────

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DownloadInfoDialog(item: QueueItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val finishedAt = when {
        item.downloadFinishedAtMs > 0 -> item.downloadFinishedAtMs
        !item.filePath.isNullOrBlank() ->
            runCatching { java.io.File(item.filePath!!).takeIf { it.exists() }?.lastModified() }.getOrNull() ?: 0L
        else -> 0L
    }
    val durationMs = if (item.downloadStartedAtMs > 0 && finishedAt > item.downloadStartedAtMs) {
        finishedAt - item.downloadStartedAtMs
    } else 0L
    val size = when {
        item.bytesTotal > 0 -> item.bytesTotal
        item.bytesDone > 0 -> item.bytesDone
        else -> 0L
    }
    val probe by produceState<MediaProbe?>(initialValue = null, item.filePath) {
        val path = item.filePath
        value = if (path.isNullOrBlank()) null else withContext(Dispatchers.IO) {
            runCatching { MediaInfoProbe.probe(path) }.getOrNull()
        }
    }
    val platformLabel = when (item.platform) {
        com.invictus.xmd.domain.download.MediaPlatform.YOUTUBE -> stringResource(R.string.info_platform_youtube)
        com.invictus.xmd.domain.download.MediaPlatform.DIRECT -> stringResource(R.string.info_platform_direct)
    }
    val fileName = item.fileName?.takeIf { it.isNotBlank() }
        ?: item.filePath?.let { java.io.File(it).name }
    val finishedText = if (finishedAt > 0) {
        val d = Date(finishedAt)
        val date = android.text.format.DateFormat.getMediumDateFormat(context).format(d)
        val time = android.text.format.DateFormat.getTimeFormat(context).format(d)
        "$date \u00b7 $time"
    } else null

    val rows = listOf(
        InfoRowData(stringResource(R.string.info_file_name), fileName, Icons.Description, copyable = true),
        InfoRowData(stringResource(R.string.info_size), size.takeIf { it > 0 }?.let { formatBytes(it) }, Icons.PieChart),
        InfoRowData(stringResource(R.string.info_media_duration), probe?.durationMs?.let { formatMediaDuration(it) }, Icons.Schedule),
        InfoRowData(stringResource(R.string.info_codec), probe?.codec, Icons.Article),
        InfoRowData(stringResource(R.string.info_quality), probe?.quality, Icons.Settings),
        InfoRowData(stringResource(R.string.info_format), item.mediaFormatLabel?.takeIf { it.isNotBlank() }, Icons.Settings),
        InfoRowData(stringResource(R.string.info_platform), platformLabel, Icons.Public),
        InfoRowData(stringResource(R.string.info_category), item.category.label, Icons.Sell),
        InfoRowData(stringResource(R.string.info_path), item.filePath?.takeIf { it.isNotBlank() }, Icons.Folder, copyable = true),
        InfoRowData(stringResource(R.string.info_source), item.sourceUrl.takeIf { it.isNotBlank() }, Icons.Link, copyable = true, singleLine = true, copyValue = item.sourceUrl),
        InfoRowData(stringResource(R.string.info_finished), finishedText, Icons.EventDone),
        InfoRowData(stringResource(R.string.info_duration), durationMs.takeIf { it > 500 }?.let { formatElapsedDuration(it) }, Icons.Timer),
    ).filter { !it.value.isNullOrBlank() }

    val colors = MaterialTheme.colorScheme
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val maxHeight = (configuration.screenHeightDp.dp - 96.dp).coerceAtLeast(240.dp)
    androidx.compose.material3.BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 560.dp),
        properties = WideDialogProperties,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f)),
            tonalElevation = 0.dp,
        ) {
            Column(modifier = Modifier.heightIn(max = maxHeight)) {
                // Header: title + round close button
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.action_details),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Surface(
                        onClick = onDismiss,
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = colors.surfaceContainerHighest,
                        contentColor = colors.onSurface,
                    ) {
                        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                            Icon(imageVector = Icons.Close, contentDescription = stringResource(R.string.action_dismiss), modifier = Modifier.size(20.dp))
                        }
                    }
                }
                HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.5f))

                // Rows
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                ) {
                    rows.forEachIndexed { index, row ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = colors.surfaceContainerHighest,
                                contentColor = colors.onSurface,
                            ) {
                                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                    Icon(imageVector = row.icon, contentDescription = null, modifier = Modifier.size(20.dp))
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = row.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.onSurfaceVariant,
                                )
                                Text(
                                    text = row.value.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurface,
                                    maxLines = if (row.singleLine) 1 else Int.MAX_VALUE,
                                    overflow = if (row.singleLine) TextOverflow.Ellipsis else TextOverflow.Clip,
                                )
                            }
                            if (row.copyable) {
                                IconButton(modifier = Modifier.size(36.dp), onClick = {
                                    clipboardManager.setText(AnnotatedString(row.copyValue ?: row.value.orEmpty()))
                                }) {
                                    Icon(imageVector = Icons.Copy, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                        if (index != rows.lastIndex) {
                            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
                HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.5f))

                // Actions: Copy path (outlined) + Dismiss (filled pill)
                val path = item.filePath?.takeIf { it.isNotBlank() }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (path != null) {
                        OutlinedButton(
                            onClick = { clipboardManager.setText(AnnotatedString(path)) },
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                            shape = androidx.compose.foundation.shape.CircleShape,
                        ) {
                            Icon(imageVector = Icons.Copy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.info_copy_path))
                        }
                    }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                        shape = androidx.compose.foundation.shape.CircleShape,
                    ) {
                        Text(stringResource(R.string.action_dismiss))
                    }
                }
            }
        }
    }
}

private data class InfoRowData(
    val label: String,
    val value: String?,
    val icon: AppIcon,
    val copyable: Boolean = false,
    val singleLine: Boolean = false,
    val copyValue: String? = null,
)

private fun formatMediaDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val sec = total % 60
    return if (h > 0) String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, sec)
    else String.format(java.util.Locale.US, "%d:%02d", m, sec)
}

@Composable
private fun RenameDialog(currentName: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember(currentName) { mutableStateOf(currentName) }
    XmdAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.wideDialogWidth(),
        properties = WideDialogProperties,
        title = { Text(stringResource(R.string.action_rename)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = XmdShapes.S,
                colors = OutlinedTextFieldDefaults.colors(),
            )
        },
        confirmButton = {
            TextButton(shapes = androidx.compose.material3.ButtonDefaults.shapes(), onClick = {
                val newName = text.trim()
                if (newName.isNotEmpty() && newName != currentName) onConfirm(newName) else onDismiss()
            }) { Text(stringResource(R.string.settings_save)) }
        },
        dismissButton = {
            TextButton(shapes = androidx.compose.material3.ButtonDefaults.shapes(), onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

private fun cleanErrorText(error: String?): String = ErrorUtils.cleanErrorText(error)

private fun isWarningText(text: String?): Boolean = ErrorUtils.isWarningText(text)

@Composable
private fun ErrorDetailsDialog(
    item: QueueItem,
    onDismiss: () -> Unit,
    onRetry: (QueueItem) -> Unit,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val errorText = item.error?.takeIf { it.isNotBlank() }
        ?: item.mediaStatusText?.takeIf { it.startsWith("Warning", ignoreCase = true) }
    val cleanedError = remember(errorText) { cleanErrorText(errorText) }
    val isWarning = remember(errorText) { isWarningText(errorText) }

    XmdAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.wideDialogWidth(),
        properties = WideDialogProperties,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = if (isWarning) Icons.Info else Icons.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = stringResource(
                        if (isWarning) R.string.dialog_warning_details_title
                        else R.string.dialog_error_details_title
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = item.fileName ?: item.sourceUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Surface(
                    shape = XmdShapes.S,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                    ) {
                        SelectionContainer {
                            Text(
                                text = cleanedError,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(cleanedError))
                        Toast.makeText(context, R.string.error_copied_toast, Toast.LENGTH_SHORT).show()
                    },
                    shape = XmdShapes.Xs,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Copy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_copy))
                }

                Button(
                    onClick = {
                        onDismiss()
                        onRetry(item)
                    },
                    shape = XmdShapes.Xs,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_retry))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = XmdShapes.Xs,
            ) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
