package com.invictus.xmd.domain.browser

import java.net.URI
import com.invictus.xmd.domain.download.DownloadEngine
import com.invictus.xmd.utils.LinkParser

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
        val uri = com.invictus.xmd.utils.UrlUtils.lenientUri(url) ?: return null
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
     * Extension-only classification -- no [HLS_PATH_HINT] keyword guessing.
     *
     * [classifyUrl]'s path-hint fallback (bare "hls"/"playlist"/"master"/
     * "index" path segments) is a reasonable trade-off when sniffing
     * sub-resource requests made *by a page the user is already watching a
     * video on* (BrowserFragment.shouldInterceptRequest) -- false positives
     * there just add a spurious chip to a "Find videos" sheet.
     *
     * It's the wrong trade-off for classifying a link with zero page
     * context, e.g. one the user pasted or shared in (see
     * [LinkParser.isHlsOrDashLink]). "master"/"index" are extremely common
     * as a bare path segment for reasons that have nothing to do with
     * video -- a GitHub `master`-branch codeload/raw/jsdelivr URL
     * (".../refs/heads/master", ".../repo@master/file.js") or any plain
     * "/index" page, for instance -- and matching them there routes an
     * ordinary file straight into the yt-dlp quality-picker flow instead of
     * downloading it. Restricting to real HLS/DASH file extensions avoids
     * that false-positive class entirely.
     */
    fun classifyUrlStrict(url: String): Sniffed? {
        val uri = com.invictus.xmd.utils.UrlUtils.lenientUri(url) ?: return null
        if (uri.scheme != "http" && uri.scheme != "https") return null

        return when {
            HLS_EXT.containsMatchIn(url) -> Sniffed(url, Kind.HLS)
            DASH_EXT.containsMatchIn(url) -> Sniffed(url, Kind.DASH)
            VIDEO_EXT.containsMatchIn(url) -> Sniffed(url, Kind.DIRECT_VIDEO)
            AUDIO_EXT.containsMatchIn(url) -> Sniffed(url, Kind.DIRECT_AUDIO)
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

    /** What could be learned about a sniffed stream without downloading it. */
    data class MediaInfo(val heights: List<Int>, val sizeBytes: Long?) {
        val isEmpty: Boolean get() = heights.isEmpty() && sizeBytes == null
    }

    private val URL_HEIGHT = Regex("""(?<![0-9])(2160|1440|1080|720|576|480|360|240|144)(?:p|(?=[_./-]))""", RegexOption.IGNORE_CASE)
    private val URL_RES = Regex("""(?<![0-9])\d{3,4}x(\d{3,4})(?![0-9])""")

    /** Best-effort height ("720" from ".../720p/..." or "..._1280x720.mp4") read from the URL alone. */
    fun qualityFromUrl(url: String): Int? {
        URL_RES.find(url)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        return URL_HEIGHT.find(url)?.groupValues?.get(1)?.toIntOrNull()
    }

    /**
     * Light network probe for the "videos found" sheet: HLS master playlists
     * (RESOLUTION=), DASH manifests (height=) and direct files (Content-Length
     * via HEAD). Blocking -- call from Dispatchers.IO. Never throws.
     */
    fun probeInfo(s: Sniffed): MediaInfo = runCatching {
        when (s.kind) {
            Kind.HLS -> {
                val text = fetchText(s.url, 65536)
                val heights = Regex("""RESOLUTION=\d+x(\d+)""").findAll(text)
                    .mapNotNull { it.groupValues[1].toIntOrNull() }.distinct().sortedDescending().toList()
                MediaInfo(heights.ifEmpty { listOfNotNull(qualityFromUrl(s.url)) }, null)
            }
            Kind.DASH -> {
                val text = fetchText(s.url, 131072)
                val heights = Regex("""height="(\d+)"""").findAll(text)
                    .mapNotNull { it.groupValues[1].toIntOrNull() }.distinct().sortedDescending().toList()
                MediaInfo(heights.ifEmpty { listOfNotNull(qualityFromUrl(s.url)) }, null)
            }
            Kind.DIRECT_VIDEO, Kind.DIRECT_AUDIO -> {
                val conn = java.net.URL(s.url).openConnection() as java.net.HttpURLConnection
                try {
                    conn.requestMethod = "HEAD"
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                    val len = conn.contentLengthLong.takeIf { it > 0 }
                    MediaInfo(if (s.kind == Kind.DIRECT_VIDEO) listOfNotNull(qualityFromUrl(s.url)) else emptyList(), len)
                } finally {
                    conn.disconnect()
                }
            }
        }
    }.getOrDefault(MediaInfo(emptyList(), null))

    private fun fetchText(url: String, maxBytes: Int): String {
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        return try {
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.inputStream.use { input ->
                val buf = ByteArray(maxBytes)
                var read = 0
                while (read < maxBytes) {
                    val n = input.read(buf, read, maxBytes - read)
                    if (n < 0) break
                    read += n
                }
                String(buf, 0, read, Charsets.UTF_8)
            }
        } finally {
            conn.disconnect()
        }
    }

    /** "1080p · 720p · 480p · ~45.2 MB" for a [MediaInfo], or null when nothing is known. */
    fun describe(info: MediaInfo): String? {
        val parts = ArrayList<String>()
        if (info.heights.isNotEmpty()) parts += info.heights.joinToString(" \u00b7 ") { "${it}p" }
        info.sizeBytes?.let {
            val mb = it / (1024.0 * 1024.0)
            parts += if (mb >= 1024) "%.2f GB".format(mb / 1024.0) else "%.1f MB".format(mb)
        }
        return parts.joinToString(" \u00b7 ").ifBlank { null }
    }

    /** Best-effort display name from the URL's last path segment, falling
     *  back to the host when the path is empty/opaque (e.g. a bare "/"). */
    fun guessLabel(url: String): String {
        val uri = com.invictus.xmd.utils.UrlUtils.lenientUri(url)
        if (com.invictus.xmd.utils.LinkParser.isYoutubeVideoPage(url)) {
            val id = Regex("[?&]v=([^&#]+)").find(url)?.groupValues?.get(1)
            return if (id.isNullOrBlank()) "YouTube video" else "YouTube video ($id)"
        }
        val last = uri?.path?.trimEnd('/')?.substringAfterLast('/')
        return last?.takeIf { it.isNotBlank() } ?: uri?.host ?: url
    }
}
