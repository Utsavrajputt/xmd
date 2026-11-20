@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.invictus.xmd.ui.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import com.invictus.xmd.ui.icons.Icon
import com.invictus.xmd.ui.icons.Icons
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import com.invictus.xmd.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.invictus.xmd.domain.browser.MediaSniffer

/**
 * Phase 5 (Browser) conversion of the old showSniffedMediaSheet() --
 * previously a BottomSheetDialog inflating sheet_sniffed_media.xml and
 * hand-building one LinearLayout row per stream. Same shape as Phase 3's
 * YtDlpQualitySheet: a ModalBottomSheet with a scrollable column of rows,
 * each a label (tap = pick this stream) plus a trailing copy-link button
 * (tap = clipboard only, doesn't dismiss the sheet).
 *
 * [streams] is a one-shot snapshot taken by the caller when the sheet is
 * opened (same synchronized-map read the old code did) -- this composable
 * doesn't observe the tab's live sniffedMedia map itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SniffedMediaSheet(
    streams: List<MediaSniffer.Sniffed>,
    pageTitle: String? = null,
    onStreamSelected: (MediaSniffer.Sniffed) -> Unit,
    onDownloadClick: (MediaSniffer.Sniffed) -> Unit = onStreamSelected,
    onCopyLink: (String) -> Unit,
    onPlayClick: ((MediaSniffer.Sniffed) -> Unit)? = null,
    isResumable: (MediaSniffer.Sniffed) -> Boolean = { false },
    onIgnoreSite: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun closeSheet() {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        // Header: how many videos were found + the page they came from.
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)) {
            Text(
                text = if (streams.size == 1) stringResource(R.string.browser_fab_videos_one)
                else stringResource(R.string.browser_fab_videos_many, streams.size),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            streams.forEachIndexed { index, stream ->
                // YouTube streams only carry an id; look up the real title (falls back to the id label).
                val isYoutube = com.invictus.xmd.utils.LinkParser.isYoutubeLink(stream.url)
                val youtubeTitle by produceState<String?>(
                    initialValue = if (isYoutube) com.invictus.xmd.domain.browser.YoutubeTitleResolver.cached(stream.url) else null,
                    stream.url,
                ) {
                    if (isYoutube && value == null) {
                        value = withContext(Dispatchers.IO) {
                            com.invictus.xmd.domain.browser.YoutubeTitleResolver.title(stream.url)
                        }
                    }
                }
                SniffedMediaRow(
                    stream = stream,
                    displayName = youtubeTitle ?: streamDisplayName(stream, index, streams.size, pageTitle),
                    onClick = {
                        onStreamSelected(stream)
                        closeSheet()
                    },
                    onDownloadClick = {
                        onDownloadClick(stream)
                        closeSheet()
                    },
                    onCopyClick = { onCopyLink(stream.url) },
                    onPlayClick = onPlayClick?.let { playAction ->
                        {
                            playAction(stream)
                            closeSheet()
                        }
                    },
                    resumable = isResumable(stream),
                )
            }
        }
        if (onIgnoreSite != null) {
            androidx.compose.material3.TextButton(
                onClick = {
                    onIgnoreSite()
                    closeSheet()
                },
                modifier = Modifier.padding(horizontal = 12.dp),
            ) {
                Text(stringResource(R.string.sniff_ignore_site))
            }
        }
    }
}

@Composable
private fun SniffedMediaRow(
    stream: MediaSniffer.Sniffed,
    displayName: String,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onCopyClick: () -> Unit,
    onPlayClick: (() -> Unit)? = null,
    resumable: Boolean = false,
) {
    // Size / duration / manifest qualities are read once the row appears; direct files that were verified
    // when found already carry size and height, so they need no second request.
    val info by produceState<MediaSniffer.MediaInfo?>(null, stream.url) {
        value = when {
            com.invictus.xmd.utils.LinkParser.isYoutubeLink(stream.url) -> null
            stream.sizeBytes != null && !with(MediaSniffer) { stream.kind.needsQualityPicker() } ->
                MediaSniffer.MediaInfo(
                    if (stream.kind == MediaSniffer.Kind.DIRECT_VIDEO) listOfNotNull(MediaSniffer.heightOf(stream)) else emptyList(),
                    stream.sizeBytes,
                )
            else -> withContext(Dispatchers.IO) { MediaSniffer.probeInfo(stream) }
        }
    }
    val chips = buildList {
        if (resumable) add(stringResource(R.string.sniff_resume_hint) to true)
        streamFormatLabel(stream)?.let { add(it to false) }
        info?.heights?.take(3)?.forEach { add("${it}p" to true) }
        info?.let { MediaSniffer.describe(it.copy(heights = emptyList())) }
            ?.split(" \u00b7 ")?.forEach { add(it to false) }
    }
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                androidx.compose.material3.Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp),
                ) {
                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (stream.kind == MediaSniffer.Kind.DIRECT_AUDIO) Icons.Music else Icons.Video,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp, end = 6.dp)) {
                    Text(
                        text = displayName,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (chips.isNotEmpty()) {
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            chips.forEach { (label, accent) -> SniffChip(label, accent) }
                        }
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                IconButton(shapes = androidx.compose.material3.IconButtonDefaults.shapes(), onClick = onCopyClick) {
                    Icon(
                        imageVector = Icons.Link,
                        contentDescription = stringResource(R.string.torrent_dialog_copy_link),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                if (onPlayClick != null) {
                    IconButton(shapes = androidx.compose.material3.IconButtonDefaults.shapes(), onClick = onPlayClick) {
                        Icon(
                            imageVector = Icons.Play,
                            contentDescription = stringResource(R.string.download_dialog_play_external),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                androidx.compose.material3.Button(
                    onClick = onDownloadClick,
                    shapes = androidx.compose.material3.ButtonDefaults.shapes(),
                    modifier = Modifier.padding(start = 4.dp, end = 6.dp),
                ) {
                    Icon(Icons.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.sniffed_download))
                }
            }
        }
    }
}

@Composable
private fun SniffChip(label: String, accent: Boolean) {
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        color = if (accent) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (accent) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

private val GENERIC_STREAM_NAME = Regex(
    """(?i)^(index|master|playlist|manifest|chunklist|stream|video|hls|main|init)(\.(m3u8|mpd|mp4|webm))?$|^[0-9a-f]{16,}(\.\w+)?$""",
)

/** URL's last segment when it reads like a real file name; otherwise the page title (numbered when
 *  several streams share it), since "index.m3u8" tells the user nothing. */
private fun streamDisplayName(
    stream: MediaSniffer.Sniffed,
    index: Int,
    total: Int,
    pageTitle: String?,
): String {
    if (stream.kind == MediaSniffer.Kind.DIRECT_VIDEO || stream.kind == MediaSniffer.Kind.DIRECT_AUDIO) {
        val n = MediaSniffer.bestName(stream, pageTitle)
        return if (total > 1) n.substringBeforeLast('.') + " (${index + 1})." + n.substringAfterLast('.') else n
    }
    stream.fileName?.takeIf { it.isNotBlank() }?.let { return it }
    val label = MediaSniffer.guessLabel(stream.url)
    if (!GENERIC_STREAM_NAME.matches(label) || pageTitle.isNullOrBlank()) return label
    return if (total > 1) "$pageTitle (${index + 1})" else pageTitle
}

private fun streamFormatLabel(stream: MediaSniffer.Sniffed): String? = when (stream.kind) {
    MediaSniffer.Kind.HLS -> "HLS"
    MediaSniffer.Kind.DASH -> "DASH"
    MediaSniffer.Kind.DIRECT_VIDEO, MediaSniffer.Kind.DIRECT_AUDIO ->
        MediaSniffer.extFor(stream.mime, stream.kind).uppercase()
}
