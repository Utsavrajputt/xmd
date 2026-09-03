package com.invictus.xmd.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.FilledTonalButton
import com.invictus.xmd.ui.icons.Icon
import com.invictus.xmd.ui.icons.Icons
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.invictus.xmd.R

internal data class HomeQuickStats(
    val downloading: Int = 0,
    val paused: Int = 0,
    val done: Int = 0,
    val failed: Int = 0,
) {
    val isEmpty: Boolean
        get() = downloading == 0 && paused == 0 && done == 0 && failed == 0
}

@Composable
internal fun HomeScreen(
    linksText: String,
    onLinksTextChange: (String) -> Unit,
    clipboardLink: String?,
    quickStats: HomeQuickStats,
    needsPrepare: Boolean,
    onClipboardAdd: () -> Unit,
    onClipboardDismiss: () -> Unit,
    onAddTorrent: () -> Unit,
    onPrepare: () -> Unit,
    onDownload: () -> Unit,
    onOpenDownloads: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        clipboardLink?.let { link ->
            ClipboardLinkBanner(
                link = link,
                onAdd = onClipboardAdd,
                onDismiss = onClipboardDismiss,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SectionLabel()
            Spacer(Modifier.height(12.dp))

            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = linksText,
                        onValueChange = onLinksTextChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.hint_links)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Link,
                                contentDescription = null,
                            )
                        },
                        minLines = 4,
                        maxLines = 8,
                    )

                    FilledTonalButton(
                        onClick = onAddTorrent,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Torrent,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_add_torrent))
                    }

                    if (needsPrepare) {
                        FilledTonalButton(
                            onClick = onPrepare,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.action_prepare))
                        }
                    }

                    Button(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(
                                if (needsPrepare) R.string.action_download
                                else R.string.action_download_direct
                            )
                        )
                    }
                }
            }

            if (!quickStats.isEmpty) {
                Spacer(Modifier.height(16.dp))
                QuickStatsCard(stats = quickStats, onClick = onOpenDownloads)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 36.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    text = stringResource(R.string.home_paste_links_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.home_supported_sources),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ClipboardLinkBanner(
    link: String,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Link,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(R.string.clipboard_link_detected, link),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = onAdd) {
                Text(stringResource(R.string.action_add))
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Close,
                    contentDescription = stringResource(R.string.action_dismiss),
                )
            }
        }
    }
}

@Composable
private fun SectionLabel() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.section_add_new_download),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun QuickStatsCard(stats: HomeQuickStats, onClick: () -> Unit) {
    val parts = mutableListOf<String>()
    if (stats.downloading > 0) {
        parts += stringResource(R.string.home_stat_downloading, stats.downloading)
    }
    if (stats.paused > 0) {
        parts += stringResource(R.string.home_stat_paused, stats.paused)
    }
    if (stats.done > 0) {
        parts += stringResource(R.string.home_stat_done, stats.done)
    }
    if (stats.failed > 0) {
        parts += stringResource(R.string.home_stat_failed, stats.failed)
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Downloads,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_download_activity),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = parts.joinToString("  •  "),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Icon(
                imageVector = Icons.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}