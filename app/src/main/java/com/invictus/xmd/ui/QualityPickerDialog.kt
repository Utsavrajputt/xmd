package com.invictus.xmd.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.invictus.xmd.R
import com.invictus.xmd.core.YtDlpManager

/**
 * Phase A conversion of MainActivity's inline quality-picker (previously a
 * MaterialAlertDialogBuilder wrapping dialog_quality_picker.xml, built
 * inside a suspendCancellableCoroutine block deep in the YouTube
 * download-trigger chain). MainActivity still owns the coroutine, the
 * probe job, and cont.resume() -- this composable is pure UI + selection
 * state, resolved back through [onConfirm]/[onDismiss] exactly like the
 * old setPositiveButton/setOnCancelListener pair did.
 *
 * Two independent selections exist (standard ladder vs. advanced probed
 * list), same as the two separate RadioGroups in the old XML -- picking a
 * row in one clears the other, and advanced wins on confirm if set
 * (mirrors the old "advanced checked first" resolution order).
 */
@Composable
fun QualityPickerDialog(
    titleText: String,
    standardOptions: List<YtDlpManager.QualityOption>,
    advancedFormats: List<YtDlpManager.ProbedFormat>,
    advancedLoading: Boolean,
    durationSeconds: Int?,
    onDismiss: () -> Unit,
    onConfirm: (YtDlpManager.QualityOption?) -> Unit,
) {
    // Default: one rung below the top of the ladder -- matches the old
    // group.getChildAt(1).isChecked default (a sane, non-extreme choice).
    var selectedStandard by remember(standardOptions) {
        mutableStateOf(standardOptions.getOrNull(1) ?: standardOptions.firstOrNull())
    }
    var selectedAdvanced by remember { mutableStateOf<YtDlpManager.ProbedFormat?>(null) }
    var advancedExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titleText) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                standardOptions.forEach { option ->
                    QualityPickerRow(
                        label = option.label,
                        selected = selectedAdvanced == null && selectedStandard == option,
                        onClick = {
                            selectedStandard = option
                            selectedAdvanced = null
                        },
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = false, onClick = { advancedExpanded = !advancedExpanded })
                        .padding(vertical = 14.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Advanced",
                        modifier = Modifier.Companion.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Icon(
                        painter = painterResource(
                            if (advancedExpanded) R.drawable.ic_arrow_down else R.drawable.ic_chevron_right
                        ),
                        contentDescription = null,
                    )
                }

                if (advancedExpanded) {
                    when {
                        advancedLoading -> Box(
                            Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                        advancedFormats.isEmpty() -> Text(
                            "Couldn't load extra formats",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        )
                        else -> advancedFormats.forEach { format ->
                            QualityPickerRow(
                                label = advancedFormatLabel(format, durationSeconds),
                                selected = selectedAdvanced == format,
                                onClick = { selectedAdvanced = format },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val resolved = selectedAdvanced?.let { format ->
                    YtDlpManager.QualityOption(
                        label = advancedFormatLabel(format, durationSeconds),
                        formatSelector = YtDlpManager.advancedSelector(format),
                        isAudioOnly = format.isAudioOnly,
                    )
                } ?: selectedStandard
                onConfirm(resolved)
            }) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
private fun QualityPickerRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
    }
}

/** Same label composition as the old buildRow()/probeAdvancedFormats() lambda. */
private fun advancedFormatLabel(format: YtDlpManager.ProbedFormat, durationSeconds: Int?): String = buildString {
    if (format.height != null) append("${format.height}p") else append("Audio")
    if (format.fps != null && format.fps > 30) append(" ${format.fps}fps")
    append(" \u00b7 ${format.ext.uppercase()}")
    if (format.vcodec != null) append(" \u00b7 ${format.vcodec.substringBefore('.')}")
    if (format.acodec != null && format.isAudioOnly) append(" \u00b7 ${format.acodec.substringBefore('.')}")
    val sizeText = YtDlpManager.formatSize(format, durationSeconds)
    if (sizeText != null) append(" \u00b7 $sizeText")
}
