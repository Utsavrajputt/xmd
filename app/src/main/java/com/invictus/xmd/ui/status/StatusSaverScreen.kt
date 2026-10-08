package com.invictus.xmd.ui.status

import android.graphics.Bitmap
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.invictus.xmd.R
import com.invictus.xmd.domain.status.StatusItem
import com.invictus.xmd.domain.status.StatusSource
import com.invictus.xmd.ui.icons.Icon
import com.invictus.xmd.ui.icons.Icons

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun StatusSaverScreen(
    viewModel: StatusSaverViewModel,
    onBack: () -> Unit,
    onGrantAccess: () -> Unit,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var previewIndex by remember { mutableStateOf<Int?>(null) }
    var deleteTargets by remember { mutableStateOf<List<StatusItem>?>(null) }

    val items = viewModel.currentItems
    val selecting = viewModel.selected.isNotEmpty()
    val isRecentTab = viewModel.tab == StatusTab.Recent

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(context.getString(message.resId, message.count))
        }
    }

    BackHandler(enabled = selecting) { viewModel.clearSelection() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (selecting) {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.status_selected_count, viewModel.selected.size),
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(
                                imageVector = Icons.Close,
                                contentDescription = stringResource(R.string.status_close),
                            )
                        }
                    },
                    actions = {
                        TextButton(onClick = viewModel::selectAll) {
                            Text(stringResource(R.string.status_select_all))
                        }
                        IconButton(
                            onClick = {
                                val picked = items.filter { it.path in viewModel.selected }
                                if (isRecentTab) viewModel.save(picked) else deleteTargets = picked
                            },
                        ) {
                            Icon(
                                imageVector = if (isRecentTab) Icons.Download else Icons.Delete,
                                contentDescription = stringResource(
                                    if (isRecentTab) R.string.status_action_save else R.string.status_action_delete,
                                ),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.status_saver_title),
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = viewModel::refresh) {
                            Icon(
                                imageVector = Icons.Refresh,
                                contentDescription = stringResource(R.string.status_refresh),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            PrimaryTabRow(
                selectedTabIndex = viewModel.tab.ordinal,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                StatusTab.entries.forEach { tab ->
                    Tab(
                        selected = viewModel.tab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = {
                            Text(
                                stringResource(
                                    if (tab == StatusTab.Recent) R.string.status_tab_recent else R.string.status_tab_saved,
                                ),
                            )
                        },
                    )
                }
            }

            if (isRecentTab) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatusSource.entries.forEach { source ->
                        FilterChip(
                            selected = viewModel.source == source,
                            onClick = { viewModel.selectSource(source) },
                            label = {
                                Text(
                                    stringResource(
                                        if (source == StatusSource.WHATSAPP) {
                                            R.string.status_source_whatsapp
                                        } else {
                                            R.string.status_source_business
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
            }

            when {
                !viewModel.hasAccess -> AccessRequired(onGrantAccess)
                viewModel.loading && items.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                items.isEmpty() -> CenteredMessage(
                    stringResource(if (isRecentTab) R.string.status_empty_recent else R.string.status_empty_saved),
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(104.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(items, key = { _, item -> item.path }) { index, item ->
                        StatusTile(
                            item = item,
                            showSaveState = isRecentTab,
                            isSaved = item.name in viewModel.savedNames,
                            selecting = selecting,
                            isSelected = item.path in viewModel.selected,
                            onClick = {
                                if (selecting) viewModel.toggleSelect(item.path) else previewIndex = index
                            },
                            onLongClick = { viewModel.toggleSelect(item.path) },
                            onSave = { viewModel.save(listOf(item)) },
                        )
                    }
                }
            }
        }
    }

    val openIndex = previewIndex
    if (openIndex != null && items.isNotEmpty()) {
        StatusPreviewDialog(
            items = items,
            startIndex = openIndex,
            isRecentTab = isRecentTab,
            savedNames = viewModel.savedNames,
            onSave = { viewModel.save(listOf(it)) },
            onDelete = { deleteTargets = listOf(it) },
            onDismiss = { previewIndex = null },
        )
    }

    deleteTargets?.let { targets ->
        AlertDialog(
            onDismissRequest = { deleteTargets = null },
            title = { Text(stringResource(R.string.status_delete_title)) },
            text = { Text(stringResource(R.string.status_delete_message, targets.size)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(targets)
                        deleteTargets = null
                        previewIndex = null
                    },
                ) { Text(stringResource(R.string.status_action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargets = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StatusTile(
    item: StatusItem,
    showSaveState: Boolean,
    isSaved: Boolean,
    selecting: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSave: () -> Unit,
) {
    val bitmap by produceState<Bitmap?>(null, item.path, item.lastModified) {
        value = StatusBitmaps.load(item, 384)
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

        if (item.isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .size(24.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Play,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
            )
        }

        if (selecting) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.4f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }

        if (showSaveState) {
            if (isSaved) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Check,
                        contentDescription = stringResource(R.string.status_tab_saved),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            } else if (!selecting) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(30.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        .clip(CircleShape)
                        .clickable(onClick = onSave),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Download,
                        contentDescription = stringResource(R.string.status_action_save),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPreviewDialog(
    items: List<StatusItem>,
    startIndex: Int,
    isRecentTab: Boolean,
    savedNames: Set<String>,
    onSave: (StatusItem) -> Unit,
    onDelete: (StatusItem) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val pagerState = rememberPagerState(
            initialPage = startIndex.coerceIn(0, items.lastIndex),
        ) { items.size }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { page -> items.getOrNull(page)?.path ?: page },
            ) { page ->
                items.getOrNull(page)?.let { item ->
                    StatusPreviewPage(item = item, active = pagerState.currentPage == page)
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Close,
                    contentDescription = stringResource(R.string.status_close),
                    tint = Color.White,
                )
            }

            val current = items.getOrNull(pagerState.currentPage)
            if (current != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp),
                ) {
                    if (!isRecentTab) {
                        Button(onClick = { onDelete(current) }) {
                            Icon(
                                imageVector = Icons.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = stringResource(R.string.status_action_delete),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    } else if (current.name in savedNames) {
                        FilledTonalButton(onClick = {}, enabled = false) {
                            Icon(
                                imageVector = Icons.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = stringResource(R.string.status_tab_saved),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    } else {
                        Button(onClick = { onSave(current) }) {
                            Icon(
                                imageVector = Icons.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = stringResource(R.string.status_action_save),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPreviewPage(item: StatusItem, active: Boolean) {
    val bitmap by produceState<Bitmap?>(null, item.path, item.lastModified) {
        value = StatusBitmaps.load(item, if (item.isVideo) 1024 else 2048)
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        if (!item.isVideo || !active) {
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        if (item.isVideo && active) {
            StatusVideoPlayer(item.path)
        }
    }
}

@Composable
private fun StatusVideoPlayer(path: String) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            FrameLayout(context).apply {
                val video = VideoView(context).apply {
                    setVideoPath(path)
                    setOnPreparedListener { player ->
                        player.isLooping = true
                        start()
                    }
                    setOnClickListener { if (isPlaying) pause() else start() }
                }
                addView(
                    video,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER,
                    ),
                )
            }
        },
        onRelease = { root -> (root.getChildAt(0) as? VideoView)?.stopPlayback() },
    )
}

@Composable
private fun AccessRequired(onGrant: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.status_permission_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.status_permission_message),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        Button(onClick = onGrant) {
            Text(stringResource(R.string.status_permission_button))
        }
    }
}

@Composable
private fun CenteredMessage(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
