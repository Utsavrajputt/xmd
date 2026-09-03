package com.invictus.xmd.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.invictus.xmd.R
import com.invictus.xmd.core.FaviconLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Phase 5 (Browser) conversion of the old showTabsDialog() -- previously a
 * BottomSheetDialog hand-building one MaterialCardView pill row per tab
 * inside a plain LinearLayout. Kept the same single-column pill-list layout
 * (not the Chrome-style grid this migration originally sketched -- a
 * deliberate scope call, see COMPOSE_MIGRATION.md) and the same
 * favicon+title row content (no page thumbnails).
 *
 * Hosted in tabsListOverlay, a dedicated full-bleed ComposeView sibling to
 * browserDialogHost (not reusing that host) -- this is an overlay with real
 * on-screen bounds anchored in fragment_browser.xml, not a Dialog-window
 * popup, same reasoning Phase 5 Step 3 used for AddressBarSuggestions.
 *
 * BrowserTabState (BrowserViewModel.tabs) isn't Compose-observable, so
 * BrowserFragment owns a one-shot [tabs] snapshot here -- mirrors the
 * sniffedSheetStreams/suggestionItems pattern already used elsewhere in
 * this Fragment -- refreshed explicitly after every mutation (open/close)
 * rather than this composable reading BrowserViewModel directly.
 */
data class TabOverlayItem(
    val id: Long,
    val title: String,
    val url: String?,
    val isPrivate: Boolean,
)

// Private tabs get a fixed dark tonal treatment regardless of app theme --
// same idea as Chrome's distinct grey/black incognito tab strip, ported
// unchanged from the old dialog's hardcoded hex values.
private val PrivateTabActiveColor = Color(0xFF3A3A3A)
private val PrivateTabInactiveColor = Color(0xFF2A2A2A)

@Composable
fun TabsListOverlay(
    visible: Boolean,
    tabs: List<TabOverlayItem>,
    currentTabId: Long?,
    onSwitch: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onAddNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(120)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        // Swallows taps that land on panel whitespace so
                        // they don't fall through to the scrim's dismiss
                        // click behind it.
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {},
                    ),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            TabRow(
                                item = tab,
                                index = index,
                                isActive = tab.id == currentTabId,
                                onClick = { onSwitch(tab.id) },
                                onCloseClick = { onClose(tab.id) },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        FloatingActionButton(
                            onClick = onAddNew,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ) {
                            Icon(
                                painter = painterResource(XmdIcons.Add),
                                contentDescription = stringResource(R.string.action_new_tab),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabRow(
    item: TabOverlayItem,
    index: Int,
    isActive: Boolean,
    onClick: () -> Unit,
    onCloseClick: () -> Unit,
) {
    // Small staggered fade-in so the list doesn't just pop in -- same idea
    // as the old dialog's row.animate().alpha(1f)... entrance, minus the
    // translationY rise (fade-only; not reproduced 1:1, a deliberate
    // simplification).
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(item.id) {
        delay((index * 24L).coerceAtMost(200L))
        alpha.animateTo(1f, tween(160))
    }

    val tonalColor = when {
        item.isPrivate -> if (isActive) PrivateTabActiveColor else PrivateTabInactiveColor
        isActive -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val onTonalColor = when {
        item.isPrivate -> Color.White
        isActive -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha.value)
            .clickable(onClick = onClick),
        color = tonalColor,
        shape = RoundedCornerShape(28.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 10.dp),
        ) {
            TabFavicon(item = item, tint = onTonalColor)
            Text(
                text = item.title.ifBlank { item.url ?: stringResource(R.string.action_new_tab) },
                color = onTonalColor,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            )
            IconButton(onClick = onCloseClick) {
                Icon(
                    painter = painterResource(XmdIcons.Close),
                    contentDescription = stringResource(R.string.action_dismiss),
                    tint = onTonalColor,
                )
            }
        }
    }
}

/**
 * Private tabs always show the incognito glyph, never the site's real
 * favicon -- fetching/showing it here would be a minor but real leak of
 * what a "private" tab is looking at (ported unchanged from the old
 * dialog's same check).
 */
@Composable
private fun TabFavicon(item: TabOverlayItem, tint: Color) {
    val bitmapState = if (!item.isPrivate && item.url != null) {
        produceState<android.graphics.Bitmap?>(initialValue = null, key1 = item.url) {
            value = withContext(Dispatchers.IO) { FaviconLoader.load(item.url) }
        }
    } else null

    Box(
        modifier = Modifier
            .size(32.dp)
            .background(color = Color.White, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = bitmapState?.value
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Icon(
                painter = painterResource(
                    if (item.isPrivate) XmdIcons.VisibilityOff else XmdIcons.Link
                ),
                contentDescription = null,
                tint = Color(0xFF1A1A1A),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
