@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)
package com.invictus.xmd.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Arrangement
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

/**
 * Content-sized variant of [wideDialogWidth]: the dialog hugs its content and
 * only grows up to the same max width. Pair with `compact = true` on
 * [XmdAlertDialog]. Use for small confirm / message dialogs.
 */
@Composable
internal fun Modifier.compactDialogWidth(): Modifier {
    val configuration = LocalConfiguration.current
    val targetWidth = minOf(configuration.screenWidthDp.dp * 0.88f, 400.dp)
    val maxHeight = (configuration.screenHeightDp.dp - 40.dp).coerceAtLeast(240.dp)
    return this
        .widthIn(max = targetWidth)
        .heightIn(max = maxHeight)
}

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


/** Corner radii of one section in the dialog's vertical stack: big outside, small where sections touch. */
private fun dialogSectionShape(index: Int, count: Int): RoundedCornerShape {
    val outer = 24.dp
    val inner = 6.dp
    val top = if (index == 0) outer else inner
    val bottom = if (index == count - 1) outer else inner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/**
 * App-wide dialog container, Material 3 Expressive "connected sections" style:
 * a tinted header (icon + title), the body, and an action tray are three separate
 * rounded tiles separated by a thin gap, inside one outlined extra-large surface.
 * Every dialog in the app goes through this so they all look and feel the same;
 * callers just pass the usual AlertDialog slots.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
    compact: Boolean = false,
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
    val colors = MaterialTheme.colorScheme
    val maxHeight = (LocalConfiguration.current.screenHeightDp.dp - 48.dp).coerceAtLeast(120.dp)
    val hasHeader = title != null || icon != null
    val sections = (if (hasHeader) 1 else 0) + (if (text != null) 1 else 0) + 1
    val bodyIndex = if (hasHeader) 1 else 0

    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        },
        properties = properties,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f)),
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 280.dp, max = 560.dp)
                    .then(if (compact) Modifier.width(IntrinsicSize.Max) else Modifier)
                    .heightIn(max = maxHeight)
                    .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (hasHeader) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = dialogSectionShape(0, sections),
                        color = colors.primaryContainer.copy(alpha = 0.55f),
                        contentColor = colors.onSurface,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (icon != null) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = colors.primaryContainer,
                                    contentColor = colors.primary,
                                ) {
                                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { icon() }
                                }
                            }
                            if (title != null) {
                                Box(Modifier.weight(1f).semantics { heading() }) {
                                    ProvideTextStyle(
                                        MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    ) { title() }
                                }
                            }
                        }
                    }
                }
                if (text != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                        shape = dialogSectionShape(bodyIndex, sections),
                        color = colors.surfaceContainerHigh,
                        contentColor = colors.onSurfaceVariant,
                    ) {
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            ProvideTextStyle(MaterialTheme.typography.bodyLarge) { text() }
                        }
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = dialogSectionShape(sections - 1, sections),
                    color = colors.surfaceContainerHigh,
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        if (dismissButton != null) dismissButton()
                        confirmButton()
                    }
                }
            }
        }
    }
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