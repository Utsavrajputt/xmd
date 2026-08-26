package com.invictus.xmd.core

import java.net.URI

/**
 * Classifies a request URL (+ optional Content-Type, when the response
 * headers are available) as a sniffable media stream for BrowserFragment's
 * "Find videos" chip -- HLS/DASH manifests, which need yt-dlp to actually
 * fetch (resolveYoutube's path, reused as-is for these), and direct
 * video/audio files, which are just a normal DownloadEngine download.
 *
 * Deliberately pure/stateless (no Context, no I/O) so it's cheap to call on
 * every single sub-resource request a page makes, from WebView's own
 * background thread in BrowserFragment.shouldInterceptRequest.
 */
object MediaSniffer {

    enum class Kind { HLS, DASH, DIRECT_VIDEO, DIRECT_AUDIO }

    data class Sniffed(val url: String, val kind: Kind)

    private val HLS_EXT = Regex("""\.m3u8(\?|$)""", RegexOption.IGNORE_CASE)
    private val DASH_EXT = Regex("""\.mpd(\?|$)""", RegexOption.IGNORE_CASE)
    private val VIDEO_EXT = Regex("""\.(mp4|webm|mkv|mov|m4v)(\?|$)""", RegexOption.IGNORE_CASE)
    private val AUDIO_EXT = Regex("""\.(mp3|m4a|aac|ogg|opus)(\?|$)""", RegexOption.IGNORE_CASE)

    // Common CDN/player patterns where the real media URL has no useful
    // extension (signed/tokenized query strings, path-based routing) --
    // matched against the path only, not query params, to avoid false
    // positives from unrelated tracking params containing these words.
    private val HLS_PATH_HINT = Regex("""(^|/)(hls|playlist|master|index)(/|\.m3u8|$)""", RegexOption.IGNORE_CASE)

    /**
     * Classifies purely from the URL -- used on every request since headers
     * usually aren't available without a full round trip. Returns null for
     * anything that isn't clearly media (the common case, so this stays
     * cheap: two regex passes on a String, no allocation beyond that).
     */
    fun classifyUrl(url: String): Sniffed? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (uri.scheme != "http" && uri.scheme != "https") return null
        val path = uri.path.orEmpty()

        return when {
            HLS_EXT.containsMatchIn(url) -> Sniffed(url, Kind.HLS)
            DASH_EXT.containsMatchIn(url) -> Sniffed(url, Kind.DASH)
            VIDEO_EXT.containsMatchIn(url) -> Sniffed(url, Kind.DIRECT_VIDEO)
            AUDIO_EXT.containsMatchIn(url) -> Sniffed(url, Kind.DIRECT_AUDIO)
            HLS_PATH_HINT.containsMatchIn(path) -> Sniffed(url, Kind.HLS)
            else -> null
        }
    }

    /**
     * Refines (or produces) a classification once a response Content-Type
     * is actually known -- catches extensionless/signed CDN URLs the pure
     * URL pass above would miss. Only called from paths that already have
     * the header cheaply available (never worth a dedicated network probe
     * per request just for this).
     */
    fun classifyContentType(url: String, contentType: String?): Sniffed? {
        val type = contentType?.substringBefore(';')?.trim()?.lowercase() ?: return classifyUrl(url)
        val fromUrl = classifyUrl(url)
        if (fromUrl != null) return fromUrl
        return when {
            type == "application/vnd.apple.mpegurl" || type == "application/x-mpegurl" -> Sniffed(url, Kind.HLS)
            type == "application/dash+xml" -> Sniffed(url, Kind.DASH)
            type.startsWith("video/") -> Sniffed(url, Kind.DIRECT_VIDEO)
            type.startsWith("audio/") -> Sniffed(url, Kind.DIRECT_AUDIO)
            else -> null
        }
    }

    /** True for [Kind]s that need yt-dlp (manifest, not a single file). */
    fun Kind.needsQualityPicker(): Boolean = this == Kind.HLS || this == Kind.DASH

    /** Best-effort display name from the URL's last path segment, falling
     *  back to the host when the path is empty/opaque (e.g. a bare "/"). */
    fun guessLabel(url: String): String {
        val uri = runCatching { URI(url) }.getOrNull()
        val last = uri?.path?.trimEnd('/')?.substringAfterLast('/')
        return last?.takeIf { it.isNotBlank() } ?: uri?.host ?: url
    }
}
