package com.invictus.xmd.ui.browser

/** Delay before the bottom nav bar hides itself on an auto-hide site. Long enough to
 *  hit a button right after the page loads / Back is pressed, short enough that the
 *  video/feed gets the full screen quickly. */
internal const val BOTTOM_BAR_AUTO_HIDE_DELAY_MS = 4_000L

internal object BottomBarAutoHide {

    /** "https://www.YouTube.com/watch?v=1" / "m.youtube.com" -> "youtube.com". Null when blank. */
    fun normalizeSite(input: String): String? {
        var host = input.trim().lowercase()
        host = host.substringAfter("://", host).substringBefore('/').substringBefore('?').substringBefore('#')
        host = host.substringBefore(':').removePrefix("www.").removePrefix("m.")
        return host.trim('.').takeIf { it.isNotEmpty() && it.any(Char::isLetter) }
    }

    /** True when [url]'s host is one of [sites] or a subdomain of it. */
    fun matches(url: String?, sites: List<String>): Boolean {
        if (url.isNullOrBlank() || sites.isEmpty()) return false
        val host = url.trim().lowercase()
            .substringAfter("://", "")
            .substringBefore('/').substringBefore('?').substringBefore('#')
            .substringBefore(':')
        if (host.isEmpty()) return false
        return sites.any { site -> host == site || host.endsWith(".$site") }
    }

    /** Settings dialog text (one site per line / comma separated) -> clean, de-duplicated host list. */
    fun parseInput(text: String): List<String> =
        text.split('\n', ',', ' ').mapNotNull(::normalizeSite).distinct()
}
