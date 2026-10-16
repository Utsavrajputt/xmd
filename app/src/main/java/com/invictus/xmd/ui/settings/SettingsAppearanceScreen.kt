package com.invictus.xmd.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.invictus.xmd.R
import com.invictus.xmd.ui.icons.AppIcon
import com.invictus.xmd.ui.icons.Icon
import com.invictus.xmd.ui.icons.Icons
import com.invictus.xmd.ui.theme.AppTheme
import com.invictus.xmd.preferences.Settings
import com.invictus.xmd.ui.shortcuts.ShortcutsScreen

/**
 * Theme color + dark mode / AMOLED switches, plus bottom-nav tab config
 * (reorder / hide / default tab). Uses mpvRx ThemePicker and ThemePreviewCards.
 */
@Composable
fun SettingsAppearanceScreen(
    currentTheme: AppTheme,
    isDark: Boolean,
    isAmoled: Boolean,
    onThemeSelected: (AppTheme, Offset) -> Unit,
    onDarkModeChanged: (Boolean) -> Unit,
    onAmoledModeChanged: (Boolean) -> Unit,
    tabOrder: List<String>,
    hiddenTabs: Set<String>,
    defaultTab: String,
    onMoveTab: (fromIndex: Int, toIndex: Int) -> Unit,
    onToggleTabVisible: (tabId: String, visible: Boolean) -> Unit,
    onDefaultTabSelected: (tabId: String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
    ) {
        ThemePicker(
            currentTheme = currentTheme,
            isDarkMode = isDark,
            isAmoled = isAmoled,
            onThemeSelected = onThemeSelected,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            SettingsSectionCard {
                SwitchSettingRow(
                    title = stringResource(R.string.settings_dark_mode),
                    subtitle = stringResource(R.string.settings_dark_mode_hint),
                    checked = isDark,
                    onCheckedChange = onDarkModeChanged,
                )

                SettingsDivider()

                SwitchSettingRow(
                    title = stringResource(R.string.settings_amoled_mode),
                    subtitle = stringResource(R.string.settings_amoled_mode_hint),
                    checked = isAmoled,
                    enabled = isDark,
                    onCheckedChange = onAmoledModeChanged,
                )
            }

            var showAppIconDialog by remember { mutableStateOf(false) }
            SettingsSectionCard(modifier = Modifier.padding(top = 16.dp)) {
                ClickableSettingRow(
                    title = stringResource(R.string.settings_app_icon_title),
                    subtitle = stringResource(R.string.settings_app_icon_hint),
                    onClick = { showAppIconDialog = true },
                )
            }
            if (showAppIconDialog) {
                AppIconDialog(onDismiss = { showAppIconDialog = false })
            }

            var showWallpaperDialog by remember { mutableStateOf(false) }
            SettingsSectionCard(modifier = Modifier.padding(top = 16.dp)) {
                ClickableSettingRow(
                    title = stringResource(R.string.settings_wallpaper_title),
                    subtitle = stringResource(R.string.settings_wallpaper_hint),
                    onClick = { showWallpaperDialog = true },
                )
            }
            if (showWallpaperDialog) {
                WallpaperDialog(onDismiss = { showWallpaperDialog = false })
            }

            SettingsSectionCard(
                modifier = Modifier.padding(top = 16.dp),
                contentPadding = PaddingValues(16.dp),
            ) {
                TabsSection(
                    tabOrder = tabOrder,
                    hiddenTabs = hiddenTabs,
                    defaultTab = defaultTab,
                    onMoveTab = onMoveTab,
                    onToggleTabVisible = onToggleTabVisible,
                    onDefaultTabSelected = onDefaultTabSelected,
                )
            }
        }
    }
}

@Composable
fun ThemePicker(
    currentTheme: AppTheme,
    isDarkMode: Boolean,
    isAmoled: Boolean,
    onThemeSelected: (AppTheme, Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        val index = AppTheme.entries.indexOf(currentTheme)
        if (index >= 0) {
            listState.animateScrollToItem(maxOf(0, index - 1))
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.settings_theme_hint),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 10.dp),
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(AppTheme.entries, key = { it.name }) { theme ->
                ThemePreviewCard(
                    theme = theme,
                    isSelected = theme == currentTheme,
                    isDarkMode = isDarkMode,
                    isAmoled = isAmoled,
                    onClick = { position -> onThemeSelected(theme, position) },
                )
            }
        }
    }
}

