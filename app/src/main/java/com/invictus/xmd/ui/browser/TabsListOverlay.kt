@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.invictus.xmd.ui.browser

import com.invictus.xmd.ui.theme.XmdShapes
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.invictus.xmd.ui.components.XmdAlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.invictus.xmd.R
import com.invictus.xmd.preferences.Settings
import com.invictus.xmd.ui.icons.Icon
import com.invictus.xmd.ui.icons.Icons
import com.invictus.xmd.utils.FaviconLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Phase 5 (Browser) Tabs tray rendered via Material 3 ModalBottomSheet.
 *
 * Rendered in a platform dialog/popup window so it automatically renders
 * above the Activity's bottom navigation bar, dimming the entire screen
 * behind it with a native scrim, downward swipe-to-dismiss gesture, and
 * predictive/system back handling.
 *
 * Supports:
 * 1. Chrome-style 2-column Grid view with live webpage content thumbnails
 * 2. Compact List view (rows with swipe-to-dismiss)
 * 3. Clear all open tabs button with confirmation prompt
 */
data class TabOverlayItem(
    val id: Long,
    val title: String,
    val url: String?,
    val isPrivate: Boolean,
    val thumbnail: Bitmap? = null,
)

// Private tabs get a fixed dark tonal treatment regardless of app theme --
// same idea as Chrome's distinct grey/black incognito tab strip.
private val PrivateTabActiveColor = Color(0xFF3A3A3A)
private val PrivateTabInactiveColor = Color(0xFF2A2A2A)

private data class TabColors(
    val container: Color,
    val onContainer: Color,
    val border: Color,
)

