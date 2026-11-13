package com.invictus.xmd.domain.torrent

import com.invictus.xmd.domain.download.DownloadCategory
import com.invictus.xmd.domain.download.ShowDetector
import com.invictus.xmd.preferences.Settings
import java.io.File

/**
 * Where a torrent's content is saved. Shared by the download service (to start the download) and
 * the leftovers cleaner (to find what a cancelled/failed torrent left behind), so the two can never
 * disagree about the folder.
 */
object TorrentPaths {
    fun resolveBaseDir(customSaveDirPath: String?, torrentName: String?): File {
        val defaultBaseDir = if (!customSaveDirPath.isNullOrBlank()) {
            // Picked via the Editor dialog's Advanced -> Change: overrides both the settings
            // default and the Torrents-subfolder convention below.
            File(customSaveDirPath)
        } else {
            val saveRoot = File(Settings.defaultSaveLocation())
            if (Settings.categorizationDisabled()) {
                saveRoot
            } else {
                // Own subfolder rather than DownloadCategory.folderName -- a torrent is very often a
                // multi-file batch that belongs together as one folder.
                File(saveRoot, "Torrents")
            }
        }

        // An episode or season pack is grouped under Shows/ (with a <Show Name>/ subfolder only when
        // "Show name folders" is on). Custom save dirs are left untouched.
        val showFolders = Settings.showNameFoldersEnabled()
        return if (customSaveDirPath.isNullOrBlank() &&
            (!Settings.categorizationDisabled() || showFolders) &&
            ShowDetector.isEpisodeOrSeasonPack(torrentName)
        ) {
            val showsRoot = File(Settings.defaultSaveLocation(), DownloadCategory.SHOWS.folderName)
            if (showFolders) File(showsRoot, ShowDetector.showFolderName(torrentName)) else showsRoot
        } else {
            defaultBaseDir
        }
    }
}
