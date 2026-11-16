package com.invictus.xmd.network

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import com.invictus.xmd.ui.browser.BrowserFragment

/**
 * DNS-over-HTTPS resolver for the in-app Browser tab only -- this never
 * touches the device's system DNS or any other traffic in the app (the
 * download engine's own OkHttpClient is untouched). Used as the [Dns]
 * for the OkHttpClient that BrowserFragment routes every WebView request
 * through (see shouldInterceptRequest), so DNS resolution for browsing
 * traffic follows whatever the user picked in the Browser's DNS setting.
 *
 * Uses the DoH JSON API (RFC 8427-ish `application/dns-json`), supported
 * by both AdGuard's public resolver and most other DoH providers, so the
 * same resolver class works for both the built-in AdGuard endpoint and
 * a user-supplied custom DoH URL.
 */
/** The DoH provider answered 0.0.0.0 / :: for this host: it is blocked by the resolver (ad / tracker lists). */
class DnsBlockedException(host: String) : UnknownHostException(host)

class DnsOverHttpsResolver(private val dohUrl: String) : Dns {

    companion object {
        const val ADGUARD_DOH_URL = "https://dns.adguard.com"
        const val GOOGLE_DOH_URL = "https://dns.google/resolve"
        // Cloudflare's plain resolver (1.1.1.1) -- no filtering, matches Google/AdGuard's role here.
        const val CLOUDFLARE_DOH_URL = "https://cloudflare-dns.com/dns-query"
        // Cloudflare's malware+ad blocking resolver (1.1.1.2) -- separate hostname per
        // Cloudflare's docs, not a query param on the plain endpoint.
        const val CLOUDFLARE_ADBLOCK_DOH_URL = "https://security.cloudflare-dns.com/dns-query"
    }

    // Separate, short-timeout client for the DoH lookups themselves --
    // deliberately not the same client whose Dns this is plugged into,
    // to avoid any recursive resolution weirdness. A real DoH round trip
    // is typically well under a second; the old 4s+4s (A then AAAA)
    // budget meant a single slow/unreachable provider could add up to
    // 8 seconds of stall to the *first* request on every new hostname a
    // page touches -- on top of that same penalty being paid separately,
    // per app, per host, unlike Chrome's OS-level Private DNS which shares
    // one persistent connection to the resolver for the whole device.
    private val lookupClient = OkHttpClient.Builder()
        .connectTimeout(1500, TimeUnit.MILLISECONDS)
        .readTimeout(1500, TimeUnit.MILLISECONDS)
        .build()

    // Small in-memory cache so repeated requests to the same host during a
    // browsing session (every sub-resource on a page) don't each trigger a
    // fresh DoH round-trip. No persistence, no TTL bookkeeping -- cleared
    // when the process dies, which is fine for a browsing-session cache.
    private val cache = ConcurrentHashMap<String, List<InetAddress>>()

    // Hosts that just failed to resolve via DoH, so a page that hammers a
    // broken/blocked/unreachable host with many sub-resource requests pays
    // the DoH timeout once per short window instead of once per request --
    // without this, a single dead DoH provider turns every request on the
    // page into its own multi-second stall before falling back to system
    // DNS. Deliberately short-lived (a few seconds) since this is purely an
    // anti-hammering guard, not a real negative-result cache.
    private val negativeCache = ConcurrentHashMap<String, Long>()
    private val negativeCacheTtlMs = 5_000L

    // Hosts the provider blocks by answering 0.0.0.0 / ::. Remembered for the session so the
    // many repeat requests a page makes to a blocked tracker fail instantly.
    private val blockedHosts = ConcurrentHashMap.newKeySet<String>()

    /** Opens the connection to the DoH provider (system DNS + TLS) ahead of the first real lookup. */
    fun warmUp() {
        runCatching { queryDoh("example.com", "A") }
    }

    // De-dupes concurrent lookups for the same hostname -- a page loading
    // a dozen images/scripts off the same CDN host fires that many
    // shouldInterceptRequest calls at once, and without this each one
    // would kick off its own redundant DoH round-trip for a hostname
    // that's already being resolved by another in-flight call.
    private val inFlight = ConcurrentHashMap<String, Any>()

    override fun lookup(hostname: String): List<InetAddress> {
        cache[hostname]?.let { return it }
        if (hostname in blockedHosts) throw DnsBlockedException(hostname)

        val recentFailure = negativeCache[hostname]
        if (recentFailure != null) {
            if (System.currentTimeMillis() - recentFailure < negativeCacheTtlMs) {
                return systemFallback(hostname)
            }
            negativeCache.remove(hostname)
        }

        val lock = inFlight.computeIfAbsent(hostname) { Any() }
        try {
            synchronized(lock) {
                // Another thread may have just finished resolving this host
                // while we were waiting on the lock -- reuse its result.
                cache[hostname]?.let { return it }
                if (hostname in blockedHosts) throw DnsBlockedException(hostname)

                val resolved = runCatching { queryDoh(hostname, type = "A") }.getOrDefault(emptyList())
                    .ifEmpty { runCatching { queryDoh(hostname, type = "AAAA") }.getOrDefault(emptyList()) }

                if (resolved.isEmpty()) {
                    // DoH gave nothing usable (provider down, hostname
                    // genuinely doesn't resolve, etc.) -- remember that
                    // briefly and fall back to the system resolver rather
                    // than failing the whole page load.
                    negativeCache[hostname] = System.currentTimeMillis()
                    return systemFallback(hostname)
                }

                // AdGuard / Cloudflare-security style blocking: the answer is 0.0.0.0 or ::.
                // Connecting there only fails; surface it as "blocked" so the caller can answer
                // at once instead of waiting for a refused connection and then letting the
                // WebView retry the same host through system DNS (which would skip the block).
                if (resolved.all { it.isAnyLocalAddress }) {
                    blockedHosts.add(hostname)
                    throw DnsBlockedException(hostname)
                }

                cache[hostname] = resolved
                return resolved
            }
        } finally {
            inFlight.remove(hostname, lock)
        }
    }

    private fun systemFallback(hostname: String): List<InetAddress> =
        runCatching { Dns.SYSTEM.lookup(hostname) }
            .getOrElse { throw UnknownHostException(hostname) }

    private fun queryDoh(hostname: String, type: String): List<InetAddress> {
        val url = dohUrl.toHttpUrl().newBuilder()
            .addQueryParameter("name", hostname)
            .addQueryParameter("type", type)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/dns-json")
            .build()

        lookupClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string().orEmpty()
            val json = JSONObject(body)
            val answers = json.optJSONArray("Answer") ?: return emptyList()
            val addresses = mutableListOf<InetAddress>()
            for (i in 0 until answers.length()) {
                val entry = answers.getJSONObject(i)
                // type 1 = A, 28 = AAAA -- only take address records, DoH
                // responses can interleave CNAME (type 5) entries too.
                if (entry.optInt("type") !in setOf(1, 28)) continue
                val ip = entry.optString("data").takeIf { it.isNotBlank() } ?: continue
                runCatching { InetAddress.getByName(ip) }.getOrNull()?.let { addresses.add(it) }
            }
            return addresses
        }
    }
}
