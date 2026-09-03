package com.invictus.xmd.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

/**
 * Async state for the yt-dlp engine row (install/delete/update/nightly
 * switch). One flag instead of scattered per-button `isEnabled`/progress
 * booleans, per COMPOSE_MIGRATION.md's plan for this screen -- every button
 * click maps to exactly one state here, and the whole row (status text,
 * spinner, all three buttons) renders off it in one place.
 */
sealed class YtDlpOpState {
    object Idle : YtDlpOpState()
    object Installing : YtDlpOpState()
    object Updating : YtDlpOpState()
    data class SwitchingChannel(val toNightly: Boolean) : YtDlpOpState()
}

/**
 * Default download quality, video preset ladder (container/fps/codec),
 * audio format, and the yt-dlp engine install/update/nightly-channel
 * controls. Rendered directly by SettingsActivity's YoutubeRoute (NavHost
 * route body) -- no Fragment host.
 *
 * All preset dropdowns persist immediately on selection via their own
 * `onXChanged` callback (same as Downloads/Browser), matching the original
 * fragment's "no Save button" behavior for these fields. The yt-dlp
 * install/update/nightly controls were already immediate and remain so,
 * now driven by [ytDlpOpState] instead of scattered enabled/visibility
 * flags.
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
    ) {
        Text(
            text = hintText,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Lite build has no YtDlpManager backing this screen -- hide
        // everything below the hint, same as the old fragment hiding every
        // child after index 0 in ytdlpRootColumn.
        if (liteMode) return@Column

        // ===== Download quality / video preset / audio format =====
        SettingsSectionCard(modifier = Modifier.padding(top = 12.dp)) {
            Text(
                text = stringResource(R.string.settings_default_quality),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.settings_default_quality_hint),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            PresetDropdownField(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth(),
                options = qualityLabels,
                selected = selectedQualityLabel,
                onSelected = onQualityChanged,
            )

            SettingsDivider()

            Text(
                text = stringResource(R.string.settings_video_preset_title),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.settings_video_preset_hint),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
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

            SettingsDivider()

            Text(
                text = stringResource(R.string.settings_audio_format_title),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.settings_audio_format_hint),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
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

        // ===== yt-dlp engine =====
        Text(
            text = stringResource(R.string.settings_ytdlp_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 20.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
        ) {
            val idleStatusText = if (ytDlpInstalled) {
                val channel = stringResource(
                    if (ytDlpUsingNightly) R.string.settings_ytdlp_channel_nightly
                    else R.string.settings_ytdlp_channel_stable
                )
                "${stringResource(R.string.settings_ytdlp_status_installed)}  •  $channel"
            } else {
                stringResource(R.string.settings_ytdlp_status_not_installed)
            }
            val statusText = when (ytDlpOpState) {
                YtDlpOpState.Idle -> idleStatusText
                YtDlpOpState.Installing -> stringResource(R.string.settings_ytdlp_installing)
                YtDlpOpState.Updating -> stringResource(R.string.settings_ytdlp_updating)
                is YtDlpOpState.SwitchingChannel -> stringResource(
                    if (ytDlpOpState.toNightly) R.string.settings_ytdlp_switching_nightly
                    else R.string.settings_ytdlp_updating
                )
            }

            Text(
                text = statusText,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )

            val busy = ytDlpOpState != YtDlpOpState.Idle
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(16.dp),
                    strokeWidth = 2.dp,
                )
            }

            if (ytDlpInstalled) {
                TextButton(onClick = onUpdateClick, enabled = !busy) {
                    Text(stringResource(R.string.settings_ytdlp_update))
                }
            }
            TextButton(onClick = onInstallOrDeleteClick, enabled = !busy) {
                Text(
                    stringResource(
                        if (ytDlpInstalled) R.string.settings_ytdlp_delete
                        else R.string.settings_ytdlp_install
                    )
                )
            }
        }

        if (ytDlpInstalled) {
            TextButton(
                onClick = onNightlyToggleClick,
                enabled = ytDlpOpState == YtDlpOpState.Idle,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                Text(
                    text = stringResource(
                        if (ytDlpUsingNightly) R.string.settings_ytdlp_switch_stable
                        else R.string.settings_ytdlp_use_nightly
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Read-only Material3 exposed dropdown -- replaces the
 * TextInputLayout.OutlinedBox.ExposedDropdownMenu + AutoCompleteTextView
 * pairing used throughout the old XML. First use of this pattern in the
 * Compose migration (every other screen so far only needed switches/segmented
 * buttons), so this is the template for any future dropdown needs too.
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
