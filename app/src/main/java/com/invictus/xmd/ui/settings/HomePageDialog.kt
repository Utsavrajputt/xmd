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

/** Picker for the browser toolbar's Home button destination -- same
 *  radio-list-with-inline-custom-fields shape as [SearchEngineDialog]. */
@Composable
fun HomePageDialog(
    currentPage: Settings.HomePage,
    currentCustomUrl: String,
    currentCustomName: String,
    onDismiss: () -> Unit,
    onSave: (page: Settings.HomePage, customUrl: String, customName: String) -> Unit,
    onInvalidCustomUrl: () -> Unit,
) {
    var selectedPage by remember { mutableStateOf(currentPage) }
    var customUrl by remember { mutableStateOf(currentCustomUrl) }
    var customName by remember { mutableStateOf(currentCustomName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.wideDialogWidth(),
        properties = WideDialogProperties,
        title = { Text(stringResource(R.string.settings_home_page)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Settings.HomePage.entries.forEach { page ->
                    val isSelected = selectedPage == page
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedPage = page }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedPage = page },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (page == Settings.HomePage.CUSTOM && customName.isNotBlank()) {
                                        "${stringResource(R.string.home_page_custom)} ($customName)"
                                    } else if (page == Settings.HomePage.SPEED_DIAL) {
                                        stringResource(R.string.home_page_speed_dial)
                                    } else {
                                        page.displayName
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = if (page == Settings.HomePage.CUSTOM && customUrl.isNotBlank()) {
                                        customUrl
                                    } else {
                                        page.domain
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        if (page == Settings.HomePage.CUSTOM && isSelected) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 48.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                            ) {
                                OutlinedTextField(
                                    value = customName,
                                    onValueChange = { customName = it },
                                    label = { Text(stringResource(R.string.home_page_custom_name)) },
                                    placeholder = { Text("e.g., Startpage") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = customUrl,
                                    onValueChange = { customUrl = it },
                                    label = { Text(stringResource(R.string.home_page_custom_url)) },
                                    placeholder = { Text("https://example.com") },
                                    supportingText = {
                                        Text(
                                            stringResource(R.string.home_page_custom_url_hint),
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
            TextButton(
                onClick = {
                    if (selectedPage == Settings.HomePage.CUSTOM) {
                        val trimmedUrl = customUrl.trim()
                        if (trimmedUrl.isBlank() || !(trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://"))) {
                            onInvalidCustomUrl()
                            return@TextButton
                        }
                        onSave(selectedPage, trimmedUrl, customName.trim())
                    } else {
                        onSave(selectedPage, customUrl.trim(), customName.trim())
                    }
                },
            ) {
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
