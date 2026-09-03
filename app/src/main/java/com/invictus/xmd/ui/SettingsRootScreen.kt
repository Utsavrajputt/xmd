package com.invictus.xmd.ui

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

/**
 * Root of the Settings screen: category rows with dividers, styled
 * after mpvRx with tablet dual-pane selection highlight support.
 */
@Composable
fun SettingsRootScreen(
    showYoutubeRow: Boolean,
    selectedRoute: String? = null,
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
