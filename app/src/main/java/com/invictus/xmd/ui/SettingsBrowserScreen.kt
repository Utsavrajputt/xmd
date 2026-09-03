package com.invictus.xmd.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.invictus.xmd.R

/**
 * Browser settings: the global adblock toggle and the website source-pack
 * import/export trigger. Private DNS mode lives in its own in-browser
 * dialog (BrowserFragment's overflow menu), unchanged -- not part of this
 * screen.
 */
@Composable
fun SettingsBrowserScreen(
    adblockEnabled: Boolean,
    onAdblockChanged: (Boolean) -> Unit,
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
            SwitchSettingRow(
                title = stringResource(R.string.settings_adblock),
                subtitle = stringResource(R.string.settings_adblock_hint),
                checked = adblockEnabled,
                onCheckedChange = onAdblockChanged,
            )
        }

        Spacer(Modifier.height(8.dp))
        SettingsSectionHeader(title = stringResource(R.string.settings_import_websites))

        SettingsSectionCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            Text(
                text = stringResource(R.string.settings_import_websites_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
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
