package com.invictus.xmd.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentContainerView
import com.invictus.xmd.R
import com.invictus.xmd.ui.icons.AppIcon
import com.invictus.xmd.ui.icons.Icon
import com.invictus.xmd.ui.icons.Icons
import com.invictus.xmd.ui.theme.LocalThemeTransitionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.invictus.xmd.preferences.Settings
import com.invictus.xmd.ui.downloads.DownloadsSelectionUiState

internal enum class MainDestination {
    Home,
    Downloads,
    Browser,
}

internal enum class MainNavigationItem(val destination: MainDestination?) {
    Home(MainDestination.Home),
    Downloads(MainDestination.Downloads),
    Add(null),
    Browser(MainDestination.Browser),
}

@Composable
internal fun MainShell(
    destination: MainDestination,
    navigationItems: List<MainNavigationItem>,
    activeDownloadCount: Int,
    searchActive: Boolean,
    searchQuery: String,
    snackbarHostState: SnackbarHostState,
    pagerPosition: Float,
    downloadsSelectionState: DownloadsSelectionUiState? = null,
    onSearchActiveChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onDestinationSelected: (MainDestination) -> Unit,
    onAddDownload: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleTheme: () -> Unit,
    onViewPagerReady: (androidx.viewpager2.widget.ViewPager2) -> Unit,
    onBottomBarDragStart: () -> Unit = {},
    onBottomBarDrag: (Float) -> Unit = {},
    onBottomBarDragEnd: () -> Unit = {},
    overlayActive: Boolean = false,
    overlay: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0

    val bottomBarSwipeModifier = Modifier.pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragStart = { onBottomBarDragStart() },
            onDragEnd = { onBottomBarDragEnd() },
            onDragCancel = { onBottomBarDragEnd() },
            onHorizontalDrag = { change, dragAmount ->
                change.consume()
                onBottomBarDrag(dragAmount)
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // Hidden while the History/Bookmarks overlay is showing --
            // SavedPagesScreen draws its own full-width header, and this bar
            // was still reserving its own height underneath/above it, which
            // showed up as a blank gap above the History/Bookmarks title.
            if (destination != MainDestination.Browser && !overlayActive) {
                DownloadsTopBar(
                    destination = destination,
                    searchActive = searchActive,
                    searchQuery = searchQuery,
                    downloadsSelectionState = if (destination == MainDestination.Downloads) downloadsSelectionState else null,
                    onSearchActiveChange = onSearchActiveChange,
                    onSearchQueryChange = onSearchQueryChange,
                    onOpenSettings = onOpenSettings,
                    onToggleTheme = onToggleTheme,
                )
            }
        },
        bottomBar = {
            if (!imeVisible) {
                val showSelectionPill = destination == MainDestination.Downloads && downloadsSelectionState != null
                AnimatedContent(
                    targetState = showSelectionPill,
                    transitionSpec = {
                        (slideInVertically { height -> height } + fadeIn())
                            .togetherWith(slideOutVertically { height -> height } + fadeOut())
                    },
                    label = "BottomBarSelectionTransition",
                ) { isSelecting ->
                    val selection = downloadsSelectionState
                    if (isSelecting && selection != null) {
                        DownloadsSelectionPill(
                            selection = selection,
                        )
                    } else {
                        MainNavigationBar(
                            destination = destination,
                            navigationItems = navigationItems,
                            position = pagerPosition,
                            activeDownloadCount = activeDownloadCount,
                            swipeModifier = bottomBarSwipeModifier,
                            onDestinationSelected = onDestinationSelected,
                            onAddDownload = onAddDownload,
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        val layoutDirection = LocalLayoutDirection.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = contentPadding.calculateStartPadding(layoutDirection),
                    top = contentPadding.calculateTopPadding(),
                    end = contentPadding.calculateEndPadding(layoutDirection),
                ),
        ) {
            AndroidView(
                factory = { context ->
                    androidx.viewpager2.widget.ViewPager2(context).apply {
                        id = R.id.viewPager
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        onViewPagerReady(this)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            overlay()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadsTopBar(
    destination: MainDestination,
    searchActive: Boolean,
    searchQuery: String,
    downloadsSelectionState: DownloadsSelectionUiState? = null,
    onSearchActiveChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onToggleTheme: () -> Unit,
) {
    if (downloadsSelectionState != null) {
        var showDropdown by remember { mutableStateOf(false) }

        TopAppBar(
            navigationIcon = {
                IconButton(onClick = downloadsSelectionState.onClose) {
                    Icon(
                        imageVector = Icons.Close,
                        contentDescription = stringResource(R.string.action_cancel),
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
            title = {
                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showDropdown = true }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "${downloadsSelectionState.selectedCount}/${downloadsSelectionState.totalCount} Selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.ArrowDown,
                            contentDescription = stringResource(R.string.selection_options),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }

                    DropdownMenu(
                        expanded = showDropdown,
                        onDismissRequest = { showDropdown = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.select_all)) },
                            leadingIcon = { Icon(Icons.Check, contentDescription = null) },
                            onClick = {
                                downloadsSelectionState.onSelectAll()
                                showDropdown = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.invert_selection)) },
                            leadingIcon = { Icon(Icons.Sync, contentDescription = null) },
                            onClick = {
                                downloadsSelectionState.onInvertSelection()
                                showDropdown = false
                            },
                        )
                    }
                }
            },
            actions = {
                if (downloadsSelectionState.canPause) {
                    IconButton(onClick = downloadsSelectionState.onPause) {
                        Icon(
                            imageVector = Icons.Pause,
                            contentDescription = stringResource(R.string.action_pause),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (downloadsSelectionState.canStart) {
                    IconButton(onClick = downloadsSelectionState.onStart) {
                        Icon(
                            imageVector = Icons.Play,
                            contentDescription = stringResource(R.string.action_resume),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        )
        return
    }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(searchActive) {
        if (searchActive) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    if (searchActive) {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = { onSearchActiveChange(false) }) {
                    Icon(
                        imageVector = Icons.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            },
            title = {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = { Text(stringResource(R.string.queue_search_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
            },
            actions = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Close,
                            contentDescription = stringResource(R.string.action_clear),
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        )
        return
    }

    val titleBounds = remember { mutableStateOf(Rect.Zero) }
    val themeTransition = LocalThemeTransitionState.current
    val coroutineScope = rememberCoroutineScope()

    TopAppBar(
        title = {
            Text(
                text = if (destination == MainDestination.Downloads) {
                    stringResource(R.string.tab_downloads)
                } else {
                    stringResource(R.string.app_header_title)
                },
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .onGloballyPositioned { coordinates ->
                        titleBounds.value = coordinates.boundsInWindow()
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { localOffset ->
                                if (themeTransition?.isAnimating == true) return@detectTapGestures

                                val windowOffset = Offset(
                                    titleBounds.value.left + localOffset.x,
                                    titleBounds.value.top + localOffset.y,
                                )
                                themeTransition?.startTransition(windowOffset)
                                coroutineScope.launch {
                                    delay(50)
                                    onToggleTheme()
                                }
                            }
                        )
                    },
            )
        },
        actions = {
            if (destination == MainDestination.Downloads) {
                IconButton(onClick = { onSearchActiveChange(true) }) {
                    Icon(
                        imageVector = Icons.Search,
                        contentDescription = stringResource(R.string.action_search),
                    )
                }
            }
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Settings,
                    contentDescription = stringResource(R.string.menu_settings),
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    )
}

@Composable
private fun DownloadsSelectionPill(
    selection: DownloadsSelectionUiState,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val isLargeScreen = screenWidthDp >= 600
    val isExtraLargeScreen = screenWidthDp >= 840

    val buttonSize = when {
        isExtraLargeScreen -> 52.dp
        isLargeScreen -> 46.dp
        else -> 38.dp
    }
    val iconSize = when {
        isExtraLargeScreen -> 28.dp
        isLargeScreen -> 24.dp
        else -> 20.dp
    }
    val pillHorizontalPadding = when {
        isExtraLargeScreen -> 20.dp
        isLargeScreen -> 16.dp
        else -> 10.dp
    }
    val pillVerticalPadding = when {
        isLargeScreen -> 8.dp
        else -> 4.dp
    }
    val actionSpacing = when {
        isExtraLargeScreen -> 12.dp
        isLargeScreen -> 8.dp
        else -> 4.dp
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
            ),
        ) {
            Row(
                modifier = Modifier
                    .wrapContentWidth()
                    .padding(horizontal = pillHorizontalPadding, vertical = pillVerticalPadding),
                horizontalArrangement = Arrangement.spacedBy(actionSpacing, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Action: Pause
                if (selection.canPause) {
                    PillActionButton(
                        icon = Icons.Pause,
                        contentDescription = stringResource(R.string.action_pause),
                        tint = MaterialTheme.colorScheme.primary,
                        size = buttonSize,
                        iconSize = iconSize,
                        onClick = selection.onPause,
                    )
                }

                // Action: Start / Resume
                if (selection.canStart) {
                    PillActionButton(
                        icon = Icons.Play,
                        contentDescription = stringResource(R.string.action_resume),
                        tint = MaterialTheme.colorScheme.primary,
                        size = buttonSize,
                        iconSize = iconSize,
                        onClick = selection.onStart,
                    )
                }

                // Action: Retry / Re-download
                if (selection.canRetry) {
                    PillActionButton(
                        icon = Icons.Refresh,
                        contentDescription = stringResource(R.string.action_redownload),
                        tint = MaterialTheme.colorScheme.primary,
                        size = buttonSize,
                        iconSize = iconSize,
                        onClick = selection.onRetry,
                    )
                }

                // Action: Copy link
                if (selection.canCopyLink) {
                    PillActionButton(
                        icon = Icons.Copy,
                        contentDescription = stringResource(R.string.action_copy_link),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        size = buttonSize,
                        iconSize = iconSize,
                        onClick = selection.onCopyLink,
                    )
                }

                // Action: Share
                if (selection.canShare) {
                    PillActionButton(
                        icon = Icons.Share,
                        contentDescription = stringResource(R.string.action_share),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        size = buttonSize,
                        iconSize = iconSize,
                        onClick = selection.onShare,
                    )
                }

                // Action: Delete
                if (selection.canDelete) {
                    PillActionButton(
                        icon = Icons.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.error,
                        size = buttonSize,
                        iconSize = iconSize,
                        onClick = selection.onDelete,
                    )
                }
            }
        }
    }
}

@Composable
private fun PillActionButton(
    icon: AppIcon,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    iconSize: Dp = 20.dp,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(size),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun MainNavigationBar(
    destination: MainDestination,
    navigationItems: List<MainNavigationItem>,
    position: Float,
    activeDownloadCount: Int,
    swipeModifier: Modifier,
    onDestinationSelected: (MainDestination) -> Unit,
    onAddDownload: () -> Unit,
) {
    val pageItems = navigationItems.filter { item -> item.destination != null }
    val showAdd = navigationItems.contains(MainNavigationItem.Add)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .wrapContentWidth()
                .then(swipeModifier)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (pageItems.isNotEmpty()) {
                ExpressivePillNavigationBar(
                    visibleTabs = pageItems,
                    position = position,
                    activeDownloadCount = activeDownloadCount,
                    onTabSelected = { item ->
                        item.destination?.let { onDestinationSelected(it) }
                    },
                )
            }

            if (showAdd) {
                AddDownloadButton(onClick = onAddDownload)
            }
        }
    }
}

@Composable
private fun ExpressivePillNavigationBar(
    visibleTabs: List<MainNavigationItem>,
    position: Float,
    activeDownloadCount: Int,
    onTabSelected: (MainNavigationItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current

    fun activeTabWidth(item: MainNavigationItem): Dp = when (item) {
        MainNavigationItem.Home -> 96.dp
        MainNavigationItem.Downloads -> 126.dp
        MainNavigationItem.Browser -> 106.dp
        MainNavigationItem.Add -> 92.dp
    }

    val inactiveTabWidth = 44.dp
    val spacing = 4.dp
    val startPadding = 6.dp

    val tabWidths = remember(position, visibleTabs) {
        visibleTabs.mapIndexed { index, item ->
            val fraction = (1f - kotlin.math.abs(position - index)).coerceIn(0f, 1f)
            androidx.compose.ui.unit.lerp(inactiveTabWidth, activeTabWidth(item), fraction)
        }
    }

    val tabOffsets = remember(tabWidths) {
        var acc = startPadding
        tabWidths.map { w ->
            val left = acc
            acc += w + spacing
            left
        }
    }

    val pageFloor = position.toInt().coerceIn(0, (visibleTabs.size - 1).coerceAtLeast(0))
    val pageCeil = (pageFloor + 1).coerceIn(0, (visibleTabs.size - 1).coerceAtLeast(0))
    val fraction = (position - pageFloor).coerceIn(0f, 1f)

    val indicatorLeft = if (tabOffsets.isNotEmpty()) {
        androidx.compose.ui.unit.lerp(tabOffsets[pageFloor], tabOffsets[pageCeil], fraction)
    } else {
        startPadding
    }

    val indicatorWidth = if (tabWidths.isNotEmpty()) {
        androidx.compose.ui.unit.lerp(tabWidths[pageFloor], tabWidths[pageCeil], fraction)
    } else {
        inactiveTabWidth
    }

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
        ),
    ) {
        Box(
            modifier = Modifier
                .wrapContentWidth()
                .padding(horizontal = startPadding, vertical = 6.dp),
        ) {
            // Sliding background pill indicator
            Box(
                modifier = Modifier
                    .offset(x = indicatorLeft - startPadding, y = 0.dp)
                    .width(indicatorWidth)
                    .height(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            )

            // Tab buttons row positioned directly on top of the track
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                visibleTabs.forEachIndexed { index, item ->
                    val tabFraction = (1f - kotlin.math.abs(position - index)).coerceIn(0f, 1f)
                    val tabWidth = tabWidths.getOrElse(index) { inactiveTabWidth }

                    val contentColor = androidx.compose.ui.graphics.lerp(
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        MaterialTheme.colorScheme.onPrimaryContainer,
                        tabFraction,
                    )

                    Box(
                        modifier = Modifier
                            .width(tabWidth)
                            .height(44.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = androidx.compose.material3.ripple(bounded = true),
                            ) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onTabSelected(item)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (item == MainNavigationItem.Downloads && activeDownloadCount > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge {
                                            Text(if (activeDownloadCount > 99) "99+" else activeDownloadCount.toString())
                                        }
                                    }
                                ) {
                                    NavigationItemIcon(item, tint = contentColor)
                                }
                            } else {
                                NavigationItemIcon(item, tint = contentColor)
                            }

                            if (tabFraction > 0.05f) {
                                Spacer(modifier = Modifier.width(androidx.compose.ui.unit.lerp(0.dp, 6.dp, tabFraction)))
                                Text(
                                    text = stringResource(item.labelRes()),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = contentColor,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Clip,
                                    modifier = Modifier.graphicsLayer {
                                        alpha = ((tabFraction - 0.25f) / 0.75f).coerceIn(0f, 1f)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationItemIcon(item: MainNavigationItem, tint: Color) {
    Icon(
        imageVector = item.appIcon(),
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(22.dp),
    )
}

@Composable
private fun AddDownloadButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.size(56.dp),
        shadowElevation = 8.dp,
        tonalElevation = 6.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Add,
                contentDescription = stringResource(R.string.tab_add),
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

private fun MainNavigationItem.appIcon(): AppIcon = when (this) {
    MainNavigationItem.Home -> Icons.Home
    MainNavigationItem.Downloads -> Icons.Downloads
    MainNavigationItem.Browser -> Icons.Public
    MainNavigationItem.Add -> Icons.Add
}

private fun MainNavigationItem.labelRes(): Int = when (this) {
    MainNavigationItem.Home -> R.string.tab_home
    MainNavigationItem.Downloads -> R.string.tab_downloads
    MainNavigationItem.Browser -> R.string.tab_browser
    MainNavigationItem.Add -> R.string.tab_add
}