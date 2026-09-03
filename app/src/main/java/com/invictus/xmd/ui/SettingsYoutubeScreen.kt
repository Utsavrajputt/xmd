package com.invictus.xmd.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.invictus.xmd.R
import com.invictus.xmd.ui.icons.Icon
import com.invictus.xmd.ui.icons.Icons

/**
 * Async state for the yt-dlp engine row (install/delete/update/nightly
 * switch).
 */
sealed class YtDlpOpState {
    object Idle : YtDlpOpState()
    object Installing : YtDlpOpState()
    object Updating : YtDlpOpState()
    data class SwitchingChannel(val toNightly: Boolean) : YtDlpOpState()
}

/**
 * YouTube / yt-dlp preferences screen styled following modern card-based hierarchy
 * while retaining all xmd settings (default quality, container/fps/codec presets,
 * audio format, and engine management).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsYoutubeScreen(
    liteMode: Boolean,
    hintText: String,
    // Default quality
    qualityLabels: List<String>,
    selectedQualityLabel: String,
    onQualityChanged: (Int) -> Unit,
    // Video preset ladder
    containerOptions: List<String>,
    selectedContainer: String,
    onContainerChanged: (Int) -> Unit,
    fpsOptions: List<String>,
    selectedFps: String,
    onFpsChanged: (Int) -> Unit,
    codecOptions: List<String>,
    selectedCodec: String,
    onCodecChanged: (Int) -> Unit,
    // Audio format
    audioFormatOptions: List<String>,
    selectedAudioFormat: String,
    onAudioFormatChanged: (Int) -> Unit,
    // yt-dlp engine
    ytDlpInstalled: Boolean,
    ytDlpUsingNightly: Boolean,
    ytDlpOpState: YtDlpOpState,
    onInstallOrDeleteClick: () -> Unit,
    onUpdateClick: () -> Unit,
    onNightlyToggleClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = hintText,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Lite build has no YtDlpManager backing this screen
        if (liteMode) return@Column

        // ===== 1. yt-dlp Installation Status Card =====
        YtdlpStatusCard(
            installed = ytDlpInstalled,
            usingNightly = ytDlpUsingNightly,
            opState = ytDlpOpState,
        )

        // ===== 2. Release Channel & Engine Controls =====
        Column {
            SettingsSectionHeader(title = stringResource(R.string.settings_ytdlp_title))
            SettingsSectionCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val busy = ytDlpOpState != YtDlpOpState.Idle
                    val stableActionLabel = if (!ytDlpInstalled) {
                        stringResource(R.string.settings_ytdlp_install)
                    } else {
                        stringResource(R.string.settings_ytdlp_update)
                    }
                    val nightlyActionLabel = stringResource(
                        if (ytDlpUsingNightly) R.string.settings_ytdlp_switch_stable
                        else R.string.settings_ytdlp_use_nightly
                    )

                    Button(
                        onClick = {
                            if (ytDlpInstalled) onUpdateClick() else onInstallOrDeleteClick()
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            imageVector = if (!ytDlpInstalled) Icons.Download else Icons.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stableActionLabel)
                    }

                    if (ytDlpInstalled) {
                        OutlinedButton(
                            onClick = onNightlyToggleClick,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Icon(Icons.Sync, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(nightlyActionLabel)
                        }

                        OutlinedButton(
                            onClick = onInstallOrDeleteClick,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                        ) {
                            Icon(Icons.Delete, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_ytdlp_delete))
                        }
                    }
                }
            }
        }

        // ===== 3. Quality Preferences =====
        Column {
            SettingsSectionHeader(title = stringResource(R.string.settings_default_quality))
            SettingsSectionCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_default_quality_hint),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PresetDropdownField(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth(),
                    options = qualityLabels,
                    selected = selectedQualityLabel,
                    onSelected = onQualityChanged,
                )
            }
        }

        // ===== 4. Video Presets =====
        Column {
            SettingsSectionHeader(title = stringResource(R.string.settings_video_preset_title))
            SettingsSectionCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_video_preset_hint),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth(),
                ) {
                    PresetDropdownField(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.settings_preset_container_label),
                        options = containerOptions,
                        selected = selectedContainer,
                        onSelected = onContainerChanged,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    PresetDropdownField(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.settings_preset_fps_label),
                        options = fpsOptions,
                        selected = selectedFps,
                        onSelected = onFpsChanged,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    PresetDropdownField(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.settings_preset_codec_label),
                        options = codecOptions,
                        selected = selectedCodec,
                        onSelected = onCodecChanged,
                    )
                }
            }
        }

        // ===== 5. Audio Format =====
        Column {
            SettingsSectionHeader(title = stringResource(R.string.settings_audio_format_title))
            SettingsSectionCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_audio_format_hint),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PresetDropdownField(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth(),
                    options = audioFormatOptions,
                    selected = selectedAudioFormat,
                    onSelected = onAudioFormatChanged,
                )
            }
        }
    }
}

/**
 * Status card styled after mpvRx's YtdlpInstallationStatus.
 */
@Composable
private fun YtdlpStatusCard(
    installed: Boolean,
    usingNightly: Boolean,
    opState: YtDlpOpState,
    modifier: Modifier = Modifier,
) {
    val isBusy = opState != YtDlpOpState.Idle
    val containerColor = when {
        !installed -> MaterialTheme.colorScheme.errorContainer
        usingNightly -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = when {
        !installed -> MaterialTheme.colorScheme.onErrorContainer
        usingNightly -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    val title = when (opState) {
        YtDlpOpState.Installing -> stringResource(R.string.settings_ytdlp_installing)
        YtDlpOpState.Updating -> stringResource(R.string.settings_ytdlp_updating)
        is YtDlpOpState.SwitchingChannel -> stringResource(
            if (opState.toNightly) R.string.settings_ytdlp_switching_nightly
            else R.string.settings_ytdlp_updating
        )
        YtDlpOpState.Idle -> when {
            !installed -> stringResource(R.string.settings_ytdlp_status_not_installed)
            usingNightly -> stringResource(R.string.settings_ytdlp_channel_nightly)
            else -> stringResource(R.string.settings_ytdlp_channel_stable)
        }
    }

    val details = when {
        !installed -> stringResource(R.string.settings_ytdlp_hint)
        usingNightly -> "Nightly channel active · updates receive latest fixes"
        else -> "Stable release channel active"
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.2f)),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                        color = contentColor,
                    )
                } else {
                    Icon(
                        imageVector = if (installed) Icons.Check else Icons.Download,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.85f),
                )
            }
        }
    }
}

/**
 * Read-only Material3 exposed dropdown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetDropdownField(
    options: List<String>,
    selected: String,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            label = label?.let { l -> { Text(l, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(index)
                        expanded = false
                    },
                )
            }
        }
    }
}
