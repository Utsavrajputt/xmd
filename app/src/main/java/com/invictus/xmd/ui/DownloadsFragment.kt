package com.invictus.xmd.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.invictus.xmd.R
import com.invictus.xmd.core.ItemStatus
import com.invictus.xmd.core.QueueItem
import com.invictus.xmd.core.QueueRepository
import com.invictus.xmd.service.DownloadService
import java.io.File

class DownloadsFragment : Fragment() {

    /** Implemented by MainActivity -- retry needs the resolve/challenge flow that lives there. */
    interface Callbacks {
        fun retryItem(itemId: String)
        fun retryAll()
    }

    private lateinit var adapter: QueueAdapter

    /** Last rendered summary-chip labels -- see the [QueueRepository.items]
     *  observer below for why this exists. */
    private var lastSummaryParts: List<String>? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_downloads, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = QueueAdapter(
            onPauseResume = { item -> onItemPauseResume(item) },
            onCancel      = { item -> DownloadService.cancelItem(requireContext(), item.id) },
            onRetry       = { item -> (activity as? Callbacks)?.retryItem(item.id) },
            onClear       = { item -> QueueRepository.removeItem(item.id) },
            onOpen        = { item -> openFile(item) },
            onLongPress   = { item -> showDownloadOptionsDialog(item) }
        )

        val recycler       = view.findViewById<RecyclerView>(R.id.queueRecycler)
        val emptyContainer = view.findViewById<View>(R.id.emptyContainer)
        val summaryBar     = view.findViewById<View>(R.id.queueSummaryBar)
        val summaryChips   = view.findViewById<ChipGroup>(R.id.queueSummaryChips)
        val cancelBtn       = view.findViewById<MaterialButton>(R.id.cancelButton)
        val clearAllBtn     = view.findViewById<MaterialButton>(R.id.clearAllButton)

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        clearAllBtn.setOnClickListener { QueueRepository.clearFinishedAndFailed() }

