package com.invictus.xmd.domain.download

import java.net.URI
import java.util.Locale

/**
 * Detects whether a file name looks like an episode of a web series / TV
 * show and works out which show it belongs to, so [CategoryDetector] can
 * route it to `Shows/<Show Name>/` instead of Videos/Movies.
 *
 * Everything here is a pure function of (fileName, sourceUrl) so the same
 * folder is computed at queue time, in the duplicate check and when the
 * download finishes -- nothing extra has to be persisted on the queue item.
 */
object ShowDetector {

    /** Folder used when an episode marker is present but no show name could be found. */
    const val UNSORTED_FOLDER = "Unsorted"

    private val VIDEO_EXT = setOf(
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "mpg", "mpeg", "3gp", "ts"
    )
    private val SUBTITLE_EXT = setOf("srt", "ass", "ssa", "vtt", "sub")

    // Group 1 of the anime pattern is the episode number; values that are far
    // more likely to be a resolution / codec than an episode number are ignored.
    private val NOT_EPISODE_NUMBERS = setOf(264, 265, 480, 576, 720, 1080, 2160)

    // In "1x264" style markers the part after the x is also checked against
    // these: x264 / x265 are codecs and 480 / 576 / 720 are video heights
    // ("DDP5.1x264", "...x720"), never episode numbers.
    private val NOT_CROSS_EPISODE_NUMBERS = setOf(264, 265, 480, 576, 720)

    /** S01E02, s1e2, S01.E02, S01E02E03. */
    private val SEASON_EPISODE = Regex("""(?i)(?<![a-z0-9])s(\d{1,2})[ ._-]?e(\d{1,3})(?:[ ._-]?e\d{1,3})*(?![a-z0-9])""")

    /**
     * 1x02, 01x02. Never 1920x1080 (the lookbehind stops a match mid-number)
     * and never an audio layout glued to a codec, as in "DDP5.1x264" or
     * "AAC2.0x265": the `(?<!\d\.)` rejects a number that follows "digit.".
     */
    private val CROSS_EPISODE = Regex("""(?i)(?<![a-z0-9])(?<!\d\.)(\d{1,2})x(\d{2,3})(?![a-z0-9])""")

    /** Episode 5, Ep.5, Ep 05, Eps 12, E05. */
    private val EPISODE_WORD = Regex("""(?i)(?<![a-z0-9])(?:episode|eps|ep)[ ._-]*(\d{1,4})(?!\d)""")
    private val EPISODE_SHORT = Regex("""(?i)(?<![a-z0-9])e(\d{2,3})(?![a-z0-9])""")

    /** "[Group] Show Name - 05 [1080p].mkv" -- only trusted with a leading release-group tag. */
    private val ANIME_STYLE = Regex("""^\s*\[[^\]]+\].*?[ ._]-[ ._](\d{1,3})(?:v\d)?(?![\dpPkK])""")

    /** Season pack markers, e.g. "Show.S01.1080p" or "Show Season 2 Complete" (torrent names). */
    private val SEASON_ONLY = Regex("""(?i)(?<![a-z0-9])(?:s(\d{1,2})|season[ ._-]*(\d{1,2}))(?![a-z0-9])""")

    private data class Marker(val start: Int)

