package com.invictus.xmd.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.invictus.xmd.R
import com.invictus.xmd.ui.icons.Icons
import com.invictus.xmd.preferences.Settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.invictus.xmd.ui.icons.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
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
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        if (showBatteryWarning) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp),
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
                        IconButton(
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
                        shape = RoundedCornerShape(10.dp),
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
