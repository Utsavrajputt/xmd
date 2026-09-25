package com.invictus.xmd.utils.media

import android.media.MediaMetadataRetriever
import com.invictus.xmd.domain.download.DownloadCategory
import java.io.File

/**
 * Single source of truth for the "long video -> Movies folder" rule
 * (bro's ask: anything 1h40m+ should land in its own Movies folder
 * instead of Videos). Used both for YouTube items -- where yt-dlp
 * already reports duration up front via probeFormats -- and for
 * direct/generic video links, where duration is only knowable once the
 * file has actually finished downloading (see DownloadService.downloadOne).
 */
object MediaDurationUtils {

    /** 100 minutes. Fixed, not user-configurable. */
    const val MOVIE_DURATION_THRESHOLD_SECONDS = 100 * 60

    fun isMovieLength(durationSeconds: Int?): Boolean =
        durationSeconds != null && durationSeconds >= MOVIE_DURATION_THRESHOLD_SECONDS

    /**
     * Shared category rule for a single YouTube download (audio-only vs.
     * video, and now movie-length video). [durationSeconds] is the
     * duration yt-dlp's probe already reported for this link -- pass null
     * when it isn't known (e.g. a playlist bulk-add, where every entry is
     * deliberately kept in VIDEOS rather than split per-item -- bro asked
     * for the whole playlist to stay together).
     */
    fun resolveYoutubeCategory(isAudioOnly: Boolean, durationSeconds: Int?): DownloadCategory = when {
        isAudioOnly -> DownloadCategory.MUSIC
        isMovieLength(durationSeconds) -> DownloadCategory.MOVIES
        else -> DownloadCategory.VIDEOS
    }

    /**
     * Reads a local video file's duration via MediaMetadataRetriever.
     * Returns null on any failure (corrupt/partial file, non-video
     * content, retriever error) -- callers treat null as "don't
     * reclassify", i.e. the file stays in its originally-detected
     * category rather than blocking/failing the download over this.
     */
    fun probeDurationSeconds(file: File): Int? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.let { millis -> (millis / 1000L).toInt() }
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}
