package com.invictus.xmd.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.invictus.xmd.ui.icons.Icon
import com.invictus.xmd.ui.icons.Icons

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
                label = option,
                selected = option == selected,
                onClick = { onSelected(index) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        modifier = modifier,
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        leadingIcon = if (selected) {
            { Icon(Icons.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else null,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    )
}
