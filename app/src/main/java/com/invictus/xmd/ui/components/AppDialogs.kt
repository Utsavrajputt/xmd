@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.text.font.FontWeight
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
 * App-wide dialog container with the Material 3 Expressive treatment: extra-large
 * 32dp corners, tonal surface-container-high surface (no elevation tint), centered
 * icon/title support and a springy scale-in. Every dialog in the app goes through this
 * so they all look and feel the same; callers just pass the usual AlertDialog slots.
 */
@Composable
internal fun XmdAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.86f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        label = "dialogScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "dialogAlpha",
    )
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        },
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = RoundedCornerShape(32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        iconContentColor = MaterialTheme.colorScheme.primary,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = 0.dp,
        properties = properties,
    )
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
        onClick = onChangeClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = changeButtonLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
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
    XmdAlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.wideDialogWidth(),
        properties = WideDialogProperties,
        title = { Text(state.title) },
        text = { Text(state.message) },
        confirmButton = {
            Button(shapes = androidx.compose.material3.ButtonDefaults.shapes(), onClick = onConfirm) {
                Text(state.confirmLabel)
            }
        },
        dismissButton = state.dismissLabel?.let { label ->
            {
                TextButton(shapes = androidx.compose.material3.ButtonDefaults.shapes(), onClick = onDismissAction) {
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
    XmdAlertDialog(
        onDismissRequest = onCancel,
        modifier = Modifier.wideDialogWidth(),
        properties = WideDialogProperties,
        title = { Text(state.title) },
        text = { Text(state.message) },
        confirmButton = {
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                if (state.fetchFromBrowserLabel != null) {
                    FilledTonalButton(shapes = androidx.compose.material3.ButtonDefaults.shapes(), onClick = onFetchFromBrowser) {
                        Text(state.fetchFromBrowserLabel)
                    }
                }
                Button(shapes = androidx.compose.material3.ButtonDefaults.shapes(), onClick = onRetry) {
                    Text(state.retryLabel)
                }
            }
        },
        dismissButton = {
            TextButton(shapes = androidx.compose.material3.ButtonDefaults.shapes(), onClick = onCancel) {
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
    XmdAlertDialog(
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
                    val rowShape = RoundedCornerShape(
                        topStart = if (index == 0) 20.dp else 6.dp,
                        topEnd = if (index == 0) 20.dp else 6.dp,
                        bottomStart = if (index == choices.lastIndex) 20.dp else 6.dp,
                        bottomEnd = if (index == choices.lastIndex) 20.dp else 6.dp,
                    )
                    Surface(
                        onClick = { onChoice(index) },
                        shape = rowShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = if (index == choices.lastIndex) 0.dp else 3.dp),
                    ) {
                        Text(
                            text = choice,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(shapes = androidx.compose.material3.ButtonDefaults.shapes(), onClick = onDismiss) {
                Text(dismissLabel)
            }
        },
    )
}