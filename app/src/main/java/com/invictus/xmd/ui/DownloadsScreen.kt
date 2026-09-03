package com.invictus.xmd.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    onDelete: (QueueItem) -> Unit,
    onCancelAll: () -> Unit,
    onRetryAll: () -> Unit,
    onClearAllFinished: () -> Unit,
) {
    // Same filter as DownloadsFragment.renderList: filename OR sourceUrl,
    // case-insensitive substring.
    val list = remember(items, query) {
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

    // Long-press options menu / rename / delete-confirm dialog state --
    // one shared slot since only one row's menu can be open at a time,
    // same as the old single MaterialAlertDialogBuilder instance.
    var optionsTarget by remember { mutableStateOf<QueueItem?>(null) }
    var renameTarget by remember { mutableStateOf<QueueItem?>(null) }
    var deleteTarget by remember { mutableStateOf<QueueItem?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // ── Summary chips bar ───────────────────────────────────────────
        // Recomputed from `list` on every recomposition, but Compose only
        // actually redraws the Row when the derived `parts` value changes
        // (structural equality) -- same "skip the rebuild when labels
        // haven't moved" effect the old removeAllViews()-guard achieved
        // manually, without needing to hand-roll it here.
        val summaryParts = remember(list) { buildSummaryParts(list) }
        if (summaryParts.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                summaryParts.forEach { label -> SummaryChip(label) }
            }
        }

        // ── Content: list or empty state ────────────────────────────────
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (list.isEmpty()) {
                EmptyState(showIcon = query.isBlank())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(list, key = { it.id }) { item ->
                        QueueItemRow(
                            item = item,
                            onPauseResume = onPauseResume,
                            onCancel = onCancel,
                            onRetry = onRetry,
                            onClear = onClear,
                            onOpen = onOpen,
                            onLongPress = { optionsTarget = it },
                        )
                    }
                }
            }
        }

        // ── Cancel All / Retry All + Clear All ──────────────────────────
        val hasActive = list.any {
            it.status == ItemStatus.DOWNLOADING || it.status == ItemStatus.PAUSED ||
                it.status == ItemStatus.RETRYING
        }
        val hasFailed = list.any { it.status == ItemStatus.FAILED }
        val hasClearable = list.any { it.status == ItemStatus.DONE || it.status == ItemStatus.FAILED }

        val showCancelOrRetry = hasActive || hasFailed
        if (list.isNotEmpty() && (showCancelOrRetry || hasClearable)) {
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                if (showCancelOrRetry) {
                    if (hasActive) {
                        OutlinedButton(
                            onClick = onCancelAll,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        ) { Text(stringResource(R.string.action_cancel_all)) }
                    } else {
                        OutlinedButton(
                            onClick = onRetryAll,
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.action_retry_all)) }
                    }
                    if (hasClearable) Box(modifier = Modifier.width(8.dp))
                }
                if (hasClearable) {
                    OutlinedButton(
                        onClick = onClearAllFinished,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.action_clear)) }
                }
            }
        }
    }

    // ── Long-press options menu ──────────────────────────────────────────
    optionsTarget?.let { item ->
        val file = item.filePath?.let { java.io.File(it) }?.takeIf { it.exists() }
        DownloadOptionsDialog(
            title = item.fileName ?: item.sourceUrl,
            showOpenWithAndRename = file != null,
            onOpenWith = { onOpenWith(item); optionsTarget = null },
            onRename = { renameTarget = item; optionsTarget = null },
            onRedownload = { onRetry(item); optionsTarget = null },
            onCopyLink = { onCopyLink(item); optionsTarget = null },
            onShare = { onShare(item); optionsTarget = null },
            onDelete = { deleteTarget = item; optionsTarget = null },
            onDismiss = { optionsTarget = null },
        )
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
    deleteTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete_download_title)) },
            text = { Text(item.fileName ?: item.sourceUrl) },
            confirmButton = {
                TextButton(
                    onClick = { onDelete(item); deleteTarget = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

private fun buildSummaryParts(list: List<QueueItem>): List<String> {
    val downloading = list.count { it.status == ItemStatus.DOWNLOADING }
    val ready = list.count { it.status == ItemStatus.READY }
    val resolving = list.count {
        it.status == ItemStatus.PENDING || it.status == ItemStatus.RESOLVING || it.status == ItemStatus.NEEDS_CHALLENGE
    }
    val paused = list.count { it.status == ItemStatus.PAUSED }
    val retrying = list.count { it.status == ItemStatus.RETRYING }
    val saving = list.count { it.status == ItemStatus.SAVING }
    val done = list.count { it.status == ItemStatus.DONE }
    val failed = list.count { it.status == ItemStatus.FAILED }

    return buildList {
        if (downloading > 0) add("$downloading downloading")
        if (ready > 0) add("$ready ready")
        if (resolving > 0) add("$resolving resolving")
        if (paused > 0) add("$paused paused")
        if (retrying > 0) add("$retrying retrying")
        if (saving > 0) add("$saving saving")
        if (done > 0) add("$done done")
        if (failed > 0) add("$failed failed")
    }
}

@Composable
private fun SummaryChip(label: String) {
    SuggestionChip(
        onClick = {},
        enabled = false,
        label = { Text(label, fontSize = 12.sp) },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            disabledLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        border = null,
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
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_downloads),
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
    onPauseResume: (QueueItem) -> Unit,
    onCancel: (QueueItem) -> Unit,
    onRetry: (QueueItem) -> Unit,
    onClear: (QueueItem) -> Unit,
    onOpen: (QueueItem) -> Unit,
    onLongPress: (QueueItem) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = {}, onLongClick = {
                    if (item.status == ItemStatus.DONE || item.status == ItemStatus.FAILED) onLongPress(item)
                }),
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxSize()
                    .background(colorForStatus(item.status)),
            )
            Column(modifier = Modifier.weight(1f).padding(12.dp)) {
                // Title row: filename + category badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.fileName ?: item.sourceUrl,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = item.category.label,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }

                Text(
                    text = statusText(item),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )

                SizeAndMetaRow(item)

                if (showsProgressBar(item.status)) {
                    val (progressValue, indeterminate) = progressFor(item)
                    Box(modifier = Modifier.padding(top = 8.dp)) {
                        if (indeterminate) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            )
                        } else {
                            LinearProgressIndicator(
                                progress = { progressValue },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            )
                        }
                    }
                }

                if (item.status == ItemStatus.DOWNLOADING && item.speedBps > 0) {
                    Text(
                        text = buildSpeedEtaText(item),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }

                // Pause/Resume + Cancel
                val hidePauseResume = item.status == ItemStatus.RETRYING ||
                    item.status == ItemStatus.PENDING || item.status == ItemStatus.RESOLVING ||
                    item.status == ItemStatus.NEEDS_CHALLENGE ||
                    (item.platform == MediaPlatform.YOUTUBE && item.status == ItemStatus.DOWNLOADING)
                val showActions = item.status == ItemStatus.DOWNLOADING || item.status == ItemStatus.PAUSED ||
                    item.status == ItemStatus.RETRYING || item.status == ItemStatus.READY ||
                    item.status == ItemStatus.PENDING || item.status == ItemStatus.RESOLVING ||
                    item.status == ItemStatus.NEEDS_CHALLENGE
                if (showActions) {
                    Row(modifier = Modifier.padding(top = 6.dp)) {
                        if (!hidePauseResume) {
                            RowActionButton(
                                text = when (item.status) {
                                    ItemStatus.READY -> stringResource(R.string.action_start)
                                    ItemStatus.PAUSED -> stringResource(R.string.action_resume)
                                    else -> stringResource(R.string.action_pause)
                                },
                                color = MaterialTheme.colorScheme.primary,
                                onClick = { onPauseResume(item) },
                                trailingSpace = true,
                            )
                        }
                        if (item.status != ItemStatus.READY) {
                            RowActionButton(
                                text = stringResource(R.string.action_cancel),
                                color = MaterialTheme.colorScheme.error,
                                onClick = { onCancel(item) },
                            )
                        }
                    }
                }

                // Retry / Open / Clear
                val showSecondary = item.status == ItemStatus.FAILED || item.status == ItemStatus.DONE ||
                    item.status == ItemStatus.READY || item.status == ItemStatus.PENDING ||
                    item.status == ItemStatus.RESOLVING || item.status == ItemStatus.NEEDS_CHALLENGE
                if (showSecondary) {
                    Row(modifier = Modifier.padding(top = 6.dp)) {
                        if (item.status == ItemStatus.FAILED) {
                            RowActionButton(
                                text = stringResource(R.string.action_retry),
                                color = MaterialTheme.colorScheme.primary,
                                onClick = { onRetry(item) },
                                trailingSpace = true,
                            )
                        }
                        if (item.status == ItemStatus.DONE && item.filePath != null) {
                            RowActionButton(
                                text = stringResource(R.string.action_open),
                                color = MaterialTheme.colorScheme.primary,
                                onClick = { onOpen(item) },
                                trailingSpace = true,
                            )
                        }
                        RowActionButton(
                            text = stringResource(R.string.action_clear),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = { onClear(item) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowActionButton(text: String, color: Color, onClick: () -> Unit, trailingSpace: Boolean = false) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 6.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = color),
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
    if (trailingSpace) Box(modifier = Modifier.width(24.dp))
}

@Composable
private fun SizeAndMetaRow(item: QueueItem) {
    when (item.status) {
        ItemStatus.DOWNLOADING, ItemStatus.PAUSED, ItemStatus.SAVING, ItemStatus.RETRYING -> {
            val sizeText = inFlightSizeText(item)
            if (sizeText != null) {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(sizeText, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
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
            val metaParts = buildList {
                if (durationMs > 500) add("Took ${formatElapsedDuration(durationMs)}")
                if (finishedAt > 0) add(dateFormat.format(Date(finishedAt)))
            }
            if (bytes > 0 || metaParts.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (bytes > 0) {
                        Text(formatBytes(bytes), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    if (metaParts.isNotEmpty()) {
                        Text(
                            text = metaParts.joinToString("  •  "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        )
                    }
                }
            }
        }
        else -> {}
    }
}

private fun inFlightSizeText(item: QueueItem): String? = when {
    item.platform == MediaPlatform.YOUTUBE -> when {
        !item.mediaStatusText.isNullOrBlank() -> item.mediaStatusText
        item.status == ItemStatus.DOWNLOADING -> if (item.progressPercent >= 0) "${item.progressPercent}%" else "Connecting…"
        else -> null
    }
    item.bytesTotal > 0 -> "${formatBytes(item.bytesDone)} / ${formatBytes(item.bytesTotal)}"
    item.bytesDone > 0 -> formatBytes(item.bytesDone)
    else -> null
}

private fun showsProgressBar(status: ItemStatus) =
    status == ItemStatus.DOWNLOADING || status == ItemStatus.DONE || status == ItemStatus.SAVING

/** Returns (progressFraction 0f..1f, isIndeterminate). */
private fun progressFor(item: QueueItem): Pair<Float, Boolean> = when (item.status) {
    ItemStatus.DOWNLOADING -> when {
        item.platform == MediaPlatform.YOUTUBE ->
            if (item.progressPercent >= 0) (item.progressPercent / 100f) to false else 0f to true
        item.bytesTotal > 0 -> ((item.bytesDone * 100 / item.bytesTotal).toFloat() / 100f) to false
        else -> 0f to true
    }
    ItemStatus.DONE, ItemStatus.SAVING -> 1f to false
    else -> 0f to true
}

private fun statusText(item: QueueItem): String = when (item.status) {
    ItemStatus.PENDING -> "⏳ Queued"
    ItemStatus.RESOLVING -> "🔄 Resolving…"
    ItemStatus.NEEDS_CHALLENGE -> "🛡 Verifying — complete check in browser"
    ItemStatus.READY -> "✅ Ready to download"
    ItemStatus.DOWNLOADING -> if (item.platform == MediaPlatform.YOUTUBE) {
        val label = item.mediaFormatLabel?.let { " • $it" }.orEmpty()
        "⬇  ${if (item.progressPercent >= 0) "${item.progressPercent}%" else "Downloading…"}$label"
    } else {
        val pct = if (item.bytesTotal > 0) (item.bytesDone * 100 / item.bytesTotal) else 0
        "⬇  ${if (item.bytesTotal > 0) "$pct%" else "Downloading…"}"
    }
    ItemStatus.PAUSED -> if (item.error == Settings.WIFI_WAIT_MARKER) "📶 Waiting for Wi-Fi" else "⏸  Paused"
    ItemStatus.RETRYING -> "🔁 ${item.error ?: "Retrying…"}"
    ItemStatus.SAVING -> "💾 Saving to storage…"
    ItemStatus.DONE -> "✔  Done"
    ItemStatus.FAILED -> "✖  ${item.error ?: "Failed"}"
}

@Composable
private fun colorForStatus(status: ItemStatus): Color = when (status) {
    ItemStatus.PENDING, ItemStatus.RESOLVING, ItemStatus.NEEDS_CHALLENGE -> MaterialTheme.colorScheme.onSurfaceVariant
    ItemStatus.READY, ItemStatus.DOWNLOADING, ItemStatus.SAVING -> MaterialTheme.colorScheme.primary
    ItemStatus.PAUSED, ItemStatus.RETRYING -> Color(0xFFFFD08C) // ff_warning
    ItemStatus.DONE -> Color(0xFF8CDB9C) // ff_success
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

private fun buildSpeedEtaText(item: QueueItem): String {
    val bps = item.speedBps
    val speedStr = when {
        bps >= 1_048_576.0 -> "%.1f MB/s".format(bps / 1_048_576.0)
        bps >= 1_024.0 -> "%.0f KB/s".format(bps / 1_024.0)
        else -> "%.0f B/s".format(bps)
    }
    val remaining = (item.bytesTotal - item.bytesDone).coerceAtLeast(0)
    val etaSec = if (bps > 1.0 && item.bytesTotal > 0) (remaining / bps).toLong() else -1L
    return if (etaSec >= 0) "$speedStr  •  ETA ${formatDuration(etaSec)}" else speedStr
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.2f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024L -> "%.0f KB".format(bytes / 1_024.0)
    else -> "$bytes B"
}

private fun formatDuration(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

// ── Dialogs ───────────────────────────────────────────────────────────────

/** Long-press options for a DONE/FAILED row -- same action list/order as
 *  DownloadsFragment.showDownloadOptionsDialog. */
@Composable
private fun DownloadOptionsDialog(
    title: String,
    showOpenWithAndRename: Boolean,
    onOpenWith: () -> Unit,
    onRename: () -> Unit,
    onRedownload: () -> Unit,
    onCopyLink: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                if (showOpenWithAndRename) {
                    DialogOptionRow(stringResource(R.string.action_open_with), onOpenWith)
                    DialogOptionRow(stringResource(R.string.action_rename), onRename)
                }
                DialogOptionRow(stringResource(R.string.action_redownload), onRedownload)
                DialogOptionRow(stringResource(R.string.action_copy_link), onCopyLink)
                DialogOptionRow(stringResource(R.string.action_share), onShare)
                DialogOptionRow(stringResource(R.string.action_delete), onDelete)
            }
        },
        confirmButton = {},
        dismissButton = {},
    )
}

@Composable
private fun DialogOptionRow(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        )
    }
}

@Composable
private fun RenameDialog(currentName: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember(currentName) { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_rename)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
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
