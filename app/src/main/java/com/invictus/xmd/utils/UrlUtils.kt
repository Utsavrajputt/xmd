package com.invictus.xmd.utils

import java.net.URI

/**
 * Lenient [URI] parsing for pasted/shared links.
 *
 * java.net.URI is strict RFC 2396: an unescaped `[`, `]`, space, `{`, `}`,
 * `|`, `^`, backtick, quote, `<`, `>`, `\` or non-ASCII char in the path/query
 * throws URISyntaxException. Real-world download links carry those all the
 * time (e.g. `.../Movie.1080p.[MkvMoviesPoint].mkv.zip`), and browsers/OkHttp
 * accept them fine -- so a strict parse made valid links show up as
 * "Not a valid URL". This escapes the offending chars (never touching the
 * scheme/authority, and leaving existing %XX escapes intact) before parsing.
 */
object UrlUtils {

    private const val ILLEGAL = " []{}|^`\"<>\\"

    fun isValidUrl(url: String): Boolean {
        val uri = lenientUri(url) ?: return false
        val scheme = uri.scheme?.lowercase()
        return scheme == "http" || scheme == "https"
    }

    fun lenientUri(link: String): URI? {
        val trimmed = link.trim()
        if (trimmed.isEmpty()) return null
        runCatching { URI(trimmed) }.getOrNull()?.let { return it }
        return runCatching { URI(escapeIllegal(trimmed)) }.getOrNull()
    }

    internal fun escapeIllegal(s: String): String {
        val schemeEnd = s.indexOf("://")
        val restStart = if (schemeEnd >= 0) {
            val authStart = schemeEnd + 3
            val idx = s.indexOfAny(charArrayOf('/', '?', '#'), authStart)
            if (idx < 0) s.length else idx
        } else 0
        val sb = StringBuilder(s.length + 16)
        sb.append(s, 0, restStart)
        var i = restStart
        while (i < s.length) {
            val c = s[i]
            when {
                c == '%' && i + 2 < s.length && isHex(s[i + 1]) && isHex(s[i + 2]) -> sb.append(c)
                c == '%' -> sb.append("%25")
                c in ILLEGAL || c.code < 0x21 || c.code > 0x7E -> {
                    // Handle surrogate pairs as one code point.
                    val cp = s.codePointAt(i)
                    val bytes = String(Character.toChars(cp)).toByteArray(Charsets.UTF_8)
                    for (b in bytes) sb.append('%').append("%02X".format(b.toInt() and 0xFF))
                    if (Character.charCount(cp) == 2) i++
                }
                else -> sb.append(c)
            }
            i++
        }
        return sb.toString()
    }

    private fun isHex(c: Char) = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'
}