@Composable
private fun tabColors(item: TabOverlayItem, isActive: Boolean): TabColors {
    val container = when {
        item.isPrivate -> if (isActive) PrivateTabActiveColor else PrivateTabInactiveColor
        isActive -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val onContainer = when {
        item.isPrivate -> Color.White
        isActive -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val border = when {
        isActive -> MaterialTheme.colorScheme.primary
        item.isPrivate -> Color(0xFF4A4A4A)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }
    return TabColors(container, onContainer, border)
}

private fun maxTabsContentHeight(configuration: Configuration): Dp {
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    return if (isLandscape) {
        (configuration.screenHeightDp * 0.72f).coerceIn(200f, 340f).dp
    } else {
        (configuration.screenHeightDp * 0.70f).coerceIn(240f, 540f).dp
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsListOverlay(
    visible: Boolean,
    tabs: List<TabOverlayItem>,
    currentTabId: Long?,
    onSwitch: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onCloseAll: () -> Unit,
    onAddNew: () -> Unit,
    onDismiss: () -> Unit,
    // Chrome-style "Tab closed · Undo": undoCount > 0 while closes can still
    // be undone; undoEvent bumps on every close so the snackbar restarts.
    undoCount: Int = 0,
    undoEvent: Int = 0,
    onUndo: () -> Unit = {},
    onUndoExpired: () -> Unit = {},
) {
    if (!visible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var isGridMode by remember { mutableStateOf(Settings.isTabsGridMode()) }
    var showConfirmCloseAll by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val undoMessage = if (undoCount > 1) {
        stringResource(R.string.tabs_closed_many, undoCount)
    } else {
        stringResource(R.string.tabs_closed_one)
    }
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(undoEvent) {
        if (undoCount > 0) {
            // A newer close cancels this effect (replacing the snackbar and
            // folding into "N tabs closed") without resolving it.
            val result = snackbarHostState.showSnackbar(
                message = undoMessage,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) onUndo() else onUndoExpired()
        }
    }

    fun closeWith(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) {
                onDismiss()
                action()
            }
        }
    }

    // Closing the ONLY tab resets it to a fresh "New tab": leave the tray so
    // that new-tab screen shows instead of the closed page's tray card.
    val closeTabFromTray: (Long) -> Unit = { id ->
        val closingLast = tabs.size <= 1
        onClose(id)
        if (closingLast) closeWith {}
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 4.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.tabs_overlay_header, tabs.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    if (tabs.size > 1 || (tabs.size == 1 && tabs[0].url != null)) {
                        IconButton(shapes = androidx.compose.material3.IconButtonDefaults.shapes(), 
                            onClick = { showConfirmCloseAll = true }
                        ) {
                            Icon(
                                imageVector = Icons.DeleteSweep,
                                contentDescription = stringResource(R.string.action_close_all_tabs),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(shapes = androidx.compose.material3.IconButtonDefaults.shapes(), 
                        onClick = {
                            val newMode = !isGridMode
                            isGridMode = newMode
                            Settings.setTabsGridMode(newMode)
                        }
                    ) {
                        Icon(
                            imageVector = if (isGridMode) Icons.ViewList else Icons.GridView,
                            contentDescription = if (isGridMode) "Switch to list view" else "Switch to grid view",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (isGridMode) {
                        IconButton(shapes = androidx.compose.material3.IconButtonDefaults.shapes(), 
                            onClick = {
                                closeWith { onAddNew() }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Add,
                                contentDescription = stringResource(R.string.action_new_tab),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                val viewModeFadeSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                val viewModeScaleSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
                AnimatedContent(
                    targetState = isGridMode,
                    transitionSpec = {
                        (fadeIn(viewModeFadeSpec) + scaleIn(initialScale = 0.95f, animationSpec = viewModeScaleSpec))
                            .togetherWith(fadeOut(viewModeFadeSpec))
                    },
                    label = "TabsViewModeTransition",
                ) { gridMode ->
                    if (gridMode) {
                        TabsGridView(
                            tabs = tabs,
                            currentTabId = currentTabId,
                            onSwitch = { id -> closeWith { onSwitch(id) } },
                            onClose = closeTabFromTray,
                        )
                    } else {
                        TabsListView(
                            tabs = tabs,
                            currentTabId = currentTabId,
                            onSwitch = { id -> closeWith { onSwitch(id) } },
                            onClose = closeTabFromTray,
                            onAddNew = { closeWith { onAddNew() } },
                        )
                    }
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) { data -> Snackbar(snackbarData = data) }
        }
    }

    if (showConfirmCloseAll) {
        XmdAlertDialog(
            onDismissRequest = { showConfirmCloseAll = false },
            title = {
                Text(
                    text = stringResource(R.string.action_close_all_tabs),
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.close_all_tabs_confirmation),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(shapes = androidx.compose.material3.ButtonDefaults.shapes(), 
                    onClick = {
                        showConfirmCloseAll = false
                        onCloseAll()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.action_close_all_tabs),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(shapes = androidx.compose.material3.ButtonDefaults.shapes(), onClick = { showConfirmCloseAll = false }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun TabsGridView(
    tabs: List<TabOverlayItem>,
    currentTabId: Long?,
    onSwitch: (Long) -> Unit,
    onClose: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isTablet = configuration.screenWidthDp >= 600
    val isLandscapeOrTablet = isLandscape || isTablet
    val columns = if (isLandscapeOrTablet) 4 else 2
    val cardAspectRatio = if (isLandscape) 0.95f else 0.85f
    val maxGridHeight = maxTabsContentHeight(configuration)

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier
                .widthIn(max = if (isTablet && !isLandscape) 640.dp else 740.dp)
                .fillMaxWidth()
                .heightIn(max = maxGridHeight),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(
                items = tabs,
                key = { it.id },
            ) { tab ->
                TabGridCard(
                    item = tab,
                    isActive = tab.id == currentTabId,
                    aspectRatio = cardAspectRatio,
                    onClick = { onSwitch(tab.id) },
                    onCloseClick = { onClose(tab.id) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun TabGridCard(
    item: TabOverlayItem,
    isActive: Boolean,
    aspectRatio: Float = 0.85f,
    onClick: () -> Unit,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = tabColors(item, isActive)

    val domain = remember(item.url) {
        item.url?.let { extractDomain(it) }.orEmpty()
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .chromeSwipeToClose(onCloseClick)
            .clip(XmdShapes.S)
            .clickable(onClick = onClick),
        color = colors.container,
        shape = XmdShapes.S,
        border = BorderStroke(if (isActive) 2.dp else 1.dp, colors.border),
        tonalElevation = if (isActive) 4.dp else 1.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Card Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TabFavicon(
                    item = item,
                    size = 22.dp,
                    iconSize = 13.dp,
                    imageSize = 15.dp,
                )
                Text(
                    text = item.title.ifBlank { item.url ?: stringResource(R.string.action_new_tab) },
                    color = colors.onContainer,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp),
                )
                IconButton(shapes = androidx.compose.material3.IconButtonDefaults.shapes(), 
                    onClick = onCloseClick,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Close,
                        contentDescription = stringResource(R.string.action_dismiss),
                        tint = colors.onContainer.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            // Card Body Preview
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 6.dp, bottom = 6.dp)
                    .clip(XmdShapes.S)
                    .background(
                        if (item.isPrivate) Color(0xFF1C1C1C)
                        else MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.85f)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (item.thumbnail != null) {
                    Image(
                        bitmap = item.thumbnail.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = if (item.isPrivate) Icons.VisibilityOff else Icons.Globe,
                        contentDescription = null,
                        tint = colors.onContainer.copy(alpha = 0.12f),
                        modifier = Modifier.size(44.dp),
                    )

                    Text(
                        text = if (item.isPrivate) "Incognito" else domain.ifBlank { stringResource(R.string.action_new_tab) },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onContainer.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TabsListView(
    tabs: List<TabOverlayItem>,
    currentTabId: Long?,
    onSwitch: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onAddNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val maxListHeight = maxTabsContentHeight(configuration)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .heightIn(max = maxListHeight),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                items = tabs,
                key = { it.id },
            ) { tab ->
                TabRow(
                    item = tab,
                    index = tabs.indexOf(tab),
                    isActive = tab.id == currentTabId,
                    onClick = { onSwitch(tab.id) },
                    onCloseClick = { onClose(tab.id) },
                )
            }
        }
        // Via-style: one centered "+" right under the last tab.
        IconButton(shapes = androidx.compose.material3.IconButtonDefaults.shapes(), onClick = onAddNew) {
            Icon(
                imageVector = Icons.Add,
                contentDescription = stringResource(R.string.action_new_tab),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabRow(
    item: TabOverlayItem,
    index: Int,
    isActive: Boolean,
    onClick: () -> Unit,
    onCloseClick: () -> Unit,
) {
    val alpha = remember { Animatable(0f) }
    val fadeSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    LaunchedEffect(item.id) {
        delay((index * 20L).coerceAtMost(160L))
        alpha.animateTo(1f, fadeSpec)
    }

    val colors = tabColors(item, isActive)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha.value)
            .chromeSwipeToClose(onCloseClick)
            .clip(XmdShapes.L)
            .clickable(onClick = onClick),
        color = colors.container,
        shape = XmdShapes.L,
        border = if (isActive) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, top = 6.dp, bottom = 6.dp, end = 10.dp),
        ) {
            TabFavicon(item = item)
            Text(
                text = item.title.ifBlank { item.url ?: stringResource(R.string.action_new_tab) },
                color = colors.onContainer,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            )
            IconButton(shapes = androidx.compose.material3.IconButtonDefaults.shapes(), onClick = onCloseClick) {
                Icon(
                    imageVector = Icons.Close,
                    contentDescription = stringResource(R.string.action_dismiss),
                    tint = colors.onContainer,
                )
            }
        }
    }
}

/**
 * Chrome tab-switcher swipe-to-close: the card/row tracks the finger
 * horizontally and fades as it leaves; releasing past ~1/3 of its width (or
 * with a decent fling) throws it off-screen in the swipe direction and closes
 * the tab, otherwise it springs back. No red backdrop, like Chrome.
 *
 * Drag deltas are applied synchronously and the release animation runs in a
 * single job that a new touch cancels -- never one coroutine per delta (those
 * raced the settle animation and left the card stuck mid-way).
 */
@Composable
private fun Modifier.chromeSwipeToClose(onClose: () -> Unit): Modifier {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var offsetX by remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    var width by remember { mutableFloatStateOf(1f) }
    val flingPx = with(LocalDensity.current) { 600.dp.toPx() }
    val spec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val currentOnClose by rememberUpdatedState(onClose)
    val dragState = rememberDraggableState { delta -> offsetX += delta }

    return this
        .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
        .graphicsLayer {
            translationX = offsetX
            alpha = (1f - abs(offsetX) / width).coerceIn(0f, 1f)
        }
        .draggable(
            state = dragState,
            orientation = Orientation.Horizontal,
            onDragStarted = {
                settleJob?.cancel()
                settleJob = null
            },
            onDragStopped = { velocity ->
                settleJob?.cancel()
                settleJob = scope.launch {
                    val dismiss = abs(offsetX) > width * 0.35f || abs(velocity) > flingPx
                    if (dismiss) {
                        val dir = when {
                            offsetX > 0f -> 1f
                            offsetX < 0f -> -1f
                            velocity > 0f -> 1f
                            else -> -1f
                        }
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        animate(
                            initialValue = offsetX,
                            targetValue = dir * width * 1.1f,
                            initialVelocity = if (velocity * dir > 0f) velocity else 0f,
                            animationSpec = spec,
                        ) { v, _ -> offsetX = v }
                        currentOnClose()
                        // Closing the only tab resets it to a blank tab (same
                        // id), so this item is NOT removed and would stay
                        // invisible. If still composed afterwards, slide back
                        // in; when the item is removed this job is cancelled.
                        delay(250)
                        animate(offsetX, 0f, animationSpec = spec) { v, _ -> offsetX = v }
                    } else {
                        animate(offsetX, 0f, animationSpec = spec) { v, _ -> offsetX = v }
                    }
                }
            },
        )
}

/**
 * Private tabs always show the incognito glyph, never the site's real favicon.
 */
@Composable
private fun TabFavicon(
    item: TabOverlayItem,
    size: Dp = 28.dp,
    iconSize: Dp = 16.dp,
    imageSize: Dp = 18.dp,
) {
    val bitmapState = if (!item.isPrivate && item.url != null) {
        produceState<android.graphics.Bitmap?>(initialValue = null, key1 = item.url) {
            value = withContext(Dispatchers.IO) { FaviconLoader.load(item.url) }
        }
    } else null

    Box(
        modifier = Modifier
            .size(size)
            .background(color = Color.White, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = bitmapState?.value
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(imageSize),
            )
        } else {
            Icon(
                imageVector = if (item.isPrivate) Icons.VisibilityOff else Icons.Link,
                contentDescription = null,
                tint = Color(0xFF1A1A1A),
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

private fun extractDomain(url: String): String {
    return try {
        val uri = Uri.parse(url)
        val host = uri.host
        if (!host.isNullOrBlank()) {
            host.removePrefix("www.")
        } else {
            url
        }
    } catch (_: Exception) {
        url
    }
}
