@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)
package com.invictus.xmd.ui.settings

import com.invictus.xmd.ui.theme.XmdShapes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.invictus.xmd.R
import com.invictus.xmd.network.DnsOverHttpsResolver
import com.invictus.xmd.preferences.Settings
import com.invictus.xmd.ui.components.WideDialogProperties
import com.invictus.xmd.ui.components.wideDialogWidth
import com.invictus.xmd.ui.icons.Icon
import com.invictus.xmd.ui.icons.Icons

private val AdGuardGreen = Color(0xFF3DDC84)
private val CloudflareOrange = Color(0xFFF6821F)
private val CustomPurple = Color(0xFF9B6BFF)

/** One DNS provider row: leading badge content, label, optional subtitle (resolver URL / hint). */
private data class DnsOption(
    val mode: Settings.DnsMode,
    val label: String,
    val subtitle: String?,
    val badge: @Composable () -> Unit,
)

/**
 * Private DNS picker. Header (globe + title + close), one rounded card per
 * provider with a brand-style badge and trailing radio (selected card gets a
 * primary outline), and Cancel / Save pill buttons.
 *
 * [onSave] is only invoked once the picked mode/URL combination is valid
 * (a CUSTOM mode needs an http(s) URL); [onInvalidCustomUrl] lets the caller
 * show the toast without this composable needing a Context.
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
    val colors = MaterialTheme.colorScheme

    val options = listOf(
        DnsOption(Settings.DnsMode.OFF, stringResource(R.string.dns_mode_off), stringResource(R.string.dns_mode_off_desc)) {
            Icon(imageVector = Icons.Block, contentDescription = null, tint = colors.primary, modifier = Modifier.size(24.dp))
        },
        DnsOption(Settings.DnsMode.ADGUARD, stringResource(R.string.dns_mode_adguard), DnsOverHttpsResolver.ADGUARD_DOH_URL) {
            Icon(imageVector = Icons.Shield, contentDescription = null, tint = AdGuardGreen, modifier = Modifier.size(24.dp))
        },
        DnsOption(Settings.DnsMode.GOOGLE, stringResource(R.string.dns_mode_google), DnsOverHttpsResolver.GOOGLE_DOH_URL) {
            Text(
                text = "G",
                style = TextStyle(
                    brush = Brush.horizontalGradient(
                        listOf(Color(0xFF4285F4), Color(0xFFEA4335), Color(0xFFFBBC05), Color(0xFF34A853)),
                    ),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                ),
            )
        },
        DnsOption(Settings.DnsMode.CLOUDFLARE, stringResource(R.string.dns_mode_cloudflare), DnsOverHttpsResolver.CLOUDFLARE_DOH_URL) {
            Icon(imageVector = Icons.Cloud, contentDescription = null, tint = CloudflareOrange, modifier = Modifier.size(24.dp))
        },
        DnsOption(Settings.DnsMode.CLOUDFLARE_ADBLOCK, stringResource(R.string.dns_mode_cloudflare_adblock), DnsOverHttpsResolver.CLOUDFLARE_ADBLOCK_DOH_URL) {
            Icon(imageVector = Icons.Shield, contentDescription = null, tint = CloudflareOrange, modifier = Modifier.size(24.dp))
        },
        DnsOption(Settings.DnsMode.CUSTOM, stringResource(R.string.dns_mode_custom), stringResource(R.string.dns_mode_custom_desc)) {
            Icon(imageVector = Icons.Tune, contentDescription = null, tint = CustomPurple, modifier = Modifier.size(24.dp))
        },
    )

    val maxHeight = (LocalConfiguration.current.screenHeightDp.dp - 48.dp).coerceAtLeast(240.dp)

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.wideDialogWidth(),
        properties = WideDialogProperties,
    ) {
        Surface(
            shape = XmdShapes.L,
            color = colors.surface,
            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f)),
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = maxHeight)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                // Header: globe badge + title + close
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier.size(44.dp).background(colors.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Globe,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Text(
                        text = stringResource(R.string.dns_settings_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Close,
                            contentDescription = stringResource(android.R.string.cancel),
                            tint = colors.onSurfaceVariant,
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    options.forEach { option ->
                        val selected = selectedMode == option.mode
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selected,
                                    role = Role.RadioButton,
                                    onClick = { selectedMode = option.mode },
                                ),
                            shape = XmdShapes.M,
                            color = if (selected) colors.primaryContainer.copy(alpha = 0.35f)
                            else colors.surfaceContainerHigh.copy(alpha = 0.6f),
                            border = BorderStroke(
                                1.dp,
                                if (selected) colors.primary else colors.outlineVariant.copy(alpha = 0.4f),
                            ),
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Box(
                                        modifier = Modifier.size(44.dp).background(colors.surfaceContainerHighest, CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) { option.badge() }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(option.label, style = MaterialTheme.typography.bodyLarge)
                                        if (option.subtitle != null) {
                                            Text(
                                                option.subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    // onClick = null: the whole card is the click target.
                                    RadioButton(selected = selected, onClick = null)
                                }
                                if (option.mode == Settings.DnsMode.CUSTOM && selected) {
                                    OutlinedTextField(
                                        value = customUrl,
                                        onValueChange = { customUrl = it },
                                        label = { Text(stringResource(R.string.dns_custom_url_label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        placeholder = {
                                            Text(
                                                stringResource(R.string.dns_custom_url_example),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        },
                                        singleLine = true,
                                        shape = XmdShapes.S,
                                        textStyle = MaterialTheme.typography.bodyMedium,
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Uri,
                                            imeAction = ImeAction.Done,
                                        ),
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = CircleShape,
                    ) { Text(stringResource(android.R.string.cancel)) }
                    Button(
                        onClick = {
                            if (selectedMode == Settings.DnsMode.CUSTOM) {
                                val url = customUrl.trim()
                                if (url.isEmpty() || !(url.startsWith("http://") || url.startsWith("https://"))) {
                                    onInvalidCustomUrl()
                                    return@Button
                                }
                                onSave(Settings.DnsMode.CUSTOM, url)
                            } else {
                                onSave(selectedMode, customUrl)
                            }
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = CircleShape,
                    ) { Text(stringResource(R.string.settings_save), fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}
