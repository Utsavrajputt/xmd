@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
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
    onCopyLink: (String) -> Unit,
    onPlayClick: ((MediaSniffer.Sniffed) -> Unit)? = null,
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
            if (!pageTitle.isNullOrBlank()) {
                Text(
                    text = pageTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            streams.forEachIndexed { index, stream ->
                SniffedMediaRow(
                    stream = stream,
                    displayName = streamDisplayName(stream, index, streams.size, pageTitle),
                    onClick = {
                        onStreamSelected(stream)
                        closeSheet()
                    },
                    onCopyClick = { onCopyLink(stream.url) },
                    onPlayClick = onPlayClick?.let { playAction ->
                        {
                            playAction(stream)
                            closeSheet()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SniffedMediaRow(
    stream: MediaSniffer.Sniffed,
    displayName: String,
    onClick: () -> Unit,
    onCopyClick: () -> Unit,
    onPlayClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
    ) {
        val icon = when (stream.kind) {
            MediaSniffer.Kind.DIRECT_AUDIO -> Icons.Music
            else -> Icons.Video
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        // Quality/size are read off the stream itself (HLS/DASH manifest or
        // HEAD) once the row appears; YouTube links have no cheap probe, so
        // their quality list shows up in the picker that opens on tap.
        val info by produceState<MediaSniffer.MediaInfo?>(null, stream.url) {
            value = if (com.invictus.xmd.utils.LinkParser.isYoutubeLink(stream.url)) null
            else withContext(Dispatchers.IO) { MediaSniffer.probeInfo(stream) }
        }
        // Always shows at least the format (HLS / MP4 ...); quality + size are
        // appended once the probe finishes.
        val detail = listOfNotNull(
            streamFormatLabel(stream),
            info?.let { MediaSniffer.describe(it) },
        ).joinToString(" \u00b7 ")
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 8.dp),
        ) {
            Text(
                text = displayName,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onPlayClick != null) {
            IconButton(shapes = androidx.compose.material3.IconButtonDefaults.shapes(), onClick = onPlayClick) {
                Icon(
                    imageVector = Icons.Play,
                    contentDescription = stringResource(R.string.download_dialog_play_external),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        IconButton(shapes = androidx.compose.material3.IconButtonDefaults.shapes(), onClick = onCopyClick) {
            Icon(
                imageVector = Icons.Link,
                contentDescription = stringResource(R.string.torrent_dialog_copy_link),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp),
            )
        }
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
    val label = MediaSniffer.guessLabel(stream.url)
    if (!GENERIC_STREAM_NAME.matches(label) || pageTitle.isNullOrBlank()) return label
    return if (total > 1) "$pageTitle (${index + 1})" else pageTitle
}

private fun streamFormatLabel(stream: MediaSniffer.Sniffed): String? = when (stream.kind) {
    MediaSniffer.Kind.HLS -> "HLS"
    MediaSniffer.Kind.DASH -> "DASH"
    MediaSniffer.Kind.DIRECT_VIDEO, MediaSniffer.Kind.DIRECT_AUDIO ->
        Regex("""\.([a-z0-9]{2,4})(?:\?|$)""", RegexOption.IGNORE_CASE)
            .find(stream.url.substringBefore('#'))?.groupValues?.get(1)?.uppercase()
}
