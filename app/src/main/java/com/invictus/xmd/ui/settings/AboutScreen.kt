package com.invictus.xmd.ui.settings

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.invictus.xmd.ui.icons.Icon
import com.invictus.xmd.ui.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.invictus.xmd.R
import com.invictus.xmd.preferences.Settings
import com.invictus.xmd.utils.GithubAvatarLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A developer credit -- [name] shown as the row title, [githubId] shown as
 * subtext and used to open `github.com/<githubId>` when the row is tapped. */
data class AboutDeveloper(val name: String, val githubId: String)

/**
 * Where an update the user has explicitly asked about (via the "Check for
 * updates now" button or a found auto-check) currently stands. UI-only --
 * doesn't reference [com.invictus.xmd.domain.update.UpdateChecker.Release]
 * directly so this file stays decoupled from the domain layer, same as
 * [AboutDeveloper] above.
 */
sealed class UpdateAvailability {
    data object Idle : UpdateAvailability()
    data object UpToDate : UpdateAvailability()
    data object Error : UpdateAvailability()
    data class Available(val version: String) : UpdateAvailability()
    /** [progress] is -1f for an indeterminate/unknown-size download. */
    data class Downloading(val version: String, val progress: Float) : UpdateAvailability()
    data class ReadyToInstall(val version: String) : UpdateAvailability()
}

/**
 * App identity, version, GitHub link, license notice, and developer
 * credits. Rendered directly by SettingsActivity's AboutRoute (NavHost
 * route body) -- no Fragment host. The open-source libraries Xmd is built
 * on live on their own screen (see LibrariesScreen), reached via the
 * "Libraries" action button below.
 *
 * Redesigned with mpvRx's About screen as the visual reference: an animated
 * gradient hero card for identity, pill-badge version tag, a pair of
 * action buttons, avatar-style rows for developer credits, and an Updates
 * section -- auto-check toggle, Stable/Preview channel picker, "Check for
 * updates now" button, and (once an update is found) an in-app Download ->
 * Install flow like mpvRx's UpdateSheet, just rendered inline in the card
 * instead of a separate bottom sheet. Trimmed down from mpvRx's version: no
 * donation section, and release notes show as plain text rather than
 * rendered Markdown (no Markdown-rendering dependency in Xmd). Unlike
 * mpvRx's "Preview (Nightly)", Xmd's second channel is labeled "Preview
 * (Beta)" -- prerelease.yml publishes tagged pre-releases (v1.1.0-beta.1,
 * -rc.1, etc), not actual nightly builds.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    versionText: String,
    onGithubClick: () -> Unit,
    onLibrariesClick: () -> Unit,
    developers: List<AboutDeveloper>,
    onDeveloperClick: (AboutDeveloper) -> Unit,
    autoCheckForUpdates: Boolean,
    onAutoCheckForUpdatesChanged: (Boolean) -> Unit,
    updateChannel: Settings.UpdateChannel,
    onUpdateChannelChanged: (Settings.UpdateChannel) -> Unit,
    isCheckingForUpdate: Boolean,
    onCheckForUpdateClick: () -> Unit,
    updateAvailability: UpdateAvailability,
    onDownloadUpdateClick: () -> Unit,
    onInstallUpdateClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // ===== Hero identity card =====
        val cs = MaterialTheme.colorScheme
        val transition = rememberInfiniteTransition()
        val fraction by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 5000),
                repeatMode = RepeatMode.Reverse,
            ),
        )
        val heroCornerRadius = 28.dp

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawWithCache {
                    val cx = size.width - size.width * fraction
                    val cy = size.height * fraction
                    val gradient = Brush.radialGradient(
                        colors = listOf(cs.primaryContainer, cs.tertiaryContainer),
                        center = Offset(cx, cy),
                        radius = 800f,
                    )
                    onDrawBehind {
                        drawRoundRect(
                            brush = gradient,
                            cornerRadius = CornerRadius(heroCornerRadius.toPx(), heroCornerRadius.toPx()),
                        )
                    }
                }
                .padding(20.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val context = LocalContext.current
                    val appIconBitmap = remember {
                        context.packageManager.getApplicationIcon(context.packageName)
                            .toBitmap()
                            .asImageBitmap()
                    }
                    Image(
                        bitmap = appIconBitmap,
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier.size(64.dp),
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = cs.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.about_tagline),
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onPrimaryContainer.copy(alpha = 0.85f),
                        )
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = cs.primary.copy(alpha = 0.16f),
                        ) {
                            Text(
                                text = versionText,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = cs.onPrimaryContainer,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val btnContainer = cs.primary
                    val btnContent = cs.onPrimary

                    // Libraries (left) -- opens the standalone Libraries
                    // screen listing the open-source projects Xmd is built
                    // on, mirroring mpvRx's separate Libraries screen.
                    Button(
                        onClick = onLibrariesClick,
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = btnContainer,
                            contentColor = btnContent,
                        ),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_library_cube),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(id = R.string.about_libraries_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }

                    // GitHub (right)
                    Button(
                        onClick = onGithubClick,
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = btnContainer,
                            contentColor = btnContent,
                        ),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_github),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(id = R.string.about_github),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        // ===== License =====
        Spacer(Modifier.height(8.dp))
        SettingsSectionHeader(title = stringResource(R.string.about_license_title))

        SettingsSectionCard(contentPadding = PaddingValues(16.dp)) {
            Row {
                Icon(
                    imageVector = Icons.Article,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp).padding(top = 2.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "AGPL-3.0",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.about_license_body),
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ===== Updates =====
        Spacer(Modifier.height(8.dp))
        SettingsSectionHeader(title = stringResource(R.string.about_updates_title))

        SettingsSectionCard {
            SwitchSettingRow(
                title = stringResource(R.string.about_auto_check_for_updates),
                subtitle = stringResource(R.string.about_check_on_startup),
                checked = autoCheckForUpdates,
                onCheckedChange = onAutoCheckForUpdatesChanged,
            )
            SettingsDivider()
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.about_update_channel_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.about_update_channel_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = updateChannel == Settings.UpdateChannel.STABLE,
                        onClick = { onUpdateChannelChanged(Settings.UpdateChannel.STABLE) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = {
                            SegmentedButtonDefaults.Icon(active = updateChannel == Settings.UpdateChannel.STABLE)
                        },
                    ) {
                        Text(stringResource(R.string.about_update_channel_stable))
                    }
                    SegmentedButton(
                        selected = updateChannel == Settings.UpdateChannel.PREVIEW,
                        onClick = { onUpdateChannelChanged(Settings.UpdateChannel.PREVIEW) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = {
                            SegmentedButtonDefaults.Icon(active = updateChannel == Settings.UpdateChannel.PREVIEW)
                        },
                    ) {
                        Text(stringResource(R.string.about_update_channel_preview))
                    }
                }

                Button(
                    onClick = onCheckForUpdateClick,
                    enabled = !isCheckingForUpdate,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Sync,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            if (isCheckingForUpdate) R.string.about_checking_for_updates
                            else R.string.about_check_for_updates_now,
                        ),
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                UpdateAvailabilityCard(
                    availability = updateAvailability,
                    onDownloadClick = onDownloadUpdateClick,
                    onInstallClick = onInstallUpdateClick,
                )
            }
        }

        // ===== Developers =====
        Spacer(Modifier.height(8.dp))
        SettingsSectionHeader(title = stringResource(R.string.about_developers_title))

        SettingsSectionCard {
            developers.forEachIndexed { index, developer ->
                DeveloperRow(
                    developer = developer,
                    onClick = { onDeveloperClick(developer) },
                )
                if (index != developers.lastIndex) SettingsDivider()
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.about_made_by),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        )
    }
}

/**
 * Inline card shown below the "Check for updates now" button once
 * something's actually happened -- mirrors mpvRx's UpdateSheet states
 * (Available -> Downloading -> ReadyToInstall) but as a plain card in the
 * existing Updates section rather than a separate ModalBottomSheet, since
 * this is About's only update-related surface. Renders nothing for
 * [UpdateAvailability.Idle] and [UpdateAvailability.UpToDate]/[UpdateAvailability.Error]
 * (those are communicated via Toast from AboutRoute instead, so they don't
 * leave a stale card sitting in the settings screen).
 */
