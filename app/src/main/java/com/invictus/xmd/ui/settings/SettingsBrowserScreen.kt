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
import androidx.compose.material3.IconButton
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
import com.invictus.xmd.ui.icons.Icon
import com.invictus.xmd.ui.icons.Icons

/**
 * Browser settings: default search engine, Brave-style Shields (blocking
 * level + per-site allowlist + lifetime stats), background playback, and
 * the website source-pack import/export trigger.
 */
@Composable
fun SettingsBrowserScreen(
    searchEngine: Settings.SearchEngine,
    customSearchName: String,
    onSearchEngineClick: () -> Unit,
    adblockLevel: Settings.AdblockLevel,
    blockedDomainCount: Int,
    lifetimeBlockedCount: Long,
    allowlistedSites: List<String>,
    onAdblockLevelChanged: (Settings.AdblockLevel) -> Unit,
    onRemoveAllowlistedSite: (String) -> Unit,
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
        SettingsSectionHeader(title = stringResource(R.string.settings_shields_header))

        SettingsSectionCard {
            RadioSettingRow(
                title = stringResource(R.string.settings_shields_standard_title),
                subtitle = stringResource(R.string.settings_shields_standard_subtitle),
                selected = adblockLevel == Settings.AdblockLevel.STANDARD,
                onClick = { onAdblockLevelChanged(Settings.AdblockLevel.STANDARD) },
            )
            SettingsDivider()
            RadioSettingRow(
                title = stringResource(R.string.settings_shields_aggressive_title),
                subtitle = stringResource(R.string.settings_shields_aggressive_subtitle),
                selected = adblockLevel == Settings.AdblockLevel.AGGRESSIVE,
                onClick = { onAdblockLevelChanged(Settings.AdblockLevel.AGGRESSIVE) },
            )
            SettingsDivider()
            RadioSettingRow(
                title = stringResource(R.string.settings_shields_off_title),
                subtitle = stringResource(R.string.settings_shields_off_subtitle),
                selected = adblockLevel == Settings.AdblockLevel.OFF,
                onClick = { onAdblockLevelChanged(Settings.AdblockLevel.OFF) },
            )
        }

        // Stats + allowlist management are only meaningful once blocking is
        // actually doing something -- hidden at OFF rather than shown with
        // a permanently-zero count, which would just read as broken.
        if (adblockLevel != Settings.AdblockLevel.OFF) {
            Spacer(Modifier.height(8.dp))
            SettingsSectionCard(contentPadding = PaddingValues(16.dp)) {
                Text(
                    text = if (blockedDomainCount > 0) {
                        stringResource(R.string.settings_shields_coverage, blockedDomainCount)
                    } else {
                        stringResource(R.string.settings_shields_coverage_loading)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.settings_shields_lifetime_count, lifetimeBlockedCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Spacer(Modifier.height(8.dp))
            SettingsSectionHeader(title = stringResource(R.string.settings_shields_allowlist_header))
            SettingsSectionCard {
                if (allowlistedSites.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_shields_allowlist_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    allowlistedSites.forEachIndexed { index, site ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = site,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { onRemoveAllowlistedSite(site) }) {
                                Icon(imageVector = Icons.Delete, contentDescription = null)
                            }
                        }
                        if (index != allowlistedSites.lastIndex) SettingsDivider()
                    }
                }
            }
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
