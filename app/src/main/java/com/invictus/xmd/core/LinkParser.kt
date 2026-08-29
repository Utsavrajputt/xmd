package com.invictus.xmd.core

import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URI
import java.util.regex.Pattern

/**
 * Parses/validates FuckingFast links and expands fitgirl-repacks source
 * pages into the FuckingFast share links they contain.
 *
 * Kotlin port of ff_downloader/core/resolver.py's non-browser pieces
 * (_file_id, _is_direct_link, _is_share_link, extract_fitgirl_links,
 * expand_sources).
 */
object LinkParser {

    private val SHARE_HOSTS = setOf("fuckingfast.co", "www.fuckingfast.co")
    private const val DIRECT_HOST = "dl.fuckingfast.co"
    private val FITGIRL_HOSTS = setOf("fitgirl-repacks.site", "www.fitgirl-repacks.site")

    // yt-dlp-backed platforms. Kept as its own set (rather than folding into
    // isGenericDownloadUrl) since these need the quality-picker flow instead
    // of a plain resolve -- future platforms (Instagram, Terabox, ...) will
    // likely get their own similar set + isXLink() check alongside this one.
    private val YOUTUBE_HOSTS = setOf(
        "youtube.com", "www.youtube.com", "m.youtube.com",
        "music.youtube.com", "youtu.be"
    )

