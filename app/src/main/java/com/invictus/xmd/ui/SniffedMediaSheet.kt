package com.invictus.xmd.ui

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import com.invictus.xmd.R
import com.invictus.xmd.core.MediaSniffer
import kotlinx.coroutines.launch

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
    onStreamSelected: (MediaSniffer.Sniffed) -> Unit,
    onCopyLink: (String) -> Unit,
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            streams.forEach { stream ->
                SniffedMediaRow(
                    stream = stream,
                    onClick = {
                        onStreamSelected(stream)
                        closeSheet()
                    },
                    onCopyClick = { onCopyLink(stream.url) },
                )
            }
        }
    }
}

@Composable
private fun SniffedMediaRow(
    stream: MediaSniffer.Sniffed,
    onClick: () -> Unit,
    onCopyClick: () -> Unit,
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
        Text(
            text = MediaSniffer.guessLabel(stream.url),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            // Compose's TextOverflow has no MIDDLE equivalent to the old
            // View version's TextUtils.TruncateAt.MIDDLE (MiddleEllipsis
            // only landed in a Compose UI version newer than this app's
            // BOM -- see Phase 0's "do not bump" note) -- end-ellipsis
            // instead, same tradeoff every other Phase 1-4 screen made.
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, top = 14.dp, bottom = 14.dp, end = 8.dp),
        )
        IconButton(onClick = onCopyClick) {
            Icon(
                imageVector = Icons.Link,
                contentDescription = stringResource(R.string.torrent_dialog_copy_link),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
