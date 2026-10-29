@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.invictus.xmd.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.invictus.xmd.ui.components.XmdAlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.invictus.xmd.R
import com.invictus.xmd.preferences.Settings
import com.invictus.xmd.ui.components.WideDialogProperties
import com.invictus.xmd.ui.components.wideDialogWidth
import com.invictus.xmd.ui.icons.Icon
import com.invictus.xmd.ui.icons.Icons

/**
 * Browser settings: search engine, home page, one row into the ad blocking
 * page, bottom navigation bar and background playback switches, and
 * the website source-pack import/export trigger.
 */
@Composable
fun SettingsBrowserScreen(
    searchEngine: Settings.SearchEngine,
    customSearchName: String,
    onSearchEngineClick: () -> Unit,
    homePage: Settings.HomePage,
    customHomeName: String,
    onHomePageClick: () -> Unit,
    adblockEnabled: Boolean,
    onOpenAdblock: () -> Unit,
    backgroundPlaybackEnabled: Boolean,
    onBackgroundPlaybackChanged: (Boolean) -> Unit,
    toolbarBottom: Boolean,
    onToolbarBottomChanged: (Boolean) -> Unit,
    bottomBarEnabled: Boolean,
    onBottomBarChanged: (Boolean) -> Unit,
    navButtonsEnabled: Boolean,
    onNavButtonsChanged: (Boolean) -> Unit,
    forceDarkEnabled: Boolean,
    onForceDarkChanged: (Boolean) -> Unit,
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
            SettingsDivider()
            val homePageSubtitle = if (homePage == Settings.HomePage.CUSTOM && customHomeName.isNotBlank()) {
                "${stringResource(R.string.home_page_custom)} ($customHomeName)"
            } else if (homePage == Settings.HomePage.SPEED_DIAL) {
                stringResource(R.string.home_page_speed_dial)
            } else {
                homePage.displayName
            }
            ClickableSettingRow(
                title = stringResource(R.string.settings_home_page),
                subtitle = homePageSubtitle,
                onClick = onHomePageClick,
            )
            SettingsDivider()
            ClickableSettingRow(
                title = stringResource(R.string.settings_adblock_title),
                subtitle = stringResource(
                    if (adblockEnabled) R.string.settings_adblock_on else R.string.settings_adblock_off
                ),
                onClick = onOpenAdblock,
            )
            SettingsDivider()
            SwitchSettingRow(
                title = stringResource(R.string.settings_toolbar_bottom),
                subtitle = stringResource(R.string.settings_toolbar_bottom_hint),
                checked = toolbarBottom,
                onCheckedChange = onToolbarBottomChanged,
            )
            SettingsDivider()
            SwitchSettingRow(
                title = stringResource(R.string.settings_bottom_bar),
                subtitle = stringResource(R.string.settings_bottom_bar_hint),
                checked = bottomBarEnabled,
                onCheckedChange = onBottomBarChanged,
            )
            SettingsDivider()
            SwitchSettingRow(
                title = stringResource(R.string.settings_nav_buttons),
                subtitle = stringResource(R.string.settings_nav_buttons_hint),
                checked = navButtonsEnabled,
                onCheckedChange = onNavButtonsChanged,
            )
            SettingsDivider()
            SwitchSettingRow(
                title = stringResource(R.string.settings_force_dark),
                subtitle = stringResource(R.string.settings_force_dark_hint),
                checked = forceDarkEnabled,
                onCheckedChange = onForceDarkChanged,
            )
            SettingsDivider()
            SwitchSettingRow(
                title = stringResource(R.string.settings_background_playback),
                subtitle = stringResource(R.string.settings_background_playback_hint),
                checked = backgroundPlaybackEnabled,
                onCheckedChange = onBackgroundPlaybackChanged,
            )
        }

        Spacer(Modifier.height(8.dp))
        WebsiteSourcesSection(onImport = onImportWebsites, onExport = onExportWebsites)
    }
}


