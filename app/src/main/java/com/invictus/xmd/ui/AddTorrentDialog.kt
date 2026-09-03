package com.invictus.xmd.ui

import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import com.invictus.xmd.ui.icons.Icon
import com.invictus.xmd.ui.icons.Icons
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.invictus.xmd.R
import java.util.Locale

/** One torrent-file row + its selection state -- replaces TorrentFileAdapter's RecyclerView row. */
data class TorrentFileRow(
    val index: Int,
    val path: String,
    val sizeBytes: Long,
    val isSelected: Boolean,
)

/** Files-list section state for [AddTorrentDialog] -- MainActivity owns the actual metadata fetch. */
data class TorrentFilesUiState(
    val loading: Boolean = false,
    val error: Boolean = false,
    val files: List<TorrentFileRow> = emptyList(),
    val magnetDetectedName: String? = null,
)

/**
 * Phase A conversion of MainActivity.showAddTorrentDialog() -- previously a
 * MaterialAlertDialogBuilder wrapping dialog_add_torrent.xml, with its file
 * list backed by TorrentFileAdapter/item_torrent_file.xml (RecyclerView),
 * now a LazyColumn. The torrent-metadata fetch (magnet) and the
 * prefillTorrentUri .torrent-file parse both still happen in MainActivity
 * -- this composable only renders whatever file list it's handed and
 * reports selection-toggle intents back up.
 *
 * Validation (empty link, zero files selected) stays in MainActivity's
 * [onStart] handler, same as the old startButton click listener -- this
 * composable just reports raw counts so the caller can decide whether to
 * show a toast and keep the dialog open.
 */
@Composable
fun AddTorrentDialog(
    prefillLink: String?,
    prefillTorrentUri: Uri?,
    prefillDisplayName: String?,
    defaultSavePath: String,
    filesState: TorrentFilesUiState,
    onLinkChanged: (String) -> Unit,
    onCopyLink: (String) -> Unit,
    onPasteRequest: () -> String?,
    onPickTorrentFile: () -> Unit,
    onToggleFile: (index: Int) -> Unit,
    onToggleSelectAll: () -> Unit,
    onChangeSaveDir: (onPicked: (String) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onStart: (link: String, name: String?, saveDir: String?, totalFiles: Int, selectedCount: Int, selectedIndices: String?) -> Unit,
) {
    var link by remember { mutableStateOf(prefillLink.orEmpty()) }
    var name by remember { mutableStateOf(prefillDisplayName.orEmpty()) }
    var nameManuallyEdited by remember { mutableStateOf(false) }
    var customSaveDir by remember { mutableStateOf<String?>(null) }
    var advancedExpanded by remember { mutableStateOf(false) }
    val linkLocked = prefillTorrentUri != null

    LaunchedEffect(filesState.magnetDetectedName) {
        if (!nameManuallyEdited) filesState.magnetDetectedName?.let { name = it }
    }

    val selectedCount = filesState.files.count { it.isSelected }
    val selectedBytes = filesState.files.filter { it.isSelected }.sumOf { it.sizeBytes }
    val allSelected = filesState.files.isNotEmpty() && filesState.files.all { it.isSelected }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Torrent,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.torrent_dialog_title))
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
                        if (!linkLocked) {
                            IconButton(
                                onClick = {
                                    val pasted = onPasteRequest()
                                    if (!pasted.isNullOrBlank()) {
                                        link = pasted
                                        onLinkChanged(pasted)
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
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = link,
                    onValueChange = {
                        link = it
                        onLinkChanged(it)
                    },
                    enabled = !linkLocked,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    placeholder = { Text(stringResource(R.string.torrent_dialog_link_hint)) },
                    minLines = 2,
                    maxLines = 4,
                )

                if (!linkLocked) {
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
                    maxLines = 2,
                )

                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.torrent_dialog_files_label),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (filesState.files.isNotEmpty()) {
                        Text(
                            stringResource(
                                R.string.torrent_dialog_selection_summary,
                                selectedCount,
                                filesState.files.size,
                                formatFileBytes(selectedBytes),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                        )
                        TextButton(
                            onClick = onToggleSelectAll,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                stringResource(if (allSelected) R.string.torrent_dialog_deselect_all else R.string.torrent_dialog_select_all),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }

                Spacer(Modifier.height(6.dp))
                when {
                    filesState.loading -> Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.torrent_dialog_fetching_metadata), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    filesState.error -> Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Text(
                            stringResource(R.string.torrent_dialog_metadata_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    filesState.files.isNotEmpty() -> Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(filesState.files, key = { it.index }) { file ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { onToggleFile(file.index) }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(checked = file.isSelected, onCheckedChange = { onToggleFile(file.index) })
                                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                        Text(
                                            file.path.substringAfterLast('/'),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        Text(
                                            formatFileBytes(file.sizeBytes),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                )
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
                                customSaveDir ?: defaultSavePath,
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
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val selectedIndices = if (filesState.files.isNotEmpty()) {
                    if (allSelected) null else filesState.files.filter { it.isSelected }.map { it.index }.joinToString(",")
                } else null
                onStart(link.trim(), name.trim().takeUnless { it.isBlank() }, customSaveDir, filesState.files.size, selectedCount, selectedIndices)
            }) { Text(stringResource(R.string.torrent_dialog_start)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.torrent_dialog_cancel)) }
        },
    )
}

private fun formatFileBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return String.format(Locale.US, "%.1f %s", value, units[digitGroups])
}
