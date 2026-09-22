package com.invictus.xmd.ui.components

import android.icu.text.DateFormatSymbols
import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.invictus.xmd.R
import com.invictus.xmd.domain.download.ScheduleMode
import com.invictus.xmd.ui.icons.Icon
import com.invictus.xmd.ui.icons.Icons
import java.util.Calendar

/** Locale-aware time for a minutes-since-midnight value. */
@Composable
fun formatMinuteOfDay(minute: Int): String {
    if (minute < 0) return "--:--"
    val context = LocalContext.current
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, minute / 60)
        set(Calendar.MINUTE, minute % 60)
    }
    return DateFormat.getTimeFormat(context).format(cal.time)
}

@Composable
private fun localizedDayLabels(width: Int): List<String> {
    val locale = LocalConfiguration.current.locales[0]
    val weekdays = DateFormatSymbols.getInstance(locale).getWeekdays(DateFormatSymbols.FORMAT, width)
    return (Calendar.SUNDAY..Calendar.SATURDAY).map { weekdays[it] }
}

/**
 * One day-of-week circle in the "Repeat on" row. Plain custom toggle
 * rather than Material's FilterChip -- FilterChip's baked-in min-width and
 * padding don't let 7 of them fit across a dialog on a phone-width screen
 * (Friday/Saturday were clipped), and its unselected/selected contrast is
 * too low on a dark surface to read at a glance. This is sized to fit all
 * seven with room to spare, with a solid, high-contrast fill when selected.
 */
