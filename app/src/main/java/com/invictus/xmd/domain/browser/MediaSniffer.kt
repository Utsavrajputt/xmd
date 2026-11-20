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
        /** Video height in px: reported by the page's player, the CDN itag or the mp4 header. */
        val height: Int? = null,
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

    private val ITAG_HEIGHT = mapOf(
        5 to 240, 6 to 270, 13 to 144, 17 to 144, 18 to 360, 22 to 720, 34 to 360, 35 to 480, 36 to 240,
        37 to 1080, 43 to 360, 44 to 480, 45 to 720, 46 to 1080, 59 to 480, 78 to 480,
    )

    /** Known quality code of a googlevideo (Blogger/YouTube CDN) URL, e.g. itag=22 -> 720. */
    private fun itagHeight(url: String): Int? {
        if (!url.contains("googlevideo.com")) return null
        val itag = Regex("""[?&]itag=(\d+)""").find(url)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        return ITAG_HEIGHT[itag]
    }

    /** Height for a stream: player-reported first, then CDN code, then a number in the URL. */
    fun heightOf(s: Sniffed): Int? = s.height ?: itagHeight(s.url) ?: qualityFromUrl(s.url)

    /** Name hints for links handed to the Add Download dialog, so its generic URL guess can't replace them. */
    private val nameHints = java.util.concurrent.ConcurrentHashMap<String, String>()
    fun registerNameHint(url: String, name: String) {
        if (nameHints.size > 64) nameHints.clear()
        nameHints[url] = name
    }
    fun nameHintFor(url: String): String? = nameHints[url]

    private fun be32(b: ByteArray, o: Int): Long =
        ((b[o].toLong() and 0xFF) shl 24) or ((b[o + 1].toLong() and 0xFF) shl 16) or
            ((b[o + 2].toLong() and 0xFF) shl 8) or (b[o + 3].toLong() and 0xFF)

    /** Largest track height found in the first part of an mp4 (moov/trak/tkhd), or null (e.g. moov at the end). */
    internal fun mp4Height(buf: ByteArray, len: Int): Int? {
        fun scan(start: Int, end: Int, depth: Int): Int? {
            var pos = start
            var best: Int? = null
            while (pos + 8 <= end) {
                var size = be32(buf, pos)
                val type = String(buf, pos + 4, 4, Charsets.ISO_8859_1)
                var header = 8
                if (size == 1L && pos + 16 <= end) {
                    size = (be32(buf, pos + 8) shl 32) or be32(buf, pos + 12); header = 16
                } else if (size == 0L) size = (end - pos).toLong()
                if (size < header) return best
                val boxEnd = (pos + size).coerceAtMost(end.toLong()).toInt()
                val payload = pos + header
                when {
                    type == "tkhd" && payload + 4 <= boxEnd -> {
                        val off = if (buf[payload].toInt() == 1) 88 else 76
                        if (payload + off + 8 <= boxEnd) {
                            val h = (be32(buf, payload + off + 4) ushr 16).toInt()
                            if (h > 0 && (best == null || h > best)) best = h
                        }
                    }
                    (type == "moov" || type == "trak") && depth < 3 ->
                        scan(payload, boxEnd, depth + 1)?.let { if (best == null || it > best!!) best = it }
                }
                if (size > Int.MAX_VALUE || pos + size >= end) break
                pos += size.toInt()
            }
            return best
        }
        return runCatching { scan(0, len, 0) }.getOrNull()
    }

    private fun probeMp4Height(url: String, pageUrl: String?, userAgent: String?, cookie: String?): Int? {
        var conn: java.net.HttpURLConnection? = null
        return try {
            conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.setRequestProperty("User-Agent", userAgent?.takeIf { it.isNotBlank() } ?: "Mozilla/5.0")
            if (!cookie.isNullOrBlank()) conn.setRequestProperty("Cookie", cookie)
            if (!pageUrl.isNullOrBlank()) conn.setRequestProperty("Referer", pageUrl)
            conn.setRequestProperty("Range", "bytes=0-262143")
            if (conn.responseCode !in 200..299) return null
            val buf = ByteArray(262144)
            var n = 0
            conn.inputStream.use { input ->
                while (n < buf.size) {
                    val r = input.read(buf, n, buf.size - n)
                    if (r < 0) break
                    n += r
                }
            }
            mp4Height(buf, n)
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

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
                    MediaInfo(if (s.kind == Kind.DIRECT_VIDEO) listOfNotNull(heightOf(s)) else emptyList(), len)
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
            val known = s.height ?: itagHeight(s.url)
            val height = known ?: if (s.kind == Kind.DIRECT_VIDEO &&
                (mime == null || mime == "video/mp4" || mime == "video/quicktime" || mime == "video/x-m4v")
            ) probeMp4Height(s.url, pageUrl, userAgent, cookie) else null
            s.copy(sizeBytes = total, fileName = name, mime = mime, height = height)
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

    private val KNOWN_MEDIA_EXT = Regex("""\.(mp4|webm|mkv|mov|m4v|mp3|m4a|aac|ogg|opus|m3u8|mpd)$""", RegexOption.IGNORE_CASE)

    /** File extension for a media Content-Type, or the kind's usual default. */
    fun extFor(mime: String?, kind: Kind): String {
        when (mime?.substringBefore(';')?.trim()?.lowercase()) {
            "video/mp4" -> return "mp4"
            "video/webm" -> return "webm"
            "video/x-matroska" -> return "mkv"
            "video/quicktime" -> return "mov"
            "video/3gpp" -> return "3gp"
            "audio/mpeg", "audio/mp3" -> return "mp3"
            "audio/mp4", "audio/x-m4a" -> return "m4a"
            "audio/aac" -> return "aac"
            "audio/ogg", "application/ogg" -> return "ogg"
            "audio/webm" -> return "weba"
            "audio/opus" -> return "opus"
        }
        return if (kind == Kind.DIRECT_AUDIO) "m4a" else "mp4"
    }

    /**
     * A usable file name for a direct stream. Opaque endpoints ("/videoplayback", "/get_video?id=..")
     * have no extension: use the page title plus the extension from Content-Type.
     */
    fun bestName(s: Sniffed, pageTitle: String?): String {
        val hasTitle = !pageTitle.isNullOrBlank()
        // Machine-made CDN names ("AQOrPyh7-80JyK7v89XvYS-E_IK1lz6...") tell the user nothing: when the page
        // has a title, that wins over them.
        s.fileName?.takeIf { it.isNotBlank() && KNOWN_MEDIA_EXT.containsMatchIn(it) && !(hasTitle && looksOpaque(it)) }
            ?.let { return it }
        val path = com.invictus.xmd.utils.UrlUtils.lenientUri(s.url)?.path.orEmpty().trimEnd('/')
        val seg = path.substringAfterLast('/')
        if (KNOWN_MEDIA_EXT.containsMatchIn(seg) && !(hasTitle && looksOpaque(seg))) return seg
        val title = pageTitle?.replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), " ")?.replace(Regex("""\s+"""), " ")
            ?.trim()?.take(80)?.trim('.', ' ')
        val base = if (!title.isNullOrBlank()) title else s.fileName?.takeIf { it.isNotBlank() } ?: seg.ifBlank { "video" }
        return "$base.${extFor(s.mime, s.kind)}"
    }

    /**
     * True for random-looking base names (a long token mixing upper case, lower case and digits, e.g. Instagram
     * / Facebook CDN files). Real titles have spaces or short words, so they stay as they are.
     */
    fun looksOpaque(fileName: String): Boolean {
        val base = fileName.substringBeforeLast('.')
        if (base.length < 16 || base.contains(' ')) return false
        return base.split('-', '_').any { t ->
            t.length >= 8 && t.any { it.isUpperCase() } && t.any { it.isLowerCase() } && t.any { it.isDigit() }
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
