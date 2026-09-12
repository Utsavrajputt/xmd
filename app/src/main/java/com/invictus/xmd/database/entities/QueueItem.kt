package com.invictus.xmd.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.invictus.xmd.domain.download.ItemStatus
import com.invictus.xmd.domain.download.MediaPlatform
import com.invictus.xmd.domain.download.DownloadCategory
import com.invictus.xmd.database.AppDatabase

/**
 * One entry in the queue. [sourceUrl] is what the user pasted (or a link
 * discovered on a fitgirl-repacks page); [directUrl] is filled in once
 * resolved to a dl.fuckingfast.co URL.
 *
 * Persisted to disk via Room (see database/AppDatabase.kt) so the queue
 * survives the app process being killed/restarted.
 */
@Entity(tableName = "queue_items")
data class QueueItem(
    @PrimaryKey
    val id: String,
    val sourceUrl: String,
    var directUrl: String? = null,
    // The webpage this link was captured from (browser "Add to Downloads"
    // only -- other entry points have no page context and leave this null).
    // Lets an expired generic direct link be recovered IDM-style by
    // re-opening the page and grabbing a fresh link, instead of just
    // re-hitting the same dead URL. See LinkRefetchActivity.
    var pageUrl: String? = null,
    var status: ItemStatus = ItemStatus.PENDING,
    var fileName: String? = null,
    var filePath: String? = null,
    var error: String? = null,
    var bytesDone: Long = 0L,
    var bytesTotal: Long = 0L,
    var speedBps: Double = 0.0,
    var downloadStartedAtMs: Long = 0L,
    var downloadFinishedAtMs: Long = 0L,
    var category: DownloadCategory = DownloadCategory.default(),
    var customSaveDirPath: String? = null,
    var platform: MediaPlatform = MediaPlatform.DIRECT,
    var mediaFormatSelector: String? = null,
    var mediaFormatLabel: String? = null,
    var progressPercent: Int = -1,
    var mediaStatusText: String? = null,
    var selectedFileIndices: String? = null
)
