package com.invictus.xmd.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.invictus.xmd.R
import com.invictus.xmd.ui.icons.Icon
import com.invictus.xmd.ui.icons.Icons

/**
 * Shared dialog sizing -- every AlertDialog in the app opts into a consistent,
 * compact phone-like aspect ratio across all devices and orientations.
 * [usePlatformDefaultWidth] must be turned off for custom width to actually
 * take effect, since the platform default caps dialog width before Compose
 * sees it.
 *
 * Capped at 400dp max width so dialogs maintain a balanced phone-like aspect ratio
 * on tablets (both landscape and portrait) and phone landscape, while scaling
 * naturally on narrower portrait phones.
 */
internal val WideDialogProperties = DialogProperties(usePlatformDefaultWidth = false)

@Composable
internal fun Modifier.wideDialogWidth(): Modifier {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    val targetWidth = minOf(screenWidth * 0.88f, 400.dp)
    val maxHeight = (screenHeight - 40.dp).coerceAtLeast(240.dp)
    return this
        .width(targetWidth)
        .heightIn(max = maxHeight)
}

/**
 * Filled pill-shaped confirm button -- used only for a dialog's primary
 * "Start" action (Add Download / Add Torrent), so it visually stands out
 * from the plain-text Cancel button next to it. Other confirm actions
 * (Save, OK, Delete, etc.) intentionally keep the default TextButton look.
 */
@Composable
internal fun StartChipButton(
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        content = content,
    )
}

/**
 * Reusable folder picker card showing current path with folder icon and "Change" button.
 * Used in AddDownloadDialog, AddTorrentDialog, and SettingsDownloadsScreen.
 */
@Composable
fun FolderPickerCard(
    path: String,
    onChangeClick: () -> Unit,
    modifier: Modifier = Modifier,
    changeButtonLabel: String = stringResource(R.string.torrent_dialog_change_path),
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
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
                text = path,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = onChangeClick,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(
                    text = changeButtonLabel,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

internal data class AppMessageDialogState(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val dismissLabel: String? = null,
    val onConfirm: () -> Unit = {},
    val onDismiss: () -> Unit = {},
    val onDismissAction: (() -> Unit)? = null,
)

@Composable
internal fun AppMessageDialog(
    state: AppMessageDialogState,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    onDismissAction: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.wideDialogWidth(),
        properties = WideDialogProperties,
        title = { Text(state.title) },
        text = { Text(state.message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(state.confirmLabel)
            }
        },
        dismissButton = state.dismissLabel?.let { label ->
            {
                TextButton(onClick = onDismissAction) {
                    Text(label)
                }
            }
        },
    )
}

/**
 * IDM-style "Link Expired" prompt with an optional third action: besides
 * Retry (try the normal, non-browser re-fetch again) and Cancel (just
 * close the dialog, item stays FAILED), it offers "Fetch from browser"
 * ([fetchFromBrowserLabel] non-null) when the item's source page is known
 * -- re-opens that page in a WebView so the user can grab a fresh link the
 * same way they would in a desktop browser. The option is simply omitted
 * when there's no source page to fall back on.
 */
internal data class ExpiredLinkDialogState(
    val title: String,
    val message: String,
    val retryLabel: String,
    val fetchFromBrowserLabel: String?,
    val cancelLabel: String,
    val onRetry: () -> Unit = {},
    val onFetchFromBrowser: () -> Unit = {},
    val onCancel: () -> Unit = {},
)

@Composable
internal fun ExpiredLinkDialog(
    state: ExpiredLinkDialogState,
    onRetry: () -> Unit,
    onFetchFromBrowser: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        modifier = Modifier.wideDialogWidth(),
        properties = WideDialogProperties,
        title = { Text(state.title) },
        text = { Text(state.message) },
        confirmButton = {
            Row {
                if (state.fetchFromBrowserLabel != null) {
                    TextButton(onClick = onFetchFromBrowser) {
                        Text(state.fetchFromBrowserLabel)
                    }
                }
                TextButton(onClick = onRetry) {
                    Text(state.retryLabel)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(state.cancelLabel)
            }
        },
    )
}

@Composable
internal fun AppChoiceDialog(
    title: String,
    choices: List<String>,
    dismissLabel: String,
    onChoice: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.wideDialogWidth(),
        properties = WideDialogProperties,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                choices.forEachIndexed { index, choice ->
                    TextButton(
                        onClick = { onChoice(index) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(choice, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel)
            }
        },
    )
}