package com.invictus.xmd.domain.browser

import android.webkit.CookieManager
import com.invictus.xmd.utils.UrlUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class WebRequest(
    val url: String,
    val headers: Map<String, String>,
    val page: String?,
) {
    val id: String = url
}

class DownloadInterceptor(
    private val scope: CoroutineScope,
) {
    private val requests = mutableMapOf<String, WebRequest>()

    fun interceptRequest(request: WebRequest) {
        requests[request.id] = request
        scope.launch {
            delay(REMOVE_REQUESTS_DELAY)
            requests.remove(request.id)
        }
    }

    fun getWebRequestOrDefault(
        url: String,
        userAgent: String?,
        page: String?,
    ): WebRequest {
        var request = requests[url]
        if (request == null) {
            request = WebRequest(
                url = url,
                headers = emptyMap(),
                page = page,
            )
        }
        return request
            .withUserAgent(userAgent)
            .withCookieManagerCookies()
            .withReferer(page)
    }

    private fun WebRequest.withUserAgent(userAgent: String?): WebRequest {
        if (userAgent.isNullOrBlank()) return this
        val userAgentKey = "User-Agent"
        if (headers.keys.any { it.equals(userAgentKey, ignoreCase = true) }) {
            return this
        }
        return copy(headers = headers + (userAgentKey to userAgent))
    }

    private fun WebRequest.withCookieManagerCookies(): WebRequest {
        val cookieFromCookieManager = runCatching {
            CookieManager.getInstance().getCookie(url)
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: return this

        val cookieKey = "Cookie"
        val currentCookie = headers.entries.firstOrNull { it.key.equals(cookieKey, ignoreCase = true) }?.value
        val mergedCookie = if (currentCookie != null && !currentCookie.contains(cookieFromCookieManager)) {
            "$currentCookie; $cookieFromCookieManager"
        } else {
            currentCookie ?: cookieFromCookieManager
        }
        return copy(headers = headers + (cookieKey to mergedCookie))
    }

    private fun WebRequest.withReferer(page: String?): WebRequest {
        val pageUrl = page ?: this.page
        if (pageUrl.isNullOrBlank()) return this
        val refererKey = "Referer"
        if (headers.keys.any { it.equals(refererKey, ignoreCase = true) }) {
            return this
        }
        return copy(page = pageUrl, headers = headers + (refererKey to pageUrl))
    }

    companion object {
        private const val REMOVE_REQUESTS_DELAY = 20_000L
    }
}
