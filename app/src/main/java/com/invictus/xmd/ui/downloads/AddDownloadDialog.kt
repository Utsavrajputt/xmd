package com.invictus.xmd.ui.downloads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.invictus.xmd.R
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.invictus.xmd.domain.download.CategoryDetector
import com.invictus.xmd.domain.download.DownloadEngine
import com.invictus.xmd.domain.download.YtDlpManager
import com.invictus.xmd.preferences.Settings
import com.invictus.xmd.repository.QueueRepository
import com.invictus.xmd.ui.MainActivity
import com.invictus.xmd.ui.components.ChipGrid
import com.invictus.xmd.ui.components.ChipLabel
import com.invictus.xmd.ui.components.ChipRow
import com.invictus.xmd.ui.components.StartChipButton
import com.invictus.xmd.ui.components.WideDialogProperties
import com.invictus.xmd.ui.components.wideDialogWidth
import com.invictus.xmd.ui.settings.DnsSettingsDialog
import com.invictus.xmd.utils.LinkParser
import com.invictus.xmd.utils.storage.FileNameUtils
import com.invictus.xmd.utils.storage.OnDuplicateStrategy

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
 * Note: the streams list (opened by the "Streams" chip after Audio) nests a scrollable Column inside this
 * dialog's own scrollable Column (the old XML used a NestedScrollView with
 * a manual touch-intercept listener for the same reason). Compose handles
 * nested vertical scroll reasonably but this pairing hasn't been verified
 * on a real device yet -- flagged in the phase summary.
 */
