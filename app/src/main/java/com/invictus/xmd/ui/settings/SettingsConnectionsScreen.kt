package com.invictus.xmd.ui.settings

import com.invictus.xmd.ui.theme.XmdShapes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import com.invictus.xmd.ui.icons.Icon
import com.invictus.xmd.ui.icons.Icons
import com.invictus.xmd.R
import java.util.Locale
import kotlin.math.roundToInt

private const val MIN_CONNECTIONS = 1
private const val MAX_CONNECTIONS = 24
private const val HIGH_CONNECTIONS = 12
private const val MIN_CONCURRENT = 1
private const val MAX_CONCURRENT = 5

/** (label, KB/s) presets; 0 means unlimited. */
private val SPEED_PRESETS = listOf(
    0 to null,
    512 to "512 KB/s",
    1024 to "1 MB/s",
    2048 to "2 MB/s",
    5120 to "5 MB/s",
)

private const val KB_PER_MB = 1024.0

/** Text -> KB/s for the given unit, or null when it isn't a number yet (empty, lone "."). */
private fun parseSpeedKBps(text: String, mb: Boolean): Int? {
    val v = text.toDoubleOrNull() ?: return null
    return (if (mb) v * KB_PER_MB else v).roundToInt().coerceAtLeast(0)
}

/** KB/s -> field text in the given unit (MB/s shows up to 3 decimals, trailing zeros trimmed); 0 shows the "Unlimited" label. */
private fun formatSpeed(kbps: Int, mb: Boolean, unlimitedLabel: String): String =
    if (kbps == 0) unlimitedLabel
    else if (!mb) kbps.toString()
    else String.format(Locale.US, "%.3f", kbps / KB_PER_MB).trimEnd('0').trimEnd('.')

/** KB/s: digits only (7 max). MB/s: digits with one dot, <=4 integer and <=3 decimal digits. */
private fun sanitizeSpeedInput(input: String, mb: Boolean): String {
    if (!mb) return input.filter(Char::isDigit).take(7)
    val cleaned = input.replace(',', '.').filter { it.isDigit() || it == '.' }
    val dot = cleaned.indexOf('.')
    if (dot < 0) return cleaned.take(4)
    val whole = cleaned.substring(0, dot).take(4)
    val frac = cleaned.substring(dot + 1).filter(Char::isDigit).take(3)
    return "$whole.$frac"
}

