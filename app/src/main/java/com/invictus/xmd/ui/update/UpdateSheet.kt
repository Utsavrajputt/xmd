@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.invictus.xmd.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.invictus.xmd.R
import com.invictus.xmd.domain.update.XmdRelease
import com.invictus.xmd.domain.update.previewBuildNumber
import com.invictus.xmd.ui.icons.Icon
import com.invictus.xmd.ui.icons.Icons
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

// Bottom sheet shown when a new release is available or downloaded, same
// layout as Bunko's UpdateSheet. Release notes render as plain text (no
// markdown dependency): GitHub bodies stay readable unformatted.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSheet(
    release: XmdRelease,
    sizeBytes: Long,
    isDownloading: Boolean,
    progress: Float,
    isInstallReady: Boolean,
    currentVersion: String,
    downloadError: String? = null,
    onDismiss: () -> Unit,
    onAction: () -> Unit,
    onIgnore: () -> Unit,
) {
    val latestVersion = release.previewBuildNumber()?.let { stringResource(R.string.update_sheet_beta_build, it) }
        ?: release.tagName.removePrefix("v").removePrefix("V")

    // Always forwarded: mid-download the controller only hides the sheet and the
    // download keeps running. Swallowing it here left the sheet animated away but
    // still composed, with its invisible window eating every touch (frozen app).
    ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            SheetHeader(isInstallReady = isInstallReady, latestVersion = latestVersion)

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                VersionTransitionRow(currentVersion = currentVersion, latestVersion = latestVersion)
                Spacer(modifier = Modifier.height(8.dp))
                ReleaseMetaRow(publishedAt = release.publishedAt, sizeBytes = sizeBytes)

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )

                Text(
                    text = stringResource(R.string.update_sheet_whats_new),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                val summary = release.commitSummary
                if (summary != null) {
                    CommitSummaryText(summary)
                } else {
                    Text(
                        text = release.body.ifBlank { stringResource(R.string.update_sheet_no_notes) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Once the APK is fully downloaded the sheet is in "ready to install" -- a
            // leftover "Downloading 100%" bar there is just noise.
            if (isDownloading || downloadError != null || (progress > 0f && !isInstallReady)) {
                DownloadProgressSection(progress = progress, errorMessage = downloadError)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!isDownloading && !isInstallReady) {
                    TextButton(shapes = androidx.compose.material3.ButtonDefaults.shapes(), onClick = onIgnore) {
                        Text(stringResource(R.string.update_sheet_ignore))
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                if (!isDownloading) {
                    TextButton(shapes = androidx.compose.material3.ButtonDefaults.shapes(), onClick = onDismiss) {
                        Text(stringResource(R.string.update_sheet_cancel))
                    }
                    Button(shapes = androidx.compose.material3.ButtonDefaults.shapes(), onClick = onAction) {
                        Text(
                            stringResource(
                                when {
                                    isInstallReady -> R.string.update_sheet_install
                                    downloadError != null || progress > 0f -> R.string.update_sheet_resume
                                    else -> R.string.update_sheet_download
                                },
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** Renders [summarizeCommitMessages] output: unbulleted lines are section titles, "•" lines are changes. */
@Composable
private fun CommitSummaryText(summary: String) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        summary.lines().forEach { line ->
            when {
                line.isBlank() -> Spacer(modifier = Modifier.height(6.dp))
                line.startsWith("\u2022") -> Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Text(
                    text = line,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun SheetHeader(isInstallReady: Boolean, latestVersion: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isInstallReady) Icons.Check else Icons.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = stringResource(
                    if (isInstallReady) R.string.update_sheet_ready_title else R.string.update_sheet_available_title,
                ),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.update_sheet_version, latestVersion),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun VersionTransitionRow(currentVersion: String, latestVersion: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VersionChip(label = currentVersion, emphasized = false)
        Text(
            text = "\u2192",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        VersionChip(label = latestVersion, emphasized = true)
    }
}

@Composable
private fun VersionChip(label: String, emphasized: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (emphasized) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (emphasized) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ReleaseMetaRow(publishedAt: String, sizeBytes: Long) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        MetaItem(label = stringResource(R.string.update_sheet_release_date), value = formatDate(publishedAt))
        MetaItem(
            label = stringResource(R.string.update_sheet_size),
            value = if (sizeBytes > 0L) formatFileSize(sizeBytes) else stringResource(R.string.update_sheet_unknown_size),
        )
    }
}

@Composable
private fun MetaItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DownloadProgressSection(progress: Float, errorMessage: String? = null) {
    val accent = if (errorMessage != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(
                    if (errorMessage != null) R.string.update_sheet_download_paused else R.string.update_sheet_downloading,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (errorMessage != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (progress >= 0f) {
                Text(
                    text = stringResource(R.string.about_update_progress_percent, progress.toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = accent,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (progress >= 0f) {
            LinearWavyProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = accent,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        } else {
            // Server sent no Content-Length: indeterminate instead of a stuck bar.
            LinearWavyProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = accent,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.update_sheet_resume_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

private fun formatFileSize(size: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.lastIndex)
    return String.format(Locale.US, "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

private fun formatDate(dateString: String): String {
    return try {
        // GitHub API returns ISO 8601: "2024-01-15T10:30:00Z"
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")
        val date = inputFormat.parse(dateString) ?: return dateString
        SimpleDateFormat("MMM dd, yyyy", Locale.US).format(date)
    } catch (_: Exception) {
        dateString
    }
}
