package com.invictus.xmd.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import com.invictus.xmd.ui.icons.Icon
import com.invictus.xmd.ui.icons.Icons
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.invictus.xmd.R
import com.invictus.xmd.core.DownloadEngine
import com.invictus.xmd.core.LinkParser
import com.invictus.xmd.core.Settings
import com.invictus.xmd.core.YtDlpManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase A conversion of MainActivity.showAddDownloadDialog() -- previously
 * a MaterialAlertDialogBuilder wrapping dialog_add_download.xml. All
 * network/probe side effects (yt-dlp format probe, real-filename probe,
 * YouTube oEmbed title probe, magnet display-name parsing) are still
 * MainActivity's own suspend/pure functions, passed in as lambdas so this
 * file owns UI/state only -- same split DnsSettingsDialog established.
 * [YtDlpManager.probeFormats] itself only needs a Context (via
 * [LocalContext]), so it's called directly here rather than threaded
 * through a lambda.
 *
 * [onDetectedTorrentLink] fires the instant the link field looks like a
 * magnet/torrent link (mirrors the old doAfterTextChanged's immediate
 * dialog?.dismiss() + showAddTorrentDialog() redirect) -- the caller is
 * expected to close this dialog and open the torrent one.
 *
 * Note: the advanced-streams list nests a scrollable Column inside this
 * dialog's own scrollable Column (the old XML used a NestedScrollView with
 * a manual touch-intercept listener for the same reason). Compose handles
 * nested vertical scroll reasonably but this pairing hasn't been verified
 * on a real device yet -- flagged in the phase summary.
 */