/**
 * Connections & Speed, as three cards: connections per download (slider with
 * a live value badge), speed limit (preset chips + custom field) and
 * simultaneous downloads (stepper). Every change persists immediately.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsConnectionsScreen(
    connections: Int,
    speedLimitKBps: Int,
    speedUnitMb: Boolean,
    maxConcurrent: Int,
    onConnectionsChanged: (Int) -> Unit,
    onSpeedLimitChanged: (Int) -> Unit,
    onSpeedUnitChanged: (Boolean) -> Unit,
    onMaxConcurrentChanged: (Int) -> Unit,
) {
    // The stored limit is always KB/s; the field just shows it in the chosen
    // unit. Text is only rewritten explicitly (chip tap, unit switch, commit)
    // so an in-progress edit like "1." or an empty field isn't clobbered.
    val unlimitedLabel = stringResource(R.string.conn_unlimited)
    var speedLimitText by remember { mutableStateOf(formatSpeed(speedLimitKBps, speedUnitMb, unlimitedLabel)) }
    var unitMenuOpen by remember { mutableStateOf(false) }
    var customFieldHadFocus by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Tidy the text after editing: an empty/partial entry falls back to the
    // saved limit, "05" / "1." become "5" / "1".
    fun commitCustomSpeed() {
        val kbps = parseSpeedKBps(speedLimitText, speedUnitMb)
        if (kbps == null) {
            speedLimitText = formatSpeed(speedLimitKBps, speedUnitMb, unlimitedLabel)
        } else {
            if (kbps != speedLimitKBps) onSpeedLimitChanged(kbps)
            speedLimitText = formatSpeed(kbps, speedUnitMb, unlimitedLabel)
        }
    }
    fun dismissCustomField() {
        focusManager.clearFocus()
        keyboardController?.hide()
    }
    val concurrent = maxConcurrent.coerceIn(MIN_CONCURRENT, MAX_CONCURRENT)

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Tap anywhere that isn't a control: drop focus + keyboard (which also
            // commits the custom speed field).
            .pointerInput(Unit) { detectTapGestures(onTap = { dismissCustomField() }) }
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Connections per download ─────────────────────────────────
        SettingsSectionCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.settings_connections),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                ValueBadge(connections.toString())
            }
            Slider(
                value = connections.toFloat(),
                onValueChange = { onConnectionsChanged(it.toInt().coerceIn(MIN_CONNECTIONS, MAX_CONNECTIONS)) },
                valueRange = MIN_CONNECTIONS.toFloat()..MAX_CONNECTIONS.toFloat(),
                steps = MAX_CONNECTIONS - MIN_CONNECTIONS - 1,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(MIN_CONNECTIONS.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(MAX_CONNECTIONS.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = stringResource(if (connections > HIGH_CONNECTIONS) R.string.conn_hint_high else R.string.conn_hint),
                style = MaterialTheme.typography.bodySmall,
                color = if (connections > HIGH_CONNECTIONS) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        // ── Speed limit ──────────────────────────────────────────────
        SettingsSectionCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            Text(
                text = stringResource(R.string.conn_speed_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.conn_speed_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SPEED_PRESETS.forEach { (value, label) ->
                    FilterChip(
                        selected = speedLimitKBps == value,
                        onClick = {
                            // Drop focus first so the field's own commit runs against
                            // the old state, then apply the preset on top.
                            dismissCustomField()
                            onSpeedLimitChanged(value)
                            speedLimitText = formatSpeed(value, speedUnitMb, unlimitedLabel)
                        },
                        label = { Text(label ?: stringResource(R.string.conn_unlimited)) },
                    )
                }
            }
            OutlinedTextField(
                value = speedLimitText,
                onValueChange = { input ->
                    val filtered = sanitizeSpeedInput(input, speedUnitMb)
                    speedLimitText = filtered
                    parseSpeedKBps(filtered, speedUnitMb)?.let(onSpeedLimitChanged)
                },
                label = { Text(stringResource(R.string.conn_custom_speed)) },
                suffix = {
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(XmdShapes.Xs)
                                .clickable { unitMenuOpen = true }
                                .padding(start = 6.dp, top = 4.dp, bottom = 4.dp),
                        ) {
                            Text(if (speedUnitMb) "MB/s" else "KB/s")
                            Icon(
                                imageVector = Icons.ChevronDown,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        DropdownMenu(
 shape = com.invictus.xmd.ui.theme.XmdShapes.M, containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh,
                            expanded = unitMenuOpen,
                            onDismissRequest = { unitMenuOpen = false },
                        ) {
                            listOf(false to "KB/s", true to "MB/s").forEach { (isMb, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        unitMenuOpen = false
                                        if (isMb != speedUnitMb) {
                                            // Convert from the saved KB/s value, so switching
                                            // back and forth never loses precision.
                                            onSpeedUnitChanged(isMb)
                                            speedLimitText = formatSpeed(speedLimitKBps, isMb, unlimitedLabel)
                                        }
                                    },
                                )
                            }
                        }
                    }
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = if (speedUnitMb) KeyboardType.Decimal else KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = {
                    commitCustomSpeed()
                    dismissCustomField()
                }),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .onFocusChanged { state ->
                        if (state.isFocused) {
                            customFieldHadFocus = true
                        } else if (customFieldHadFocus) {
                            customFieldHadFocus = false
                            commitCustomSpeed()
                        }
                    },
            )
        }

        // ── Simultaneous downloads ───────────────────────────────────
        SettingsSectionCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.conn_concurrent_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.conn_concurrent_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                FilledTonalIconButton(
                    onClick = { onMaxConcurrentChanged(concurrent - 1) },
                    enabled = concurrent > MIN_CONCURRENT,
                ) { Text("−", style = MaterialTheme.typography.titleLarge) }
                Text(
                    text = concurrent.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(36.dp),
                )
                FilledTonalIconButton(
                    onClick = { onMaxConcurrentChanged(concurrent + 1) },
                    enabled = concurrent < MAX_CONCURRENT,
                ) { Text("+", style = MaterialTheme.typography.titleLarge) }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ValueBadge(text: String) {
    Surface(
        shape = XmdShapes.S,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp).widthIn(min = 28.dp),
            textAlign = TextAlign.Center,
        )
    }
}
