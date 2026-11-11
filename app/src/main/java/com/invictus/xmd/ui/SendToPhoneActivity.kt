package com.invictus.xmd.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.invictus.xmd.R
import com.invictus.xmd.domain.account.AccountException
import com.invictus.xmd.domain.account.AccountManager
import com.invictus.xmd.domain.account.GistSync
import com.invictus.xmd.ui.theme.XmdTheme
import kotlinx.coroutines.launch

/**
 * Share-sheet target "Send to my phone": shows the shared link with a device picker and drops it in
 * the user's Gist for the other device to pick up. Transparent, like [ShareReceiverActivity].
 */
class SendToPhoneActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val link = Regex("""(https?://|magnet:)\S+""", RegexOption.IGNORE_CASE).find(text)?.value ?: text.trim()

        if (AccountManager.account.value == null) {
            Toast.makeText(this, R.string.send_to_phone_sign_in, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (!GistSync.isSendable(link)) {
            Toast.makeText(this, R.string.github_send_invalid, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContent {
            XmdTheme {
                val devices by GistSync.devices.collectAsState()
                var target by remember { mutableStateOf(GistSync.TARGET_ALL) }
                LaunchedEffect(Unit) {
                    try {
                        GistSync.poll(force = true, notify = false)
                    } catch (_: AccountException) {
                    }
                }
                AlertDialog(
                    onDismissRequest = { finish() },
                    title = { Text(getString(R.string.send_to_phone_label)) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(link, maxLines = 3)
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = target == GistSync.TARGET_ALL,
                                    onClick = { target = GistSync.TARGET_ALL },
                                    label = { Text(getString(R.string.github_send_all)) },
                                )
                                devices.forEach { d ->
                                    FilterChip(selected = target == d.id, onClick = { target = d.id }, label = { Text(d.name) })
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            lifecycleScope.launch {
                                try {
                                    GistSync.send(link, target)
                                    Toast.makeText(this@SendToPhoneActivity, R.string.github_send_done, Toast.LENGTH_SHORT).show()
                                } catch (e: AccountException) {
                                    Toast.makeText(this@SendToPhoneActivity, e.message.orEmpty(), Toast.LENGTH_LONG).show()
                                }
                                finish()
                            }
                        }) { Text(getString(R.string.github_send_button)) }
                    },
                    dismissButton = { TextButton(onClick = { finish() }) { Text(getString(R.string.account_cancel)) } },
                )
            }
        }
    }
}
