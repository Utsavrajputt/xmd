@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.invictus.xmd.ui.settings

import com.invictus.xmd.ui.theme.XmdShapes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.invictus.xmd.R
import com.invictus.xmd.ui.icons.Icons
import com.invictus.xmd.preferences.Settings
import com.invictus.xmd.preferences.Profile
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.invictus.xmd.ui.icons.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight

/**
 * Root of the Settings screen: category rows with dividers, styled
 * after mpvRx with tablet dual-pane selection highlight support.
 */
@Composable
fun SettingsRootScreen(
    showYoutubeRow: Boolean,
    selectedRoute: String? = null,
    showBatteryWarning: Boolean = false,
    onDismissBatteryWarning: () -> Unit = {},
    onFixBatteryOptimization: () -> Unit = {},
    onOpenAppearance: () -> Unit,
    onOpenConnections: () -> Unit,
    onOpenBrowser: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenYoutube: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenProfile: () -> Unit = {},
    onOpenStatusSaver: () -> Unit = {},
    onOpenStorage: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            ProfileHeaderCard(
                isSelected = selectedRoute == Route.PROFILE,
                storageSelected = selectedRoute == Route.STORAGE,
                onClick = onOpenProfile,
                onOpenStatusSaver = onOpenStatusSaver,
                onOpenStorage = onOpenStorage,
            )
            Spacer(Modifier.height(16.dp))

            if (showBatteryWarning) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = XmdShapes.S,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(22.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.settings_battery_warning_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            IconButton(shapes = androidx.compose.material3.IconButtonDefaults.shapes(), 
                                onClick = onDismissBatteryWarning,
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Close,
                                    contentDescription = stringResource(R.string.action_dismiss),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.settings_battery_warning_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = onFixBatteryOptimization,
                            shape = XmdShapes.Xs,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                            modifier = Modifier.height(36.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.settings_battery_warning_fix),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            SettingsSectionCard {
                CategoryRow(
                    icon = Icons.Palette,
                    title = stringResource(R.string.settings_category_appearance),
                    subtitle = stringResource(R.string.settings_category_appearance_desc),
                    isSelected = selectedRoute == Route.APPEARANCE,
                    isFirst = true,
                    onClick = onOpenAppearance,
                )
                CategoryRowGap()
                CategoryRow(
                    icon = Icons.Sync,
                    title = stringResource(R.string.settings_category_connections),
                    subtitle = stringResource(R.string.settings_category_connections_desc),
                    isSelected = selectedRoute == Route.CONNECTIONS,
                    onClick = onOpenConnections,
                )
                CategoryRowGap()
                CategoryRow(
                    icon = Icons.Public,
                    title = stringResource(R.string.settings_category_browser),
                    subtitle = stringResource(R.string.settings_category_browser_desc),
                    isSelected = selectedRoute == Route.BROWSER,
                    onClick = onOpenBrowser,
                )
                CategoryRowGap()
                CategoryRow(
                    icon = Icons.Downloads,
                    title = stringResource(R.string.settings_category_downloads),
                    subtitle = stringResource(R.string.settings_category_downloads_desc),
                    isSelected = selectedRoute == Route.DOWNLOADS,
                    isLast = !showYoutubeRow,
                    onClick = onOpenDownloads,
                )
                if (showYoutubeRow) {
                    CategoryRowGap()
                    CategoryRow(
                        icon = Icons.Youtube,
                        title = stringResource(R.string.settings_category_youtube),
                        subtitle = stringResource(R.string.settings_category_youtube_desc),
                        isSelected = selectedRoute == Route.YOUTUBE,
                        isLast = true,
                        onClick = onOpenYoutube,
                    )
                }
            }

            Column(modifier = Modifier.padding(top = 16.dp)) {
                SettingsSectionCard {
                    CategoryRow(
                        icon = Icons.Info,
                        title = stringResource(R.string.settings_category_about),
                        subtitle = stringResource(R.string.settings_category_about_desc),
                        isSelected = selectedRoute == Route.ABOUT,
                        isFirst = true,
                        isLast = true,
                        onClick = onOpenAbout,
                    )
                }
            }
        }

    }
}

/** Top-of-Settings profile hero: avatar + name over the themed background, tap to open the Profile page.
 *  Two quick-action chips (Status Saver, Storage) sit along its bottom edge. */
