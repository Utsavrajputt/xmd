package com.invictus.xmd.domain.status

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import java.io.File

/** The two WhatsApp flavours whose `.Statuses` folder we can read. */
enum class StatusSource(val packageName: String, val rootName: String) {
    WHATSAPP("com.whatsapp", "WhatsApp"),
    BUSINESS("com.whatsapp.w4b", "WhatsApp Business"),
}

data class StatusItem(
    val path: String,
    val name: String,
    val isVideo: Boolean,
    val lastModified: Long,
)

sealed interface SaveResult {
    data object Saved : SaveResult
    data object AlreadySaved : SaveResult
    data object Failed : SaveResult
}

/**
 * Reads WhatsApp / WhatsApp Business statuses straight from disk (relies on
 * the all-files access XMD already holds) and copies them into a plain
 * "WhatsApp Status" folder.
 *
 * Deliberately independent from QueueRepository / the download engine, so a
 * saved status never shows up in the Downloads list.
 *
 * Save location:
 *  1. Android/media/<whatsapp pkg>/<WhatsApp root>/Media/WhatsApp Status
 *  2. DCIM/WhatsApp Status  (fallback if (1) can't be created / written)
 */
object StatusSaverRepository {

    const val SAVE_FOLDER_NAME = "WhatsApp Status"

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
    private val VIDEO_EXTENSIONS = setOf("mp4", "3gp", "mkv", "webm")

    private fun storageRoot(): File = Environment.getExternalStorageDirectory()

    private fun statusDirs(source: StatusSource): List<File> = listOf(
        File(storageRoot(), "Android/media/${source.packageName}/${source.rootName}/Media/.Statuses"),
        File(storageRoot(), "${source.rootName}/Media/.Statuses"),
    )

    private fun primarySaveDir(source: StatusSource): File =
        File(storageRoot(), "Android/media/${source.packageName}/${source.rootName}/Media/$SAVE_FOLDER_NAME")

    private fun dcimSaveDir(): File =
        File(File(storageRoot(), Environment.DIRECTORY_DCIM), SAVE_FOLDER_NAME)

    /** Every folder a saved status may live in (Saved tab shows the union). */
    private fun allSaveDirs(): List<File> =
        StatusSource.entries.map { primarySaveDir(it) } + dcimSaveDir()

    fun hasAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        }

    private fun toItem(file: File): StatusItem? {
        if (!file.isFile) return null
        val ext = file.extension.lowercase()
        val isVideo = ext in VIDEO_EXTENSIONS
        if (!isVideo && ext !in IMAGE_EXTENSIONS) return null
        return StatusItem(file.path, file.name, isVideo, file.lastModified())
    }

    private fun listDirs(dirs: List<File>): List<StatusItem> {
        val seen = HashSet<String>()
        return dirs
            .flatMap { it.listFiles().orEmpty().asList() }
            .mapNotNull(::toItem)
            .filter { seen.add(it.name) }
            .sortedByDescending { it.lastModified }
    }

    fun listRecent(source: StatusSource): List<StatusItem> = listDirs(statusDirs(source))

    fun listSaved(): List<StatusItem> = listDirs(allSaveDirs())

    fun savedNames(): Set<String> = listSaved().mapTo(HashSet()) { it.name }

    fun save(context: Context, source: StatusSource, src: File): SaveResult {
        if (!src.isFile) return SaveResult.Failed
        if (src.name in savedNames()) return SaveResult.AlreadySaved

        for (dir in listOf(primarySaveDir(source), dcimSaveDir())) {
            try {
                if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) continue
                val dest = File(dir, src.name)
                if (copyVerified(src, dest)) {
                    scan(context, dest.path)
                    return SaveResult.Saved
                }
            } catch (e: Exception) {
                // try next location
            }
        }
        return SaveResult.Failed
    }

    fun deleteSaved(context: Context, paths: Collection<String>): Int {
        var deleted = 0
        for (path in paths) {
            val file = File(path)
            if (file.delete()) {
                deleted++
                scan(context, path)
            }
        }
        return deleted
    }

    private fun copyVerified(src: File, dest: File): Boolean {
        return try {
            src.inputStream().use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            if (dest.length() == src.length()) {
                dest.setLastModified(src.lastModified())
                true
            } else {
                dest.delete()
                false
            }
        } catch (e: Exception) {
            dest.delete()
            false
        }
    }

    private fun scan(context: Context, path: String) {
        MediaScannerConnection.scanFile(context.applicationContext, arrayOf(path), null, null)
    }
}