        QueueRepository.items.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)

            val isEmpty = list.isEmpty()
            recycler.visibility       = if (isEmpty) View.GONE else View.VISIBLE
            emptyContainer.visibility = if (isEmpty) View.VISIBLE else View.GONE

            if (isEmpty) {
                summaryBar.visibility = View.GONE
                cancelBtn.visibility = View.GONE
                clearAllBtn.visibility = View.GONE
                return@observe
            }

            // ── Cancel All / Retry All -- same button slot, context-switches ──
            val hasActive = list.any {
                it.status == ItemStatus.DOWNLOADING || it.status == ItemStatus.PAUSED ||
                it.status == ItemStatus.RETRYING
            }
            val hasFailed = list.any { it.status == ItemStatus.FAILED }
            val hasClearable = list.any {
                it.status == ItemStatus.DONE || it.status == ItemStatus.FAILED
            }

            when {
                hasActive -> {
                    cancelBtn.visibility = View.VISIBLE
                    cancelBtn.text = getString(R.string.action_cancel_all)
                    val errorColor = resolveThemeColor(com.google.android.material.R.attr.colorError)
                    cancelBtn.setTextColor(errorColor)
                    cancelBtn.strokeColor = ColorStateList.valueOf(errorColor)
                    cancelBtn.setOnClickListener { DownloadService.cancelAll(requireContext()) }
                }
                hasFailed -> {
                    cancelBtn.visibility = View.VISIBLE
                    cancelBtn.text = getString(R.string.action_retry_all)
                    val accentColor = resolveThemeColor(com.google.android.material.R.attr.colorPrimary)
                    cancelBtn.setTextColor(accentColor)
                    cancelBtn.strokeColor = ColorStateList.valueOf(accentColor)
                    cancelBtn.setOnClickListener { (activity as? Callbacks)?.retryAll() }
                }
                else -> cancelBtn.visibility = View.GONE
            }

            clearAllBtn.visibility = if (hasClearable) View.VISIBLE else View.GONE

            val downloading = list.count { it.status == ItemStatus.DOWNLOADING }
            val ready       = list.count { it.status == ItemStatus.READY }
            val resolving   = list.count {
                it.status == ItemStatus.PENDING ||
                it.status == ItemStatus.RESOLVING ||
                it.status == ItemStatus.NEEDS_CHALLENGE
            }
            val paused  = list.count { it.status == ItemStatus.PAUSED }
            val retrying = list.count { it.status == ItemStatus.RETRYING }
            val saving  = list.count { it.status == ItemStatus.SAVING }
            val done    = list.count { it.status == ItemStatus.DONE }
            val failed  = list.count { it.status == ItemStatus.FAILED }

            val parts = mutableListOf<String>()
            if (downloading > 0) parts += "$downloading downloading"
            if (ready > 0)       parts += "$ready ready"
            if (resolving > 0)   parts += "$resolving resolving"
            if (paused > 0)      parts += "$paused paused"
            if (retrying > 0)    parts += "$retrying retrying"
            if (saving > 0)      parts += "$saving saving"
            if (done > 0)        parts += "$done done"
            if (failed > 0)      parts += "$failed failed"

            // This observer fires on every progress tick (up to ~5x/sec per
            // active download) since it's the same QueueRepository.items
            // LiveData the byte-progress updates ride on -- but `parts` only
            // actually changes when an item's *status* crosses a bucket
            // boundary (e.g. downloading -> done), which is rare compared to
            // the tick rate. Rebuilding the chip row from scratch every tick
            // means removeAllViews() + inflating brand-new Chip views (each
            // one resolving theme attributes) purely to redraw the exact same
            // labels, competing with the UI thread for no visible change --
            // skip the rebuild entirely when the labels haven't moved.
            if (parts != lastSummaryParts) {
                lastSummaryParts = parts
                summaryChips.removeAllViews()
                parts.forEach { label -> summaryChips.addView(buildStatChip(label)) }
            }
            summaryBar.visibility = if (parts.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    /** Small filled-tonal stat chip, e.g. "2 downloading", for the queue summary row. */
    private fun buildStatChip(label: String): Chip {
        val chip = Chip(requireContext())
        chip.text = label
        chip.isClickable = false
        chip.isCheckable = false
        chip.isFocusable = false
        chip.chipStrokeWidth = 0f
        chip.setEnsureMinTouchTargetSize(false)
        val tonalBg = resolveThemeColor(com.google.android.material.R.attr.colorSecondaryContainer)
        val tonalFg = resolveThemeColor(com.google.android.material.R.attr.colorOnSecondaryContainer)
        chip.chipBackgroundColor = ColorStateList.valueOf(tonalBg)
        chip.setTextColor(tonalFg)
        chip.textSize = 12f
        return chip
    }

    private fun onItemPauseResume(item: QueueItem) {
        when (item.status) {
            ItemStatus.READY -> DownloadService.start(requireContext())
            ItemStatus.PAUSED -> DownloadService.resumeItem(requireContext(), item.id)
            else -> DownloadService.pauseItem(requireContext(), item.id)
        }
    }

    /**
     * Hands a completed download to an external app (video player, music
     * player, PDF viewer, etc.) via FileProvider -- a raw file:// Uri would
     * throw FileUriExposedException on Android 7+, and even below that,
     * most apps expect a content:// Uri with a real mime type to pick the
     * right handler.
     */
    private fun openFile(item: QueueItem) {
        val path = item.filePath
        val file = path?.let { File(it) }
        if (file == null || !file.exists()) {
            Toast.makeText(requireContext(), R.string.open_file_missing, Toast.LENGTH_SHORT).show()
            return
        }

        val context = requireContext()
        val uri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: IllegalArgumentException) {
            // Thrown when the file's path isn't covered by file_paths.xml --
            // shouldn't happen for anything DownloadService itself wrote,
            // but fails loud+safe instead of crashing if it ever does.
            Toast.makeText(requireContext(), R.string.open_file_missing, Toast.LENGTH_SHORT).show()
            return
        }

        val extension = file.extension.lowercase()
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "*/*"

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooser = Intent.createChooser(intent, getString(R.string.action_open))
        try {
            startActivity(chooser)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.open_file_no_app, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Long-press menu for a DONE/FAILED row -- Open with / Rename /
     * Re-download / Copy download link / Share / Delete, matching the
     * system Downloads app's per-file context menu. Options that need an
     * on-disk file (Open with, Rename) are left out when there isn't one
     * (e.g. a FAILED item that never produced a file) rather than shown
     * and failing with a toast.
     */
    private fun showDownloadOptionsDialog(item: QueueItem) {
        val file = item.filePath?.let { File(it) }?.takeIf { it.exists() }

        val actions = mutableListOf<Pair<String, () -> Unit>>()
        if (file != null) {
            actions += getString(R.string.action_open_with) to { openFile(item) }
            actions += getString(R.string.action_rename) to { showRenameDialog(item, file) }
        }
        actions += getString(R.string.action_redownload) to {
            (activity as? Callbacks)?.retryItem(item.id)
        }
        actions += getString(R.string.action_copy_link) to { copyDownloadLink(item) }
        actions += getString(R.string.action_share) to { shareItem(item, file) }
        actions += getString(R.string.action_delete) to { confirmDeleteItem(item, file) }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(item.fileName ?: item.sourceUrl)
            .setItems(actions.map { it.first }.toTypedArray()) { _, which -> actions[which].second() }
            .show()
    }

    /** Renames the on-disk file and mirrors the new name into the queue
     *  entry (fileName + filePath) so the row, Open, and Share all pick it
     *  up immediately -- QueueRepository.update also persists it to Room. */
    private fun showRenameDialog(item: QueueItem, file: File) {
        val input = EditText(requireContext()).apply {
            setText(file.name)
            setSelection(0, file.nameWithoutExtension.length)
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.action_rename)
            .setView(input)
            .setPositiveButton(R.string.settings_save) { _, _ ->
                val newName = input.text?.toString()?.trim().orEmpty()
                if (newName.isEmpty() || newName == file.name) return@setPositiveButton
                val newFile = File(file.parentFile, newName)
                if (newFile.exists()) {
                    Toast.makeText(requireContext(), R.string.rename_conflict_toast, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (file.renameTo(newFile)) {
                    QueueRepository.update(item.id) { it.copy(fileName = newName, filePath = newFile.absolutePath) }
                } else {
                    Toast.makeText(requireContext(), R.string.rename_failed_toast, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** directUrl is the actual CDN/media link once resolved -- falls back
     *  to the pasted sourceUrl (e.g. a plain direct link) when there's
     *  nothing to resolve. */
    private fun copyDownloadLink(item: QueueItem) {
        val link = item.directUrl ?: item.sourceUrl
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Download link", link))
        Toast.makeText(requireContext(), R.string.link_copied_toast, Toast.LENGTH_SHORT).show()
    }

    /** Shares the actual file via FileProvider when one exists on disk,
     *  otherwise falls back to sharing the download link as plain text. */
    private fun shareItem(item: QueueItem, file: File?) {
        val context = requireContext()
        val intent = if (file != null) {
            val uri = try {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } catch (e: IllegalArgumentException) {
                Toast.makeText(context, R.string.open_file_missing, Toast.LENGTH_SHORT).show()
                return
            }
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
            Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, item.directUrl ?: item.sourceUrl)
            }
        }
        startActivity(Intent.createChooser(intent, getString(R.string.action_share)))
    }

    /** Deletes the on-disk file (if any) and drops the row from the queue.
     *  A FAILED item with no file just removes the row. */
    private fun confirmDeleteItem(item: QueueItem, file: File?) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_download_title)
            .setMessage(item.fileName ?: item.sourceUrl)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                file?.delete()
                QueueRepository.removeItem(item.id)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Resolves a color from the current *theme* (whichever Theme.Xmd.* is
     *  active), not a static @color resource -- so button accents like
     *  "Retry All" follow the selected app theme instead of always being
     *  Default's blue. */
    private fun resolveThemeColor(attrResId: Int): Int {
        val tv = android.util.TypedValue()
        requireContext().theme.resolveAttribute(attrResId, tv, true)
        return tv.data
    }
}
