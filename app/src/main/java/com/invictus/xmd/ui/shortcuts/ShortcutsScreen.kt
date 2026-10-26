@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.invictus.xmd.ui.shortcuts

import com.invictus.xmd.ui.theme.XmdShapes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import com.invictus.xmd.ui.components.XmdAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.invictus.xmd.ui.icons.Icon
import com.invictus.xmd.ui.icons.Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invictus.xmd.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.invictus.xmd.database.entities.Shortcut
import com.invictus.xmd.ui.browser.BrowserFragment
import com.invictus.xmd.ui.components.WideDialogProperties
import com.invictus.xmd.ui.components.wideDialogWidth
import com.invictus.xmd.utils.FaviconLoader

/**
 * Chrome-style speed-dial "new tab" page: SHORTCUTS header + Reorder/Done
 * toggle, a 4-column grid of tiles plus a trailing "+" tile, and the
 * add/edit/options dialogs. Replaces the RecyclerView+ShortcutAdapter+
 * ItemTouchHelper+3×MaterialAlertDialogBuilder that used to live on
 * BrowserFragment -- see ShortcutsViewModel for where that state moved.
 *
 * [onOpenUrl] loads a tapped tile's URL in the current tab; [onPickIcon]
 * launches the system photo picker (owned by BrowserFragment, since a
 * ViewModel can't hold an ActivityResultLauncher) -- the result comes back
 * via ShortcutsViewModel.onIconPicked, not through this composable.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShortcutsScreen(
    modifier: Modifier = Modifier,
    viewModel: ShortcutsViewModel = viewModel(),
    onOpenUrl: (Shortcut) -> Unit,
    onPickIcon: () -> Unit,
    bottomPadding: Dp = 96.dp,
) {
    val shortcuts by viewModel.displayOrder.collectAsState()
    val reorderMode by viewModel.reorderMode.collectAsState()
    val dialog by viewModel.dialog.collectAsState()
    val pendingIconUri by viewModel.pendingIconUri.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(14.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.label_shortcuts),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(if (reorderMode) R.string.action_done else R.string.action_reorder),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier
                    .combinedClickable(onClick = { viewModel.toggleReorderMode() })
                    .padding(6.dp),
            )
        }

        ReorderableShortcutGrid(
            shortcuts = shortcuts,
            reorderMode = reorderMode,
            onMove = viewModel::moveItem,
            onTap = onOpenUrl,
            onLongPress = viewModel::onTileLongPressed,
            onAddTap = viewModel::onAddTapped,
            modifier = Modifier.fillMaxWidth(),
            bottomPadding = bottomPadding,
        )
    }

    when (val d = dialog) {
        is ShortcutsViewModel.Dialog.None -> Unit
        is ShortcutsViewModel.Dialog.Add -> AddEditShortcutDialog(
            titleRes = R.string.add_bookmark_title,
            existing = null,
            pendingIconUri = pendingIconUri,
            onPickIcon = onPickIcon,
            onSave = viewModel::onSaveAdd,
            onDismiss = viewModel::onDialogDismissed,
        )
        is ShortcutsViewModel.Dialog.Edit -> AddEditShortcutDialog(
            titleRes = R.string.edit_bookmark_title,
            existing = d.shortcut,
            pendingIconUri = pendingIconUri,
            onPickIcon = onPickIcon,
            onSave = { title, url -> viewModel.onSaveEdit(d.shortcut, title, url) },
            onDismiss = viewModel::onDialogDismissed,
        )
        is ShortcutsViewModel.Dialog.Options -> ShortcutOptionsDialog(
            shortcut = d.shortcut,
            onEdit = { viewModel.onEditSelected(d.shortcut) },
            onDelete = { viewModel.onDeleteSelected(d.shortcut) },
            onDismiss = viewModel::onDialogDismissed,
        )
    }
}

// ── Grid + drag-to-reorder ───────────────────────────────────────────────

private const val GRID_COLUMNS = 5

/**
 * Hand-rolled drag-to-reorder (no reorderable-grid primitive in the Compose
 * Foundation BOM pinned at Phase 0 -- see COMPOSE_MIGRATION.md Phase 4).
 * Long-press starts a drag (only while [reorderMode] is on, matching the
 * old ItemTouchHelper's isLongPressDragEnabled=false + explicit startDrag);
 * dragging swaps items once the dragged tile's center crosses a
 * neighbor's, same trigger condition ItemTouchHelper used. The trailing
 * "+" tile is never a drag source or target.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReorderableShortcutGrid(
    shortcuts: List<Shortcut>,
    reorderMode: Boolean,
    onMove: (Int, Int) -> Unit,
    onTap: (Shortcut) -> Unit,
    onLongPress: (Shortcut) -> Unit,
    onAddTap: () -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 96.dp,
) {
    var draggingIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    // Last-measured on-screen bounds of each grid slot, keyed by index --
    // used to find which neighbor the dragged tile's center has crossed.
    val itemBounds = remember { mutableStateMapOf<Int, Rect>() }
    val haptic = LocalHapticFeedback.current
    // Titles used by more than one tile -- those show their host under the
    // name so near-identical tiles (mirrors, "Vegamovies ...") can be told apart.
    val duplicateTitles = remember(shortcuts) {
        shortcuts.groupBy { it.title.trim().lowercase() }.filterValues { it.size > 1 }.keys
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        modifier = modifier,
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = bottomPadding),
    ) {
        itemsIndexed(shortcuts, key = { _, s -> s.id }) { index, shortcut ->
            val isDragging = index == draggingIndex
            // index shifts as siblings reorder mid-drag; pointerInput below
            // is keyed on the stable shortcut.id instead, and reads the
            // latest index through this rather than closing over a stale
            // one -- otherwise a mid-drag recomposition would restart (and
            // abort) the gesture the moment this tile's position changes.
            val liveIndex by androidx.compose.runtime.rememberUpdatedState(index)
            val interaction = remember(shortcut.id) { MutableInteractionSource() }
            ShortcutTile(
                shortcut = shortcut,
                showHost = shortcut.title.trim().lowercase() in duplicateTitles,
                interactionSource = interaction,
                modifier = Modifier
                    // Smooth reflow for the tiles NOT under the finger, matching
                    // RecyclerView's default item animator; the dragged tile is
                    // driven imperatively by dragOffset above instead, so it
                    // doesn't fight its own animateItem().
                    .then(if (!isDragging) Modifier.animateItem() else Modifier)
                    .onGloballyPositioned { coords -> itemBounds[index] = coords.boundsInParent() }
                    .graphicsLayer {
                        translationX = if (isDragging) dragOffset.x else 0f
                        translationY = if (isDragging) dragOffset.y else 0f
                        shadowElevation = if (isDragging) 8f else 0f
                    }
                    .zIndex(if (isDragging) 1f else 0f)
                    .then(
                        if (reorderMode) {
                            Modifier.pointerInputDragReorder(
                                itemId = shortcut.id,
                                indexProvider = { liveIndex },
                                itemBounds = itemBounds,
                                itemCount = shortcuts.size,
                                onDragStart = { draggingIndex = liveIndex; dragOffset = Offset.Zero },
                                onDragBy = { dragOffset += it },
                                onSwap = { from, to ->
                                    val oldBounds = itemBounds[from]
                                    val newBounds = itemBounds[to]
                                    if (oldBounds != null && newBounds != null) {
                                        dragOffset += Offset(oldBounds.left - newBounds.left, oldBounds.top - newBounds.top)
                                    }
                                    onMove(from, to)
                                    draggingIndex = to
                                },
                                onDragEnd = { draggingIndex = -1; dragOffset = Offset.Zero },
                            )
                        } else {
                            Modifier.combinedClickable(
                                interactionSource = interaction,
                                indication = null,
                                onClick = { onTap(shortcut) },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onLongPress(shortcut)
                                },
                            )
                        }
                    ),
            )
        }
        item {
            AddShortcutTile(onClick = if (!reorderMode) onAddTap else null)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.pointerInputDragReorder(
    itemId: String,
    indexProvider: () -> Int,
    itemBounds: Map<Int, Rect>,
    itemCount: Int,
    onDragStart: () -> Unit,
    onDragBy: (Offset) -> Unit,
    onSwap: (from: Int, to: Int) -> Unit,
    onDragEnd: () -> Unit,
): Modifier = this.then(
    // Keyed on the stable id (not the position-derived index) so a mid-drag
    // reorder doesn't cancel this pointerInput coroutine and drop the drag.
    Modifier.pointerInput(itemId, itemCount) {
        var currentIndex = indexProvider()
        var accumulatedOffset = Offset.Zero
        detectDragGesturesAfterLongPress(
            onDragStart = {
                currentIndex = indexProvider()
                accumulatedOffset = Offset.Zero
                onDragStart()
            },
            onDrag = { change, dragAmount ->
                change.consume()
                accumulatedOffset += dragAmount
                onDragBy(dragAmount)
                val draggedCenter = (itemBounds[currentIndex]?.center ?: return@detectDragGesturesAfterLongPress) + accumulatedOffset
                // The last slot is always the "+" tile -- never a valid swap target.
                val target = itemBounds.entries
                    .filter { it.key != currentIndex && it.key < itemCount }
                    .minByOrNull { (it.value.center - draggedCenter).getDistance() }
                if (target != null && (target.value.center - draggedCenter).getDistance() < target.value.width / 2) {
                    onSwap(currentIndex, target.key)
                    currentIndex = target.key
                }
            },
            onDragEnd = { onDragEnd() },
            onDragCancel = { onDragEnd() },
        )
    }
)

// ── Tiles ─────────────────────────────────────────────────────────────────

private val TileShape = XmdShapes.M
private val GridTileShape = XmdShapes.S
private val GridIconSize = 46.dp

@Composable
private fun ShortcutTile(
    shortcut: Shortcut,
    showHost: Boolean,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
) {
    var iconBitmap by remember(shortcut.id, shortcut.customIconPath, shortcut.url) {
        mutableStateOf<android.graphics.Bitmap?>(null)
    }
    androidx.compose.runtime.LaunchedEffect(shortcut.id, shortcut.customIconPath, shortcut.url) {
        val customPath = shortcut.customIconPath
        iconBitmap = withContext(Dispatchers.IO) {
            if (customPath != null) {
                runCatching { android.graphics.BitmapFactory.decodeFile(customPath) }.getOrNull()
            } else {
                FaviconLoader.load(shortcut.url)
            }
        }
    }

    val host = remember(shortcut.url) {
        (runCatching { java.net.URI(shortcut.url).host }.getOrNull() ?: shortcut.url).removePrefix("www.")
    }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "tile_press")
    val title = shortcut.title.ifBlank { stringResource(R.string.label_bookmark_placeholder) }

    // Fallback avatar colour is stable per host so a tile keeps its colour.
    val scheme = MaterialTheme.colorScheme
    val (avatarBg, avatarFg) = when (kotlin.math.abs(host.hashCode()) % 3) {
        0 -> scheme.primaryContainer to scheme.onPrimaryContainer
        1 -> scheme.secondaryContainer to scheme.onSecondaryContainer
        else -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    }

    Column(
        modifier = modifier
            .padding(horizontal = 2.dp, vertical = 6.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(
            modifier = Modifier.size(GridIconSize),
            shape = GridTileShape,
            colors = CardDefaults.cardColors(containerColor = avatarBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            val bitmap = iconBitmap
            if (bitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = title.trim().firstOrNull()?.uppercase() ?: "?",
                        color = avatarFg,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = if (showHost) 1 else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (showHost) {
            Text(
                text = host,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 8.sp,
                lineHeight = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AddShortcutTile(onClick: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .padding(horizontal = 2.dp, vertical = 6.dp)
            .then(if (onClick != null) Modifier.combinedClickable(onClick = onClick) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(GridIconSize)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, GridTileShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, GridTileShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Add,
                contentDescription = stringResource(R.string.action_add_shortcut),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.action_add),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

// ── Dialogs ───────────────────────────────────────────────────────────────

@Composable
private fun AddEditShortcutDialog(
    titleRes: Int,
    existing: Shortcut?,
    pendingIconUri: android.net.Uri?,
    onPickIcon: () -> Unit,
    onSave: (title: String, url: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var url by remember(existing?.id) { mutableStateOf(existing?.url.orEmpty()) }
    val context = LocalContext.current

    var previewBitmap by remember(existing?.id, existing?.customIconPath, pendingIconUri) {
        mutableStateOf<android.graphics.Bitmap?>(null)
    }
    androidx.compose.runtime.LaunchedEffect(existing?.id, existing?.customIconPath, pendingIconUri, existing?.url) {
        previewBitmap = withContext(Dispatchers.IO) {
            when {
                pendingIconUri != null -> runCatching {
                    context.contentResolver.openInputStream(pendingIconUri)?.use {
                        android.graphics.BitmapFactory.decodeStream(it)
                    }
                }.getOrNull()
                existing?.customIconPath != null -> runCatching {
                    android.graphics.BitmapFactory.decodeFile(existing.customIconPath)
                }.getOrNull()
                existing != null -> FaviconLoader.load(existing.url)
                else -> null
            }
        }
    }

    XmdAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.wideDialogWidth(),
        properties = WideDialogProperties,
        title = { Text(stringResource(titleRes)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, TileShape)
                        .combinedClickableCompat(onPickIcon),
                    contentAlignment = Alignment.Center,
                ) {
                    val bitmap = previewBitmap
                    if (bitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.action_shortcut_pick_icon),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    modifier = Modifier.combinedClickableCompat(onPickIcon),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.hint_shortcut_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.hint_url)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(),
                )
            }
        },
        confirmButton = {
            TextButton(shapes = androidx.compose.material3.ButtonDefaults.shapes(), onClick = {
                if (url.trim().isEmpty()) return@TextButton
                onSave(title.trim(), url.trim())
            }) {
                Text(stringResource(if (existing == null) R.string.action_add else R.string.settings_save))
            }
        },
        dismissButton = {
            TextButton(shapes = androidx.compose.material3.ButtonDefaults.shapes(), onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
private fun ShortcutOptionsDialog(
    shortcut: Shortcut,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val host = remember(shortcut.url) {
        (runCatching { java.net.URI(shortcut.url).host }.getOrNull() ?: shortcut.url).removePrefix("www.")
    }
    XmdAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.wideDialogWidth(),
        properties = WideDialogProperties,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), XmdShapes.S),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = shortcut.title.trim().firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = shortcut.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = host,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                DialogOptionRow(
                    icon = Icons.Edit,
                    label = stringResource(R.string.edit_bookmark_title),
                    onClick = onEdit,
                )
                DialogOptionRow(
                    icon = Icons.Delete,
                    label = stringResource(R.string.action_delete),
                    onClick = onDelete,
                    destructive = true,
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(shapes = androidx.compose.material3.ButtonDefaults.shapes(), onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
private fun DialogOptionRow(
    icon: com.invictus.xmd.ui.icons.AppIcon,
    label: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val accent = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        shape = XmdShapes.S,
        color = if (destructive) MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = accent,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit): Modifier =
    this.combinedClickable(onClick = onClick)
