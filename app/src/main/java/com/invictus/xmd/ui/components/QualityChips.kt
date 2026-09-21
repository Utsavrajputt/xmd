package com.invictus.xmd.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Reusable chip-based picker pieces, first built for the yt-dlp
 * settings screen's quality/container/fps/codec/audio sections and
 * reused as-is by [com.invictus.xmd.ui.downloads.AddDownloadDialog]'s
 * inline quality picker -- same look everywhere a user picks one of
 * a short fixed list of options.
 */

/** Small label placed above a [ChipRow] when it needs its own heading. */
@Composable
internal fun ChipLabel(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

/**
 * Fixed [columns]-per-row chip grid (e.g. 4x2 for an 8-option quality
 * ladder). Rows are evenly split via chunked() rather than a reflowing
 * FlowRow so the layout stays predictable regardless of label length.
 * A short trailing row is padded with invisible spacers so its chips
 * stay the same width as a full row instead of stretching to fill it.
 */
@Composable
internal fun ChipGrid(
    options: List<String>,
    selected: String,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 4,
    /** Long labels (e.g. "Streams (14)") shrink slightly and may wrap to two lines instead of clipping. */
    wrapLongLabels: Boolean = false,
    /** Options shown dimmed and not tappable. */
    disabledOptions: Set<String> = emptySet(),
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.withIndex().chunked(columns).forEach { rowOptions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowOptions.forEach { (index, option) ->
                    AppFilterChip(
                        modifier = Modifier.weight(1f),
                        label = option,
                        selected = option == selected,
                        onClick = { onSelected(index) },
                        enabled = option !in disabledOptions,
                        wrapLongLabel = wrapLongLabels,
                    )
                }
                repeat(columns - rowOptions.size) {
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** Single-row, horizontally-scrollable chip strip for shorter option lists (container, fps, codec, audio format). */
@Composable
internal fun ChipRow(
    options: List<String>,
    selected: String,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { index, option ->
            AppFilterChip(
                modifier = Modifier.widthIn(min = 60.dp),
                label = option,
                selected = option == selected,
                onClick = { onSelected(index) },
            )
        }
    }
}

@Composable
internal fun AppFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    wrapLongLabel: Boolean = false,
) {
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    }

    val dimAlpha = if (enabled) 1f else 0.38f
    val compact = wrapLongLabel && label.length > 8

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(34.dp),
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, borderColor.copy(alpha = borderColor.alpha * dimAlpha)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                fontSize = if (compact) 10.sp else 12.sp,
                lineHeight = if (compact) 11.sp else TextUnit.Unspecified,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = contentColor.copy(alpha = contentColor.alpha * dimAlpha),
                maxLines = if (compact) 2 else 1,
                softWrap = compact,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center,
            )
        }
    }
}
