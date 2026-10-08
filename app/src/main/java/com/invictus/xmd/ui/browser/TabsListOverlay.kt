package com.invictus.xmd.ui.browser

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
) {
    if (!visible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var isGridMode by remember { mutableStateOf(Settings.isTabsGridMode()) }
    var showConfirmCloseAll by remember { mutableStateOf(false) }

    fun closeWith(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) {
                onDismiss()
                action()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
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
                    IconButton(
                        onClick = { showConfirmCloseAll = true }
                    ) {
                        Icon(
                            imageVector = Icons.DeleteSweep,
                            contentDescription = stringResource(R.string.action_close_all_tabs),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(
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
                    IconButton(
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

            AnimatedContent(
                targetState = isGridMode,
                transitionSpec = {
                    (fadeIn(tween(180)) + scaleIn(initialScale = 0.95f, animationSpec = tween(180)))
                        .togetherWith(fadeOut(tween(140)))
                },
                label = "TabsViewModeTransition",
            ) { gridMode ->
                if (gridMode) {
                    TabsGridView(
                        tabs = tabs,
                        currentTabId = currentTabId,
                        onSwitch = { id -> closeWith { onSwitch(id) } },
                        onClose = onClose,
                    )
                } else {
                    TabsListView(
                        tabs = tabs,
                        currentTabId = currentTabId,
                        onSwitch = { id -> closeWith { onSwitch(id) } },
                        onClose = onClose,
                        onAddNew = { closeWith { onAddNew() } },
                    )
                }
            }
        }
    }

    if (showConfirmCloseAll) {
        AlertDialog(
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
                TextButton(
                    onClick = {
                        showConfirmCloseAll = false
                        closeWith { onCloseAll() }
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
                TextButton(onClick = { showConfirmCloseAll = false }) {
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
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = colors.container,
        shape = RoundedCornerShape(14.dp),
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
                IconButton(
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
                    .clip(RoundedCornerShape(12.dp))
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
        IconButton(onClick = onAddNew) {
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
    LaunchedEffect(item.id) {
        delay((index * 20L).coerceAtMost(160L))
        alpha.animateTo(1f, tween(140))
    }

    val colors = tabColors(item, isActive)

    val haptics = LocalHapticFeedback.current
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd || value == SwipeToDismissBoxValue.EndToStart) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onCloseClick()
                true
            } else {
                false
            }
        },
    )

    LaunchedEffect(item.id) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            dismissState.reset()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha.value),
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val alignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                Alignment.CenterStart
            } else {
                Alignment.CenterEnd
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment,
            ) {
                Icon(
                    imageVector = Icons.Close,
                    contentDescription = stringResource(R.string.action_dismiss),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
        },
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            color = colors.container,
            shape = RoundedCornerShape(28.dp),
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
                IconButton(onClick = onCloseClick) {
                    Icon(
                        imageVector = Icons.Close,
                        contentDescription = stringResource(R.string.action_dismiss),
                        tint = colors.onContainer,
                    )
                }
            }
        }
    }
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
