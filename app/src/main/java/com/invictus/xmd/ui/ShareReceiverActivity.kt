package com.invictus.xmd.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.invictus.xmd.BuildConfig
import com.invictus.xmd.R
import java.util.UUID
import com.invictus.xmd.service.DownloadService
import com.invictus.xmd.ui.theme.XmdTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.libtorrent4j.TorrentInfo
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import com.invictus.xmd.database.entities.QueueItem
import com.invictus.xmd.domain.download.CategoryDetector
import com.invictus.xmd.domain.download.DownloadCategory
import com.invictus.xmd.domain.download.DownloadEngine
import com.invictus.xmd.domain.download.ItemStatus
import com.invictus.xmd.domain.download.MediaPlatform
import com.invictus.xmd.domain.download.YtDlpManager
import com.invictus.xmd.domain.torrent.TorrentSession
import com.invictus.xmd.preferences.Settings
import com.invictus.xmd.repository.QueueRepository
import com.invictus.xmd.ui.downloads.AddDownloadDialog
import com.invictus.xmd.ui.downloads.AddTorrentDialog
import com.invictus.xmd.ui.downloads.TorrentFileRow
import com.invictus.xmd.ui.downloads.TorrentFilesUiState
import com.invictus.xmd.utils.LinkParser
import com.invictus.xmd.utils.storage.FileNameUtils
import com.invictus.xmd.utils.storage.OnDuplicateStrategy
import com.invictus.xmd.utils.storage.StorageUtils

/**
 * Transparent floating activity that intercepts:
 * 1. "Share" -> xmd (ACTION_SEND with text/plain) e.g. from YouTube, browsers, etc.
 * 2. External download manager links (ACTION_VIEW on http/https with wildcard mime) from browsers.
 * 3. Magnet links (ACTION_VIEW on magnet: scheme).
 * 4. Torrent files (ACTION_VIEW on application/x-bittorrent).
 *
 * Instead of bringing the full app (MainActivity) to the foreground, this activity
 * remains transparent and presents either [AddDownloadDialog] or [AddTorrentDialog]
 * directly over the caller's app. When the user taps "Start Download", the task is
 * queued and launched in the background via [DownloadService], and this activity
 * immediately finishes, leaving the user uninterrupted in their current app.
 */
class ShareReceiverActivity : AppCompatActivity() {

    private data class TorrentDialogData(
        val prefillLink: String? = null,
        val prefillTorrentUri: Uri? = null,
        val prefillDisplayName: String? = null,
        val filesState: TorrentFilesUiState = TorrentFilesUiState(),
    )

    private var currentDownloadLink by mutableStateOf<String?>(null)
    private var currentTorrentData by mutableStateOf<TorrentDialogData?>(null)

