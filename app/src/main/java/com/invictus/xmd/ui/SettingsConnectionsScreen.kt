package com.invictus.xmd.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.invictus.xmd.R

private val CONNECTION_OPTIONS = listOf(2, 4, 8, 16)

/**
 * Parallel connections per download (segmented picker, replacing the old
 * RadioGroup), global speed limit, max concurrent downloads. Values are only
 * persisted when [onSave] fires (Save button), same as the original
 * dialog/fragment -- unlike Downloads/Browser/Appearance, this screen keeps
 * a Save button rather than persisting per-field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsConnectionsScreen(
    initialConnections: Int,
    initialSpeedLimitKBps: Int,
    initialMaxConcurrent: Int,
    onSave: (connections: Int, speedLimitKBps: Int, maxConcurrent: Int) -> Unit,
) {
    var connections by remember { mutableStateOf(initialConnections) }
    var speedLimitText by remember { mutableStateOf(initialSpeedLimitKBps.toString()) }
    var maxConcurrentText by remember { mutableStateOf(initialMaxConcurrent.toString()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        SettingsSectionCard(contentPadding = PaddingValues(16.dp)) {
            Text(
                text = stringResource(R.string.settings_connections),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 16.dp),
            ) {
                CONNECTION_OPTIONS.forEachIndexed { index, value ->
                    SegmentedButton(
                        selected = connections == value,
                        onClick = { connections = value },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = CONNECTION_OPTIONS.size),
                    ) {
                        Text(value.toString())
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                modifier = Modifier.padding(bottom = 16.dp),
            )

            OutlinedTextField(
                value = speedLimitText,
                onValueChange = { speedLimitText = it.filter(Char::isDigit) },
                label = { Text(stringResource(R.string.settings_speed_limit)) },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = maxConcurrentText,
                onValueChange = { maxConcurrentText = it.filter(Char::isDigit) },
                label = { Text(stringResource(R.string.settings_max_concurrent)) },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
        }

        Button(
            onClick = {
                onSave(
                    connections,
                    speedLimitText.toIntOrNull() ?: 0,
                    maxConcurrentText.toIntOrNull() ?: 2,
                )
            },
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp).height(48.dp),
        ) {
            Text(stringResource(R.string.settings_save))
        }
    }
}
