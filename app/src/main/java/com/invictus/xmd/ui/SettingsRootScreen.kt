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
 * Root of the Settings screen: category rows that push the matching
 * sub-fragment via the [onOpen*] callbacks (still wired to
 * [SettingsActivity.openCategory] / the existing FragmentManager back
 * stack -- only this screen's own rendering moved to Compose, navigation
 * between settings screens is unchanged).
 */
@Composable
fun SettingsRootScreen(
    showYoutubeRow: Boolean,
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
                onClick = onOpenAppearance,
            )
            CategoryRowGap()
            CategoryRow(
                icon = Icons.Sync,
                title = stringResource(R.string.settings_category_connections),
                subtitle = stringResource(R.string.settings_category_connections_desc),
                onClick = onOpenConnections,
            )
            CategoryRowGap()
            CategoryRow(
                icon = Icons.Public,
                title = stringResource(R.string.settings_category_browser),
                subtitle = stringResource(R.string.settings_category_browser_desc),
                onClick = onOpenBrowser,
            )
            CategoryRowGap()
            CategoryRow(
                icon = Icons.Downloads,
                title = stringResource(R.string.settings_category_downloads),
                subtitle = stringResource(R.string.settings_category_downloads_desc),
                onClick = onOpenDownloads,
            )
            // Lite build has no yt-dlp engine behind this screen -- row is
            // dropped entirely rather than shown leading nowhere useful,
            // same BuildConfig.HAS_YOUTUBE_SUPPORT gate as before.
            if (showYoutubeRow) {
                CategoryRowGap()
                CategoryRow(
                    icon = Icons.Youtube,
                    title = stringResource(R.string.settings_category_youtube),
                    subtitle = stringResource(R.string.settings_category_youtube_desc),
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
                    onClick = onOpenAbout,
                )
            }
        }
    }
}