    private val clipboardManager by lazy { getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    private val filenameClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    private var pendingSaveDirCallback: ((String) -> Unit)? = null

    private val pickSaveDirLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            val path = StorageUtils.resolveTreeUriToPath(treeUri)
            if (path != null) {
                pendingSaveDirCallback?.invoke(path)
            }
        }
        pendingSaveDirCallback = null
    }

    private val pickTorrentFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onTorrentFilePicked(uri)
        }
    }

    private var torrentMetadataJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this) {
            dismissAndFinish()
        }

        setContent {
            XmdTheme {
                currentDownloadLink?.let { initialLink ->
                    AddDownloadDialog(
                        initialLink = initialLink,
                        defaultSavePath = defaultSavePath(),
                        magnetDisplayName = { magnetDisplayName(it) },
                        extractYoutubeFallbackName = { extractYoutubeFallbackName(it) },
                        probeYoutubeTitle = { link -> withContext(Dispatchers.IO) { probeYoutubeTitle(link) } },
                        probeRealFilename = { link ->
                            withContext(Dispatchers.IO) { DownloadEngine.probeRealFilename(filenameClient, link) }
                        },
                        onDetectedTorrentLink = { torrentLink ->
                            currentDownloadLink = null
                            showAddTorrentDialog(prefillLink = torrentLink)
                        },
                        onPickTorrentFile = {
                            currentDownloadLink = null
                            pickTorrentFileLauncher.launch(
                                arrayOf("application/x-bittorrent", "application/octet-stream")
                            )
                        },
                        onCopyLink = { text ->
                            if (text.isNotBlank()) {
                                clipboardManager.setPrimaryClip(
                                    ClipData.newPlainText(getString(R.string.clipboard_download_link_label), text)
                                )
                                Toast.makeText(this, R.string.link_copied_toast, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onPasteRequest = {
                            val clipText = clipboardManager.primaryClip?.getItemAt(0)?.text?.toString()?.trim().orEmpty()
                            if (clipText.isBlank()) {
                                Toast.makeText(this, R.string.clipboard_empty_toast, Toast.LENGTH_SHORT).show()
                                null
                            } else {
                                Toast.makeText(this, R.string.dialog_link_pasted_toast, Toast.LENGTH_SHORT).show()
                                clipText
                            }
                        },
                        onChangeSaveDir = { onPicked ->
                            pendingSaveDirCallback = onPicked
                            pickSaveDirLauncher.launch(null)
                        },
                        onDismiss = {
                            dismissAndFinish()
                        },
                        onStart = { link, name, saveDir, quality, audioFormat, duplicateStrategy ->
                            currentDownloadLink = null
                            when {
                                LinkParser.isTorrentLink(link) -> {
                                    showAddTorrentDialog(prefillLink = link)
                                }
                                LinkParser.isShareLink(link) || LinkParser.isFitgirlPage(link) -> {
                                    // Cloudflare challenge requires visible WebView in MainActivity
                                    startActivity(
                                        Intent(this, MainActivity::class.java)
                                            .setAction(Intent.ACTION_SEND)
                                            .putExtra(Intent.EXTRA_TEXT, link)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                    )
                                    finish()
                                }
                                LinkParser.needsYtDlp(link) -> {
                                    startYoutubeDownload(link, name, saveDir, quality, audioFormat, duplicateStrategy)
                                }
                                LinkParser.isGenericDownloadUrl(link) -> {
                                    startDirectDownload(link, name, saveDir, duplicateStrategy)
                                }
                                else -> {
                                    Toast.makeText(this, getString(R.string.download_invalid_url_error, link), Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                    )
                }

                currentTorrentData?.let { state ->
                    AddTorrentDialog(
                        prefillLink = state.prefillLink,
                        prefillTorrentUri = state.prefillTorrentUri,
                        prefillDisplayName = state.prefillDisplayName,
                        defaultSavePath = defaultSavePath(),
                        filesState = state.filesState,
                        onLinkChanged = { newLink -> onAddTorrentLinkChanged(newLink) },
                        onCopyLink = { text ->
                            if (text.isNotBlank()) {
                                clipboardManager.setPrimaryClip(
                                    ClipData.newPlainText(getString(R.string.clipboard_magnet_link_label), text)
                                )
                                Toast.makeText(this, R.string.torrent_dialog_link_copied_toast, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onPasteRequest = {
                            val clipText = clipboardManager.primaryClip?.getItemAt(0)?.text?.toString()?.trim().orEmpty()
                            if (clipText.isBlank()) {
                                Toast.makeText(this, R.string.clipboard_empty_toast, Toast.LENGTH_SHORT).show()
                                null
                            } else {
                                Toast.makeText(this, R.string.dialog_link_pasted_toast, Toast.LENGTH_SHORT).show()
                                clipText
                            }
                        },
                        onPickTorrentFile = {
                            currentTorrentData = null
                            pickTorrentFileLauncher.launch(
                                arrayOf("application/x-bittorrent", "application/octet-stream")
                            )
                        },
                        onToggleFile = { index -> toggleTorrentFileSelection(index) },
                        onToggleSelectAll = { toggleTorrentSelectAll() },
                        onChangeSaveDir = { onPicked ->
                            pendingSaveDirCallback = onPicked
                            pickSaveDirLauncher.launch(null)
                        },
                        onDismiss = {
                            dismissAndFinish()
                        },
                        onStart = onStart@{ link, name, saveDir, totalFiles, selectedCount, selectedIndices ->
                            if (totalFiles > 0 && selectedCount == 0) {
                                Toast.makeText(this, R.string.torrent_dialog_no_files_selected, Toast.LENGTH_SHORT).show()
                                return@onStart
                            }
                            val uri = state.prefillTorrentUri
                            currentTorrentData = null
                            if (uri != null) {
                                startTorrentFileDownload(uri, name, saveDir, selectedIndices)
                            } else if (!LinkParser.isTorrentLink(link)) {
                                Toast.makeText(this, R.string.torrent_dialog_invalid_link, Toast.LENGTH_SHORT).show()
                                finish()
                            } else {
                                startTorrentMagnetDownload(link, name, saveDir, selectedIndices)
                            }
                        },
                    )
                }
            }
        }

        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent) {
        val action = intent.action
        val dataUri = intent.data

        if (action == Intent.ACTION_VIEW && dataUri != null) {
            val scheme = dataUri.scheme.orEmpty().lowercase()
            if (scheme == "magnet") {
                currentDownloadLink = null
                showAddTorrentDialog(prefillLink = dataUri.toString())
                return
            }
            if (scheme == "content" || scheme == "file") {
                val displayName = queryDisplayName(dataUri)
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        dataUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                currentDownloadLink = null
                showAddTorrentDialog(prefillTorrentUri = dataUri, prefillDisplayName = displayName)
                return
            }
            val urlString = dataUri.toString().trim()
            if (urlString.isNotBlank()) {
                if (LinkParser.isTorrentLink(urlString)) {
                    currentDownloadLink = null
                    showAddTorrentDialog(prefillLink = urlString)
                } else {
                    currentTorrentData = null
                    currentDownloadLink = urlString
                }
                return
            }
        }

        if (action == Intent.ACTION_SEND) {
            val streamUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            if (streamUri != null) {
                val displayName = queryDisplayName(streamUri)
                currentDownloadLink = null
                showAddTorrentDialog(prefillTorrentUri = streamUri, prefillDisplayName = displayName)
                return
            }

            val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
            val url = if (text.startsWith("magnet:", ignoreCase = true)) {
                text
            } else {
                Regex("""https?://\S+""").find(text)?.value ?: text.takeIf {
                    it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
                }
            }

            if (!url.isNullOrBlank()) {
                if (LinkParser.isTorrentLink(url)) {
                    currentDownloadLink = null
                    showAddTorrentDialog(prefillLink = url)
                } else {
                    currentTorrentData = null
                    currentDownloadLink = url
                }
                return
            }
        }

        Toast.makeText(this, R.string.share_no_link_found, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun dismissAndFinish() {
        torrentMetadataJob?.cancel()
        currentDownloadLink = null
        currentTorrentData = null
        finish()
    }

    private fun defaultSavePath(): String = Settings.defaultSaveLocation()

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
        }
    }.getOrNull()

    private fun onTorrentFilePicked(uri: Uri) {
        val displayName = queryDisplayName(uri)
        if (displayName != null && !displayName.endsWith(".torrent", ignoreCase = true)) {
            Toast.makeText(this, R.string.torrent_file_invalid_type, Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        showAddTorrentDialog(prefillTorrentUri = uri, prefillDisplayName = displayName)
    }

    private fun showAddTorrentDialog(
        prefillLink: String? = null,
        prefillTorrentUri: Uri? = null,
        prefillDisplayName: String? = null,
    ) {
        torrentMetadataJob?.cancel()
        currentTorrentData = TorrentDialogData(
            prefillLink = prefillLink,
            prefillTorrentUri = prefillTorrentUri,
            prefillDisplayName = prefillDisplayName,
        )
        when {
            prefillTorrentUri != null -> {
                lifecycleScope.launch {
                    val ti = withContext(Dispatchers.IO) {
                        runCatching {
                            contentResolver.openInputStream(prefillTorrentUri)?.use { input ->
                                TorrentInfo.bdecode(input.readBytes())
                            }
                        }.getOrNull()
                    }
                    if (ti != null) applyTorrentInfoToDialog(ti)
                }
            }
            !prefillLink.isNullOrBlank() && LinkParser.isMagnetLink(prefillLink) -> {
                loadTorrentMetadataForMagnet(prefillLink)
            }
        }
    }

    private fun onAddTorrentLinkChanged(link: String) {
        val state = currentTorrentData ?: return
        currentTorrentData = state.copy(prefillLink = link)
        if (state.prefillTorrentUri == null && LinkParser.isMagnetLink(link)) {
            loadTorrentMetadataForMagnet(link)
        } else if (!LinkParser.isMagnetLink(link)) {
            torrentMetadataJob?.cancel()
            currentTorrentData = currentTorrentData?.copy(filesState = TorrentFilesUiState())
        }
    }

    private fun loadTorrentMetadataForMagnet(link: String) {
        torrentMetadataJob?.cancel()
        currentTorrentData = currentTorrentData?.copy(filesState = TorrentFilesUiState(loading = true))
        torrentMetadataJob = lifecycleScope.launch {
            val tempDir = File(cacheDir, "torrent_meta")
            val bytes = withContext(Dispatchers.IO) {
                TorrentSession.fetchMetadata(link, timeoutSeconds = 25, tempDir)
            }
            val ti = bytes?.let { runCatching { TorrentInfo.bdecode(it) }.getOrNull() }
            if (ti != null) {
                applyTorrentInfoToDialog(ti)
            } else {
                currentTorrentData = currentTorrentData?.copy(filesState = TorrentFilesUiState(error = true))
            }
        }
    }

    private fun applyTorrentInfoToDialog(ti: TorrentInfo) {
        val count = ti.numFiles()
        val entries = (0 until count).map { idx ->
            TorrentFileRow(
                index = idx,
                path = runCatching { ti.files().filePath(idx) }.getOrNull() ?: "File ${idx + 1}",
                sizeBytes = runCatching { ti.files().fileSize(idx) }.getOrNull() ?: 0L,
                isSelected = true,
            )
        }
        val detectedName = ti.name().takeIf { it.isNotBlank() }
        currentTorrentData = currentTorrentData?.copy(
            filesState = TorrentFilesUiState(files = entries, magnetDetectedName = detectedName)
        )
    }

    private fun toggleTorrentFileSelection(index: Int) {
        val state = currentTorrentData ?: return
        val updated = state.filesState.files.map { if (it.index == index) it.copy(isSelected = !it.isSelected) else it }
        currentTorrentData = state.copy(filesState = state.filesState.copy(files = updated))
    }

    private fun toggleTorrentSelectAll() {
        val state = currentTorrentData ?: return
        val allSelected = state.filesState.files.isNotEmpty() && state.filesState.files.all { it.isSelected }
        val updated = state.filesState.files.map { it.copy(isSelected = !allSelected) }
        currentTorrentData = state.copy(filesState = state.filesState.copy(files = updated))
    }

    private fun startDirectDownload(
        link: String,
        name: String?,
        customSaveDirPath: String?,
        duplicateStrategy: OnDuplicateStrategy? = null,
    ) {
        val category = CategoryDetector.detect(link, hint = name)
        val resolvedName = name?.takeUnless { it.isBlank() }
            ?: DownloadEngine.filenameFromLink(link).ifBlank { DownloadEngine.filenameFromUrl(link) }
        val targetFile = FileNameUtils.resolveDestinationFile(resolvedName, customSaveDirPath, category)

        val finalName = when (duplicateStrategy) {
            OnDuplicateStrategy.OverrideDownload -> {
                QueueRepository.removeDuplicatesOf(targetFile)
                if (targetFile.exists()) targetFile.delete()
                resolvedName
            }
            OnDuplicateStrategy.AddNumbered -> {
                val activeFiles = QueueRepository.current().mapNotNull { FileNameUtils.destinationFileOf(it) }.toSet()
                FileNameUtils.numberedNameIfExists(targetFile, activeFiles)
            }
            null -> {
                if (FileNameUtils.isDuplicate(targetFile, QueueRepository.current())) {
                    val activeFiles = QueueRepository.current().mapNotNull { FileNameUtils.destinationFileOf(it) }.toSet()
                    FileNameUtils.numberedNameIfExists(targetFile, activeFiles)
                } else {
                    resolvedName
                }
            }
        }

        val newItem = QueueItem(
            id = UUID.randomUUID().toString(),
            sourceUrl = link,
            directUrl = link,
            status = ItemStatus.READY,
            fileName = finalName,
            customSaveDirPath = customSaveDirPath,
            category = category,
        )
        QueueRepository.enqueue(newItem)
        DownloadService.start(this)
        Toast.makeText(this, R.string.download_started_confirmation, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun startYoutubeDownload(
        link: String,
        name: String?,
        customSaveDirPath: String?,
        chosenQuality: YtDlpManager.QualityOption?,
        chosenAudioPreset: Settings.AudioFormatPreset,
        duplicateStrategy: OnDuplicateStrategy? = null,
    ) {
        if (!BuildConfig.HAS_YOUTUBE_SUPPORT) {
            Toast.makeText(this, R.string.share_full_build_required, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (!YtDlpManager.isInstalled(this)) {
            Toast.makeText(this, R.string.share_ytdlp_install_required, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val quality = chosenQuality ?: run {
            val options = YtDlpManager.standardQualityOptions(isGenericOrHls = !LinkParser.isYoutubeLink(link))
            options.firstOrNull { it.label.startsWith("1080p") } ?: options.firstOrNull()
        }

        if (quality == null) {
            Toast.makeText(this, R.string.download_quality_unavailable, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (quality.isAudioOnly) {
            Settings.setPresetAudioFormat(chosenAudioPreset)
        }

        val formatLabel = if (quality.isAudioOnly) {
            "Audio (${chosenAudioPreset.name})"
        } else {
            quality.label
        }

        val category = if (quality.isAudioOnly) DownloadCategory.MUSIC else DownloadCategory.VIDEOS
        val resolvedName = name?.takeUnless { it.isBlank() } ?: extractYoutubeFallbackName(link)
        val targetFile = FileNameUtils.resolveDestinationFile(resolvedName, customSaveDirPath, category)

        val finalName = when (duplicateStrategy) {
            OnDuplicateStrategy.OverrideDownload -> {
                QueueRepository.removeDuplicatesOf(targetFile)
                if (targetFile.exists()) targetFile.delete()
                resolvedName
            }
            OnDuplicateStrategy.AddNumbered -> {
                val activeFiles = QueueRepository.current().mapNotNull { FileNameUtils.destinationFileOf(it) }.toSet()
                FileNameUtils.numberedNameIfExists(targetFile, activeFiles)
            }
            null -> {
                if (FileNameUtils.isDuplicate(targetFile, QueueRepository.current())) {
                    val activeFiles = QueueRepository.current().mapNotNull { FileNameUtils.destinationFileOf(it) }.toSet()
                    FileNameUtils.numberedNameIfExists(targetFile, activeFiles)
                } else {
                    resolvedName
                }
            }
        }

        val newItem = QueueItem(
            id = UUID.randomUUID().toString(),
            sourceUrl = link,
            status = ItemStatus.READY,
            platform = MediaPlatform.YOUTUBE,
            mediaFormatSelector = quality.formatSelector,
            mediaFormatLabel = formatLabel,
            category = category,
            fileName = finalName,
            customSaveDirPath = customSaveDirPath,
        )
        QueueRepository.enqueue(newItem)
        DownloadService.start(this)
        Toast.makeText(this, R.string.download_started_confirmation, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun startTorrentMagnetDownload(
        link: String,
        name: String?,
        customSaveDirPath: String?,
        selectedIndices: String?,
        duplicateStrategy: OnDuplicateStrategy? = null,
    ) {
        val resolvedName = name?.takeUnless { it.isBlank() } ?: magnetDisplayName(link) ?: "Magnet Download"
        val category = CategoryDetector.detect(link, hint = resolvedName)
        val targetFile = FileNameUtils.resolveDestinationFile(resolvedName, customSaveDirPath, category)

        val finalName = when (duplicateStrategy) {
            OnDuplicateStrategy.OverrideDownload -> {
                QueueRepository.removeDuplicatesOf(targetFile)
                if (targetFile.exists()) targetFile.delete()
                resolvedName
            }
            OnDuplicateStrategy.AddNumbered -> {
                val activeFiles = QueueRepository.current().mapNotNull { FileNameUtils.destinationFileOf(it) }.toSet()
                FileNameUtils.numberedNameIfExists(targetFile, activeFiles)
            }
            null -> {
                if (FileNameUtils.isDuplicate(targetFile, QueueRepository.current())) {
                    val activeFiles = QueueRepository.current().mapNotNull { FileNameUtils.destinationFileOf(it) }.toSet()
                    FileNameUtils.numberedNameIfExists(targetFile, activeFiles)
                } else {
                    resolvedName
                }
            }
        }

        val newItem = QueueItem(
            id = UUID.randomUUID().toString(),
            sourceUrl = link,
            directUrl = link,
            status = ItemStatus.READY,
            fileName = finalName,
            customSaveDirPath = customSaveDirPath,
            selectedFileIndices = selectedIndices,
            category = category,
        )
        QueueRepository.enqueue(newItem)
        DownloadService.start(this)
        Toast.makeText(this, R.string.download_started_confirmation, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun startTorrentFileDownload(
        uri: Uri,
        name: String?,
        customSaveDirPath: String?,
        selectedIndices: String?,
        duplicateStrategy: OnDuplicateStrategy? = null,
    ) {
        val link = uri.toString()
        val resolvedName = name?.takeUnless { it.isBlank() } ?: "Torrent Download"
        val category = CategoryDetector.detect(link, hint = resolvedName)
        val targetFile = FileNameUtils.resolveDestinationFile(resolvedName, customSaveDirPath, category)

        val finalName = when (duplicateStrategy) {
            OnDuplicateStrategy.OverrideDownload -> {
                QueueRepository.removeDuplicatesOf(targetFile)
                if (targetFile.exists()) targetFile.delete()
                resolvedName
            }
            OnDuplicateStrategy.AddNumbered -> {
                val activeFiles = QueueRepository.current().mapNotNull { FileNameUtils.destinationFileOf(it) }.toSet()
                FileNameUtils.numberedNameIfExists(targetFile, activeFiles)
            }
            null -> {
                if (FileNameUtils.isDuplicate(targetFile, QueueRepository.current())) {
                    val activeFiles = QueueRepository.current().mapNotNull { FileNameUtils.destinationFileOf(it) }.toSet()
                    FileNameUtils.numberedNameIfExists(targetFile, activeFiles)
                } else {
                    resolvedName
                }
            }
        }

        val newItem = QueueItem(
            id = UUID.randomUUID().toString(),
            sourceUrl = link,
            directUrl = link,
            status = ItemStatus.READY,
            fileName = finalName,
            customSaveDirPath = customSaveDirPath,
            selectedFileIndices = selectedIndices,
            category = category,
        )
        QueueRepository.enqueue(newItem)
        DownloadService.start(this)
        Toast.makeText(this, R.string.download_started_confirmation, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun extractYoutubeFallbackName(url: String): String {
        val clean = url.trim()
        val id = when {
            clean.contains("youtu.be/") -> clean.substringAfter("youtu.be/").substringBefore("?").substringBefore("/")
            clean.contains("/shorts/") -> clean.substringAfter("/shorts/").substringBefore("?").substringBefore("/")
            clean.contains("v=") -> Regex("""[?&]v=([^&]+)""").find(clean)?.groupValues?.get(1)
            else -> null
        }
        return if (!id.isNullOrBlank()) "YouTube ($id)" else "YouTube Video"
    }

    private fun probeYoutubeTitle(url: String): String? = runCatching {
        val cleanUrl = url.trim()
        val encoded = URLEncoder.encode(cleanUrl, "UTF-8")
        val req = Request.Builder()
            .url("https://www.youtube.com/oembed?url=$encoded&format=json")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .build()
        filenameClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val body = resp.body?.string() ?: return@use null
            JSONObject(body).optString("title").takeIf { it.isNotBlank() }
        }
    }.getOrNull()

    private fun magnetDisplayName(link: String): String? {
        if (!LinkParser.isMagnetLink(link)) return null
        val dn = Regex("[?&]dn=([^&]+)").find(link)?.groupValues?.get(1) ?: return null
        return runCatching { Uri.decode(dn.replace('+', ' ')) }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}