    private val FILE_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]+")
    private val SHARE_LINK_PATTERN = Pattern.compile(
        "https?://(?:www\\.)?fuckingfast\\.co/(?:f/)?[A-Za-z0-9_-]+[^\\s\"'<>]*",
        Pattern.CASE_INSENSITIVE
    )

    fun isDirectLink(link: String): Boolean {
        val uri = runCatching { URI(link.trim()) }.getOrNull() ?: return false
        return (uri.scheme == "http" || uri.scheme == "https") && uri.host == DIRECT_HOST
    }

    /** True for a magnet: URI (magnet:?xt=urn:btih:...). */
    fun isMagnetLink(link: String): Boolean =
        link.trim().startsWith("magnet:?", ignoreCase = true)

    /**
     * True for an http(s) link that points straight at a .torrent file, or
     * a content:// URI for a .torrent file picked from local storage via
     * the system file picker (HomeFragment's "Pick .torrent file" button --
     * the picker's mime filter already restricts choices to .torrent, so
     * any content:// URI reaching here is trusted to be one).
     */
    fun isTorrentFileLink(link: String): Boolean {
        val uri = runCatching { URI(link.trim()) }.getOrNull() ?: return false
        if (uri.scheme == "content") return true
        if (uri.scheme != "http" && uri.scheme != "https") return false
        val name = uri.path?.substringAfterLast('/')?.substringBefore('?').orEmpty()
        return name.endsWith(".torrent", ignoreCase = true)
    }

    fun isTorrentLink(link: String): Boolean = isMagnetLink(link) || isTorrentFileLink(link)

    /**
     * True for any well-formed http(s) URL that isn't a FuckingFast share
     * link or a fitgirl-repacks page — i.e. something already downloadable
     * as-is (dl.fuckingfast.co, but also R2/S3/other CDN direct links a
     * user might paste after resolving elsewhere). Magnet/.torrent links are
     * "generic" in the same sense — nothing to resolve, DownloadService can
     * pick them up and start immediately — even though they don't use an
     * http(s) scheme themselves (magnet: has no host at all).
     */
    fun isGenericDownloadUrl(link: String): Boolean {
        val trimmed = link.trim()
        if (isTorrentLink(trimmed)) return true
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return false
        if (uri.scheme != "http" && uri.scheme != "https") return false
        if (uri.host.isNullOrBlank()) return false
        if (isShareLink(link)) return false
        if (uri.host in FITGIRL_HOSTS) return false
        if (uri.host in YOUTUBE_HOSTS) return false
        // HLS (.m3u8) / DASH (.mpd) manifests aren't downloadable as-is --
        // the "file" at that URL is just a text playlist pointing at the
        // real media segments, so these need yt-dlp (needsYtDlp) instead of
        // a plain byte-for-byte download like every other generic URL here.
        if (isHlsOrDashLink(link)) return false
        return true
    }

    fun isShareLink(link: String): Boolean {
        val uri = runCatching { URI(link.trim()) }.getOrNull() ?: return false
        return uri.host in SHARE_HOSTS
    }

    fun isFitgirlPage(link: String): Boolean {
        val uri = runCatching { URI(link.trim()) }.getOrNull() ?: return false
        return uri.host in FITGIRL_HOSTS
    }

    /** True for a youtube.com/youtu.be video (or music.youtube.com) link -- routed to the yt-dlp quality-picker flow instead of a normal resolve. */
    fun isYoutubeLink(link: String): Boolean {
        val uri = runCatching { URI(link.trim()) }.getOrNull() ?: return false
        return uri.host in YOUTUBE_HOSTS
    }

    /**
     * True for a direct HLS (.m3u8) or DASH (.mpd) manifest link -- these
     * are streams, not a single file, so (like YouTube) they need yt-dlp to
     * fetch every segment and mux them into one playable file rather than a
     * plain byte-for-byte download. Reuses [MediaSniffer]'s own URL
     * classifier so a link is never treated differently here than it would
     * be by the "Find videos" sniffer sheet.
     */
    fun isHlsOrDashLink(link: String): Boolean {
        val kind = MediaSniffer.classifyUrl(link.trim())?.kind ?: return false
        return kind == MediaSniffer.Kind.HLS || kind == MediaSniffer.Kind.DASH
    }

    /**
     * True for anything that needs the yt-dlp quality-picker flow instead
     * of a normal resolve -- YouTube, plus any plain HLS/DASH link pasted
     * or shared directly (not just ones caught by the in-browser sniffer).
     * Every routing decision (MainActivity, ShareReceiverActivity) should
     * check this rather than isYoutubeLink alone, or a pasted .m3u8 link
     * falls through to isGenericDownloadUrl and gets "downloaded" as the
     * raw manifest text instead of the actual video.
     */
    fun needsYtDlp(link: String): Boolean = isYoutubeLink(link) || isHlsOrDashLink(link)

    /** Extracts the file id from a fuckingfast.co share URL, e.g. fuckingfast.co/f/abc123 -> abc123 */
    fun fileId(link: String): String {
        val uri = URI(link.trim())
        if (uri.host !in SHARE_HOSTS) {
            throw ResolutionError("Unsupported FuckingFast URL: $link")
        }
        var path = uri.path.trim('/')
        path = if (path.startsWith("f/")) {
            path.removePrefix("f/").substringBefore('/')
        } else {
            path.substringBefore('/')
        }
        if (path.isEmpty() || !FILE_ID_PATTERN.matcher(path).matches()) {
            throw ResolutionError("Could not determine file id from: $link")
        }
        return path
    }

    /** Scans a fitgirl-repacks page for embedded fuckingfast.co share links. */
    fun extractFitgirlLinks(url: String, client: OkHttpClient): List<String> {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .build()

        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw ResolutionError("Could not read source page: HTTP ${response.code}")
            }
            response.body?.string() ?: ""
        }

        val candidates = LinkedHashSet<String>()

        val doc = Jsoup.parse(body)
        for (anchor in doc.select("a[href]")) {
            val href = anchor.attr("href").trim()
            if (href.isNotEmpty()) candidates.add(href)
        }

        val matcher = SHARE_LINK_PATTERN.matcher(body)
        while (matcher.find()) candidates.add(matcher.group())

        val unique = candidates
            .map { it.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}') }
            .filter { isShareLink(it) }
            .distinct()

        if (unique.isEmpty()) {
            throw ResolutionError("No FuckingFast share links were found on that page")
        }
        return unique
    }

    /** Expands a list of raw pasted links/pages into concrete fuckingfast links. */
    fun expandSources(links: List<String>, client: OkHttpClient): List<String> {
        val expanded = LinkedHashSet<String>()
        for (raw in links) {
            val link = raw.trim().trim('"').trim('\'')
            if (link.isEmpty()) continue
            val host = runCatching { URI(link).host }.getOrNull()
            if (host in FITGIRL_HOSTS) {
                expanded.addAll(extractFitgirlLinks(link, client))
            } else {
                expanded.add(link)
            }
        }
        return expanded.toList()
    }
}