@Composable
private fun UpdateAvailabilityCard(
    availability: UpdateAvailability,
    onDownloadClick: () -> Unit,
    onInstallClick: () -> Unit,
) {
    val version: String
    val progress: Float?
    val isReadyToInstall: Boolean
    when (availability) {
        is UpdateAvailability.Available -> {
            version = availability.version
            progress = null
            isReadyToInstall = false
        }
        is UpdateAvailability.Downloading -> {
            version = availability.version
            progress = availability.progress
            isReadyToInstall = false
        }
        is UpdateAvailability.ReadyToInstall -> {
            version = availability.version
            progress = null
            isReadyToInstall = true
        }
        else -> return
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isReadyToInstall) Icons.Check else Icons.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        if (isReadyToInstall) R.string.about_ready_to_install else R.string.about_update_available,
                        version,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            if (progress != null) {
                if (progress >= 0f) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.about_update_progress_percent, progress.toInt()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            } else {
                Button(
                    onClick = if (isReadyToInstall) onInstallClick else onDownloadClick,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text(
                        text = stringResource(
                            if (isReadyToInstall) R.string.about_install_update else R.string.about_download_update,
                        ),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun DeveloperRow(developer: AboutDeveloper, onClick: () -> Unit) {
    var avatarBitmap by remember(developer.githubId) {
        mutableStateOf<android.graphics.Bitmap?>(null)
    }
    LaunchedEffect(developer.githubId) {
        avatarBitmap = withContext(Dispatchers.IO) {
            GithubAvatarLoader.load(developer.githubId)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = avatarBitmap
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = Icons.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = developer.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "@${developer.githubId}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(
            imageVector = Icons.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(18.dp),
        )
    }
}
