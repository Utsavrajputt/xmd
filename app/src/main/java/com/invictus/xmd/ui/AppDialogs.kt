package com.invictus.xmd.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal data class AppMessageDialogState(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val dismissLabel: String? = null,
    val onConfirm: () -> Unit = {},
    val onDismiss: () -> Unit = {},
    val onDismissAction: (() -> Unit)? = null,
)

@Composable
internal fun AppMessageDialog(
    state: AppMessageDialogState,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    onDismissAction: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(state.title) },
        text = { Text(state.message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(state.confirmLabel)
            }
        },
        dismissButton = state.dismissLabel?.let { label ->
            {
                TextButton(onClick = onDismissAction) {
                    Text(label)
                }
            }
        },
    )
}

@Composable
internal fun AppChoiceDialog(
    title: String,
    choices: List<String>,
    dismissLabel: String,
    onChoice: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                choices.forEachIndexed { index, choice ->
                    TextButton(
                        onClick = { onChoice(index) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(choice, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel)
            }
        },
    )
}