@Composable
fun ThemePreviewCard(
    theme: AppTheme,
    isSelected: Boolean,
    isDarkMode: Boolean,
    isAmoled: Boolean,
    onClick: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    var cardOrigin by remember { mutableStateOf(Offset.Zero) }
    val colorScheme = if (isDarkMode) {
        if (isAmoled) theme.getAmoledColorScheme() else theme.getDarkColorScheme()
    } else {
        theme.getLightColorScheme()
    }
    val selectionColor = MaterialTheme.colorScheme.primary
    val borderWidth = if (isSelected) 3.dp else 1.dp
    val borderColor = if (isSelected) selectionColor else if (isAmoled && isDarkMode) Color(0xFF242424) else Color.Transparent
    val elevation = if (isSelected) 8.dp else 2.dp

    Column(
        modifier = modifier
            .width(100.dp)
            .onGloballyPositioned { cardOrigin = it.boundsInWindow().topLeft }
            .pointerInput(Unit) {
                detectTapGestures { localPosition ->
                    onClick(cardOrigin + localPosition)
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(width = 90.dp, height = 140.dp)
                .shadow(
                    elevation = elevation,
                    shape = RoundedCornerShape(12.dp),
                    ambientColor = if (isSelected) selectionColor.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.2f),
                    spotColor = if (isSelected) selectionColor.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.2f),
                )
                .clip(RoundedCornerShape(12.dp))
                .background(colorScheme.surface)
                .border(
                    width = borderWidth,
                    color = borderColor,
                    shape = RoundedCornerShape(12.dp),
                ),
        ) {
            Column(
                modifier = Modifier
                    .matchParentSize()
                    .padding(if (isSelected) 3.dp else 1.dp)
                    .clip(RoundedCornerShape(if (isSelected) 9.dp else 11.dp))
                    .background(colorScheme.background)
                    .padding(8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colorScheme.surfaceVariant),
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp),
                    color = colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 24.dp, height = 12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(colorScheme.primary),
                        )
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(colorScheme.tertiary),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colorScheme.surfaceVariant),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(colorScheme.secondary),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = stringResource(theme.titleRes),
            style = MaterialTheme.typography.bodySmall,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private data class TabMeta(val icon: AppIcon, val labelRes: Int)

private val TAB_META = mapOf(
    Settings.TabId.DOWNLOADS to TabMeta(Icons.Downloads, R.string.tab_downloads),
    Settings.TabId.ADD to TabMeta(Icons.Add, R.string.tab_add),
    Settings.TabId.BROWSER to TabMeta(Icons.Public, R.string.tab_browser),
)

