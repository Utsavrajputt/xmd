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

    /**
     * [sizeBytes] / [fileName] / [mime] are filled in by [verify] (null = unknown / not verified).
     * [url] stays the exact URL the page used; the list is deduped by [normalizedKey].
     */
    data class Sniffed(
        val url: String,
        val kind: Kind,
        val sizeBytes: Long? = null,
        val fileName: String? = null,
        val mime: String? = null,
    )

    /**
     * Dedupe key. Direct files and manifests are the same media when only a signed token / cache-buster in
     * the query changed (each refresh used to add another "video" row), so they key on host + path;
     * anything else keys on the full URL minus the fragment.
     */
    fun normalizedKey(s: Sniffed): String {
        val uri = com.invictus.xmd.utils.UrlUtils.lenientUri(s.url) ?: return s.url.substringBefore('#')
        val path = uri.path.orEmpty()
        val byPath = HLS_EXT.containsMatchIn(path) || DASH_EXT.containsMatchIn(path) ||
            VIDEO_EXT.containsMatchIn(path) || AUDIO_EXT.containsMatchIn(path)
        return if (byPath) "${uri.scheme}://${uri.host}${path}" else s.url.substringBefore('#')
    }

    private val HLS_EXT = Regex("""\.m3u8(\?|$)""", RegexOption.IGNORE_CASE)
    private val DASH_EXT = Regex("""\.mpd(\?|$)""", RegexOption.IGNORE_CASE)
    private val VIDEO_EXT = Regex("""\.(mp4|webm|mkv|mov|m4v)(\?|$)""", RegexOption.IGNORE_CASE)
    private val AUDIO_EXT = Regex("""\.(mp3|m4a|aac|ogg|opus)(\?|$)""", RegexOption.IGNORE_CASE)

    // Common CDN/player patterns where the real media URL has no useful
    // extension (signed/tokenized query strings, path-based routing) --
    // matched against the path only, not query params, to avoid false
    // positives from unrelated tracking params containing these words.
    // Only "hls"/"playlist" -- bare "master"/"index" segments are everywhere (GitHub
    // ".../raw/master/icon.svg", "/index/...") and made ordinary images show up as videos.
    // "master.m3u8"/"index.m3u8" are already caught by HLS_EXT.
    private val HLS_PATH_HINT = Regex("""(^|/)(hls|playlist)(/|$)""", RegexOption.IGNORE_CASE)

    // Anything ending in one of these is a plain static asset, never a stream, even when
    // a path segment looks like a streaming hint.
    private val STATIC_ASSET_EXT = Regex(
        """\.(jpe?g|png|gif|webp|avif|svg|ico|bmp|js|mjs|css|json|html?|txt|xml|woff2?|ttf|otf|map|pdf|zip)$""",
        RegexOption.IGNORE_CASE,
    )

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

        // Extensions are matched against the PATH only. Matching the whole URL made
        // "...?redirect=https://x/y.mp4" or tracking params ending in ".mp4" look like media.
        return when {
            HLS_EXT.containsMatchIn(path) -> Sniffed(url, Kind.HLS)
            DASH_EXT.containsMatchIn(path) -> Sniffed(url, Kind.DASH)
            VIDEO_EXT.containsMatchIn(path) -> Sniffed(url, Kind.DIRECT_VIDEO)
            AUDIO_EXT.containsMatchIn(path) -> Sniffed(url, Kind.DIRECT_AUDIO)
            HLS_PATH_HINT.containsMatchIn(path) && !STATIC_ASSET_EXT.containsMatchIn(path) -> Sniffed(url, Kind.HLS)
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

    private val SEGMENT_PATH = Regex("""\.(ts|m4s|cmfv|cmfa|fmp4|aac)(\?|$)|(^|/)(seg|segment|chunk|frag)[-_]?\d*""", RegexOption.IGNORE_CASE)

    /** HLS/DASH media segments (not the media itself) -- never listed. */
    fun isSegment(url: String, contentType: String? = null): Boolean {
        val path = com.invictus.xmd.utils.UrlUtils.lenientUri(url)?.path.orEmpty()
        if (SEGMENT_PATH.containsMatchIn(path)) return true
        val t = contentType?.substringBefore(';')?.trim()?.lowercase()
        return t == "video/mp2t" || t == "video/iso.segment" || t == "audio/mp2t"
    }

    /**
     * Header-based classification for a response the app already holds (DoH proxy path): catches
     * extensionless/signed CDN URLs by Content-Type, ignoring segments and tiny known sizes.
     */
    fun classifyResponse(url: String, contentType: String?, contentLength: Long?): Sniffed? {
        if (isSegment(url, contentType)) return null
        val s = classifyContentType(url, contentType) ?: return null
        if (contentLength != null && contentLength in 1 until MIN_AUDIO_BYTES) return null
        return s
    }

    /** True for [Kind]s that need yt-dlp (manifest, not a single file). */
    fun Kind.needsQualityPicker(): Boolean = this == Kind.HLS || this == Kind.DASH

    /** What could be learned about a sniffed stream without downloading it. */
    data class MediaInfo(val heights: List<Int>, val sizeBytes: Long?, val durationSec: Long? = null) {
        val isEmpty: Boolean get() = heights.isEmpty() && sizeBytes == null && durationSec == null
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
                // A media playlist (not a master one) lists every segment: the total is the duration.
                val duration = if (text.contains("#EXT-X-ENDLIST")) {
                    Regex("""#EXTINF:([0-9.]+)""").findAll(text)
                        .sumOf { it.groupValues[1].toDoubleOrNull() ?: 0.0 }
                        .toLong().takeIf { it > 0 }
                } else null
                MediaInfo(heights.ifEmpty { listOfNotNull(qualityFromUrl(s.url)) }, null, duration)
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

    private const val MIN_VIDEO_BYTES = 100L * 1024
    private const val MIN_AUDIO_BYTES = 50L * 1024
    private val NOT_MEDIA_MIME = Regex(
        """^(text/|image/|font/|application/(json|xml|javascript|x-javascript|xhtml\+xml|pdf|zip|x-shockwave-flash))""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Confirms a sniffed candidate is really media before the page's "videos found" pill counts it
     * (ABDM probes the real response the same way: status, content-type, size). Sent the way the page's
     * own player would -- WebView User-Agent, cookies and the page as Referer -- so protected CDNs answer
     * with the file and not a 403 stub. Blocking: call from Dispatchers.IO. Returns the candidate with
     * size / name / mime filled in, or null when it should be hidden:
     *  - HTTP error, HTML/JSON/image/script content-type;
     *  - manifests whose body isn't really "#EXTM3U" / "<MPD";
     *  - direct files whose known size is tiny (ad stingers, 1x1 stubs, error bodies = the "0.0 MB" rows).
     * A network failure keeps only candidates with a real media extension (a strong signal on its own).
     */
    fun verify(s: Sniffed, pageUrl: String?, userAgent: String?, cookie: String?): Sniffed? {
        if (com.invictus.xmd.utils.LinkParser.isYoutubeLink(s.url)) return s
        val strongExt = classifyUrlStrict(s.url.substringBefore('?').substringBefore('#')) != null
        val manifest = s.kind == Kind.HLS || s.kind == Kind.DASH
        var conn: java.net.HttpURLConnection? = null
        return try {
            conn = java.net.URL(s.url).openConnection() as java.net.HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", userAgent?.takeIf { it.isNotBlank() } ?: "Mozilla/5.0")
            if (!cookie.isNullOrBlank()) conn.setRequestProperty("Cookie", cookie)
            if (!pageUrl.isNullOrBlank()) conn.setRequestProperty("Referer", pageUrl)
            if (!manifest) conn.setRequestProperty("Range", "bytes=0-0")
            val code = conn.responseCode
            if (code !in 200..299) return null
            val mime = conn.contentType?.substringBefore(';')?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

            if (manifest) {
                if (mime != null && mime.startsWith("text/html")) return null
                val head = conn.inputStream.use { input ->
                    val buf = ByteArray(4096)
                    var n = 0
                    while (n < buf.size) {
                        val r = input.read(buf, n, buf.size - n)
                        if (r < 0) break
                        n += r
                    }
                    String(buf, 0, n, Charsets.UTF_8).trimStart('\uFEFF', ' ', '\n', '\r', '\t')
                }
                val ok = if (s.kind == Kind.HLS) head.startsWith("#EXTM3U") else head.contains("<MPD")
                return if (ok) s.copy(mime = mime) else null
            }

            if (mime != null && NOT_MEDIA_MIME.containsMatchIn(mime)) return null
            val total = conn.getHeaderField("Content-Range")
                ?.substringAfterLast('/')?.trim()?.toLongOrNull()
                ?: if (code == 200) conn.contentLengthLong.takeIf { it > 0 } else null
            val min = if (s.kind == Kind.DIRECT_AUDIO) MIN_AUDIO_BYTES else MIN_VIDEO_BYTES
            if (total != null && total < min) return null
            // No type at all and no media extension: nothing says this is media.
            if (mime == null && !strongExt) return null
            val name = com.invictus.xmd.domain.download.DownloadEngine
                .filenameFromContentDisposition(conn.getHeaderField("Content-Disposition"))
            s.copy(sizeBytes = total, fileName = name, mime = mime)
        } catch (e: java.io.IOException) {
            if (strongExt) s else null
        } catch (e: RuntimeException) {
            if (strongExt) s else null
        } finally {
            conn?.disconnect()
        }
    }

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
        info.durationSec?.let { d ->
            parts += if (d >= 3600) "%d:%02d:%02d".format(d / 3600, d % 3600 / 60, d % 60)
            else "%d:%02d".format(d / 60, d % 60)
        }
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