@Composable
private fun DayToggle(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .then(
                if (!selected) {
                    Modifier.background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = CircleShape,
                    )
                } else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Localized summary for a day-of-week bitmask. */
@Composable
fun formatDaysMask(mask: Int): String {
    val dayLabels = localizedDayLabels(DateFormatSymbols.SHORT)
    return when (mask) {
        0x7F -> stringResource(R.string.schedule_every_day)
        0b0111110 -> stringResource(R.string.schedule_weekdays)
        0b1000001 -> stringResource(R.string.schedule_weekends)
        0 -> stringResource(R.string.schedule_never)
        else -> (0..6)
            .filter { (mask and (1 shl it)) != 0 }
            .joinToString(", ") { dayLabels[it] }
    }
}

/**
 * Start/end time wheels + a day-of-week chip row, used both for the global
 * quiet-hours default (SettingsDownloadsScreen) and a per-item custom
 * window (AddDownloadDialog/AddTorrentDialog). A wrapping window (start >
 * end, e.g. 23:00-06:00) is fully supported by DownloadScheduler.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeRangePickerDialog(
    initialStartMinute: Int,
    initialEndMinute: Int,
    initialDaysMask: Int,
    onConfirm: (startMinute: Int, endMinute: Int, daysMask: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val dayLabels = localizedDayLabels(DateFormatSymbols.NARROW)
    var showingEnd by remember { mutableStateOf(false) }
    var daysMask by remember { mutableStateOf(initialDaysMask) }
    val startState = rememberTimePickerState(
        initialHour = (initialStartMinute.takeIf { it >= 0 } ?: 60) / 60,
        initialMinute = (initialStartMinute.takeIf { it >= 0 } ?: 60) % 60,
        is24Hour = is24Hour,
    )
    val endState = rememberTimePickerState(
        initialHour = (initialEndMinute.takeIf { it >= 0 } ?: 360) / 60,
        initialMinute = (initialEndMinute.takeIf { it >= 0 } ?: 360) % 60,
        is24Hour = is24Hour,
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(
                    if (!showingEnd) R.string.schedule_start_time else R.string.schedule_end_time
                ),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            TimePicker(state = if (!showingEnd) startState else endState)

            Spacer(modifier = Modifier.size(16.dp))
            Text(
                text = stringResource(R.string.schedule_repeat_on),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                dayLabels.forEachIndexed { index, label ->
                    val bit = 1 shl index
                    val selected = (daysMask and bit) != 0
                    DayToggle(
                        label = label,
                        selected = selected,
                        onClick = { daysMask = daysMask xor bit },
                    )
                }
            }

            Spacer(modifier = Modifier.size(20.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
                Spacer(modifier = Modifier.weight(1f))
                if (!showingEnd) {
                    Button(onClick = { showingEnd = true }) { Text(stringResource(R.string.action_next)) }
                } else {
                    Button(
                        onClick = {
                            val start = startState.hour * 60 + startState.minute
                            val end = endState.hour * 60 + endState.minute
                            onConfirm(start, end, daysMask)
                        },
                        enabled = daysMask != 0,
                    ) { Text(stringResource(R.string.settings_save)) }
                }
            }
        }
        }
    }
}

/** One-time absolute start time -- date isn't picked separately here since
 *  "today, or tomorrow if that time already passed" covers the common case
 *  ("start this at 2am tonight"); [onConfirm] receives the resolved epoch ms. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneTimeStartPickerDialog(
    initialAtMs: Long,
    onConfirm: (atMs: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val now = remember { Calendar.getInstance() }
    val initial = remember {
        Calendar.getInstance().apply {
            if (initialAtMs > System.currentTimeMillis()) timeInMillis = initialAtMs
        }
    }
    val state = rememberTimePickerState(
        initialHour = initial.get(Calendar.HOUR_OF_DAY),
        initialMinute = initial.get(Calendar.MINUTE),
        is24Hour = is24Hour,
    )
    var startTomorrow by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.schedule_start_at),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            TimePicker(state = state)
            Spacer(modifier = Modifier.size(12.dp))
            Row {
                FilterChip(
                    selected = !startTomorrow,
                    onClick = { startTomorrow = false },
                    label = { Text(stringResource(R.string.schedule_today)) },
                    modifier = Modifier.padding(end = 6.dp),
                )
                FilterChip(
                    selected = startTomorrow,
                    onClick = { startTomorrow = true },
                    label = { Text(stringResource(R.string.schedule_tomorrow)) },
                )
            }

            Spacer(modifier = Modifier.size(20.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
                Spacer(modifier = Modifier.weight(1f))
                Button(onClick = {
                    val cal = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, state.hour)
                        set(Calendar.MINUTE, state.minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                        if (startTomorrow || timeInMillis <= now.timeInMillis) {
                            add(Calendar.DAY_OF_YEAR, 1)
                        }
                    }
                    onConfirm(cal.timeInMillis)
                }) { Text(stringResource(R.string.settings_save)) }
            }
        }
        }
    }
}

/**
 * Human-readable summary of a schedule, e.g. for the collapsed-Advanced-
 * section row in AddDownloadDialog. [compact] drops the "Start at " / "Custom
 * window " prefixes so it fits in a narrow trailing label next to the
 * section's own "Advanced" title.
 */
@Composable
fun scheduleLabel(
    scheduleMode: ScheduleMode,
    scheduledAtMs: Long,
    windowStartMinute: Int,
    windowEndMinute: Int,
    compact: Boolean = false,
): String = when (scheduleMode) {
    ScheduleMode.NONE -> stringResource(R.string.schedule_start_now)
    ScheduleMode.INHERIT_GLOBAL -> stringResource(R.string.schedule_quiet_hours)
    ScheduleMode.ONE_TIME -> {
        val time = formatMinuteOfDay(
            Calendar.getInstance().apply { timeInMillis = scheduledAtMs }
                .let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
        )
        if (compact) time else stringResource(R.string.schedule_start_at_time, time)
    }
    ScheduleMode.CUSTOM_WINDOW -> {
        val range = "${formatMinuteOfDay(windowStartMinute)}\u2013${formatMinuteOfDay(windowEndMinute)}"
        if (compact) range else stringResource(R.string.schedule_custom_window_time, range)
    }
}

/**
 * Compact per-item schedule control for AddDownloadDialog/AddTorrentDialog's
 * Advanced section -- same card + DropdownMenu shape as the
 * "Save to" folder picker card. [globalSchedulerEnabled] hides the
 * "Use quiet hours" option when there's no global default to inherit.
 */
@Composable
fun ScheduleSelectorRow(
    scheduleMode: ScheduleMode,
    scheduledAtMs: Long,
    windowStartMinute: Int,
    windowEndMinute: Int,
    windowDaysMask: Int,
    globalSchedulerEnabled: Boolean,
    onChanged: (mode: ScheduleMode, atMs: Long, startMin: Int, endMin: Int, daysMask: Int) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showWindowDialog by remember { mutableStateOf(false) }
    var showOneTimeDialog by remember { mutableStateOf(false) }

    val label = when (scheduleMode) {
        ScheduleMode.NONE -> stringResource(R.string.schedule_start_now)
        ScheduleMode.INHERIT_GLOBAL -> stringResource(R.string.schedule_use_quiet_hours)
        ScheduleMode.ONE_TIME -> stringResource(R.string.schedule_start_at_time, formatMinuteOfDay(
            Calendar.getInstance().apply { timeInMillis = scheduledAtMs }
                .let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
        ))
        ScheduleMode.CUSTOM_WINDOW -> stringResource(
            R.string.schedule_custom_window_time,
            "${formatMinuteOfDay(windowStartMinute)}\u2013${formatMinuteOfDay(windowEndMinute)}",
        )
    }

    Column {
        // Label + card mirror the "Save to" / FolderPickerCard row above it
        // so the two Advanced rows read as one family.
        Text(
            text = stringResource(R.string.schedule_label),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Box {
            Surface(
                onClick = { menuExpanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Icon(
                        imageVector = Icons.ArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.schedule_start_now)) },
                    onClick = {
                        menuExpanded = false
                        onChanged(ScheduleMode.NONE, 0L, -1, -1, 0x7F)
                    },
                )
                if (globalSchedulerEnabled) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.schedule_use_quiet_hours)) },
                        onClick = {
                            menuExpanded = false
                            onChanged(ScheduleMode.INHERIT_GLOBAL, 0L, -1, -1, 0x7F)
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.schedule_start_specific_time)) },
                    onClick = {
                        menuExpanded = false
                        showOneTimeDialog = true
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.schedule_custom_window)) },
                    onClick = {
                        menuExpanded = false
                        showWindowDialog = true
                    },
                )
            }
        }
    }

    if (showWindowDialog) {
        TimeRangePickerDialog(
            initialStartMinute = windowStartMinute,
            initialEndMinute = windowEndMinute,
            initialDaysMask = windowDaysMask,
            onConfirm = { start, end, mask ->
                showWindowDialog = false
                onChanged(ScheduleMode.CUSTOM_WINDOW, 0L, start, end, mask)
            },
            onDismiss = { showWindowDialog = false },
        )
    }

    if (showOneTimeDialog) {
        OneTimeStartPickerDialog(
            initialAtMs = scheduledAtMs,
            onConfirm = { atMs ->
                showOneTimeDialog = false
                onChanged(ScheduleMode.ONE_TIME, atMs, -1, -1, 0x7F)
            },
            onDismiss = { showOneTimeDialog = false },
        )
    }
}
