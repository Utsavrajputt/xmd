@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.invictus.xmd.ui.settings

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.invictus.xmd.R
import com.invictus.xmd.domain.account.AccountException
import com.invictus.xmd.domain.account.AccountManager
import com.invictus.xmd.domain.account.BackupRevision
import com.invictus.xmd.domain.account.BackupScope
import com.invictus.xmd.domain.account.GistSync
import com.invictus.xmd.ui.icons.Icon
import com.invictus.xmd.ui.icons.Icons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Profile-page row that opens the "GitHub & Sync" screen. */
@Composable
fun SettingsGithubEntryCard(onClick: () -> Unit) {
    val account by AccountManager.account.collectAsState()
    SettingsSectionCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Icon(Icons.Cloud, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.github_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    account?.let { stringResource(R.string.account_signed_in_as, it.login) }
                        ?: stringResource(R.string.github_entry_signed_out),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.ChevronRight, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(22.dp))
        }
    }
}

/** Dedicated GitHub screen: account + manual backup, auto-backup, history and "Send to phone". */
@Composable
fun SettingsGithubScreen() {
    val account by AccountManager.account.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsAccountSection()
        if (account != null) {
            AutoBackupCard()
            BackupHistoryCard()
            SendToPhoneCard()
        }
    }
}

@Composable
private fun AutoBackupCard() {
    var enabled by remember { mutableStateOf(AccountManager.autoBackupEnabled()) }
    SettingsSectionCard {
        SwitchSettingRow(
            title = stringResource(R.string.github_auto_backup_title),
            subtitle = stringResource(R.string.github_auto_backup_subtitle),
            checked = enabled,
            onCheckedChange = {
                enabled = it
                AccountManager.setAutoBackupEnabled(it)
            },
        )
    }
}

@Composable
private fun BackupHistoryCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var history by remember { mutableStateOf<List<BackupRevision>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<BackupRevision?>(null) }
    var restoreScope by remember { mutableStateOf(BackupScope()) }

    fun fail(e: Throwable) {
        if (e is CancellationException) throw e
        Toast.makeText(context, if (e is AccountException) e.message.orEmpty() else "Something went wrong", Toast.LENGTH_LONG).show()
    }

    SettingsSectionCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.History, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.github_history_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        val list = history
        if (list == null) {
            OutlinedButton(
                onClick = {
                    loading = true
                    scope.launch {
                        try {
                            history = AccountManager.backupHistory()
                        } catch (e: Throwable) {
                            fail(e)
                        } finally {
                            loading = false
                        }
                    }
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (loading) "…" else stringResource(R.string.github_history_load)) }
        } else if (list.isEmpty()) {
            Text(
                stringResource(R.string.github_history_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            list.forEach { rev ->
                val ago = DateUtils.getRelativeTimeSpanString(rev.backupAtMs, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !busy) {
                            restoreScope = BackupScope()
                            picked = rev
                        }
                        .padding(vertical = 10.dp),
                ) {
                    Text(
                        stringResource(R.string.github_history_row, ago, rev.deviceName ?: "—"),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.CloudDownload, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
                }
            }
        }
    }

    picked?.let { rev ->
        AlertDialog(
            onDismissRequest = { picked = null },
            title = { Text(stringResource(R.string.github_history_restore_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.github_history_restore_message))
                    Spacer(Modifier.height(8.dp))
                    HistoryScopeRow(stringResource(R.string.account_opt_settings), restoreScope.settings) { restoreScope = restoreScope.copy(settings = it) }
                    HistoryScopeRow(stringResource(R.string.account_opt_profile), restoreScope.profile) { restoreScope = restoreScope.copy(profile = it) }
                    HistoryScopeRow(stringResource(R.string.account_opt_bookmarks), restoreScope.bookmarks) { restoreScope = restoreScope.copy(bookmarks = it) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = restoreScope.any,
                    onClick = {
                        picked = null
                        busy = true
                        scope.launch {
                            try {
                                AccountManager.restoreRevision(rev, restoreScope)
                                Toast.makeText(context, R.string.github_history_done, Toast.LENGTH_LONG).show()
                                history = AccountManager.backupHistory() // now includes the safety backup
                            } catch (e: Throwable) {
                                fail(e)
                            } finally {
                                busy = false
                            }
                        }
                    },
                ) { Text(stringResource(R.string.account_restore)) }
            },
            dismissButton = { TextButton(onClick = { picked = null }) { Text(stringResource(R.string.account_cancel)) } },
        )
    }
}

@Composable
private fun HistoryScopeRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SendToPhoneCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val devices by GistSync.devices.collectAsState()
    val received by GistSync.received.collectAsState()
    var inboxOn by remember { mutableStateOf(AccountManager.inboxEnabled()) }
    var link by remember { mutableStateOf("") }
    var target by remember { mutableStateOf(GistSync.TARGET_ALL) }
    var sending by remember { mutableStateOf(false) }

    // Registers this device for others and refreshes the device list + inbox when the screen opens.
    LaunchedEffect(Unit) {
        try {
            GistSync.registerDevice()
            GistSync.poll(force = true)
        } catch (e: AccountException) {
            // Offline or rate-limited: the screen still works with what is known.
        }
    }

    SettingsSectionCard {
        SwitchSettingRow(
            title = stringResource(R.string.github_inbox_enabled_title),
            subtitle = stringResource(R.string.github_inbox_enabled_subtitle),
            checked = inboxOn,
            onCheckedChange = {
                inboxOn = it
                AccountManager.setInboxEnabled(it)
            },
        )
    }

    SettingsSectionCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Share, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.github_send_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.github_send_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.github_this_device, GistSync.deviceName(context)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = link,
            onValueChange = { link = it },
            label = { Text(stringResource(R.string.github_send_field)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = target == GistSync.TARGET_ALL,
                onClick = { target = GistSync.TARGET_ALL },
                label = { Text(stringResource(R.string.github_send_all)) },
            )
            devices.forEach { d ->
                FilterChip(selected = target == d.id, onClick = { target = d.id }, label = { Text(d.name) })
            }
        }
        if (devices.isEmpty()) {
            Text(
                stringResource(R.string.github_send_no_devices),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                if (!GistSync.isSendable(link)) {
                    Toast.makeText(context, R.string.github_send_invalid, Toast.LENGTH_SHORT).show()
                    return@Button
                }
                sending = true
                scope.launch {
                    try {
                        GistSync.send(link, target)
                        link = ""
                        Toast.makeText(context, R.string.github_send_done, Toast.LENGTH_SHORT).show()
                    } catch (e: Throwable) {
                        if (e is CancellationException) throw e
                        Toast.makeText(context, if (e is AccountException) e.message.orEmpty() else "Something went wrong", Toast.LENGTH_LONG).show()
                    } finally {
                        sending = false
                    }
                }
            },
            enabled = !sending && link.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.github_send_button)) }
    }

    if (received.isNotEmpty()) {
        SettingsSectionCard(contentPadding = PaddingValues(16.dp)) {
            Text(stringResource(R.string.github_received_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            received.forEach { item ->
                val ago = DateUtils.getRelativeTimeSpanString(item.atMs, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { runCatching { context.startActivity(GistSync.openIntent(context, item.url)) } }
                        .padding(vertical = 8.dp),
                ) {
                    Text(item.url, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(R.string.github_received_row, item.fromName, ago),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
