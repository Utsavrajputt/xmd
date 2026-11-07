@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.invictus.xmd.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.invictus.xmd.R
import com.invictus.xmd.domain.account.AccountException
import com.invictus.xmd.domain.account.AccountManager
import com.invictus.xmd.domain.account.BackupScope
import com.invictus.xmd.domain.account.GitHubAuth
import com.invictus.xmd.ui.icons.Icon
import com.invictus.xmd.ui.icons.Icons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

/** Profile-page card: "Sign in with GitHub" or, when signed in, Backup now / Restore. */
@Composable
fun SettingsAccountSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val account by AccountManager.account.collectAsState()
    val lastBackup by AccountManager.lastBackupMs.collectAsState()

    var deviceCode by remember { mutableStateOf<GitHubAuth.DeviceCode?>(null) }
    var signInJob by remember { mutableStateOf<Job?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    var backupScope by remember { mutableStateOf(runCatching { AccountManager.backupScope() }.getOrDefault(BackupScope())) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    fun fail(e: Throwable) {
        if (e is CancellationException) throw e
        toast(if (e is AccountException) e.message.orEmpty() else "Something went wrong")
    }

    fun startSignIn() {
        signInJob = scope.launch {
            try {
                val code = GitHubAuth.requestDeviceCode()
                deviceCode = code
                val token = GitHubAuth.pollForToken(code)
                AccountManager.completeSignIn(token)
            } catch (e: Throwable) {
                fail(e)
            } finally {
                deviceCode = null
            }
        }
    }

    SettingsSectionCard(contentPadding = PaddingValues(16.dp)) {
        val acc = account
        if (acc == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Cloud, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.account_signed_out_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.account_signed_out_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (GitHubAuth.isConfigured()) {
                Button(onClick = { startSignIn() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.account_sign_in))
                }
            } else {
                Text(
                    stringResource(R.string.account_not_configured),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccountAvatar(acc.avatarUrl)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(acc.name ?: acc.login, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.account_signed_in_as, acc.login),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { AccountManager.signOut() }, enabled = !busy) {
                    Text(stringResource(R.string.account_sign_out))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.account_what_to_back_up),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            ScopeRow(stringResource(R.string.account_opt_settings), backupScope.settings) {
                backupScope = backupScope.copy(settings = it).also(AccountManager::setBackupScope)
            }
            ScopeRow(stringResource(R.string.account_opt_profile), backupScope.profile) {
                backupScope = backupScope.copy(profile = it).also(AccountManager::setBackupScope)
            }
            ScopeRow(stringResource(R.string.account_opt_bookmarks), backupScope.bookmarks) {
                backupScope = backupScope.copy(bookmarks = it).also(AccountManager::setBackupScope)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (lastBackup > 0) {
                    stringResource(
                        R.string.account_last_backup,
                        DateUtils.getRelativeTimeSpanString(lastBackup, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                    )
                } else {
                    stringResource(R.string.account_never_backed_up)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                AccountManager.backupNow(backupScope)
                                toast(context.getString(R.string.account_backup_done))
                            } catch (e: Throwable) {
                                fail(e)
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy && backupScope.any,
                    modifier = Modifier.weight(1f),
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.CloudUpload, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.account_backup_now))
                    }
                }
                OutlinedButton(
                    onClick = { confirmRestore = true },
                    enabled = !busy && backupScope.any,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.CloudDownload, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.account_restore))
                }
            }
        }
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text(stringResource(R.string.account_restore_title)) },
            text = { Text(stringResource(R.string.account_restore_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    busy = true
                    scope.launch {
                        try {
                            AccountManager.restore(backupScope)
                            toast(context.getString(R.string.account_restore_done))
                        } catch (e: Throwable) {
                            fail(e)
                        } finally {
                            busy = false
                        }
                    }
                }) { Text(stringResource(R.string.account_restore)) }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text(stringResource(R.string.account_cancel)) } },
        )
    }

    deviceCode?.let { code ->
        AlertDialog(
            onDismissRequest = {
                signInJob?.cancel()
                deviceCode = null
            },
            title = { Text(stringResource(R.string.account_code_title)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        code.userCode,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 4.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.account_code_hint),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("GitHub code", code.userCode))
                        }) { Text(stringResource(R.string.account_code_copy)) }
                        Button(onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(code.verificationUri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }) { Text(stringResource(R.string.account_code_open)) }
                    }
                    Spacer(Modifier.height(12.dp))
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    signInJob?.cancel()
                    deviceCode = null
                }) { Text(stringResource(R.string.account_cancel)) }
            },
        )
    }
}

@Composable
private fun ScopeRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AccountAvatar(url: String?) {
    val bitmap by produceState<Bitmap?>(null, url) {
        value = if (url == null) null else withContext(Dispatchers.IO) {
            runCatching {
                GitHubAuth.http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                    r.body?.bytes()?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                }
            }.getOrNull()
        }
    }
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp))
        } ?: Icon(Icons.Person, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(26.dp))
    }
}
