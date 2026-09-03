package com.invictus.xmd.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.unit.dp
import com.invictus.xmd.R

/**
 * Post-migration cleanup conversion of BrowserFragment.showAddBookmarkDialog()
 * -- previously a MaterialAlertDialogBuilder wrapping dialog_add_bookmark.xml
 * (2x TextInputLayout/TextInputEditText + MaterialCheckBox). Never had its
 * own lettered phase (missed by the original A-G plan); found and converted
 * during the post-Phase-F/G audit. Same state/validation shape the XML
 * version had: title+url text fields prefilled by the caller, a checkbox
 * for the optional matching Shortcut, empty-URL blocked client-side same as
 * before (BrowserFragment still does the actual empty-URL toast/normalize/
 * BookmarkRepository.add/ShortcutRepository.add/success-toast -- this
 * composable is UI/state only, same split every other Phase A/B/C dialog
 * in this codebase uses).
 */
@Composable
fun AddBookmarkDialog(
    initialUrl: String?,
    initialTitle: String?,
    onDismiss: () -> Unit,
    onConfirm: (url: String, title: String, alsoAddShortcut: Boolean) -> Unit,
) {
    var titleText by remember { mutableStateOf(initialTitle.orEmpty()) }
    var urlText by remember { mutableStateOf(initialUrl.orEmpty()) }
    var alsoAddShortcut by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_bookmark_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text(stringResource(R.string.hint_bookmark_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text(stringResource(R.string.hint_url)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    Checkbox(
                        checked = alsoAddShortcut,
                        onCheckedChange = { alsoAddShortcut = it },
                    )
                    Text(stringResource(R.string.bookmark_also_add_shortcut))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(urlText, titleText, alsoAddShortcut) }) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
