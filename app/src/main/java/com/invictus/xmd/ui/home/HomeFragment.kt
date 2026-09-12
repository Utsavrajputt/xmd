package com.invictus.xmd.ui.home

import android.content.ClipboardManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.invictus.xmd.R
import com.invictus.xmd.ui.theme.XmdTheme
import com.invictus.xmd.domain.download.ItemStatus
import com.invictus.xmd.repository.QueueRepository
import com.invictus.xmd.ui.MainActivity
import com.invictus.xmd.utils.LinkParser

class HomeFragment : Fragment() {

    // ── Callback interface implemented by MainActivity ────────────────────
    // Torrent-adding is deliberately NOT part of this interface -- the
    // addTorrentButton below opens MainActivity's own showAddTorrentDialog()
    // directly (file selection, metadata fetch, save-path picker etc. all
    // live there now; duplicating that here would mean two dialogs to keep
    // in sync).
    interface Callbacks {
        fun triggerPrepare(lines: List<String>)
        fun triggerDownloadReady()
        fun triggerDownloadDirect(lines: List<String>)
        fun openDownloadsTab()
    }

    // ── State ─────────────────────────────────────────────────────────────
    private var linksText: String by mutableStateOf("")
    private var lastHandledClipboardText: String? = null
    private var pendingClipboardLink: String? by mutableStateOf(null)

    private val clipboardManager by lazy {
        requireContext().getSystemService(ClipboardManager::class.java)
    }
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        checkClipboard()
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        linksText = savedInstanceState?.getString(STATE_LINKS_TEXT).orEmpty()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val items by QueueRepository.items.collectAsStateWithLifecycle()
            val quickStats = HomeQuickStats(
                downloading = items.count { item -> item.status == ItemStatus.DOWNLOADING },
                paused = items.count { item -> item.status == ItemStatus.PAUSED },
                done = items.count { item -> item.status == ItemStatus.DONE },
                failed = items.count { item -> item.status == ItemStatus.FAILED },
            )
            val hasReadyItems = items.any { item -> item.status == ItemStatus.READY }

            XmdTheme {
                HomeScreen(
                    linksText = linksText,
                    onLinksTextChange = { value -> linksText = value },
                    clipboardLink = pendingClipboardLink,
                    quickStats = quickStats,
                    needsPrepare = requiresPrepare(currentInputLines(), hasReadyItems),
                    onClipboardAdd = ::onClipboardAddClicked,
                    onClipboardDismiss = ::dismissClipboardBanner,
                    onPasteClipboard = ::pasteClipboard,
                    onAddTorrent = { (activity as? MainActivity)?.showAddTorrentDialog() },
                    onPrepare = ::onPrepareClicked,
                    onDownload = ::onDownloadClicked,
                    onOpenDownloads = { (activity as? Callbacks)?.openDownloadsTab() },
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_LINKS_TEXT, linksText)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)
        checkClipboard()
    }

    override fun onPause() {
        clipboardManager.removePrimaryClipChangedListener(clipboardListener)
        super.onPause()
    }

    // ── Clipboard ─────────────────────────────────────────────────────────

    /**
     * Clipboard reads only work while the app is in the foreground (Android 10+
     * privacy restriction). We show a banner so the user can tap to add the link
     * rather than auto-adding it silently. Generic: any share/fitgirl link, or
     * a YouTube/Instagram/Facebook link, triggers the banner.
     */
    private fun checkClipboard() {
        val clip = clipboardManager.primaryClip ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).coerceToText(requireContext())
            ?.toString()?.trim().orEmpty()
        if (text.isEmpty() || text == lastHandledClipboardText) return
        val isRecognized = LinkParser.isShareLink(text) ||
            LinkParser.isFitgirlPage(text) ||
            LinkParser.isYoutubeLink(text) ||
            LinkParser.isInstagramLink(text) ||
            LinkParser.isFacebookLink(text)
        if (!isRecognized) return
        if (linksText.contains(text)) return
        if (QueueRepository.current().any { it.sourceUrl == text }) return

        pendingClipboardLink = text
    }

    private fun onClipboardAddClicked() {
        val link = pendingClipboardLink ?: return
        linksText = if (linksText.isBlank()) link else "$linksText\n$link"
        lastHandledClipboardText = link
        dismissClipboardBanner()
    }

    private fun dismissClipboardBanner() {
        lastHandledClipboardText = pendingClipboardLink ?: lastHandledClipboardText
        pendingClipboardLink = null
    }

    /**
     * Manual paste -- always available in the textfield's trailing icon,
     * independent of the auto-detect banner above. Pastes whatever's on the
     * clipboard (any text, not just a recognized share/torrent link).
     */
    private fun pasteClipboard() {
        val clip = clipboardManager.primaryClip ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).coerceToText(requireContext())
            ?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return
        linksText = if (linksText.isBlank()) text else "$linksText\n$text"
    }

    // ── Button state ──────────────────────────────────────────────────────

    private fun requiresPrepare(lines: List<String>, hasReadyItems: Boolean): Boolean {
        // Only FuckingFast/Fitgirl-style share links need the Prepare step
        // (challenge/expand-sources) -- normal/direct/YouTube links show
        // just the plain Download button, no Prepare, no "Ready Files"
        // wording. Empty input still counts as needing Prepare-mode *only*
        // if there are already-prepared READY items waiting (so "Download
        // Ready Files" stays reachable after Prepare clears the text field);
        // otherwise empty input falls back to plain Download.
        if (lines.isEmpty()) return hasReadyItems
        return lines.any {
            LinkParser.isShareLink(it) || LinkParser.isFitgirlPage(it)
        }
    }

    // ── Actions — delegated to MainActivity via Callbacks ─────────────────

    private fun onPrepareClicked() {
        val lines = currentInputLines()
        if (lines.isEmpty()) {
            Toast.makeText(requireContext(), R.string.home_links_required, Toast.LENGTH_SHORT).show()
            return
        }
        (activity as? Callbacks)?.triggerPrepare(lines)
    }

    private fun onDownloadClicked() {
        val lines = currentInputLines()
        val hasReadyItems = QueueRepository.current().any { it.status == ItemStatus.READY }

        if (requiresPrepare(lines, hasReadyItems)) {
            val readyCount = QueueRepository.current()
                .count { it.status == ItemStatus.READY }
            if (readyCount == 0) {
                Toast.makeText(
                    requireContext(),
                    R.string.no_ready_files_yet,
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            (activity as? Callbacks)?.triggerDownloadReady()
            linksText = ""
            return
        }

        // Direct-URL fast-path: skip Prepare entirely
        if (lines.isEmpty()) {
            Toast.makeText(requireContext(), R.string.home_links_required, Toast.LENGTH_SHORT).show()
            return
        }

        // Drop anything that isn't a real link before handing off -- without
        // this, plain garbage text (e.g. "uu") sailed through untouched and
        // got "downloaded" as a literal URL.
        val (valid, invalid) = lines.partition { LinkParser.isSupportedDirectInput(it) }
        if (valid.isEmpty()) {
            Toast.makeText(requireContext(), R.string.home_no_valid_links, Toast.LENGTH_SHORT).show()
            return
        }
        if (invalid.isNotEmpty()) {
            Toast.makeText(
                requireContext(),
                getString(R.string.home_skipped_invalid_links, invalid.size),
                Toast.LENGTH_SHORT
            ).show()
        }
        (activity as? Callbacks)?.triggerDownloadDirect(valid)
        linksText = ""
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun currentInputLines(): List<String> =
        linksText
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    companion object {
        private const val STATE_LINKS_TEXT = "links_text"
    }
}
