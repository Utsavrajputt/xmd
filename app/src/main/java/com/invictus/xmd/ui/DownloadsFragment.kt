package com.invictus.xmd.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.invictus.xmd.R
import com.invictus.xmd.core.ItemStatus
import com.invictus.xmd.core.QueueItem
import com.invictus.xmd.core.QueueRepository
import com.invictus.xmd.service.DownloadService
import com.invictus.xmd.ui.theme.XmdTheme
import java.io.File

/**
 * Rendering moved to Compose ([DownloadsScreen]); this Fragment hosts a
 * [ComposeView] instead of inflating fragment_downloads.xml + QueueAdapter.
 * Business logic that needs a real Context/Intent (opening files, sharing,
 * clipboard, renaming on disk) stays here and is wired into DownloadsScreen
 * via lambdas, same pattern SettingsActivity's AboutRoute/AboutScreen uses.
 */
class DownloadsFragment : Fragment() {

    /** Implemented by MainActivity -- retry needs the resolve/challenge flow that lives there. */
    interface Callbacks {
        fun retryItem(itemId: String)
        fun retryAll()
    }

    // Search query comes from MainActivity's in-header search box via
    // setFilterQuery(), same as before -- held as Compose state so the
    // screen recomposes immediately when it changes.
    private var queryState by mutableStateOf("")

    /** Called by MainActivity when the in-header search query updates. */
    fun setFilterQuery(query: String) {
        queryState = query
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val items by QueueRepository.items.collectAsStateWithLifecycle()
            XmdTheme {
                DownloadsScreen(
                    items = items,
                    query = queryState,
                    onPauseResume = { onItemPauseResume(it) },
                    onCancel = { DownloadService.cancelItem(requireContext(), it.id) },
                    onRetry = { (activity as? Callbacks)?.retryItem(it.id) },
                    onClear = { QueueRepository.removeItem(it.id) },
                    onOpen = { openFile(it) },
                    onOpenWith = { openFile(it) },
                    onRename = { item, newName -> renameFile(item, newName) },
                    onCopyLink = { copyDownloadLink(it) },
                    onShare = { item -> shareItem(item, item.filePath?.let(::File)?.takeIf { it.exists() }) },
                    onDelete = { deleteItem(it) },
                    onCancelAll = { DownloadService.cancelAll(requireContext()) },
                    onRetryAll = { (activity as? Callbacks)?.retryAll() },
                    onClearAllFinished = { QueueRepository.clearFinishedAndFailed() },
                )
            }
        }
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

    /** Renames the on-disk file and mirrors the new name into the queue
     *  entry (fileName + filePath) so the row, Open, and Share all pick it
     *  up immediately -- QueueRepository.update also persists it to Room. */
    private fun renameFile(item: QueueItem, newName: String) {
        val file = item.filePath?.let { File(it) } ?: return
        val newFile = File(file.parentFile, newName)
        if (newFile.exists()) {
            Toast.makeText(requireContext(), R.string.rename_conflict_toast, Toast.LENGTH_SHORT).show()
            return
        }
        if (file.renameTo(newFile)) {
            QueueRepository.update(item.id) { it.copy(fileName = newName, filePath = newFile.absolutePath) }
        } else {
            Toast.makeText(requireContext(), R.string.rename_failed_toast, Toast.LENGTH_SHORT).show()
        }
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
    private fun deleteItem(item: QueueItem) {
        item.filePath?.let { File(it) }?.delete()
        QueueRepository.removeItem(item.id)
    }
}