/** Ad blocking page -- one ON/OFF switch plus the per-site allowlist, opened from one row in [SettingsBrowserScreen]. */
@Composable
fun SettingsAdblockScreen(
    adblockEnabled: Boolean,
    allowlistedSites: List<String>,
    onAdblockEnabledChanged: (Boolean) -> Unit,
    listHostCount: Int,
    listUpdatedText: String?,
    listUpdating: Boolean,
    listUpdateResultRes: Int?,
    onUpdateList: () -> Unit,
    onAddAllowlistedSite: (String) -> Unit,
    onRemoveAllowlistedSite: (String) -> Unit,
) {
    var showAddSiteDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        SettingsSectionCard {
            SwitchSettingRow(
                title = stringResource(R.string.settings_adblock_switch_title),
                subtitle = stringResource(R.string.settings_adblock_switch_subtitle),
                checked = adblockEnabled,
                onCheckedChange = onAdblockEnabledChanged,
            )
        }

        // The list and the allowlist only matter while blocking is on.
        if (adblockEnabled) {
            Spacer(Modifier.height(8.dp))
            SettingsSectionCard(contentPadding = PaddingValues(16.dp)) {
                Text(
                    text = if (listHostCount > 0) {
                        stringResource(R.string.settings_adblock_list_count, listHostCount)
                    } else {
                        stringResource(R.string.settings_adblock_list_loading)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = listUpdatedText?.let { stringResource(R.string.settings_adblock_list_updated, it) }
                        ?: stringResource(R.string.settings_adblock_list_never),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                listUpdateResultRes?.let {
                    Text(
                        text = stringResource(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                androidx.compose.material3.TextButton(
                    onClick = onUpdateList,
                    enabled = !listUpdating,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text(
                        stringResource(
                            if (listUpdating) R.string.settings_adblock_list_updating
                            else R.string.settings_adblock_list_update_now
                        )
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            SettingsSectionCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = if (allowlistedSites.isEmpty()) 0.dp else 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.settings_shields_allowlist_header),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(shapes = androidx.compose.material3.ButtonDefaults.shapes(), onClick = { showAddSiteDialog = true }) {
                        Text(stringResource(R.string.settings_shields_allowlist_add))
                    }
                }
                if (allowlistedSites.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_shields_allowlist_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    )
                } else {
                    SettingsDivider()
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
                            IconButton(shapes = androidx.compose.material3.IconButtonDefaults.shapes(), onClick = { onRemoveAllowlistedSite(site) }) {
                                Icon(imageVector = Icons.Delete, contentDescription = null)
                            }
                        }
                        if (index != allowlistedSites.lastIndex) SettingsDivider()
                    }
                }
            }
        }
    }

    if (showAddSiteDialog) {
        AddWhitelistedSiteDialog(
            onDismiss = { showAddSiteDialog = false },
            onAdd = { host ->
                onAddAllowlistedSite(host)
                showAddSiteDialog = false
            },
        )
    }
}

/**
 * Direct "Add" entry point for the ad blocker allowlist (was previously only
 * reachable via the Browser's "Block ads & trackers on this site" overflow
 * menu item, which has been removed). Accepts either a bare host
 * ("example.com") or a full URL and extracts the host from it.
 */
@Composable
private fun AddWhitelistedSiteDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var showInvalid by remember { mutableStateOf(false) }

    XmdAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.wideDialogWidth(),
        properties = WideDialogProperties,
        title = { Text(stringResource(R.string.settings_shields_add_site_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        showInvalid = false
                    },
                    placeholder = { Text(stringResource(R.string.settings_shields_add_site_hint)) },
                    singleLine = true,
                    isError = showInvalid,
                    supportingText = if (showInvalid) {
                        { Text(stringResource(R.string.settings_shields_add_site_invalid)) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(shapes = androidx.compose.material3.ButtonDefaults.shapes(), 
                onClick = {
                    val host = extractHostForAllowlist(input)
                    if (host == null) {
                        showInvalid = true
                    } else {
                        onAdd(host)
                    }
                },
            ) {
                Text(stringResource(R.string.settings_shields_allowlist_add))
            }
        },
        dismissButton = {
            TextButton(shapes = androidx.compose.material3.ButtonDefaults.shapes(), onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

/**
 * Pulls a bare host out of whatever the user typed -- a full URL
 * ("https://example.com/path") or just a domain ("example.com") -- so the
 * allowlist always stores the same shape of key that
 * Settings.setAdblockAllowlisted expects (it lowercases and strips any
 * "www." itself, same as it did for the page host coming from the old
 * per-site browser menu toggle). Returns null when nothing that looks
 * like a domain can be recovered.
 */
private fun extractHostForAllowlist(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null

    val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
    val host = runCatching { android.net.Uri.parse(withScheme).host }.getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: return null

    return if (host.contains('.')) host else null
}
