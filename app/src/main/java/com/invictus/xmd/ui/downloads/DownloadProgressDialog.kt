package com.invictus.xmd.ui.downloads

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.invictus.xmd.R
import com.invictus.xmd.database.entities.QueueItem
import com.invictus.xmd.domain.download.ItemStatus
import com.invictus.xmd.domain.download.MediaPlatform
import com.invictus.xmd.preferences.Settings
import com.invictus.xmd.repository.QueueRepository
import com.invictus.xmd.ui.components.WideDialogProperties
import com.invictus.xmd.ui.components.wideDialogWidth
import com.invictus.xmd.ui.icons.Icon
import com.invictus.xmd.ui.icons.Icons
import com.invictus.xmd.utils.formatBytes
import com.invictus.xmd.utils.formatRemainingTimeChrome
import com.invictus.xmd.utils.formatSpeed
import java.io.File

/**
 * Download progress dialog inspired by AB Download Manager (abdm),
 * showing live download progress, transfer speed, remaining time,
 * file properties, and controls (Pause/Resume/Cancel/Open).
 *
 * When opened from an external application/browser, provides prominent
 * "Hide" (runs in background) and "Open in app" buttons.
 */
@Composable
fun DownloadProgressDialog(
    itemId: String,
    isExternalMode: Boolean = false,
    onDismiss: () -> Unit,
    onPauseResume: (QueueItem) -> Unit,
    onCancel: (QueueItem) -> Unit,
    onRetry: (QueueItem) -> Unit,
    onOpen: (QueueItem) -> Unit = {},
    onOpenInApp: (() -> Unit)? = null,
) {
    val items by QueueRepository.items.collectAsStateWithLifecycle()
    val item = remember(items, itemId) { items.firstOrNull { it.id == itemId } }

    if (item == null) {
        // Item is no longer in queue; auto-dismiss
        return
    }

    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val configuration = LocalConfiguration.current
    val maxHeight = (configuration.screenHeightDp.dp - 64.dp).coerceAtLeast(280.dp)

    val (progressFrac, isIndeterminate) = remember(item.status, item.bytesDone, item.bytesTotal, item.progressPercent) {
        calcProgress(item)
    }
    val animatedProgress by animateFloatAsState(
        targetValue = progressFrac,
        animationSpec = tween(durationMillis = 200, easing = LinearEasing),
        label = "progressFrac",
    )
    val pct = remember(item.status, item.bytesDone, item.bytesTotal, item.progressPercent) {
        calcPercent(item)
    }

    val statusColor = when (item.status) {
        ItemStatus.DOWNLOADING -> colors.primary
        ItemStatus.PAUSED -> colors.tertiary
        ItemStatus.DONE -> colors.primary
        ItemStatus.FAILED -> colors.error
        ItemStatus.RETRYING -> colors.secondary
        ItemStatus.SAVING -> colors.primary
        else -> colors.onSurfaceVariant
    }

    val titleText = when (item.status) {
        ItemStatus.DOWNLOADING -> if (pct != null) "$pct% Downloading" else "Downloading"
        ItemStatus.PAUSED -> if (pct != null) "$pct% Paused" else "Paused"
        ItemStatus.DONE -> stringResource(R.string.download_complete_title)
        ItemStatus.FAILED -> "Download failed"
        ItemStatus.RETRYING -> "Retrying…"
        ItemStatus.SAVING -> "Saving…"
        ItemStatus.READY -> "Ready to download"
        ItemStatus.RESOLVING -> "Resolving…"
        ItemStatus.NEEDS_CHALLENGE -> "Verifying…"
        ItemStatus.PENDING -> "Queued"
    }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.wideDialogWidth(),
        properties = WideDialogProperties,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f)),
            tonalElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .padding(20.dp),
            ) {
                // ── Header: Status icon + Title + Action buttons (Open in App & Close) ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = statusColor.copy(alpha = 0.15f),
                        contentColor = statusColor,
                    ) {
                        Box(
                            modifier = Modifier.size(42.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            val icon = when (item.status) {
                                ItemStatus.DOWNLOADING -> Icons.Download
                                ItemStatus.PAUSED -> Icons.Pause
                                ItemStatus.DONE -> Icons.Check
                                ItemStatus.FAILED -> Icons.Close
                                ItemStatus.RETRYING -> Icons.Refresh
                                ItemStatus.SAVING -> Icons.Sync
                                else -> Icons.Schedule
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = statusColor,
                            )
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = when (item.platform) {
                                MediaPlatform.YOUTUBE -> "YouTube · ${item.category.label}"
                                MediaPlatform.DIRECT -> {
                                    if (item.sourceUrl.startsWith("magnet:", ignoreCase = true)) {
                                        "Torrent · ${item.category.label}"
                                    } else {
                                        "Direct · ${item.category.label}"
                                    }
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }

                    if (onOpenInApp != null) {
                        IconButton(onClick = onOpenInApp) {
                            Icon(
                                imageVector = Icons.OpenInNew,
                                contentDescription = stringResource(R.string.action_open_in_app),
                                tint = colors.onSurfaceVariant,
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Close,
                            contentDescription = stringResource(R.string.action_hide),
                            tint = colors.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── Scrollable Body ──
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {
                    // Filename
                    val fileName = item.fileName?.takeIf { it.isNotBlank() } ?: item.sourceUrl
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee(iterations = Int.MAX_VALUE),
                    )

                    Spacer(Modifier.height(12.dp))

                    // Progress bar
                    ProgressIndicatorBar(
                        progress = animatedProgress,
                        isIndeterminate = isIndeterminate,
                        isDownloading = item.status == ItemStatus.DOWNLOADING,
                        indicatorColor = statusColor,
                        trackColor = colors.surfaceContainerHighest,
                    )

                    Spacer(Modifier.height(8.dp))

                    // Progress sizes & percentage
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val downloadedStr = if (item.bytesDone > 0) formatBytes(item.bytesDone) else "0 B"
                        val totalStr = if (item.bytesTotal > 0) formatBytes(item.bytesTotal) else if (pct != null) "" else "?"
                        val sizeSummary = if (totalStr.isNotEmpty()) "$downloadedStr / $totalStr" else downloadedStr

                        Text(
                            text = sizeSummary,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )

                        if (pct != null) {
                            Text(
                                text = "$pct%",
                                style = MaterialTheme.typography.labelLarge,
                                color = statusColor,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // ── Properties Card (similar to abdm's info section) ──
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colors.surfaceContainer,
                        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // Status row
                            PropertyRow(
                                label = stringResource(R.string.download_status_label),
                                value = when (item.status) {
                                    ItemStatus.DOWNLOADING -> "Downloading"
                                    ItemStatus.PAUSED -> "Paused"
                                    ItemStatus.DONE -> "Complete"
                                    ItemStatus.FAILED -> "Failed"
                                    ItemStatus.RETRYING -> "Retrying…"
                                    ItemStatus.SAVING -> "Saving…"
                                    ItemStatus.READY -> "Ready"
                                    ItemStatus.RESOLVING -> "Resolving…"
                                    ItemStatus.NEEDS_CHALLENGE -> "Verifying…"
                                    ItemStatus.PENDING -> "Queued"
                                },
                                valueColor = statusColor,
                            )

                            // Speed
                            val speedText = if (item.status == ItemStatus.DOWNLOADING && item.speedBps > 0) {
                                formatSpeed(item.speedBps)
                            } else "—"
                            PropertyRow(
                                label = stringResource(R.string.download_speed_label),
                                value = speedText,
                            )

                            // Time left / ETA
                            val etaText = remember(item.status, item.speedBps, item.bytesDone, item.bytesTotal) {
                                if (item.status == ItemStatus.DOWNLOADING && item.speedBps > 0 && item.bytesTotal > item.bytesDone) {
                                    val remainingBytes = item.bytesTotal - item.bytesDone
                                    val remainingSeconds = (remainingBytes / item.speedBps).toLong()
                                    formatRemainingTimeChrome(remainingSeconds)
                                } else "—"
                            }
                            PropertyRow(
                                label = stringResource(R.string.download_eta_label),
                                value = etaText,
                            )

                            // Total size
                            val totalSizeText = if (item.bytesTotal > 0) formatBytes(item.bytesTotal) else if (item.bytesDone > 0) formatBytes(item.bytesDone) else "Unknown"
                            PropertyRow(
                                label = stringResource(R.string.info_size),
                                value = totalSizeText,
                            )

                            // Resume support
                            val resumeSupport = when {
                                item.platform == MediaPlatform.YOUTUBE -> stringResource(R.string.download_resume_supported)
                                item.sourceUrl.startsWith("magnet:", ignoreCase = true) -> stringResource(R.string.download_resume_supported)
                                item.bytesTotal > 0 -> stringResource(R.string.download_resume_supported)
                                else -> stringResource(R.string.download_resume_unknown)
                            }
                            PropertyRow(
                                label = stringResource(R.string.download_resume_label),
                                value = resumeSupport,
                                valueColor = if (resumeSupport == stringResource(R.string.download_resume_supported)) colors.primary else colors.onSurfaceVariant,
                            )

                            // Save Location
                            val clipboardManager = LocalClipboardManager.current
                            val saveDir = item.customSaveDirPath ?: Settings.defaultSaveLocation()
                            PropertyRow(
                                label = stringResource(R.string.info_path),
                                value = item.filePath ?: saveDir,
                                isCopyable = true,
                                onCopy = {
                                    clipboardManager.setText(AnnotatedString(item.filePath ?: saveDir))
                                    Toast.makeText(context, R.string.link_copied_toast, Toast.LENGTH_SHORT).show()
                                },
                            )
                        }
                    }

                    // Error warning if present
                    if (item.status == ItemStatus.FAILED || !item.error.isNullOrBlank()) {
                        val errMsg = item.error.orEmpty().ifBlank { "Download failed" }
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = colors.errorContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Close,
                                    contentDescription = null,
                                    tint = colors.onErrorContainer,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = errMsg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onErrorContainer,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(14.dp))

                // ── Action Buttons Footer ──
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when (item.status) {
                        ItemStatus.DOWNLOADING, ItemStatus.PAUSED, ItemStatus.RETRYING, ItemStatus.READY, ItemStatus.SAVING -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                // Pause / Resume
                                val isPaused = item.status == ItemStatus.PAUSED || item.status == ItemStatus.READY
                                FilledTonalButton(
                                    onClick = { onPauseResume(item) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Icon(
                                        imageVector = if (isPaused) Icons.Play else Icons.Pause,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(if (isPaused) R.string.action_resume else R.string.action_pause))
                                }

                                // Cancel
                                OutlinedButton(
                                    onClick = { onCancel(item) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = colors.error,
                                    ),
                                    border = BorderStroke(1.dp, colors.error.copy(alpha = 0.5f)),
                                ) {
                                    Icon(
                                        imageVector = Icons.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.action_cancel))
                                }
                            }

                            // External mode navigation: Hide & Open in App
                            if (isExternalMode) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    OutlinedButton(
                                        onClick = onDismiss,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.VisibilityOff,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.action_hide))
                                    }

                                    if (onOpenInApp != null) {
                                        Button(
                                            onClick = onOpenInApp,
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.OpenInNew,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(stringResource(R.string.action_open_in_app))
                                        }
                                    }
                                }
                            } else {
                                OutlinedButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Text(stringResource(R.string.action_hide))
                                }
                            }
                        }

                        ItemStatus.DONE -> {
                            Button(
                                onClick = { onOpen(item) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Play,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.download_open_file))
                            }

                            if (isExternalMode) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    OutlinedButton(
                                        onClick = onDismiss,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                    ) {
                                        Text(stringResource(R.string.action_hide))
                                    }

                                    if (onOpenInApp != null) {
                                        FilledTonalButton(
                                            onClick = onOpenInApp,
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.OpenInNew,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(stringResource(R.string.action_open_in_app))
                                        }
                                    }
                                }
                            } else {
                                OutlinedButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Text(stringResource(R.string.action_dismiss))
                                }
                            }
                        }

                        ItemStatus.FAILED -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Button(
                                    onClick = { onRetry(item) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.action_retry))
                                }

                                OutlinedButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Text(stringResource(R.string.action_dismiss))
                                }
                            }

                            if (isExternalMode && onOpenInApp != null) {
                                FilledTonalButton(
                                    onClick = onOpenInApp,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.action_open_in_app))
                                }
                            }
                        }

                        else -> {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(stringResource(R.string.action_dismiss))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PropertyRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    isCopyable: Boolean = false,
    onCopy: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.35f),
        )

        Row(
            modifier = Modifier.weight(0.65f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .then(if (isCopyable) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier),
            )

            if (isCopyable && onCopy != null) {
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Copy,
                        contentDescription = stringResource(R.string.action_copy),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressIndicatorBar(
    progress: Float,
    isIndeterminate: Boolean,
    isDownloading: Boolean,
    indicatorColor: Color,
    trackColor: Color,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "dialog_progress_anim")
    val indeterminateOffset by infiniteTransition.animateFloat(
        initialValue = -0.35f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "dialog_indeterminate_offset",
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(trackColor),
    ) {
        val centerY = size.height / 2f
        val strokeWidth = size.height
        val totalWidth = size.width

        if (isIndeterminate) {
            val barLength = totalWidth * 0.35f
            val startX = (indeterminateOffset * totalWidth).coerceIn(0f, totalWidth)
            val endX = ((indeterminateOffset * totalWidth) + barLength).coerceIn(0f, totalWidth)
            if (endX > startX) {
                drawLine(
                    color = indicatorColor,
                    start = Offset(startX, centerY),
                    end = Offset(endX, centerY),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
        } else {
            val progressWidth = totalWidth * progress.coerceIn(0f, 1f)
            if (progressWidth > 0f) {
                drawLine(
                    color = indicatorColor,
                    start = Offset(0f, centerY),
                    end = Offset(progressWidth, centerY),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

private fun calcProgress(item: QueueItem): Pair<Float, Boolean> = when (item.status) {
    ItemStatus.DOWNLOADING, ItemStatus.PAUSED -> when {
        item.platform == MediaPlatform.YOUTUBE ->
            if (item.progressPercent >= 0) (item.progressPercent / 100f) to false else 0f to (item.status == ItemStatus.DOWNLOADING)
        item.bytesTotal > 0 -> ((item.bytesDone.toFloat() / item.bytesTotal.toFloat())).coerceIn(0f, 1f) to false
        else -> 0f to (item.status == ItemStatus.DOWNLOADING)
    }
    ItemStatus.DONE, ItemStatus.SAVING -> 1f to false
    else -> 0f to true
}

private fun calcPercent(item: QueueItem): Int? = when (item.status) {
    ItemStatus.DOWNLOADING, ItemStatus.PAUSED -> when {
        item.platform == MediaPlatform.YOUTUBE -> if (item.progressPercent >= 0) item.progressPercent else null
        item.bytesTotal > 0 -> ((item.bytesDone.toDouble() / item.bytesTotal.toDouble()) * 100.0).toInt().coerceIn(0, 100)
        else -> null
    }
    ItemStatus.DONE, ItemStatus.SAVING -> 100
    else -> null
}

/**
 * Opens a downloaded file in an external viewer using FileProvider.
 */
fun openDownloadedFile(context: Context, item: QueueItem) {
    val path = item.filePath
    val file = path?.let { File(it) }
    if (file == null || !file.exists()) {
        Toast.makeText(context, R.string.open_file_missing, Toast.LENGTH_SHORT).show()
        return
    }

    val uri = try {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } catch (e: IllegalArgumentException) {
        Toast.makeText(context, R.string.open_file_missing, Toast.LENGTH_SHORT).show()
        return
    }

    val extension = file.extension.lowercase()
    val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"

    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    val chooser = Intent.createChooser(intent, context.getString(R.string.action_open))
    try {
        context.startActivity(chooser)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.open_file_no_app, Toast.LENGTH_SHORT).show()
    }
}
