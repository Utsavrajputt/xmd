package com.invictus.xmd.ui.settings

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Observer
import com.invictus.xmd.R
import com.invictus.xmd.database.entities.Shortcut
import com.invictus.xmd.domain.browser.FmhySync
import com.invictus.xmd.preferences.Settings
import com.invictus.xmd.repository.ShortcutRepository
import com.invictus.xmd.ui.icons.Icon
import com.invictus.xmd.ui.icons.Icons

/**
 * Settings -> Browser -> "Website Sources": two cards.
 *  1. Source pack: live saved-site count + Import / Export as two large tiles.
 *  2. FMHY Sync: status line (last sync + what changed), Refresh button with
 *     inline progress, auto-sync switch and "recommended only" switch.
 */
@Composable
fun WebsiteSourcesSection(onImport: () -> Unit, onExport: () -> Unit) {
    val context = LocalContext.current
    val sync by FmhySync.state.collectAsState()

    var count by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val observer = Observer<List<Shortcut>> { count = it.size }
        ShortcutRepository.shortcuts.observeForever(observer)
        onDispose { ShortcutRepository.shortcuts.removeObserver(observer) }
    }

    var autoSync by remember { mutableStateOf(Settings.fmhyAutoSync()) }

    SettingsSectionHeader(title = stringResource(R.string.settings_import_websites))

    // ── Source pack ──────────────────────────────────────────────────
    SettingsSectionCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Globe)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.ws_saved_count, count),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.ws_pack_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ActionButton(Icons.FileOpen, stringResource(R.string.ws_import), onImport, Modifier.weight(1f))
            ActionButton(Icons.Share, stringResource(R.string.ws_export), onExport, Modifier.weight(1f))
        }
    }

    Spacer(Modifier.height(12.dp))

    // ── FMHY sync ────────────────────────────────────────────────────
    SettingsSectionCard {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Sync)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.fmhy_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = statusText(sync.running),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            ActionButton(
                icon = Icons.Sync,
                label = stringResource(if (sync.running) R.string.fmhy_syncing else R.string.fmhy_refresh),
                enabled = !sync.running,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                onClick = {
                    FmhySync.refreshNow { result ->
                        val msg = when (result) {
                            is FmhySync.Result.Done ->
                                context.getString(R.string.fmhy_result_done, result.updated)
                            FmhySync.Result.UpToDate -> context.getString(R.string.fmhy_result_up_to_date)
                            FmhySync.Result.Failed -> context.getString(R.string.fmhy_result_failed)
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                },
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        SwitchSettingRow(
            title = stringResource(R.string.fmhy_auto_title),
            subtitle = stringResource(R.string.fmhy_auto_hint),
            checked = autoSync,
            onCheckedChange = { autoSync = it; Settings.setFmhyAutoSync(it) },
        )
    }
}

@Composable
private fun statusText(running: Boolean): String {
    if (running) return stringResource(R.string.fmhy_syncing)
    val at = Settings.fmhyLastSyncMs()
    val summary = Settings.fmhyLastSummary()
    if (at <= 0L || summary == null) return stringResource(R.string.fmhy_never)
    val ago = DateUtils.getRelativeTimeSpanString(at, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    return stringResource(R.string.fmhy_last, ago, summary)
}

@Composable
private fun IconTile(icon: com.invictus.xmd.ui.icons.AppIcon) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** One button style for every action in this section: same height, same shape, icon + label on one line. */
@Composable
private fun ActionButton(
    icon: com.invictus.xmd.ui.icons.AppIcon,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}