@Composable
fun AddDownloadDialog(
    initialLink: String,
    defaultSavePath: String,
    magnetDisplayName: (String) -> String?,
    extractYoutubeFallbackName: (String) -> String,
    probeYoutubeTitle: suspend (String) -> String?,
    probeRealFilename: suspend (String) -> String?,
    onDetectedTorrentLink: (String) -> Unit,
    onPickTorrentFile: () -> Unit,
    onCopyLink: (String) -> Unit,
    onPasteRequest: () -> String?,
    onChangeSaveDir: (onPicked: (String) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onStart: (
        link: String,
        name: String?,
        saveDir: String?,
        quality: YtDlpManager.QualityOption?,
        audioFormat: Settings.AudioFormatPreset,
    ) -> Unit,
) {
    val context = LocalContext.current
    var link by remember { mutableStateOf(initialLink) }
    var name by remember { mutableStateOf("") }
    var nameManuallyEdited by remember { mutableStateOf(false) }
    var customSaveDir by remember { mutableStateOf<String?>(null) }
    var advancedExpanded by remember { mutableStateOf(false) }
    var audioFormatPreset by remember { mutableStateOf(Settings.presetAudioFormat()) }

    val needsYtDlp = LinkParser.needsYtDlp(link)
    val isGeneric = !LinkParser.isYoutubeLink(link)
    val needsPrepare = remember(link) {
        val trimmed = link.trim()
        trimmed.isNotBlank() && (LinkParser.isShareLink(trimmed) || LinkParser.isFitgirlPage(trimmed))
    }

    var selectedQualityLabel by remember { mutableStateOf<String?>(null) }
    var selectedQualityOption by remember { mutableStateOf<YtDlpManager.QualityOption?>(null) }
    var advancedLoading by remember { mutableStateOf(false) }
    var advancedFormats by remember { mutableStateOf<List<YtDlpManager.ProbedFormat>>(emptyList()) }
    var advancedDurationSeconds by remember { mutableStateOf<Int?>(null) }
    var selectedAdvancedFormat by remember { mutableStateOf<YtDlpManager.ProbedFormat?>(null) }

    val standardOptions = remember(needsYtDlp, isGeneric) {
        if (needsYtDlp) YtDlpManager.standardQualityOptions(isGenericOrHls = isGeneric) else emptyList()
    }
    val videoOptions = remember(standardOptions) { standardOptions.filter { !it.isAudioOnly } }
    val audioOption = remember(standardOptions) {
        standardOptions.firstOrNull { it.isAudioOnly }
            ?: YtDlpManager.QualityOption("Audio only", YtDlpManager.AUDIO_ONLY_SELECTOR, isAudioOnly = true)
    }
    val qualityItems = remember(videoOptions) { videoOptions.map { it.label } + "Audio only" }

    // Reset quality selection + kick off the advanced probe whenever the
    // effective link changes -- mirrors updateQualitySection()'s
    // currentQualityLink guard via the LaunchedEffect key.
    LaunchedEffect(link, needsYtDlp) {
        if (!needsYtDlp) {
            selectedQualityLabel = null
            selectedQualityOption = null
            advancedFormats = emptyList()
            selectedAdvancedFormat = null
            return@LaunchedEffect
        }
        val savedQuality = Settings.ytDlpDefaultQualityLabel()
        val initial = when {
            savedQuality.startsWith("Audio only", ignoreCase = true) -> "Audio only"
            savedQuality.isNotBlank() && qualityItems.contains(savedQuality) -> savedQuality
            qualityItems.contains("1080p") -> "1080p"
            qualityItems.contains("720p") -> "720p"
            else -> qualityItems.firstOrNull() ?: "1080p"
        }
        selectedQualityLabel = initial
        selectedQualityOption = if (initial == "Audio only") audioOption
        else videoOptions.firstOrNull { it.label == initial }
        selectedAdvancedFormat = null

        advancedLoading = true
        advancedFormats = emptyList()
        val probe = withContext(Dispatchers.IO) { YtDlpManager.probeFormats(link, context) }
        advancedLoading = false
        advancedDurationSeconds = probe.durationSeconds
        advancedFormats = probe.formats.sortedWith(
            compareByDescending<YtDlpManager.ProbedFormat> { it.height ?: -1 }
                .thenByDescending { it.fps ?: -1 }
                .thenByDescending { it.tbr ?: -1.0 }
        )
    }

    // Name auto-fill -- mirrors updateNameForLink(), skipped once the user
    // has edited the name field by hand.
    LaunchedEffect(link, nameManuallyEdited) {
        if (nameManuallyEdited) return@LaunchedEffect
        if (link.isBlank()) {
            name = ""
            return@LaunchedEffect
        }
        when {
            LinkParser.isMagnetLink(link) -> {
                magnetDisplayName(link)?.takeIf { it.isNotBlank() }?.let { name = it }
            }
            LinkParser.isYoutubeLink(link) -> {
                name = extractYoutubeFallbackName(link)
                val probed = probeYoutubeTitle(link)
                if (!nameManuallyEdited && !probed.isNullOrBlank()) name = probed
            }
            else -> {
                val guessed = DownloadEngine.filenameFromLink(link).ifBlank { DownloadEngine.filenameFromUrl(link) }
                if (guessed.isNotBlank()) name = guessed
                val probed = probeRealFilename(link)
                if (!nameManuallyEdited && !probed.isNullOrBlank()) name = probed
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when {
                        needsPrepare -> Icons.Sync
                        needsYtDlp -> Icons.Youtube
                        else -> Icons.Download
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    if (needsPrepare) stringResource(R.string.action_prepare) + " " + stringResource(R.string.download_dialog_title)
                    else stringResource(R.string.download_dialog_title)
                )
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.torrent_dialog_link_label),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = { onCopyLink(link) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Copy,
                                contentDescription = stringResource(R.string.torrent_dialog_copy_link),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        IconButton(
                            onClick = {
                                val pasted = onPasteRequest()
                                if (!pasted.isNullOrBlank()) {
                                    if (LinkParser.isTorrentLink(pasted) && pasted.contains("xt=", ignoreCase = true)) {
                                        onDetectedTorrentLink(pasted)
                                    } else {
                                        nameManuallyEdited = false
                                        link = pasted
                                    }
                                }
                            },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Paste,
                                contentDescription = stringResource(R.string.dialog_paste_link),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = link,
                    onValueChange = { text ->
                        link = text
                        if (LinkParser.isTorrentLink(text) && text.contains("xt=", ignoreCase = true)) {
                            onDetectedTorrentLink(text)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    placeholder = { Text(stringResource(R.string.download_dialog_link_hint)) },
                    minLines = 2,
                    maxLines = 4,
                )

                if (!needsYtDlp) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onPickTorrentFile,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.torrent_dialog_pick_file))
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(R.string.torrent_dialog_name_label),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameManuallyEdited = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    placeholder = { Text(stringResource(R.string.download_dialog_name_placeholder)) },
                    maxLines = 2,
                )

                if (needsYtDlp) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(R.string.download_dialog_quality_label),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        var qualityMenuExpanded by remember { mutableStateOf(false) }
                        Box(Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { qualityMenuExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text(selectedQualityLabel ?: "\u2014")
                            }
                            DropdownMenu(expanded = qualityMenuExpanded, onDismissRequest = { qualityMenuExpanded = false }) {
                                qualityItems.forEach { item ->
                                    DropdownMenuItem(text = { Text(item) }, onClick = {
                                        qualityMenuExpanded = false
                                        selectedQualityLabel = item
                                        selectedAdvancedFormat = null
                                        selectedQualityOption = if (item == "Audio only") audioOption
                                        else videoOptions.firstOrNull { it.label == item }
                                    })
                                }
                            }
                        }
                        if (selectedQualityLabel == "Audio only") {
                            var audioMenuExpanded by remember { mutableStateOf(false) }
                            val audioFormatChoices = listOf(
                                "MP3" to Settings.AudioFormatPreset.MP3,
                                "M4A" to Settings.AudioFormatPreset.M4A,
                                "Opus" to Settings.AudioFormatPreset.OPUS,
                                "Original" to Settings.AudioFormatPreset.ORIGINAL,
                            )
                            Box(Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = { audioMenuExpanded = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    Text(audioFormatChoices.firstOrNull { it.second == audioFormatPreset }?.first ?: "MP3")
                                }
                                DropdownMenu(expanded = audioMenuExpanded, onDismissRequest = { audioMenuExpanded = false }) {
                                    audioFormatChoices.forEach { (label, preset) ->
                                        DropdownMenuItem(text = { Text(label) }, onClick = {
                                            audioMenuExpanded = false
                                            audioFormatPreset = preset
                                        })
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { advancedExpanded = !advancedExpanded }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.torrent_dialog_advanced_label),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = if (advancedExpanded) Icons.ArrowDown else Icons.ChevronRight,
                        contentDescription = null,
                    )
                }

                if (advancedExpanded) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.torrent_dialog_save_to_label),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = customSaveDir ?: defaultSavePath,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = { onChangeSaveDir { path -> customSaveDir = path } },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    stringResource(R.string.torrent_dialog_change_path),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }

                    if (needsYtDlp) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            stringResource(R.string.download_dialog_advanced_streams_title),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        when {
                            advancedLoading -> Text(
                                stringResource(R.string.download_dialog_advanced_streams_probing),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                            advancedFormats.isEmpty() -> Text(
                                stringResource(R.string.download_dialog_advanced_streams_empty),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                            else -> Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 210.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    advancedFormats.forEachIndexed { index, format ->
                                        val label = advancedStreamLabel(format, advancedDurationSeconds)
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .selectable(
                                                    selected = selectedAdvancedFormat == format,
                                                    onClick = {
                                                        selectedAdvancedFormat = format
                                                        selectedQualityOption = YtDlpManager.QualityOption(
                                                            label = label,
                                                            formatSelector = YtDlpManager.advancedSelector(format),
                                                            isAudioOnly = format.isAudioOnly,
                                                        )
                                                        if (format.isAudioOnly) selectedQualityLabel = "Audio only"
                                                    },
                                                )
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            RadioButton(selected = selectedAdvancedFormat == format, onClick = null)
                                            Text(
                                                label,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(start = 8.dp),
                                            )
                                        }
                                        if (index < advancedFormats.lastIndex) {
                                            HorizontalDivider(
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (link.isNotBlank()) {
                    onStart(link.trim(), name.trim().takeUnless { it.isBlank() }, customSaveDir, selectedQualityOption, audioFormatPreset)
                }
            }) {
                if (needsPrepare) {
                    Icon(
                        imageVector = Icons.Sync,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_prepare))
                } else {
                    Text(stringResource(R.string.torrent_dialog_start))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.torrent_dialog_cancel)) }
        },
    )
}

private fun advancedStreamLabel(format: YtDlpManager.ProbedFormat, durationSeconds: Int?): String = buildString {
    if (format.height != null) append("${format.height}p") else append("Audio")
    if (format.fps != null && format.fps > 30) append(" ${format.fps}fps")
    append(" \u00b7 ${format.ext.uppercase()}")
    if (format.vcodec != null) append(" \u00b7 ${format.vcodec.substringBefore('.')}")
    if (format.acodec != null && format.isAudioOnly) append(" \u00b7 ${format.acodec.substringBefore('.')}")
    val sizeText = YtDlpManager.formatSize(format, durationSeconds)
    if (sizeText != null) append(" \u00b7 $sizeText")
}
