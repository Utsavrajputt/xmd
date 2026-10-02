package com.invictus.xmd.utils.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.invictus.xmd.R
import com.invictus.xmd.utils.LinkParser

object MediaPlaybackUtils {

    /**
     * Dispatches a media URL (direct stream, HLS/DASH manifest, audio/video file,
     * or video sharing page) to an external media player app (e.g. VLC, MX Player, MPV, Just Player).
     *
     * Directly starts the VIEW intent so Android displays the standard system disambiguation
     * dialog with "Just once" and "Always" choices (rather than an uncustomizable chooser).
     */
    fun openMediaInExternalPlayer(context: Context, url: String, isAudioOnly: Boolean = false) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return
        val uri = Uri.parse(trimmed)
        val isYt = LinkParser.isYoutubeLink(trimmed)
        val mime = when {
            isYt -> null
            trimmed.contains(".m3u8", ignoreCase = true) -> "application/x-mpegURL"
            trimmed.contains(".mpd", ignoreCase = true) -> "application/dash+xml"
            isAudioOnly || trimmed.contains(".mp3", ignoreCase = true) || trimmed.contains(".m4a", ignoreCase = true) ||
                trimmed.contains(".aac", ignoreCase = true) || trimmed.contains(".flac", ignoreCase = true) ||
                trimmed.contains(".wav", ignoreCase = true) || trimmed.contains(".ogg", ignoreCase = true) ||
                trimmed.contains(".opus", ignoreCase = true) -> "audio/*"
            else -> "video/*"
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            if (mime != null) {
                setDataAndType(uri, mime)
            } else {
                data = uri
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            } catch (e2: Exception) {
                Toast.makeText(context, R.string.no_player_found, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
