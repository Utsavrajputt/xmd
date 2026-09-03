package com.invictus.xmd.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.invictus.xmd.BuildConfig
import com.invictus.xmd.R
import com.invictus.xmd.core.DownloadCategory
import com.invictus.xmd.core.ItemStatus
import com.invictus.xmd.core.LinkParser
import com.invictus.xmd.core.MediaPlatform
import com.invictus.xmd.core.QueueItem
import com.invictus.xmd.core.QueueRepository
import com.invictus.xmd.core.Settings
import com.invictus.xmd.core.YtDlpManager
import com.invictus.xmd.service.DownloadService
import com.invictus.xmd.ui.theme.XmdTheme
import kotlinx.coroutines.launch

/**
 * Entry point for links handed to xmd from another app's "external
 * downloader" hook -- Morphe's Player > External downloads setting,
 * browsers without a download-manager chooser, etc. -- instead of
 * MainActivity.
 *
 * Why this exists instead of just handling ACTION_SEND in MainActivity
 * (which it used to): opening MainActivity brings xmd's whole UI to the
 * foreground, which briefly kicks the caller (YouTube inside Morphe, a
 * browser tab, ...) off-screen. This activity is themed fully transparent
 * (Theme.Xmd.Transparent, see themes.xml) so nothing behind it ever
 * disappears -- it just shows a small bottom sheet (quality picker for
 * YouTube, nothing at all for a plain direct-download link) and finishes
 * itself the moment a choice is made, exactly like YTDLnis/Seal do.
 *
 * The quality-picker UI itself is Compose (see [YtDlpQualitySheet] below).
 * The Activity window remains transparent through the manifest bootstrap
 * theme while [XmdTheme] resolves the selected Kotlin-owned palette for the
 * sheet content.
 *
 * Deliberately narrow in scope: only YouTube/HLS/DASH links (quality
 * picker) and
 * plain generic-download links (queue + start immediately, no UI) are
 * handled invisibly. Share-links that need the Cloudflare-challenge
 * WebView still hand off to MainActivity -- that flow can't happen without
 * a visible screen, so there's no point pretending otherwise.
 */
class ShareReceiverActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent) {
        val url = extractUrl(intent)
        val isHttp = url != null && (url.startsWith("http://") || url.startsWith("https://"))
        val isMagnet = url != null && url.startsWith("magnet:", ignoreCase = true)
        if (url.isNullOrEmpty() || !(isHttp || isMagnet)) {
            Toast.makeText(this, R.string.share_no_link_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        QueueRepository.setLinks(listOf(url))
        val item = QueueRepository.current().firstOrNull { it.sourceUrl == url } ?: run {
            finish()
            return
        }

        when {
            LinkParser.needsYtDlp(url) -> showYtDlpQualitySheet(item)
            LinkParser.isGenericDownloadUrl(url) -> {
                QueueRepository.update(item.id) { it.copy(directUrl = url, status = ItemStatus.READY) }
                DownloadService.start(this)
                Toast.makeText(this, R.string.download_started_confirmation, Toast.LENGTH_SHORT).show()
                finish()
            }
            LinkParser.isShareLink(url) || LinkParser.isFitgirlPage(url) -> {
                // Needs the Cloudflare-challenge WebView -- no way to do
                // that invisibly, so this one case does still hand off to
                // the full app.
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .setAction(Intent.ACTION_SEND)
                        .putExtra(Intent.EXTRA_TEXT, url)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
                finish()
            }
            else -> {
                QueueRepository.update(item.id) {
                    it.copy(
                        status = ItemStatus.FAILED,
                        error = getString(R.string.download_invalid_url_error, url),
                    )
                }
                Toast.makeText(this, R.string.share_unsupported_link, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun extractUrl(intent: Intent): String? = when (intent.action) {
        Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty().let { text ->
            if (text.startsWith("magnet:", ignoreCase = true)) text
            else Regex("""https?://\S+""").find(text)?.value
        }
        Intent.ACTION_VIEW -> intent.data?.toString()
        else -> null
    }?.trim()

    // ── yt-dlp quality bottom sheet (YouTube, or a direct HLS/DASH link) ───

    private fun showYtDlpQualitySheet(item: QueueItem) {
        if (!BuildConfig.HAS_YOUTUBE_SUPPORT) {
            Toast.makeText(this, R.string.share_full_build_required, Toast.LENGTH_LONG).show()
            QueueRepository.update(item.id) {
                it.copy(
                    status = ItemStatus.FAILED,
                    error = getString(R.string.download_full_build_required_error),
                )
            }
            finish()
            return
        }
        if (!YtDlpManager.isInstalled(this)) {
            Toast.makeText(this, R.string.share_ytdlp_install_required, Toast.LENGTH_LONG).show()
            QueueRepository.update(item.id) {
                it.copy(status = ItemStatus.FAILED, error = getString(R.string.ytdlp_not_installed_title))
            }
            finish()
            return
        }

        val options = YtDlpManager.standardQualityOptions(
            isGenericOrHls = !LinkParser.isYoutubeLink(item.sourceUrl)
        )

        setContent {
            XmdTheme {
                YtDlpQualitySheet(
                        title = item.fileName ?: item.sourceUrl,
                        subtitle = item.sourceUrl,
                        options = options,
                        // Same default as the old picker: one below the
                        // top of the ladder (1440p) rather than either
                        // extreme.
                        initialSelectedIndex = 1,
                        onDownload = { chosen ->
                            QueueRepository.update(item.id) {
                                it.copy(
                                    status = ItemStatus.READY,
                                    platform = MediaPlatform.YOUTUBE,
                                    mediaFormatSelector = chosen.formatSelector,
                                    mediaFormatLabel = chosen.label,
                                    category = if (chosen.isAudioOnly) DownloadCategory.MUSIC else DownloadCategory.VIDEOS
                                )
                            }
                            DownloadService.start(this@ShareReceiverActivity)
                            Toast.makeText(
                                this@ShareReceiverActivity,
                                R.string.download_started_confirmation,
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                        onCancelled = {
                            QueueRepository.update(item.id) {
                                it.copy(
                                    status = ItemStatus.FAILED,
                                    error = getString(R.string.download_cancelled_error),
                                )
                            }
                        },
                        onClosed = { finish() },
                )
            }
        }
    }
}

/**
 * Compose replacement for sheet_share_quality.xml + the RadioGroup rows
 * ShareReceiverActivity used to build by hand. [onDownload] fires once with
 * the chosen option when Download is tapped; otherwise (Cancel button,
 * swipe-down, or scrim tap) [onCancelled] fires. [onClosed] always fires
 * last, once the sheet has finished animating away -- the caller's cue to
 * finish() the host Activity.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YtDlpQualitySheet(
    title: String,
    subtitle: String,
    options: List<YtDlpManager.QualityOption>,
    initialSelectedIndex: Int,
    onDownload: (YtDlpManager.QualityOption) -> Unit,
    onCancelled: () -> Unit,
    onClosed: () -> Unit,
) {
    var selectedIndex by remember { mutableIntStateOf(initialSelectedIndex.coerceIn(0, options.lastIndex)) }
    var resolved by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun closeSheet() {
        scope.launch {
            sheetState.hide()
        }.invokeOnCompletion {
            if (!sheetState.isVisible) {
                if (!resolved) onCancelled()
                onClosed()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (!resolved) onCancelled()
            onClosed()
        },
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                options.forEachIndexed { index, option ->
                    QualityOptionRow(
                        option = option,
                        selected = index == selectedIndex,
                        onClick = { selectedIndex = index },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { closeSheet() }) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(
                    onClick = {
                        resolved = true
                        onDownload(options[selectedIndex])
                        closeSheet()
                    },
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text(stringResource(R.string.action_download_direct))
                }
            }
        }
    }
}

@Composable
private fun QualityOptionRow(
    option: YtDlpManager.QualityOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Icon(
            painter = painterResource(if (option.isAudioOnly) XmdIcons.Music else XmdIcons.Video),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = option.label,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
        RadioButton(selected = selected, onClick = onClick)
    }
}
