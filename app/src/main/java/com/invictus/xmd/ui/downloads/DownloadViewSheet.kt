@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.invictus.xmd.ui.downloads

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.invictus.xmd.ui.icons.AppIcon
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.invictus.xmd.R
import com.invictus.xmd.ui.icons.Icon
import com.invictus.xmd.ui.icons.Icons
import com.invictus.xmd.ui.theme.XmdShapes

/**
 * "Sort & view" bottom sheet for the Downloads list. Every change is applied (and persisted by
 * the caller) immediately, so the list behind the sheet updates live; "Done" only closes it.
 */
@Composable
internal fun DownloadViewSheet(
    options: DownloadViewOptions,
    /** False on the Active tab, where grouping isn't applied. */
    groupingApplies: Boolean,
    onChange: (DownloadViewOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp),
        ) {
            // ── Header ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.download_view_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { onChange(DownloadViewOptions()) },
                    enabled = !options.isDefault,
                ) {
                    Icon(
                        imageVector = Icons.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.download_view_reset))
                }
            }

            // ── Sort by ──
            SectionLabel(stringResource(R.string.download_view_sort_by))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SortTile(
                    label = stringResource(R.string.download_view_sort_date),
                    icon = Icons.Schedule,
                    selected = options.sortBy == DownloadSortBy.DATE,
                    modifier = Modifier.weight(1f),
                ) { onChange(options.withSort(DownloadSortBy.DATE)) }
                SortTile(
                    label = stringResource(R.string.download_view_sort_name),
                    glyph = "Aa",
                    selected = options.sortBy == DownloadSortBy.NAME,
                    modifier = Modifier.weight(1f),
                ) { onChange(options.withSort(DownloadSortBy.NAME)) }
                SortTile(
                    label = stringResource(R.string.download_view_sort_size),
                    icon = Icons.DataUsage,
                    selected = options.sortBy == DownloadSortBy.SIZE,
                    modifier = Modifier.weight(1f),
                ) { onChange(options.withSort(DownloadSortBy.SIZE)) }
                SortTile(
                    label = stringResource(R.string.download_view_sort_type),
                    icon = Icons.GridView,
                    selected = options.sortBy == DownloadSortBy.TYPE,
                    modifier = Modifier.weight(1f),
                ) { onChange(options.withSort(DownloadSortBy.TYPE)) }
            }

            Spacer(Modifier.height(10.dp))
            // Direction: the sort's natural direction first, the flipped one second.
            val naturalAsc = options.sortBy.defaultAscending
            val (naturalLabel, flippedLabel) = when (options.sortBy) {
                DownloadSortBy.DATE -> R.string.download_view_dir_newest to R.string.download_view_dir_oldest
                DownloadSortBy.NAME -> R.string.download_view_dir_az to R.string.download_view_dir_za
                DownloadSortBy.SIZE -> R.string.download_view_dir_largest to R.string.download_view_dir_smallest
                DownloadSortBy.TYPE -> R.string.download_view_dir_type_fwd to R.string.download_view_dir_type_rev
            }
            ConnectedChoice(
                labels = listOf(stringResource(naturalLabel), stringResource(flippedLabel)),
                selectedIndex = if (options.ascending == naturalAsc) 0 else 1,
                onSelect = { index ->
                    onChange(options.copy(ascending = if (index == 0) naturalAsc else !naturalAsc))
                },
            )

            // ── Group by ──
            SectionLabel(stringResource(R.string.download_view_group_by))
            ConnectedChoice(
                labels = listOf(
                    stringResource(R.string.download_view_group_none),
                    stringResource(R.string.download_view_group_date),
                    stringResource(R.string.download_view_group_type),
                ),
                selectedIndex = options.groupBy.ordinal,
                onSelect = { onChange(options.copy(groupBy = DownloadGroupBy.entries[it])) },
            )
            if (!groupingApplies && options.groupBy != DownloadGroupBy.NONE) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.download_view_group_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── Layout ──
            SectionLabel(stringResource(R.string.download_view_layout))
            ConnectedChoice(
                labels = listOf(
                    stringResource(R.string.download_view_layout_comfortable),
                    stringResource(R.string.download_view_layout_compact),
                ),
                selectedIndex = if (options.compact) 1 else 0,
                onSelect = { onChange(options.copy(compact = it == 1)) },
            )

            // ── Fields ──
            SectionLabel(stringResource(R.string.download_view_fields))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                DownloadField.entries.forEach { field ->
                    val selected = field in options.fields
                    FilterChip(
                        selected = selected,
                        onClick = {
                            onChange(
                                options.copy(fields = if (selected) options.fields - field else options.fields + field),
                            )
                        },
                        label = { Text(stringResource(field.labelRes()), fontSize = 13.sp) },
                        leadingIcon = if (selected) {
                            {
                                Icon(
                                    imageVector = Icons.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            }
                        } else {
                            null
                        },
                        shape = XmdShapes.Xs,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(stringResource(R.string.download_view_done), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Switching sort resets the direction to that sort's natural one (Date -> newest first, Name -> A-Z, ...). */
private fun DownloadViewOptions.withSort(sortBy: DownloadSortBy): DownloadViewOptions =
    if (sortBy == this.sortBy) this else copy(sortBy = sortBy, ascending = sortBy.defaultAscending)

private fun DownloadField.labelRes(): Int = when (this) {
    DownloadField.ICON -> R.string.download_view_field_icon
    DownloadField.BADGE -> R.string.download_view_field_badge
    DownloadField.SIZE -> R.string.download_view_field_size
    DownloadField.DURATION -> R.string.download_view_field_duration
    DownloadField.DATE -> R.string.download_view_field_date
    DownloadField.ACTIONS -> R.string.download_view_field_actions
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 10.dp),
    )
}

/** One square-ish option card: tinted circle + label. Selected = primary container. */
@Composable
private fun SortTile(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    icon: AppIcon? = null,
    glyph: String? = null,
    onClick: () -> Unit,
) {
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "tileContainer",
    )
    val content by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "tileContent",
    )
    val badge by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "tileBadge",
    )
    val onBadge = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = XmdShapes.M,
        color = container,
        border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(36.dp).background(badge, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (icon != null) {
                    Icon(imageVector = icon, contentDescription = null, tint = onBadge, modifier = Modifier.size(20.dp))
                } else if (glyph != null) {
                    Text(
                        text = glyph,
                        color = onBadge,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = label,
                color = content,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

/** Segmented single-choice row using the same connected ToggleButtons as the Settings screens. */
@Composable
private fun ConnectedChoice(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        labels.forEachIndexed { index, label ->
            ToggleButton(
                checked = index == selectedIndex,
                onCheckedChange = { if (it) onSelect(index) },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp),
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    labels.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) {
                Text(label, maxLines = 1, fontSize = 13.sp)
            }
        }
    }
}
