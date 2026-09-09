package com.invictus.xmd.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.invictus.xmd.R
import com.invictus.xmd.preferences.Settings
import com.invictus.xmd.ui.browser.BrowserFragment
import com.invictus.xmd.ui.icons.Icon
import com.invictus.xmd.ui.icons.Icons

/**
 * Browser settings: default search engine, global adblock toggle, background
 * playback, and website source-pack import/export trigger.
 */
@Composable
fun SettingsBrowserScreen(
    searchEngine: Settings.SearchEngine,
    customSearchName: String,
    onSearchEngineClick: () -> Unit,
    adblockEnabled: Boolean,
    blockedDomainCount: Int,
    onAdblockChanged: (Boolean) -> Unit,
    backgroundPlaybackEnabled: Boolean,
    onBackgroundPlaybackChanged: (Boolean) -> Unit,
    onImportWebsites: () -> Unit,
    onExportWebsites: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        SettingsSectionCard {
            val engineSubtitle = if (searchEngine == Settings.SearchEngine.CUSTOM && customSearchName.isNotBlank()) {
                "${stringResource(R.string.search_engine_custom)} ($customSearchName)"
            } else {
                searchEngine.displayName
            }
            ClickableSettingRow(
                title = stringResource(R.string.settings_search_engine),
                subtitle = engineSubtitle,
                onClick = onSearchEngineClick,
            )
        }

        Spacer(Modifier.height(8.dp))
        SettingsSectionCard {
            SwitchSettingRow(
                title = stringResource(R.string.settings_adblock),
                // Falls back to the static hint while the list is still
                // loading (or hasn't loaded at all yet) rather than
                // showing a "Blocking 0 domains" that reads as broken.
                subtitle = if (blockedDomainCount > 0) {
                    stringResource(R.string.settings_adblock_hint_count, blockedDomainCount)
                } else {
                    stringResource(R.string.settings_adblock_hint)
                },
                checked = adblockEnabled,
                onCheckedChange = onAdblockChanged,
            )
        }

        Spacer(Modifier.height(8.dp))
        SettingsSectionCard {
            SwitchSettingRow(
                title = stringResource(R.string.settings_background_playback),
                subtitle = stringResource(R.string.settings_background_playback_hint),
                checked = backgroundPlaybackEnabled,
                onCheckedChange = onBackgroundPlaybackChanged,
            )
        }

        Spacer(Modifier.height(8.dp))
        SettingsSectionHeader(title = stringResource(R.string.settings_import_websites))

        SettingsSectionCard(contentPadding = PaddingValues(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            RoundedCornerShape(14.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Globe,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_import_websites_row_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.settings_import_websites_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                OutlinedButton(onClick = onImportWebsites, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_import_websites_button))
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(onClick = onExportWebsites, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_export_websites_button))
                }
            }
        }
    }
}
