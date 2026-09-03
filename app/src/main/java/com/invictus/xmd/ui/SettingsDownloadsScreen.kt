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

/**
 * Auto-retry, save-to-Downloads, and Wi-Fi-only. Each switch persists
 * immediately via its own [onXChanged] callback (no Save button), matching
 * the original fragment's behavior including the wifi-only-just-enabled
 * pause-in-flight-downloads side effect (handled in
 * SettingsActivity's DownloadsRoute, not here -- this composable is presentation
 * only).
 */
@Composable
fun SettingsDownloadsScreen(
    autoRetry: Boolean,
    saveToDownloads: Boolean,
    wifiOnly: Boolean,
    onAutoRetryChanged: (Boolean) -> Unit,
    onSaveToDownloadsChanged: (Boolean) -> Unit,
    onWifiOnlyChanged: (Boolean) -> Unit,
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
            SwitchSettingRow(
                title = stringResource(R.string.settings_save_to_downloads),
                subtitle = stringResource(R.string.settings_save_to_downloads_hint),
                checked = saveToDownloads,
                onCheckedChange = onSaveToDownloadsChanged,
            )
            SettingsDivider()
            SwitchSettingRow(
                title = stringResource(R.string.settings_wifi_only),
                subtitle = stringResource(R.string.settings_wifi_only_hint),
                checked = wifiOnly,
                onCheckedChange = onWifiOnlyChanged,
            )
        }
    }
}
