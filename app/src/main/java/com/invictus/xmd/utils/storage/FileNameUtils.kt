package com.invictus.xmd.utils.storage

import java.io.File
import com.invictus.xmd.database.entities.QueueItem
import com.invictus.xmd.domain.download.DownloadCategory
import com.invictus.xmd.domain.download.ShowDetector
import com.invictus.xmd.preferences.Settings

/**
 * Strategy chosen when adding a download whose destination file conflicts
 * with an existing download item or on-disk file.
 *
 * Mirrors `ab-download-manager`'s `OnDuplicateStrategy`.
 */
enum class OnDuplicateStrategy {
    /** Appends an index (e.g. `file_1.ext`) so the download creates a new separate file. */
    AddNumbered,

    /** Overwrites the existing on-disk file and replaces the conflicting queue item. */
    OverrideDownload,

    /**
     * Keeps the failed/expired download (its id, part files and progress) and just swaps in the new
     * link, exactly like "Fetch from browser" does after a link expires.
     */
    ResumeExisting;

    companion object {
        fun default() = AddNumbered
    }
}

object FileNameUtils {

    /**
     * Computes the target save directory for a file given an optional custom directory
     * override and category, respecting Settings default save location and categorization flag.
     */
    fun resolveDestinationFolder(
        customSaveDir: String?,
        category: DownloadCategory,
        fileName: String? = null,
        sourceUrl: String? = null,
        titleHint: String? = null,
    ): File {
        return if (!customSaveDir.isNullOrBlank()) {
            File(customSaveDir)
        } else {
            val saveRoot = File(Settings.defaultSaveLocation())
            val showFolders = Settings.showNameFoldersEnabled()
            if (category == DownloadCategory.SHOWS && showFolders) {
                // Opt-in: Shows/<Show Name>/ -- one subfolder per series
                // (applies even when folder categorization is disabled).
                File(File(saveRoot, category.folderName), ShowDetector.showFolderName(fileName, sourceUrl, titleHint))
            } else if (Settings.categorizationDisabled()) {
                saveRoot
            } else {
                // Includes SHOWS with the setting off -> flat Shows/.
                File(saveRoot, category.folderName)
            }
        }
    }

    /**
     * Computes the absolute target [File] on disk for a download item.
     */
    fun resolveDestinationFile(
        fileName: String,
        customSaveDir: String?,
        category: DownloadCategory,
        sourceUrl: String? = null,
    ): File {
        return File(resolveDestinationFolder(customSaveDir, category, fileName, sourceUrl), fileName)
    }

    /**
     * Determines the destination file for an existing [QueueItem].
     */
    fun destinationFileOf(item: QueueItem): File? {
        if (!item.filePath.isNullOrBlank()) {
            return File(item.filePath!!)
        }
        val name = item.fileName?.takeUnless { it.isBlank() } ?: return null
        return resolveDestinationFile(name, item.customSaveDirPath, item.category, item.sourceUrl)
    }

    /**
     * A direct download that failed because its link expired (the error is ExpiredLinkException's
     * "...expired or is unavailable..." text, the same check MainActivity uses to show the
     * expired-link dialog), so a fresh link can pick it up where it stopped.
     */
    fun isResumableWithNewLink(item: QueueItem): Boolean =
        item.platform == com.invictus.xmd.domain.download.MediaPlatform.DIRECT &&
            item.status == com.invictus.xmd.domain.download.ItemStatus.FAILED &&
            item.error?.contains("expired", ignoreCase = true) == true

    /**
     * Finds an existing download in the queue whose destination file matches [targetFile].
     */
    fun findConflictingDownload(targetFile: File, queue: List<QueueItem>): QueueItem? {
        val targetPath = targetFile.absoluteFile
        return queue.firstOrNull { item ->
            val existingFile = destinationFileOf(item)?.absoluteFile
            existingFile != null && existingFile == targetPath
        }
    }

    /**
     * Returns whether [targetFile] conflicts with an already-finished/on-disk file
     * or an item currently tracked in [queue].
     */
    fun isDuplicate(targetFile: File, queue: List<QueueItem>): Boolean {
        if (targetFile.exists()) return true
        return findConflictingDownload(targetFile, queue) != null
    }

    /**
     * Generates a numbered file name if [targetFile] exists on disk or matches
     * any file in [activeQueueFiles], matching ab-download-manager's numbered naming.
     * For example, `video.mp4` -> `video_1.mp4`, `video_2.mp4`, etc.
     */
    fun numberedNameIfExists(targetFile: File, activeQueueFiles: Set<File> = emptySet()): String {
        val targetAbs = targetFile.absoluteFile
        val activeAbs = activeQueueFiles.map { it.absoluteFile }.toSet()
        if (!targetAbs.exists() && targetAbs !in activeAbs) {
            return targetFile.name
        }
        val ext = targetFile.extension.takeIf { it.isNotEmpty() }?.let { ".$it" }.orEmpty()
        val baseName = targetFile.nameWithoutExtension
        val parent = targetFile.parentFile

        var counter = 1
        while (counter < 100_000) {
            val candidate = File(parent, "${baseName}_${counter}${ext}").absoluteFile
            if (!candidate.exists() && candidate !in activeAbs) {
                return candidate.name
            }
            counter++
        }
        return "${baseName}_${System.currentTimeMillis()}${ext}"
    }
}
