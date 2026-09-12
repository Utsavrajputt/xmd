package com.invictus.xmd.ui.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.invictus.xmd.R
import com.invictus.xmd.ui.components.wideDialogWidth

/**
 * Shown from [com.invictus.xmd.ui.MainActivity.triggerDownloadYoutubeCustom]
 * (Add Download flow only) instead of the old generic "install from
 * Settings" message dialog, so a missing yt-dlp engine no longer bounces
 * the user out to Settings to tap Install themselves.
 *
 * Modeled on mpvRx's YtdlpInstallPromptDialog: a third, visually separated
 * Configure action is pinned to the start of the button row so it doesn't
 * read as a variant of Cancel/Install -- Configure still opens
 * Settings > YouTube (SettingsYoutubeScreen), same destination the old
 * "Install now" button used to send the user to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YtDlpInstallPromptDialog(
    isOpen: Boolean,
    onInstall: () -> Unit,
    onConfigure: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!isOpen) return

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.wideDialogWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    stringResource(R.string.ytdlp_not_installed_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = AlertDialogDefaults.titleContentColor,
                )
                Text(
                    stringResource(R.string.ytdlp_install_prompt_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AlertDialogDefaults.textContentColor,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = onConfigure) {
                        Text(
                            stringResource(R.string.generic_configure),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Row {
                        TextButton(onClick = onDismiss) {
                            Text(
                                stringResource(android.R.string.cancel),
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        TextButton(onClick = onInstall) {
                            Text(
                                stringResource(R.string.settings_ytdlp_install),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Progress UI shown while [com.invictus.xmd.domain.download.YtDlpManager.install]
 * runs, triggered from [YtDlpInstallPromptDialog]'s Install action.
 *
 * Unlike mpvRx's yt-dlp (a Python subprocess with a readable stdout log),
 * youtubedl-android's init() is a single blocking unpack call with no
 * incremental progress or log lines, so this only shows an indeterminate
 * bar plus a static message -- same idea as mpvRx's install progress
 * dialog, minus the per-line log.
 *
 * Not dismissable by back-press or outside-tap while busy -- [onCancel] is
 * the only way out, same as mpvRx's. Cancelling only stops the dialog from
 * waiting on the coroutine; the underlying unpack call itself is a plain
 * blocking call with no cancellation hook, so it may keep running to
 * completion in the background even after Cancel is tapped -- harmless,
 * since a finished install just leaves yt-dlp ready for next time.
 */
@Composable
fun YtDlpInstallProgressDialog(
    isOpen: Boolean,
    error: String?,
    onCancel: () -> Unit,
) {
    if (!isOpen) return

    Dialog(
        onDismissRequest = { /* no-op: Cancel is the only exit while busy */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Surface(
            modifier = Modifier.wideDialogWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    stringResource(R.string.ytdlp_install_progress_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = AlertDialogDefaults.titleContentColor,
                )

                if (error == null) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        stringResource(R.string.ytdlp_install_progress_message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                TextButton(onClick = onCancel) {
                    Text(
                        stringResource(android.R.string.cancel),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
