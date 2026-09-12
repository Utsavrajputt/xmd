package com.invictus.xmd.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.invictus.xmd.R
import com.invictus.xmd.preferences.Settings
import com.invictus.xmd.ui.downloads.AddDownloadDialog
import com.invictus.xmd.ui.downloads.AddTorrentDialog

/** MB/GB unit toggle for the data-limit value fields. */
private enum class DataUnit(val bytesPerUnit: Long) { MB(1024L * 1024), GB(1024L * 1024 * 1024) }

/**
 * Auto-retry, default save location, folder categorization, and Wi-Fi-only.
 * Each control persists immediately via its own [onXChanged] callback (no
 * Save button), matching the original fragment's behavior including the
 * wifi-only-just-enabled pause-in-flight-downloads side effect (handled in
 * SettingsActivity's DownloadsRoute, not here -- this composable is presentation
 * only).
 *
 * [defaultLocationPath] and [onChangeDefaultLocation] replace the old
 * "Save to Downloads Folder" switch with a folder picker -- the same SAF
 * ACTION_OPEN_DOCUMENT_TREE flow used by the per-download "Change" button in
 * AddDownloadDialog/AddTorrentDialog -- so the user picks any folder as the
 * default instead of a fixed choice between the app folder and Downloads.
 * [categorizeIntoFolders] is the new, independent toggle for whether
 * downloads are still sorted into Videos/Music/Documents/... subfolders
 * under that location.
 */
@Composable
fun SettingsDownloadsScreen(
    autoRetry: Boolean,
    defaultLocationPath: String,
    categorizeIntoFolders: Boolean,
    wifiOnly: Boolean,
    dataLimitEnabled: Boolean,
    dataLimitBytes: Long,
    dataLimitScope: Settings.DataLimitScope,
    onAutoRetryChanged: (Boolean) -> Unit,
    onChangeDefaultLocation: () -> Unit,
    onCategorizeIntoFoldersChanged: (Boolean) -> Unit,
    onWifiOnlyChanged: (Boolean) -> Unit,
    onDataLimitEnabledChanged: (Boolean) -> Unit,
    onDataLimitBytesChanged: (Long) -> Unit,
    onDataLimitScopeChanged: (Settings.DataLimitScope) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        SettingsSectionCard {
            SwitchSettingRow(
                title = stringResource(R.string.settings_auto_retry),
                subtitle = stringResource(R.string.settings_auto_retry_hint),
                checked = autoRetry,
                onCheckedChange = onAutoRetryChanged,
            )
            SettingsDivider()
            DefaultLocationRow(
                path = defaultLocationPath,
                onChangeClick = onChangeDefaultLocation,
            )
            SettingsDivider()
            SwitchSettingRow(
                title = stringResource(R.string.settings_disable_categorization),
                subtitle = stringResource(R.string.settings_disable_categorization_hint),
                checked = !categorizeIntoFolders,
                onCheckedChange = { disabled -> onCategorizeIntoFoldersChanged(!disabled) },
            )
            SettingsDivider()
            SwitchSettingRow(
                title = stringResource(R.string.settings_wifi_only),
                subtitle = stringResource(R.string.settings_wifi_only_hint),
                checked = wifiOnly,
                onCheckedChange = onWifiOnlyChanged,
            )
        }

        Spacer(modifier = Modifier.size(16.dp))

        SettingsSectionCard {
            SwitchSettingRow(
                title = stringResource(R.string.settings_data_limit),
                subtitle = stringResource(R.string.settings_data_limit_hint),
                checked = dataLimitEnabled,
                onCheckedChange = onDataLimitEnabledChanged,
            )
            if (dataLimitEnabled) {
                DataLimitScopeRow(
                    scope = dataLimitScope,
                    onScopeChanged = onDataLimitScopeChanged,
                )
                DataLimitValueRow(
                    bytes = dataLimitBytes,
                    onBytesChanged = onDataLimitBytesChanged,
                )
            }
        }
    }
}

/** Display label for a [Settings.DataLimitScope], shared by the dropdown's
 *  closed-state button and its menu items below. */
@Composable
private fun dataLimitScopeLabel(scope: Settings.DataLimitScope): String = when (scope) {
    Settings.DataLimitScope.MOBILE -> stringResource(R.string.settings_data_limit_scope_mobile)
    Settings.DataLimitScope.WIFI -> stringResource(R.string.settings_data_limit_scope_wifi)
    Settings.DataLimitScope.TOTAL -> stringResource(R.string.settings_data_limit_scope_total)
}

/**
 * Which network(s) count toward the data limit above -- a dropdown (same
 * OutlinedButton + DropdownMenu shape as the quality picker in
 * AddDownloadDialog) offering Mobile Data Only / Wi-Fi Only / Mobile +
 * Wi-Fi, replacing what used to be two separate toggles (one per scope).
 */
@Composable
private fun DataLimitScopeRow(
    scope: Settings.DataLimitScope,
    onScopeChanged: (Settings.DataLimitScope) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = stringResource(R.string.settings_data_limit_scope_label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Box {
            OutlinedButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(dataLimitScopeLabel(scope), modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                Settings.DataLimitScope.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(dataLimitScopeLabel(option)) },
                        onClick = {
                            menuExpanded = false
                            onScopeChanged(option)
                        },
                    )
                }
            }
        }
    }
}

/**
 * Numeric value field + MB/GB segmented unit picker for one data-limit
 * setting. Defaults the unit shown to whatever the current [bytes] value
 * divides evenly into GB as (so re-opening Settings after setting "2 GB"
 * shows "2 GB", not "2048 MB") -- purely a display choice, the persisted
 * value is always plain bytes via [onBytesChanged].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DataLimitValueRow(
    bytes: Long,
    onBytesChanged: (Long) -> Unit,
) {
    var unit by remember(bytes) {
        mutableStateOf(if (bytes % DataUnit.GB.bytesPerUnit == 0L) DataUnit.GB else DataUnit.MB)
    }
    var valueText by remember(bytes, unit) {
        mutableStateOf((bytes / unit.bytesPerUnit).toString())
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = valueText,
                onValueChange = { input ->
                    val filtered = input.filter(Char::isDigit)
                    valueText = filtered
                    filtered.toLongOrNull()?.let { onBytesChanged(it * unit.bytesPerUnit) }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(12.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.width(140.dp)) {
                DataUnit.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = unit == option,
                        onClick = {
                            unit = option
                            valueText.toLongOrNull()?.let { onBytesChanged(it * option.bytesPerUnit) }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = DataUnit.entries.size),
                    ) {
                        Text(option.name)
                    }
                }
            }
        }
    }
}

/**
 * Title + current path row with a "Change" button that launches the SAF
 * folder picker (via [onChangeClick], wired to ActivityResultContracts.
 * OpenDocumentTree by the caller) -- same layout as the "Save to" row in
 * AddDownloadDialog/AddTorrentDialog's Advanced section, reused here so
 * picking the default location feels like the same action.
 */
@Composable
private fun DefaultLocationRow(
    path: String,
    onChangeClick: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = stringResource(R.string.settings_default_location),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.settings_default_location_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
        )
        com.invictus.xmd.ui.components.FolderPickerCard(
            path = path,
            onChangeClick = onChangeClick,
        )
    }
}
