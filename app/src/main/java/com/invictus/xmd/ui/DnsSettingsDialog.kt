package com.invictus.xmd.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.invictus.xmd.R
import com.invictus.xmd.core.DnsOverHttpsResolver
import com.invictus.xmd.core.Settings

/**
 * Phase 5 (Browser) conversion of MainActivity.showDnsSettingsDialog() --
 * previously a MaterialAlertDialogBuilder wrapping dialog_dns_settings.xml
 * (a RadioGroup of AppCompatRadioButtons + a TextInputLayout revealed only
 * for the CUSTOM option). Deliberately touches MainActivity ahead of
 * schedule -- see COMPOSE_MIGRATION.md's Phase 5 "DnsSettingsDialog" step
 * for why this was previously deferred and why it's done now.
 *
 * [onSave] is only invoked once the picked mode/URL combination is valid
 * (mirrors the old builder's setPositiveButton, which called
 * return@setPositiveButton on a bad CUSTOM URL instead of dismissing);
 * [onInvalidCustomUrl] lets the caller show the same toast the old code did
 * without this composable needing a Context of its own.
 */
@Composable
fun DnsSettingsDialog(
    currentMode: Settings.DnsMode,
    currentCustomUrl: String,
    onDismiss: () -> Unit,
    onSave: (Settings.DnsMode, String) -> Unit,
    onInvalidCustomUrl: () -> Unit,
) {
    var selectedMode by remember { mutableStateOf(currentMode) }
    var customUrl by remember { mutableStateOf(currentCustomUrl) }

    val options = listOf(
        Settings.DnsMode.OFF to Pair(stringResource(R.string.dns_mode_off), null),
        Settings.DnsMode.ADGUARD to Pair(stringResource(R.string.dns_mode_adguard), DnsOverHttpsResolver.ADGUARD_DOH_URL),
        Settings.DnsMode.GOOGLE to Pair(stringResource(R.string.dns_mode_google), DnsOverHttpsResolver.GOOGLE_DOH_URL),
        Settings.DnsMode.CLOUDFLARE to Pair(stringResource(R.string.dns_mode_cloudflare), DnsOverHttpsResolver.CLOUDFLARE_DOH_URL),
        Settings.DnsMode.CLOUDFLARE_ADBLOCK to Pair(stringResource(R.string.dns_mode_cloudflare_adblock), DnsOverHttpsResolver.CLOUDFLARE_ADBLOCK_DOH_URL),
        Settings.DnsMode.CUSTOM to Pair(stringResource(R.string.dns_mode_custom), null),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dns_settings_title)) },
        text = {
            Column {
                options.forEach { (mode, labelAndAddress) ->
                    val (label, address) = labelAndAddress
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedMode == mode,
                                onClick = { selectedMode = mode },
                            )
                            .padding(vertical = 8.dp),
                    ) {
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selectedMode == mode, onClick = { selectedMode = mode })
                            Column {
                                Text(label, style = MaterialTheme.typography.bodyLarge)
                                // Mirrors labelWithAddress()'s two-line row (title on
                                // top, resolved DoH host underneath, dimmer/smaller) --
                                // the old View version built this with a SpannableString
                                // since RadioButton.text is a single CharSequence; Compose
                                // just stacks two Text()s instead.
                                if (address != null) {
                                    Text(
                                        address,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        if (mode == Settings.DnsMode.CUSTOM && selectedMode == Settings.DnsMode.CUSTOM) {
                            OutlinedTextField(
                                value = customUrl,
                                onValueChange = { customUrl = it },
                                label = { Text(stringResource(R.string.dns_custom_url_hint)) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 40.dp, top = 4.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (selectedMode == Settings.DnsMode.CUSTOM) {
                    val url = customUrl.trim()
                    if (url.isEmpty() || !(url.startsWith("http://") || url.startsWith("https://"))) {
                        onInvalidCustomUrl()
                        return@TextButton
                    }
                    onSave(Settings.DnsMode.CUSTOM, url)
                } else {
                    onSave(selectedMode, customUrl)
                }
            }) {
                Text(stringResource(R.string.settings_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
