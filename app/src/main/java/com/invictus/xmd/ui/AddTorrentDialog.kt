package com.invictus.xmd.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
                    painterResource(XmdIcons.Torrent),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.torrent_dialog_title))
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.torrent_dialog_link_label),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onCopyLink(link) }) {
                        Icon(painterResource(XmdIcons.Copy), contentDescription = stringResource(R.string.torrent_dialog_copy_link))
                    }
                    if (!linkLocked) {
                        IconButton(onClick = {
                            val pasted = onPasteRequest()
                            if (!pasted.isNullOrBlank()) {
                                link = pasted
                                onLinkChanged(pasted)
                            }
                        }) {
                            Icon(painterResource(XmdIcons.Paste), contentDescription = stringResource(R.string.dialog_paste_link))
                        }
                    }
                }
                OutlinedTextField(
                    value = link,
                    onValueChange = {
                        link = it
                        onLinkChanged(it)
                    },
                    enabled = !linkLocked,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.torrent_dialog_link_hint)) },
                    minLines = 2,
                    maxLines = 4,
                )

                if (!linkLocked) {
                    TextButton(onClick = onPickTorrentFile) {
                        Text(stringResource(R.string.torrent_dialog_pick_file))
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.torrent_dialog_name_label), style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameManuallyEdited = true },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                )

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.torrent_dialog_files_label), style = MaterialTheme.typography.labelMedium)
                    if (filesState.files.isNotEmpty()) {
                        Text(
                            stringResource(
                                R.string.torrent_dialog_selection_summary,
                                selectedCount,
                                filesState.files.size,
                                formatFileBytes(selectedBytes),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                        )
                        TextButton(onClick = onToggleSelectAll) {
                            Text(stringResource(if (allSelected) R.string.torrent_dialog_deselect_all else R.string.torrent_dialog_select_all))
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }

                when {
                    filesState.loading -> Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.torrent_dialog_fetching_metadata), style = MaterialTheme.typography.bodySmall)
                    }
                    filesState.error -> Text(
                        stringResource(R.string.torrent_dialog_metadata_failed),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                    filesState.files.isNotEmpty() -> LazyColumn(Modifier.heightIn(max = 220.dp)) {
                        items(filesState.files, key = { it.index }) { file ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleFile(file.index) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = file.isSelected, onCheckedChange = { onToggleFile(file.index) })
                                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                    Text(file.path.substringAfterLast('/'), maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        formatFileBytes(file.sizeBytes),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
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
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        painterResource(if (advancedExpanded) XmdIcons.ArrowDown else XmdIcons.ChevronRight),
                        contentDescription = null,
                    )
                }
                if (advancedExpanded) {
                    Text(stringResource(R.string.torrent_dialog_save_to_label), style = MaterialTheme.typography.labelSmall)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            customSaveDir ?: defaultSavePath,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = { onChangeSaveDir { path -> customSaveDir = path } }) {
                            Text(stringResource(R.string.torrent_dialog_change_path))
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
