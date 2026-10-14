package com.invictus.xmd.domain.browser

import java.net.URI

/**
 * Generic "old domain redirects to new domain" detector for speed-dial shortcuts.
 *
 * Sites like hdhub4u keep changing their domain; the old one usually just
 * redirects to the new one. When a tile is opened and the page ends up on a
 * different host, [newUrlFor] decides whether the tile should be re-pointed.
 *
 * To avoid hijacks (login/SSO pages, ad redirects, Cloudflare etc.) the new
 * host must still look like the same site: its brand label (host minus TLD)
 * has to match the old host's label or the tile's title.
 */
object ShortcutRedirectUpdater {

    /** Returns the URL the shortcut should now point at, or null for "leave it alone". */
    fun newUrlFor(oldUrl: String, title: String, finalUrl: String): String? {
        val old = parse(oldUrl) ?: return null
        val fin = parse(finalUrl) ?: return null
        val oldHost = hostKey(old)
        val newHost = hostKey(fin)
        if (oldHost.isEmpty() || newHost.isEmpty() || oldHost == newHost) return null
        if (!looksLikeSameSite(oldHost, newHost, title)) return null

        val scheme = fin.scheme?.lowercase() ?: return null
        val authority = fin.authority ?: return null
        val oldPath = old.rawPath.orEmpty()
        return if (oldPath.isEmpty() || oldPath == "/") {
            "$scheme://$authority/"
        } else {
            // Tile pointed at a specific page: keep the final URL, minus the fragment.
            finalUrl.substringBefore('#')
        }
    }

    internal fun looksLikeSameSite(oldHost: String, newHost: String, title: String): Boolean {
        val a = label(oldHost)
        val b = label(newHost)
        val t = title.lowercase().filter { it.isLetterOrDigit() }
        if (a.length >= 3 && a == b) return true
        if (a.length >= 4 && b.length >= 4 && (a.contains(b) || b.contains(a))) return true
        if (t.length >= 4 && (b.contains(t) || t.contains(b) && b.length >= 4)) return true
        return false
    }

    /** `www.hdhub4u.tv` -> `hdhub4u`, `foo.co.uk` -> `foo`. */
    internal fun label(host: String): String {
        val parts = host.split('.').filter { it.isNotEmpty() }
        if (parts.size < 2) return host
        var rest = parts.dropLast(1)
        if (rest.size > 1 && rest.last() in SECOND_LEVEL) rest = rest.dropLast(1)
        return rest.last()
    }

    private val SECOND_LEVEL = setOf("co", "com", "org", "net", "gov", "ac", "edu")

    private fun parse(url: String): URI? {
        val u = com.invictus.xmd.utils.UrlUtils.lenientUri(url) ?: return null
        val scheme = u.scheme?.lowercase()
        return if (scheme == "http" || scheme == "https") u else null
    }

    private fun hostKey(u: URI): String = (u.host ?: "").lowercase().removePrefix("www.")
}