    private fun extensionOf(name: String): String =
        name.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.ROOT)

    private fun stripExtension(name: String): String {
        val ext = extensionOf(name)
        return if (ext.isNotEmpty() && ext.length <= 5 && (ext in VIDEO_EXT || ext in SUBTITLE_EXT)) {
            name.dropLast(ext.length + 1)
        } else name
    }

    private fun findEpisodeMarker(base: String): Marker? {
        SEASON_EPISODE.find(base)?.let { return Marker(it.range.first) }
        CROSS_EPISODE.findAll(base)
            .firstOrNull { (it.groupValues[2].toIntOrNull() ?: 0) !in NOT_CROSS_EPISODE_NUMBERS }
            ?.let { return Marker(it.range.first) }
        EPISODE_WORD.find(base)?.let { return Marker(it.range.first) }
        EPISODE_SHORT.find(base)?.let { return Marker(it.range.first) }
        ANIME_STYLE.find(base)?.let { m ->
            val number = m.groupValues[1].toIntOrNull()
            if (number != null && number !in NOT_EPISODE_NUMBERS) {
                // The separator dash sits just before the number; the show name ends there.
                val dash = base.lastIndexOf('-', m.groups[1]!!.range.first)
                return Marker(if (dash >= 0) dash else m.groups[1]!!.range.first)
            }
        }
        return null
    }

    /**
     * True when [fileName] is a video (or a subtitle for one) whose name
     * carries an episode marker. Files without a recognised extension are
     * never treated as episodes.
     */
    fun isEpisode(fileName: String?): Boolean {
        if (fileName.isNullOrBlank()) return false
        val name = fileName.substringAfterLast('/').trim()
        val ext = extensionOf(name)
        if (ext !in VIDEO_EXT && ext !in SUBTITLE_EXT) return false
        return findEpisodeMarker(stripExtension(name)) != null
    }

    /**
     * yt-dlp titles have no file extension (the extension is picked later),
     * so detection there runs on the bare title.
     */
    fun titleLooksLikeEpisode(title: String?): Boolean {
        if (title.isNullOrBlank()) return false
        return findEpisodeMarker(title.trim()) != null
    }

    /** Torrent top-level names: a single episode, or a whole season pack. */
    fun isEpisodeOrSeasonPack(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val base = stripExtension(name.trim())
        return findEpisodeMarker(base) != null || SEASON_ONLY.containsMatchIn(base)
    }

    /**
     * Folder name for the show [fileName] belongs to. Order: the text before
     * the episode marker in the file name -> the same in the yt-dlp/page
     * title ([titleHint]) -> the URL's folder segments -> [UNSORTED_FOLDER].
     */
    fun showFolderName(fileName: String?, sourceUrl: String? = null, titleHint: String? = null): String {
        val fromFile = extractShowName(fileName, allowSeasonOnly = true)
        if (fromFile != null) return fromFile
        val fromTitle = extractShowName(titleHint, allowSeasonOnly = true)
        if (fromTitle != null) return fromTitle
        urlFolderCandidates(sourceUrl).forEach { segment ->
            // A segment such as "Show.Name.S01" -> "Show Name"; a plain
            // "Show Name" folder is used as-is.
            extractShowName(segment, allowSeasonOnly = true)?.let { return it }
            val plain = clean(segment)
            if (plain.isNotBlank() && !plain.all { it.isDigit() }) return normaliseCase(plain)
        }
        return UNSORTED_FOLDER
    }

    private fun urlFolderCandidates(sourceUrl: String?): List<String> {
        if (sourceUrl.isNullOrBlank() || !sourceUrl.startsWith("http", ignoreCase = true)) return emptyList()
        val path = com.invictus.xmd.utils.UrlUtils.lenientUri(sourceUrl)?.path.orEmpty()
        val segments = path.split('/').filter { it.isNotBlank() }
        if (segments.size < 2) return emptyList()
        return segments.dropLast(1).asReversed().map { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
            .filterNot { GENERIC_SEGMENTS.contains(it.lowercase(Locale.ROOT)) }
    }

    private val GENERIC_SEGMENTS = setOf(
        "download", "downloads", "files", "file", "video", "videos", "media", "dl", "get", "stream", "v", "d", "f", "shows", "series",
    )

    private fun extractShowName(source: String?, allowSeasonOnly: Boolean): String? {
        if (source.isNullOrBlank()) return null
        val base = stripExtension(source.substringAfterLast('/').trim())
        val marker = findEpisodeMarker(base)?.start
            ?: (if (allowSeasonOnly) SEASON_ONLY.find(base)?.range?.first else null)
            ?: return null
        val name = clean(base.substring(0, marker))
        return name.takeIf { it.isNotBlank() }?.let(::normaliseCase)
    }

    /** Dots/underscores -> spaces, drop [group]/(tags), trailing separators and illegal path chars. */
    private fun clean(raw: String): String {
        var s = raw
        s = s.replace(Regex("""\[[^\]]*]"""), " ")
        s = s.replace(Regex("""\((?:19|20)\d{2}\)"""), " ")
        s = s.replace(Regex("""\([^)]*\)"""), " ")
        s = s.replace('.', ' ').replace('_', ' ')
        s = s.replace(Regex("""[\\/:*?"<>|]"""), " ")
        s = s.replace(Regex("""\s+"""), " ").trim()
        s = s.trim(' ', '-', '–', '—', ':', '|', ',')
        return s.take(80).trim()
    }

    /** "the boys" / "THE BOYS" -> "The Boys" so all episodes land in one folder. */
    private fun normaliseCase(name: String): String {
        if (name != name.lowercase(Locale.ROOT) && name != name.uppercase(Locale.ROOT)) return name
        return name.lowercase(Locale.ROOT).split(' ').joinToString(" ") { word ->
            word.replaceFirstChar { it.titlecase(Locale.ROOT) }
        }
    }
}
