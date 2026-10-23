@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.invictus.xmd.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.invictus.xmd.ui.components.XmdAlertDialog
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.invictus.xmd.R
import com.invictus.xmd.preferences.Settings
import com.invictus.xmd.ui.components.WideDialogProperties
import com.invictus.xmd.ui.components.wideDialogWidth

@Composable
fun SearchEngineDialog(
    currentEngine: Settings.SearchEngine,
    currentCustomUrl: String,
    currentCustomName: String,
    onDismiss: () -> Unit,
    onSave: (engine: Settings.SearchEngine, customUrl: String, customName: String) -> Unit,
    onInvalidCustomUrl: () -> Unit,
) {
    var selectedEngine by remember { mutableStateOf(currentEngine) }
    var customUrl by remember { mutableStateOf(currentCustomUrl) }
    var customName by remember { mutableStateOf(currentCustomName) }

    XmdAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.wideDialogWidth(),
        properties = WideDialogProperties,
        title = { Text(stringResource(R.string.settings_search_engine)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Settings.SearchEngine.entries.forEach { engine ->
                    val isSelected = selectedEngine == engine
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedEngine = engine }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedEngine = engine },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (engine == Settings.SearchEngine.CUSTOM && customName.isNotBlank()) {
                                        "${stringResource(R.string.search_engine_custom)} ($customName)"
                                    } else {
                                        engine.displayName
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = if (engine == Settings.SearchEngine.CUSTOM && customUrl.isNotBlank()) {
                                        customUrl
                                    } else {
                                        engine.domain
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        if (engine == Settings.SearchEngine.CUSTOM && isSelected) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 48.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                            ) {
                                OutlinedTextField(
                                    value = customName,
                                    onValueChange = { customName = it },
                                    label = { Text(stringResource(R.string.search_engine_custom_name)) },
                                    placeholder = { Text("e.g., Startpage") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = customUrl,
                                    onValueChange = { customUrl = it },
                                    label = { Text(stringResource(R.string.search_engine_custom_url)) },
                                    placeholder = { Text("https://example.com/search?q=%s") },
                                    supportingText = {
                                        Text(
                                            stringResource(R.string.search_engine_custom_url_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(shapes = androidx.compose.material3.ButtonDefaults.shapes(), 
                onClick = {
                    if (selectedEngine == Settings.SearchEngine.CUSTOM) {
                        val trimmedUrl = customUrl.trim()
                        if (trimmedUrl.isBlank() || !(trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://"))) {
                            onInvalidCustomUrl()
                            return@TextButton
                        }
                        onSave(selectedEngine, trimmedUrl, customName.trim())
                    } else {
                        onSave(selectedEngine, customUrl.trim(), customName.trim())
                    }
                },
            ) {
                Text(stringResource(R.string.settings_save))
            }
        },
        dismissButton = {
            TextButton(shapes = androidx.compose.material3.ButtonDefaults.shapes(), onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