@Composable
fun AddDownloadDialog(
    initialLink: String,
    /** Pre-fills the Name field -- used when the browser's own download
     *  intercept (WebView's DownloadListener) already has a guessed or
     *  probed filename by the time this dialog opens, so the user isn't
     *  starting from a blank field for a link they didn't type themselves. */
    initialName: String = "",
    defaultSavePath: String,
    magnetDisplayName: (String) -> String?,
    extractYoutubeFallbackName: (String) -> String,
    probeYoutubeTitle: suspend (String) -> String?,
    probeRealFilename: suspend (String) -> String?,
    onDetectedTorrentLink: (String) -> Unit,
    onPickTorrentFile: () -> Unit,
    /** False hides the "Pick .torrent file instead" button entirely --
     *  used when this dialog was opened from the browser's own download
     *  click (WebView's DownloadListener), where the link is already a
     *  concrete http(s) download and offering a torrent-file picker makes
     *  no sense. Manual "Add download" entry points (FAB, retry, share
     *  intent) keep the default true. */
    allowPickTorrentFile: Boolean = true,
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
        duplicateStrategy: OnDuplicateStrategy?,
        scheduleMode: com.invictus.xmd.domain.download.ScheduleMode,
        scheduledAtMs: Long,
        windowStartMinute: Int,
        windowEndMinute: Int,
        windowDaysMask: Int,
        sponsorBlockMode: YtDlpManager.SponsorBlockMode,
        sponsorBlockCategories: Set<String>,
    ) -> Unit,
    /** Playlist entries for the "choose videos" picker; empty result for a non-playlist link. Full flavor only -- lite returns empty. */
    probePlaylist: suspend (String) -> YtDlpManager.PlaylistProbeResult = { YtDlpManager.PlaylistProbeResult(null, emptyList()) },
) {
    val context = LocalContext.current
    var link by remember { mutableStateOf(initialLink) }
    var name by remember { mutableStateOf(initialName) }
    var nameManuallyEdited by remember { mutableStateOf(false) }
    var customSaveDir by remember { mutableStateOf<String?>(null) }
    var advancedExpanded by remember { mutableStateOf(false) }
    var audioFormatPreset by remember { mutableStateOf(Settings.presetAudioFormat()) }

    // ── SponsorBlock (Advanced) ──────────────────────────────────────────
    var sponsorBlockMode by remember { mutableStateOf(YtDlpManager.SponsorBlockMode.OFF) }
    val sponsorBlockCategories = remember { mutableStateListOf("sponsor") }

    // ── Playlist picker ──────────────────────────────────────────────────
    var playlistResult by remember { mutableStateOf<YtDlpManager.PlaylistProbeResult?>(null) }
    var playlistProbing by remember { mutableStateOf(false) }
    var playlistPickerOpen by remember { mutableStateOf(false) }
    val selectedPlaylistIds = remember { mutableStateListOf<String>() }

    var onDuplicateStrategy by remember { mutableStateOf<OnDuplicateStrategy?>(null) }
    var showSolutionsDialog by remember { mutableStateOf(false) }
    var scheduleMode by remember { mutableStateOf(com.invictus.xmd.domain.download.ScheduleMode.NONE) }
    var scheduledAtMs by remember { mutableStateOf(0L) }
    var windowStartMinute by remember { mutableStateOf(-1) }
    var windowEndMinute by remember { mutableStateOf(-1) }
    var windowDaysMask by remember { mutableStateOf(0x7F) }

    LaunchedEffect(name, customSaveDir) {
        onDuplicateStrategy = null
    }

    val category = remember(link, name) { CategoryDetector.detect(link, hint = name) }
    val trimmedName = name.trim()
    val targetFile = remember(trimmedName, customSaveDir, category) {
        if (trimmedName.isNotBlank()) {
            FileNameUtils.resolveDestinationFile(trimmedName, customSaveDir, category)
        } else null
    }
    val queueItems by QueueRepository.items.collectAsStateWithLifecycle()
    val conflictingItem = remember(targetFile, queueItems) {
        targetFile?.let { FileNameUtils.findConflictingDownload(it, queueItems) }
    }
    val diskFileExists = remember(targetFile) {
        targetFile?.exists() == true
    }
    val isDuplicate = conflictingItem != null || diskFileExists

    val needsYtDlp = LinkParser.needsYtDlp(link)
    val isGeneric = !LinkParser.isYoutubeLink(link)
    val needsPrepare = remember(link) {
        val trimmed = link.trim()
        trimmed.isNotBlank() && (LinkParser.isShareLink(trimmed) || LinkParser.isFitgirlPage(trimmed))
    }

    var selectedQualityLabel by remember { mutableStateOf<String?>(null) }
    var selectedQualityOption by remember { mutableStateOf<YtDlpManager.QualityOption?>(null) }
    var streamsExpanded by remember { mutableStateOf(false) }
    var advancedLoading by remember { mutableStateOf(false) }
    var advancedFormats by remember { mutableStateOf<List<YtDlpManager.ProbedFormat>>(emptyList()) }
    var advancedDurationSeconds by remember { mutableStateOf<Int?>(null) }
    var selectedAdvancedFormat by remember { mutableStateOf<YtDlpManager.ProbedFormat?>(null) }

    var availableQualityOptions by remember(needsYtDlp, isGeneric) {
        mutableStateOf<List<YtDlpManager.QualityOption>>(
            if (needsYtDlp) YtDlpManager.standardQualityOptions(isGenericOrHls = isGeneric) else emptyList()
        )
    }

    val videoOptions = remember(availableQualityOptions) { availableQualityOptions.filter { !it.isAudioOnly } }
    val audioOption = remember(availableQualityOptions) {
        availableQualityOptions.firstOrNull { it.isAudioOnly }
            ?: YtDlpManager.QualityOption("Audio", YtDlpManager.AUDIO_ONLY_SELECTOR, isAudioOnly = true)
    }
    // Streams chip label doubles as a status: "Streams…" while probing,
    // "Streams (N)" once formats are in, plain (and disabled) when the
    // probe found nothing -- so the chip never opens onto an empty list.
    val streamsLabel = when {
        advancedLoading -> "$STREAMS_CHIP_LABEL\u2026"
        advancedFormats.isNotEmpty() -> "$STREAMS_CHIP_LABEL (${advancedFormats.size})"
        else -> STREAMS_CHIP_LABEL
    }
    val qualityItems = remember(videoOptions, streamsLabel) { videoOptions.map { it.label } + "Audio" + streamsLabel }

    val finalQualityOption = selectedQualityOption

    // Reset quality selection + kick off the advanced probe whenever the
    // effective link changes -- mirrors updateQualitySection()'s
    // currentQualityLink guard via the LaunchedEffect key.
    LaunchedEffect(link) {
        val trimmed = link.trim()
        if (!LinkParser.isYoutubePlaylistLink(trimmed)) {
            playlistResult = null
            return@LaunchedEffect
        }
        playlistProbing = true
        val result = probePlaylist(trimmed)
        playlistProbing = false
        playlistResult = result
        // Pre-select everything -- "Add all N" is the common case; unchecking
        // a couple is less friction than starting from an all-empty list.
        selectedPlaylistIds.clear()
        selectedPlaylistIds.addAll(result.entries.map { it.id })
    }

    LaunchedEffect(link, needsYtDlp) {
        streamsExpanded = false
        sponsorBlockMode = YtDlpManager.SponsorBlockMode.OFF
        sponsorBlockCategories.clear()
        sponsorBlockCategories.add("sponsor")
        playlistResult = null
        playlistPickerOpen = false
        selectedPlaylistIds.clear()
        if (!needsYtDlp) {
            selectedQualityLabel = null
            selectedQualityOption = null
            advancedFormats = emptyList()
            selectedAdvancedFormat = null
            availableQualityOptions = emptyList()
            return@LaunchedEffect
        }
        val defaultStdOptions = YtDlpManager.standardQualityOptions(isGenericOrHls = isGeneric)
        availableQualityOptions = defaultStdOptions
        val savedQuality = Settings.ytDlpDefaultQualityLabel()
        val initialVideoOptions = defaultStdOptions.filter { !it.isAudioOnly }
        val initialItems = initialVideoOptions.map { it.label } + "Audio"
        val initial = when {
            savedQuality.startsWith("Audio", ignoreCase = true) -> "Audio"
            savedQuality.isNotBlank() && initialItems.contains(savedQuality) -> savedQuality
            initialItems.contains("1080p") -> "1080p"
            initialItems.contains("720p") -> "720p"
            else -> initialItems.firstOrNull() ?: "1080p"
        }
        selectedQualityLabel = initial
        selectedQualityOption = if (initial == "Audio") audioOption
        else initialVideoOptions.firstOrNull { it.label == initial }
        selectedAdvancedFormat = null

        advancedLoading = true
        advancedFormats = emptyList()
        val probe = withContext(Dispatchers.IO) { YtDlpManager.probeFormats(link, context) }
        advancedLoading = false
        advancedDurationSeconds = probe.durationSeconds
        if (!nameManuallyEdited && !probe.title.isNullOrBlank()) {
            name = probe.title
        }
        val sorted = probe.formats.sortedWith(
            compareByDescending<YtDlpManager.ProbedFormat> { it.height ?: -1 }
                .thenByDescending { it.fps ?: -1 }
                .thenByDescending { it.tbr ?: -1.0 }
        )
        advancedFormats = sorted

        if (sorted.isNotEmpty()) {
            val probedQualityOptions = YtDlpManager.qualityOptionsFromProbedFormats(sorted, isGenericOrHls = isGeneric)
            if (probedQualityOptions.isNotEmpty()) {
                availableQualityOptions = probedQualityOptions
                val updatedVideoOptions = probedQualityOptions.filter { !it.isAudioOnly }
                val updatedItems = updatedVideoOptions.map { it.label } + "Audio"
                val prevLabel = selectedQualityLabel
                val newSelection = when {
                    prevLabel?.startsWith("Audio", ignoreCase = true) == true -> "Audio"
                    savedQuality.startsWith("Audio", ignoreCase = true) -> "Audio"
                    savedQuality.isNotBlank() && updatedItems.contains(savedQuality) -> savedQuality
                    prevLabel != null && updatedItems.contains(prevLabel) -> prevLabel
                    updatedItems.contains("1080p") -> "1080p"
                    updatedItems.contains("720p") -> "720p"
                    else -> updatedItems.firstOrNull() ?: "1080p"
                }
                selectedQualityLabel = newSelection
                selectedQualityOption = if (newSelection == "Audio") {
                    probedQualityOptions.firstOrNull { it.isAudioOnly } ?: audioOption
                } else {
                    updatedVideoOptions.firstOrNull { it.label == newSelection }
                }
            }
        }
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
        modifier = Modifier.wideDialogWidth(),
        properties = WideDialogProperties,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when {
                        needsPrepare -> Icons.Sync
                        LinkParser.isYoutubeLink(link) -> Icons.Youtube
                        needsYtDlp -> Icons.Video
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

                if (!needsYtDlp && allowPickTorrentFile) {
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
                    isError = isDuplicate && onDuplicateStrategy == null,
                )

                if (isDuplicate && onDuplicateStrategy == null) {
                    Text(
                        text = stringResource(R.string.download_already_exists),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                    )
                } else if (isDuplicate && onDuplicateStrategy != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val strategyText = if (onDuplicateStrategy == OnDuplicateStrategy.AddNumbered) {
                            stringResource(R.string.download_strategy_add_a_numbered_file)
                        } else {
                            stringResource(R.string.download_strategy_override_existing_file)
                        }
                        Text(
                            text = strategyText,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                        )
                        TextButton(
                            onClick = { showSolutionsDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(stringResource(R.string.change_solution), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // ── Playlist picker ──────────────────────────────────────
                // Only for an actual /playlist or ...&list=... link; a plain
                // single-video URL never triggers the probe (playlistResult
                // stays null), so this block simply doesn't render for it.
                val playlistEntries = playlistResult?.entries.orEmpty()
                if (playlistProbing || playlistEntries.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.ViewList,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (playlistProbing) {
                                        stringResource(R.string.download_dialog_playlist_loading)
                                    } else {
                                        stringResource(R.string.download_dialog_playlist_detected, playlistEntries.size)
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                )
                                if (playlistProbing) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                }
                            }
                            if (!playlistProbing && playlistEntries.isNotEmpty()) {
                                Spacer(Modifier.height(10.dp))
                                ChipRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    options = listOf(
                                        stringResource(R.string.download_dialog_playlist_just_this),
                                        stringResource(R.string.download_dialog_playlist_choose),
                                    ),
                                    selected = if (playlistPickerOpen) {
                                        stringResource(R.string.download_dialog_playlist_choose)
                                    } else {
                                        stringResource(R.string.download_dialog_playlist_just_this)
                                    },
                                    onSelected = { index -> playlistPickerOpen = index == 1 },
                                )
                                AnimatedVisibility(visible = playlistPickerOpen) {
                                    Column {
                                        Spacer(Modifier.height(10.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = stringResource(
                                                    R.string.download_dialog_playlist_selected_count,
                                                    selectedPlaylistIds.size, playlistEntries.size,
                                                ),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.weight(1f),
                                            )
                                            TextButton(
                                                onClick = {
                                                    if (selectedPlaylistIds.size == playlistEntries.size) {
                                                        selectedPlaylistIds.clear()
                                                    } else {
                                                        selectedPlaylistIds.clear()
                                                        selectedPlaylistIds.addAll(playlistEntries.map { it.id })
                                                    }
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            ) {
                                                Text(
                                                    text = if (selectedPlaylistIds.size == playlistEntries.size) {
                                                        stringResource(R.string.download_dialog_playlist_select_none)
                                                    } else {
                                                        stringResource(R.string.download_dialog_playlist_select_all)
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        LazyColumn(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 260.dp),
                                        ) {
                                            items(playlistEntries, key = { it.id }) { entry ->
                                                val checked = entry.id in selectedPlaylistIds
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .toggleable(
                                                            value = checked,
                                                            onValueChange = { on ->
                                                                if (on) selectedPlaylistIds.add(entry.id)
                                                                else selectedPlaylistIds.remove(entry.id)
                                                            },
                                                        )
                                                        .padding(vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Checkbox(checked = checked, onCheckedChange = null)
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(
                                                        text = entry.title,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (needsYtDlp) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(R.string.download_dialog_quality_label),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    // Chip grid instead of a dropdown -- every quality rung
                    // is a single tap, same pattern as the yt-dlp settings
                    // screen's quality/audio pickers. "Streams" is the last
                    // chip; it opens the raw probed stream list below and is
                    // only highlighted once one of those streams is picked.
                    ChipGrid(
                        modifier = Modifier.fillMaxWidth(),
                        options = qualityItems,
                        selected = if (selectedAdvancedFormat != null) streamsLabel else selectedQualityLabel ?: "",
                        onSelected = { index ->
                            val item = qualityItems[index]
                            if (index == qualityItems.lastIndex) {
                                streamsExpanded = !streamsExpanded
                            } else {
                                // Picking a ladder rung (or Audio) while the probed
                                // streams list is open makes it stale -- close it
                                // rather than leaving it showing options for the
                                // previous height.
                                streamsExpanded = false
                                selectedQualityLabel = item
                                selectedAdvancedFormat = null
                                selectedQualityOption = if (item == "Audio") audioOption
                                else videoOptions.firstOrNull { it.label == item }
                            }
                        },
                        columns = 4,
                        wrapLongLabels = true,
                        disabledOptions = if (advancedFormats.isEmpty()) setOf(streamsLabel) else emptySet(),
                    )
                    if (selectedQualityLabel == "Audio") {
                        val audioFormatChoices = listOf(
                            "MP3" to Settings.AudioFormatPreset.MP3,
                            "M4A" to Settings.AudioFormatPreset.M4A,
                            "Opus" to Settings.AudioFormatPreset.OPUS,
                            "Original" to Settings.AudioFormatPreset.ORIGINAL,
                        )
                        Spacer(Modifier.height(10.dp))
                        ChipLabel(stringResource(R.string.settings_audio_format_title))
                        ChipRow(
                            modifier = Modifier.fillMaxWidth(),
                            options = audioFormatChoices.map { it.first },
                            selected = audioFormatChoices.firstOrNull { it.second == audioFormatPreset }?.first ?: "MP3",
                            onSelected = { index -> audioFormatPreset = audioFormatChoices[index].second },
                        )
                    }


                    AnimatedVisibility(visible = streamsExpanded) {
                        Column {
                            Spacer(Modifier.height(10.dp))
                            ChipLabel(stringResource(R.string.download_dialog_advanced_streams_title))
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
                                                            if (format.isAudioOnly) selectedQualityLabel = "Audio"
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
                        maxLines = 1,
                    )
                    // Schedule summary while collapsed -- only when one is
                    // actually set ("Start now" is the default, so nothing shown).
                    if (!advancedExpanded && scheduleMode != com.invictus.xmd.domain.download.ScheduleMode.NONE) {
                        Text(
                            text = com.invictus.xmd.ui.components.scheduleLabel(
                                scheduleMode, scheduledAtMs, windowStartMinute, windowEndMinute, compact = true,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    Icon(
                        imageVector = Icons.ArrowDown,
                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp)
                            .rotate(if (advancedExpanded) 0f else -90f),
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
                    com.invictus.xmd.ui.components.FolderPickerCard(
                        path = customSaveDir ?: defaultSavePath,
                        onChangeClick = { onChangeSaveDir { path -> customSaveDir = path } },
                    )

                    Spacer(Modifier.height(14.dp))
                    com.invictus.xmd.ui.components.ScheduleSelectorRow(
                        scheduleMode = scheduleMode,
                        scheduledAtMs = scheduledAtMs,
                        windowStartMinute = windowStartMinute,
                        windowEndMinute = windowEndMinute,
                        windowDaysMask = windowDaysMask,
                        globalSchedulerEnabled = Settings.schedulerEnabled(),
                        onChanged = { mode, atMs, startMin, endMin, daysMask ->
                            scheduleMode = mode
                            scheduledAtMs = atMs
                            windowStartMinute = startMin
                            windowEndMinute = endMin
                            windowDaysMask = daysMask
                        },
                    )

                    if (needsYtDlp) {
                        Spacer(Modifier.height(14.dp))
                        ChipLabel(stringResource(R.string.download_dialog_sponsorblock_title))
                        ChipRow(
                            modifier = Modifier.fillMaxWidth(),
                            options = listOf(
                                stringResource(R.string.download_dialog_sponsorblock_off),
                                stringResource(R.string.download_dialog_sponsorblock_mark),
                                stringResource(R.string.download_dialog_sponsorblock_remove),
                            ),
                            selected = when (sponsorBlockMode) {
                                YtDlpManager.SponsorBlockMode.OFF -> stringResource(R.string.download_dialog_sponsorblock_off)
                                YtDlpManager.SponsorBlockMode.MARK -> stringResource(R.string.download_dialog_sponsorblock_mark)
                                YtDlpManager.SponsorBlockMode.REMOVE -> stringResource(R.string.download_dialog_sponsorblock_remove)
                            },
                            onSelected = { index ->
                                sponsorBlockMode = YtDlpManager.SponsorBlockMode.entries[index]
                            },
                        )
                        AnimatedVisibility(visible = sponsorBlockMode != YtDlpManager.SponsorBlockMode.OFF) {
                            Column {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = stringResource(R.string.download_dialog_sponsorblock_categories_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 6.dp),
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    YtDlpManager.SPONSORBLOCK_CATEGORIES.forEach { category ->
                                        val checked = category in sponsorBlockCategories
                                        AppFilterChip(
                                            label = category.replace('_', ' ').replaceFirstChar { it.uppercase() },
                                            selected = checked,
                                            onClick = {
                                                if (checked) {
                                                    // Keep at least one category selected -- an
                                                    // empty set falls back to yt-dlp's own
                                                    // "sponsor"-only default, silently diverging
                                                    // from what the chips show as picked.
                                                    if (sponsorBlockCategories.size > 1) sponsorBlockCategories.remove(category)
                                                } else {
                                                    sponsorBlockCategories.add(category)
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isDuplicate && onDuplicateStrategy == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val completedFile = targetFile?.takeIf { it.exists() }
                        ?: conflictingItem?.filePath?.let { File(it) }?.takeIf { it.exists() }
                    if (completedFile != null) {
                        OutlinedButton(
                            onClick = { openFile(context, completedFile) },
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(stringResource(R.string.open_existing_file))
                        }
                    }
                    StartChipButton(onClick = { showSolutionsDialog = true }) {
                        Text(stringResource(R.string.show_solutions))
                    }
                }
            } else {
                val playlistEntriesForStart = playlistResult?.entries.orEmpty()
                val startingPlaylistSelection = playlistPickerOpen && playlistEntriesForStart.isNotEmpty()
                StartChipButton(onClick = {
                    if (startingPlaylistSelection) {
                        // One onStart call per selected entry -- reuses the
                        // exact same enqueue path as a single download, just
                        // looped, so no new plumbing was needed in the two
                        // callers (MainActivity / ShareReceiverActivity).
                        playlistEntriesForStart
                            .filter { it.id in selectedPlaylistIds }
                            .forEach { entry ->
                                onStart(
                                    entry.url,
                                    entry.title,
                                    customSaveDir,
                                    finalQualityOption,
                                    audioFormatPreset,
                                    onDuplicateStrategy,
                                    scheduleMode,
                                    scheduledAtMs,
                                    windowStartMinute,
                                    windowEndMinute,
                                    windowDaysMask,
                                    sponsorBlockMode,
                                    sponsorBlockCategories.toSet(),
                                )
                            }
                    } else if (link.isNotBlank()) {
                        onStart(
                            link.trim(),
                            name.trim().takeUnless { it.isBlank() },
                            customSaveDir,
                            finalQualityOption,
                            audioFormatPreset,
                            onDuplicateStrategy,
                            scheduleMode,
                            scheduledAtMs,
                            windowStartMinute,
                            windowEndMinute,
                            windowDaysMask,
                            sponsorBlockMode,
                            sponsorBlockCategories.toSet(),
                        )
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
                    } else if (startingPlaylistSelection) {
                        Text(
                            stringResource(
                                R.string.download_dialog_playlist_add_n,
                                selectedPlaylistIds.size,
                            ),
                        )
                    } else {
                        Text(stringResource(R.string.torrent_dialog_start))
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.torrent_dialog_cancel)) }
        },
    )

    if (showSolutionsDialog) {
        AlertDialog(
            onDismissRequest = { showSolutionsDialog = false },
            modifier = Modifier.wideDialogWidth(),
            properties = WideDialogProperties,
            title = {
                Text(
                    stringResource(R.string.select_a_solution),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column {
                    Text(
                        stringResource(R.string.select_download_strategy_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    DuplicateSolutionCard(
                        title = stringResource(R.string.download_strategy_add_a_numbered_file),
                        description = stringResource(R.string.download_strategy_add_a_numbered_file_description),
                        isSelected = onDuplicateStrategy == OnDuplicateStrategy.AddNumbered,
                        onClick = {
                            onDuplicateStrategy = OnDuplicateStrategy.AddNumbered
                            showSolutionsDialog = false
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    DuplicateSolutionCard(
                        title = stringResource(R.string.download_strategy_override_existing_file),
                        description = stringResource(R.string.download_strategy_override_existing_file_description),
                        isSelected = onDuplicateStrategy == OnDuplicateStrategy.OverrideDownload,
                        onClick = {
                            onDuplicateStrategy = OnDuplicateStrategy.OverrideDownload
                            showSolutionsDialog = false
                        },
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSolutionsDialog = false }) {
                    Text(stringResource(R.string.torrent_dialog_cancel))
                }
            }
        )
    }
}

@Composable
private fun DuplicateSolutionCard(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun openFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val ext = file.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, R.string.open_file_missing, Toast.LENGTH_SHORT).show()
    }
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

private const val STREAMS_CHIP_LABEL = "Streams"