@Composable
private fun TabsSection(
    tabOrder: List<String>,
    hiddenTabs: Set<String>,
    defaultTab: String,
    onMoveTab: (fromIndex: Int, toIndex: Int) -> Unit,
    onToggleTabVisible: (tabId: String, visible: Boolean) -> Unit,
    onDefaultTabSelected: (tabId: String) -> Unit,
) {
    val context = LocalContext.current

    Text(
        text = stringResource(R.string.settings_tabs_title),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = stringResource(R.string.settings_tabs_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
    )

    var draggingIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    // Last-measured on-screen bounds of each row, keyed by index -- used to
    // find which neighbor the dragged row's center has crossed. Same
    // approach as ReorderableShortcutGrid's pointerInputDragReorder.
    val itemBounds = remember { mutableStateMapOf<Int, Rect>() }

    // Only page tabs can be reordered (Home, Downloads, Browser).
    // The "+ Add" button is a fixed action button and cannot be rearranged.
    val pageTabs = remember(tabOrder) {
        tabOrder.filter { it != Settings.TabId.ADD }
    }

    pageTabs.forEachIndexed { index, tabId ->
        val meta = TAB_META[tabId] ?: return@forEachIndexed
        val isDragging = index == draggingIndex
        // Keyed on the stable tabId (not the position-derived index) so a
        // mid-drag reorder doesn't reset this row's composition state --
        // same reasoning as itemsIndexed's key param in ReorderableShortcutGrid.
        key(tabId) {
            // index shifts as siblings reorder mid-drag; the drag gesture
            // reads the latest index through this instead of a stale capture.
            val liveIndex by rememberUpdatedState(index)
            TabConfigRow(
                icon = meta.icon,
                label = stringResource(meta.labelRes),
                visible = tabId !in hiddenTabs,
                isDragging = isDragging,
                dragOffsetY = if (isDragging) dragOffset.y else 0f,
                canReorder = true,
                modifier = Modifier.onGloballyPositioned { coords -> itemBounds[index] = coords.boundsInParent() },
                dragHandleModifier = Modifier.pointerInputTabDragReorder(
                    tabId = tabId,
                    indexProvider = { liveIndex },
                    itemBounds = itemBounds,
                    onDragStart = { draggingIndex = liveIndex; dragOffset = Offset.Zero },
                    onDragBy = { dragOffset += it },
                    onSwap = { from, to ->
                        val oldBounds = itemBounds[from]
                        val newBounds = itemBounds[to]
                        if (oldBounds != null && newBounds != null) {
                            dragOffset += Offset(0f, oldBounds.top - newBounds.top)
                        }
                        onMoveTab(from, to)
                        draggingIndex = to
                    },
                    onDragEnd = { draggingIndex = -1; dragOffset = Offset.Zero },
                ),
                onVisibleChange = { visible ->
                    val visiblePageCount = pageTabs.count { it !in hiddenTabs }
                    if (!visible && visiblePageCount <= 1) {
                        Toast.makeText(
                            context,
                            R.string.settings_tabs_last_visible_toast,
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        onToggleTabVisible(tabId, visible)
                    }
                },
            )
        }
    }

    // Pinned "+ Add" tab row -- cannot be rearranged, so no drag handle or reorder gestures
    val addMeta = TAB_META[Settings.TabId.ADD]
    if (addMeta != null) {
        key(Settings.TabId.ADD) {
            TabConfigRow(
                icon = addMeta.icon,
                label = stringResource(addMeta.labelRes),
                visible = Settings.TabId.ADD !in hiddenTabs,
                isDragging = false,
                dragOffsetY = 0f,
                canReorder = false,
                dragHandleModifier = Modifier,
                onVisibleChange = { visible ->
                    onToggleTabVisible(Settings.TabId.ADD, visible)
                },
            )
        }
    }

    HorizontalDivider(
        modifier = Modifier.padding(top = 10.dp, bottom = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
    )

    Text(
        text = stringResource(R.string.settings_default_tab_title),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 8.dp),
    )

    pageTabs.forEach { tabId ->
        if (tabId in hiddenTabs) return@forEach
        val meta = TAB_META[tabId] ?: return@forEach
        DefaultTabRadioRow(
            icon = meta.icon,
            label = stringResource(meta.labelRes),
            selected = tabId == defaultTab,
            onSelect = { onDefaultTabSelected(tabId) },
        )
    }
}

/**
 * Hand-rolled drag-to-reorder for the (non-lazy) Bottom Tabs Column --
 * mirrors ReorderableShortcutGrid's pointerInputDragReorder in
 * ShortcutsScreen.kt (same swap-on-crossed-center approach), adapted to a
 * single vertical axis and a dedicated drag handle instead of long-press
 * anywhere on the row.
 */
private fun Modifier.pointerInputTabDragReorder(
    tabId: String,
    indexProvider: () -> Int,
    itemBounds: Map<Int, Rect>,
    onDragStart: () -> Unit,
    onDragBy: (Offset) -> Unit,
    onSwap: (from: Int, to: Int) -> Unit,
    onDragEnd: () -> Unit,
): Modifier = this.then(
    // Keyed on the stable tabId so a mid-drag reorder doesn't cancel this
    // pointerInput coroutine and drop the drag.
    Modifier.pointerInput(tabId) {
        var currentIndex = indexProvider()
        var accumulatedOffset = Offset.Zero
        detectDragGestures(
            onDragStart = {
                currentIndex = indexProvider()
                accumulatedOffset = Offset.Zero
                onDragStart()
            },
            onDrag = { change, dragAmount ->
                change.consume()
                accumulatedOffset += dragAmount
                onDragBy(dragAmount)
                val draggedCenter = (itemBounds[currentIndex]?.center ?: return@detectDragGestures) + accumulatedOffset
                val target = itemBounds.entries
                    .filter { it.key != currentIndex }
                    .minByOrNull { (it.value.center - draggedCenter).getDistance() }
                if (target != null && (target.value.center - draggedCenter).getDistance() < target.value.height / 2) {
                    onSwap(currentIndex, target.key)
                    currentIndex = target.key
                }
            },
            onDragEnd = { onDragEnd() },
            onDragCancel = { onDragEnd() },
        )
    }
)

/**
 * A single reorderable row in the Bottom Tabs list. Dragging the handle on
 * the left reorders the row; the switch on the right independently
 * shows/hides the tab. [modifier] carries the row-level position tracking
 * from TabsSection, while [dragHandleModifier] carries the
 * pointerInputTabDragReorder wiring -- kept separate so only the handle
 * itself starts a drag, leaving the switch and page scroll untouched.
 */
@Composable
private fun TabConfigRow(
    icon: AppIcon,
    label: String,
    visible: Boolean,
    isDragging: Boolean,
    dragOffsetY: Float,
    dragHandleModifier: Modifier,
    onVisibleChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    canReorder: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                translationY = dragOffsetY
                shadowElevation = if (isDragging) 4f else 0f
            }
            .background(
                color = if (isDragging) {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                } else {
                    Color.Transparent
                },
                shape = RoundedCornerShape(8.dp),
            )
            .padding(vertical = 6.dp, horizontal = 4.dp),
    ) {
        if (canReorder) {
            Icon(
                imageVector = Icons.DragHandle,
                contentDescription = stringResource(R.string.action_reorder),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(22.dp).then(dragHandleModifier),
            )
        } else {
            Spacer(modifier = Modifier.size(22.dp))
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 10.dp, end = 12.dp).size(18.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = visible, onCheckedChange = onVisibleChange)
    }
}

@Composable
private fun DefaultTabRadioRow(
    icon: AppIcon,
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(vertical = 8.dp),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 10.dp).size(18.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