@Composable
private fun ProfileHeaderCard(
    isSelected: Boolean,
    storageSelected: Boolean,
    onClick: () -> Unit,
    onOpenStatusSaver: () -> Unit,
    onOpenStorage: () -> Unit,
) {
    val name = Profile.name().trim()
    val heroText = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) MaterialTheme.colorScheme.onSurface
        else androidx.compose.ui.graphics.Color.White
    val (freeBytes, totalBytes) = androidx.compose.runtime.remember {
        com.invictus.xmd.domain.storage.StorageStatsRepository.deviceSpace()
    }
    val storageHint = if (totalBytes > 0L) {
        stringResource(
            R.string.settings_chip_storage_hint,
            com.invictus.xmd.utils.formatBytes(freeBytes),
            com.invictus.xmd.utils.formatBytes(totalBytes),
        )
    } else {
        ""
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .settingsHeroBackground()
            .then(
                if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, XmdShapes.L)
                else Modifier
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 16.dp),
        ) {
            ProfileAvatar(
                size = 60.dp,
                modifier = Modifier.border(2.dp, heroText.copy(alpha = 0.55f), CircleShape),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (name.isEmpty()) stringResource(R.string.settings_profile_setup_title) else name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = heroText,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        if (name.isEmpty()) R.string.settings_profile_setup_desc else R.string.settings_profile_tap_hint
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = heroText.copy(alpha = 0.78f),
                )
            }
            Icon(
                imageVector = Icons.ChevronRight,
                contentDescription = null,
                tint = heroText.copy(alpha = 0.78f),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HeroChip(
                icon = Icons.WhatsApp,
                accent = androidx.compose.ui.graphics.Color(0xFF25D366),
                title = stringResource(R.string.settings_chip_status_saver),
                hint = stringResource(R.string.settings_chip_status_saver_hint),
                selected = false,
                onClick = onOpenStatusSaver,
                modifier = Modifier.weight(1f),
            )
            HeroChip(
                icon = Icons.PieChart,
                accent = androidx.compose.ui.graphics.Color(0xFFFFB300),
                title = stringResource(R.string.settings_chip_storage),
                hint = storageHint,
                selected = storageSelected,
                onClick = onOpenStorage,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Frosted pill inside the hero: coloured icon badge + title + hint, white text over the themed sky. */
@Composable
private fun HeroChip(
    icon: com.invictus.xmd.ui.icons.AppIcon,
    accent: androidx.compose.ui.graphics.Color,
    title: String,
    hint: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme by com.invictus.xmd.preferences.Settings.themeFlow.collectAsState()
    val primary = MaterialTheme.colorScheme.primary
    val black = androidx.compose.ui.graphics.Color.Black
    val light = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val white = if (light) MaterialTheme.colorScheme.onSurface else androidx.compose.ui.graphics.Color.White
    // Light themes: light tinted chip with dark text. Dark: Aurora keeps its fixed navy/blue, every
    // other theme tints the chip from its own primary.
    val fillSelected: androidx.compose.ui.graphics.Color
    val fillIdle: androidx.compose.ui.graphics.Color
    val stroke: androidx.compose.ui.graphics.Color
    if (light) {
        fillSelected = androidx.compose.ui.graphics.lerp(androidx.compose.ui.graphics.Color.White, primary, 0.26f)
        fillIdle = androidx.compose.ui.graphics.lerp(androidx.compose.ui.graphics.Color.White, primary, 0.12f)
        stroke = primary
    } else if (theme == com.invictus.xmd.ui.theme.AppTheme.Aurora) {
        fillSelected = androidx.compose.ui.graphics.Color(0xFF0E3386)
        fillIdle = androidx.compose.ui.graphics.Color(0xFF081C4F)
        stroke = androidx.compose.ui.graphics.Color(0xFF2DB3FF)
    } else {
        fillSelected = androidx.compose.ui.graphics.lerp(black, primary, 0.50f)
        fillIdle = androidx.compose.ui.graphics.lerp(black, primary, 0.30f)
        stroke = primary
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(XmdShapes.M)
            .background(
                if (selected) fillSelected.copy(alpha = 0.90f)
                else fillIdle.copy(alpha = 0.62f)
            )
            .border(
                1.dp,
                stroke.copy(alpha = if (selected) 0.70f else 0.32f),
                XmdShapes.M,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(accent.copy(alpha = 0.22f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = white,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (hint.isNotEmpty()) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
                    color = white.copy(alpha = 0.78f),
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}
