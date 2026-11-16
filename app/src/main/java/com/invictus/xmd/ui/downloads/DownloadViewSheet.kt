@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.invictus.xmd.ui.downloads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.invictus.xmd.R
import com.invictus.xmd.ui.components.XmdAlertDialog
import com.invictus.xmd.ui.icons.AppIcon
import com.invictus.xmd.ui.icons.Icon
import com.invictus.xmd.ui.icons.Icons
import com.invictus.xmd.ui.theme.XmdShapes

/**
 * Compact "Sort & view" dialog for the Downloads list. Every change is applied (and persisted by
 * the caller) immediately, so the list behind the dialog updates live; "Done" only closes it.
 * Rarely-touched per-item fields live in a collapsed section to keep the dialog short.
 */
@Composable
internal fun DownloadViewDialog(
    options: DownloadViewOptions,
    /** False on the Active tab, where grouping isn't applied. */
    groupingApplies: Boolean,
    onChange: (DownloadViewOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    var fieldsExpanded by rememberSaveable { mutableStateOf(false) }
    var tabsExpanded by rememberSaveable { mutableStateOf(false) }

    XmdAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.download_view_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // ── Sort by: tapping the selected one reverses it ──
                SectionLabel(stringResource(R.string.download_view_sort_by), first = true)
                val sortBys = DownloadSortBy.entries
                ConnectedChoice(
                    labels = listOf(
                        stringResource(R.string.download_view_sort_date),
                        stringResource(R.string.download_view_sort_name),
                        stringResource(R.string.download_view_sort_size),
                        stringResource(R.string.download_view_sort_type),
                    ),
                    selectedIndex = options.sortBy.ordinal,
                    selectedIcon = if (options.ascending) Icons.ArrowUp else Icons.ArrowDown,
                    onSelect = { index ->
                        val picked = sortBys[index]
                        onChange(
                            if (picked == options.sortBy) {
                                options.copy(ascending = !options.ascending)
                            } else {
                                options.copy(sortBy = picked, ascending = picked.defaultAscending)
                            },
                        )
                    },
                )
                val directionLabel = stringResource(options.directionLabelRes())
                Text(
                    text = stringResource(R.string.download_view_dir_caption, directionLabel),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, start = 2.dp),
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
                    Text(
                        text = stringResource(R.string.download_view_group_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, start = 2.dp),
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

                // ── Tabs (Active is always there) -- collapsed by default ──
                ExpandableSection(
                    title = stringResource(R.string.download_view_tabs),
                    expanded = tabsExpanded,
                    onToggle = { tabsExpanded = !tabsExpanded },
                ) {
                    val tabs = buildList {
                        add(DOWNLOAD_TAB_ALL to stringResource(R.string.download_filter_all))
                        DownloadTypeFilter.entries.forEach { add(it.name to stringResource(it.labelRes)) }
                    }
                    tabs.forEach { (key, label) ->
                        val shown = key !in options.hiddenTabs
                        OptionChip(label = label, selected = shown) {
                            onChange(
                                options.copy(hiddenTabs = if (shown) options.hiddenTabs + key else options.hiddenTabs - key),
                            )
                        }
                    }
                }

                // ── Item details -- collapsed by default ──
                ExpandableSection(
                    title = stringResource(R.string.download_view_fields),
                    expanded = fieldsExpanded,
                    onToggle = { fieldsExpanded = !fieldsExpanded },
                ) {
                    DownloadField.entries.forEach { field ->
                        val selected = field in options.fields
                        OptionChip(label = stringResource(field.labelRes()), selected = selected) {
                            onChange(
                                options.copy(fields = if (selected) options.fields - field else options.fields + field),
                            )
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onChange(DownloadViewOptions()) },
                enabled = !options.isDefault,
            ) { Text(stringResource(R.string.download_view_reset)) }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.download_view_done)) }
        },
        compact = false,
    )
}

/** Key used in [DownloadViewOptions.hiddenTabs] for the "All" tab (type tabs use their enum name). */
internal const val DOWNLOAD_TAB_ALL = "ALL"

private fun DownloadViewOptions.directionLabelRes(): Int = when (sortBy) {
    DownloadSortBy.DATE -> if (ascending) R.string.download_view_dir_oldest else R.string.download_view_dir_newest
    DownloadSortBy.NAME -> if (ascending) R.string.download_view_dir_az else R.string.download_view_dir_za
    DownloadSortBy.SIZE -> if (ascending) R.string.download_view_dir_smallest else R.string.download_view_dir_largest
    DownloadSortBy.TYPE -> if (ascending) R.string.download_view_dir_type_fwd else R.string.download_view_dir_type_rev
}

private fun DownloadField.labelRes(): Int = when (this) {
    DownloadField.ICON -> R.string.download_view_field_icon
    DownloadField.BADGE -> R.string.download_view_field_badge
    DownloadField.SIZE -> R.string.download_view_field_size
    DownloadField.DURATION -> R.string.download_view_field_duration
    DownloadField.DATE -> R.string.download_view_field_date
    DownloadField.ACTIONS -> R.string.download_view_field_actions
    DownloadField.FULL_NAME -> R.string.download_view_field_full_name
}

@Composable
private fun SectionLabel(text: String, first: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = if (first) 0.dp else 12.dp, bottom = 6.dp),
    )
}

/** Header row with a rotating chevron; [content] (a flow of [OptionChip]s) is shown only while expanded. */
@Composable
private fun ExpandableSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    val arrowRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "sectionArrow")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.ChevronDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp).rotate(arrowRotation),
        )
    }
    AnimatedVisibility(visible = expanded) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        ) { content() }
    }
}

/**
 * Plain toggle pill: same size, no outline, no check icon (so nothing shifts when toggled).
 * Selected = filled primary container with bold label; unselected = quiet surface tone.
 */
@Composable
private fun OptionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "chipContainer",
    )
    val content by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "chipContent",
    )
    Surface(
        onClick = onClick,
        shape = XmdShapes.Xs,
        color = container,
        contentColor = content,
        modifier = Modifier.height(32.dp),
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
            )
        }
    }
}

/**
 * Segmented single-choice row using the same connected ToggleButtons as the Settings screens.
 * [onSelect] fires on every tap (including the already-selected one) so callers can use a
 * second tap for something like "reverse"; [selectedIcon] is drawn after the selected label.
 */
@Composable
private fun ConnectedChoice(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    selectedIcon: AppIcon? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            ToggleButton(
                checked = selected,
                onCheckedChange = { onSelect(index) },
                modifier = Modifier.weight(1f).height(38.dp),
                contentPadding = PaddingValues(horizontal = 6.dp),
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    labels.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) {
                Text(label, maxLines = 1, fontSize = 13.sp)
                if (selected && selectedIcon != null) {
                    Spacer(Modifier.size(2.dp))
                    Icon(imageVector = selectedIcon, contentDescription = null, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}
