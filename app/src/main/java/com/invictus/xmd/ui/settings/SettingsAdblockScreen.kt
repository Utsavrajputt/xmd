@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.invictus.xmd.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.invictus.xmd.R
import com.invictus.xmd.ui.components.WideDialogProperties
import com.invictus.xmd.ui.components.XmdAlertDialog
import com.invictus.xmd.ui.components.wideDialogWidth
import com.invictus.xmd.ui.icons.Icon
import com.invictus.xmd.ui.icons.Icons

/** One filter list as the Ad blocking screen shows it. */
data class AdblockListRow(
    val id: String,
    val title: String,
    val description: String,
    val groupRes: Int,
    val enabled: Boolean,
    val ruleCount: Int,
    /** Formatted "last updated" date, or null if never downloaded. */
    val updatedText: String?,
    val error: String?,
    val downloading: Boolean,
    val custom: Boolean,
)

/**
 * Ad blocking page: the master switch, a rule-count / "update now" card, the
 * auto-update schedule, every filter list grouped by purpose (each with its
 * own switch), the user's own lists, and the per-site allowlist.
 */
@Composable
fun SettingsAdblockScreen(
    adblockEnabled: Boolean,
    allowlistedSites: List<String>,
    onAdblockEnabledChanged: (Boolean) -> Unit,
    ruleCount: Int,
    building: Boolean,
    listUpdatedText: String?,
    lists: List<AdblockListRow>,
    updateDays: Int,
    wifiOnly: Boolean,
    listUpdating: Boolean,
    listUpdateResultRes: Int?,
    onUpdateList: () -> Unit,
    onListToggled: (String, Boolean) -> Unit,
    onAddList: (String, String) -> Boolean,
    onRemoveList: (String) -> Unit,
    onUpdateDaysChanged: (Int) -> Unit,
    onWifiOnlyChanged: (Boolean) -> Unit,
    cosmeticEnabled: Boolean,
    scriptletsEnabled: Boolean,
    onCosmeticChanged: (Boolean) -> Unit,
    onScriptletsChanged: (Boolean) -> Unit,
    userRuleCount: Int,
    onOpenRules: () -> Unit,
    onAddAllowlistedSite: (String) -> Unit,
    onRemoveAllowlistedSite: (String) -> Unit,
) {
    var showAddSiteDialog by remember { mutableStateOf(false) }
    var showAddListDialog by remember { mutableStateOf(false) }
    var showIntervalDialog by remember { mutableStateOf(false) }

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

        // Everything below only matters while blocking is on.
        if (adblockEnabled) {
            Spacer(Modifier.height(8.dp))
            SettingsSectionCard(contentPadding = PaddingValues(16.dp)) {
                Text(
                    text = when {
                        building && ruleCount == 0 -> stringResource(R.string.settings_adblock_building)
                        ruleCount > 0 -> stringResource(R.string.settings_adblock_rule_count, ruleCount)
                        else -> stringResource(R.string.settings_adblock_list_loading)
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
                TextButton(
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
                SwitchSettingRow(
                    title = stringResource(R.string.settings_adblock_cosmetic_title),
                    subtitle = stringResource(R.string.settings_adblock_cosmetic_subtitle),
                    checked = cosmeticEnabled,
                    onCheckedChange = onCosmeticChanged,
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = stringResource(R.string.settings_adblock_scriptlets_title),
                    subtitle = stringResource(R.string.settings_adblock_scriptlets_subtitle),
                    checked = scriptletsEnabled,
                    onCheckedChange = onScriptletsChanged,
                )
                SettingsDivider()
                ClickableSettingRow(
                    title = stringResource(R.string.settings_adblock_rules_title),
                    subtitle = if (userRuleCount > 0) {
                        stringResource(R.string.settings_adblock_rules_subtitle_count, userRuleCount)
                    } else {
                        stringResource(R.string.settings_adblock_rules_subtitle)
                    },
                    onClick = onOpenRules,
                )
            }

            Spacer(Modifier.height(8.dp))
            SettingsSectionCard {
                ClickableSettingRow(
                    title = stringResource(R.string.settings_adblock_update_title),
                    subtitle = intervalLabel(updateDays),
                    onClick = { showIntervalDialog = true },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = stringResource(R.string.settings_adblock_wifi_only_title),
                    subtitle = stringResource(R.string.settings_adblock_wifi_only_subtitle),
                    checked = wifiOnly,
                    onCheckedChange = onWifiOnlyChanged,
                    enabled = updateDays > 0,
                )
            }

            // Lists, grouped in catalog order (the user's own last).
            val groups = LinkedHashMap<Int, MutableList<AdblockListRow>>()
            for (row in lists) groups.getOrPut(row.groupRes) { ArrayList() }.add(row)
            for ((groupRes, rows) in groups) {
                SettingsSectionHeader(title = stringResource(groupRes))
                SettingsSectionCard {
                    rows.forEachIndexed { index, row ->
                        FilterListRow(
                            row = row,
                            onToggled = { onListToggled(row.id, it) },
                            onRemove = { onRemoveList(row.id) },
                        )
                        if (index != rows.lastIndex) SettingsDivider()
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            SettingsSectionCard {
                ClickableSettingRow(
                    title = stringResource(R.string.settings_adblock_add_list),
                    subtitle = stringResource(R.string.settings_adblock_add_list_subtitle),
                    onClick = { showAddListDialog = true },
                )
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
    if (showAddListDialog) {
        AddFilterListDialog(
            onDismiss = { showAddListDialog = false },
            onAdd = { name, url ->
                if (onAddList(name, url)) {
                    showAddListDialog = false
                    true
                } else {
                    false
                }
            },
        )
    }
    if (showIntervalDialog) {
        UpdateIntervalDialog(
            current = updateDays,
            onDismiss = { showIntervalDialog = false },
            onPick = {
                onUpdateDaysChanged(it)
                showIntervalDialog = false
            },
        )
    }
}

@Composable
private fun intervalLabel(days: Int): String = when (days) {
    0 -> stringResource(R.string.settings_adblock_update_manual)
    1 -> stringResource(R.string.settings_adblock_update_daily)
    else -> stringResource(R.string.settings_adblock_update_days, days)
}

@Composable
private fun FilterListRow(
    row: AdblockListRow,
    onToggled: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = if (row.custom) 4.dp else 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = row.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (row.enabled) {
                val status = when {
                    row.downloading -> stringResource(R.string.settings_adblock_list_status_downloading)
                    row.error != null -> stringResource(R.string.settings_adblock_list_status_error, row.error)
                    row.updatedText == null -> stringResource(R.string.settings_adblock_list_status_pending)
                    else -> stringResource(R.string.settings_adblock_list_status_rules, row.ruleCount, row.updatedText)
                }
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (row.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (row.downloading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        Switch(checked = row.enabled, onCheckedChange = onToggled)
        if (row.custom) {
            IconButton(shapes = androidx.compose.material3.IconButtonDefaults.shapes(), onClick = onRemove) {
                Icon(imageVector = Icons.Delete, contentDescription = stringResource(R.string.settings_adblock_remove_list))
            }
        }
    }
}

@Composable
private fun UpdateIntervalDialog(
    current: Int,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
) {
    XmdAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.wideDialogWidth(),
        properties = WideDialogProperties,
        title = { Text(stringResource(R.string.settings_adblock_update_title)) },
        text = {
            Column {
                for (days in listOf(1, 3, 7, 0)) {
                    RadioSettingRow(
                        title = intervalLabel(days),
                        subtitle = "",
                        selected = current == days,
                        onClick = { onPick(days) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(shapes = androidx.compose.material3.ButtonDefaults.shapes(), onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun AddFilterListDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Boolean,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var showInvalid by remember { mutableStateOf(false) }

    XmdAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.wideDialogWidth(),
        properties = WideDialogProperties,
        title = { Text(stringResource(R.string.settings_adblock_add_list_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text(stringResource(R.string.settings_adblock_add_list_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        showInvalid = false
                    },
                    placeholder = { Text(stringResource(R.string.settings_adblock_add_list_url_hint)) },
                    singleLine = true,
                    isError = showInvalid,
                    supportingText = if (showInvalid) {
                        { Text(stringResource(R.string.settings_adblock_add_list_invalid)) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                shapes = androidx.compose.material3.ButtonDefaults.shapes(),
                onClick = { if (!onAdd(name, url)) showInvalid = true },
            ) { Text(stringResource(R.string.settings_shields_allowlist_add)) }
        },
        dismissButton = {
            TextButton(shapes = androidx.compose.material3.ButtonDefaults.shapes(), onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
