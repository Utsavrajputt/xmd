@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.invictus.xmd.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.invictus.xmd.R
import com.invictus.xmd.domain.browser.adblock.UserRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * "My rules": the user's own filter rules, one per line, in the same syntax the
 * filter lists use (`||ads.example.com^`, `example.com##.banner`, `@@||site.com^`,
 * a bare domain, ...). They are applied after every list and win over them. The
 * long-press "Hide element" picker appends its `host##selector` lines here too.
 */
@Composable
fun SettingsAdblockRulesScreen(
    initialText: String,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initialText) }
    var savedText by remember { mutableStateOf(initialText) }
    var invalid by remember { mutableStateOf<List<Int>>(emptyList()) }

    // Validation compiles every line, so it runs off the main thread and waits for typing to pause.
    LaunchedEffect(text) {
        delay(400)
        invalid = withContext(Dispatchers.Default) { UserRules.invalidLines(text) }
    }

    val changed = text != savedText
    val ruleCount = remember(text) { UserRules.count(text) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
            placeholder = { Text(stringResource(R.string.settings_adblock_rules_hint)) },
            isError = invalid.isNotEmpty(),
        )

        Text(
            text = if (invalid.isEmpty()) {
                stringResource(R.string.settings_adblock_rules_status_ok, ruleCount)
            } else {
                stringResource(
                    R.string.settings_adblock_rules_status_invalid,
                    ruleCount,
                    invalid.take(8).joinToString(", "),
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (invalid.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        )

        var showHelp by remember { mutableStateOf(false) }
        if (showHelp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.8f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(R.string.settings_adblock_rules_help),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = { showHelp = !showHelp }) {
                Text(stringResource(if (showHelp) R.string.settings_adblock_rules_help_hide else R.string.settings_adblock_rules_help_show))
            }
            Button(
                onClick = {
                    onSave(text)
                    savedText = text
                },
                enabled = changed,
            ) {
                Text(stringResource(R.string.settings_adblock_rules_save))
            }
        }
    }
}
