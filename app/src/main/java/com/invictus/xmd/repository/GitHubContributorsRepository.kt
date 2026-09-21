package com.invictus.xmd.repository

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/** One row of the About screen's Developers list: a GitHub account and its commit count on the repo. */
data class GitHubContributor(
    val login: String,
    val avatarUrl: String?,
    val profileUrl: String,
    val contributions: Int,
)

/**
 * Fetches the repo's contributors from GitHub's public API (no token
 * needed) for the About screen's Developers section -- modeled on mpvRx's
 * GitHubContributorsRepository, but with the result cached on disk for
 * 72 hours instead of a 30-minute in-memory cache: same 72h window
 * [com.invictus.xmd.utils.GithubAvatarLoader] uses for avatars, and
 * surviving a cold app start so unauthenticated GitHub rate limits (60
 * requests/hour/IP) aren't burned on every About visit.
 *
 * Behavior:
 *  - fresh cache (< 72h old) -> returned without touching the network
 *  - stale/missing cache -> fetched; a failed fetch falls back to the
 *    stale cache if there is one, so a flaky connection never blanks an
 *    already-loaded list
 *  - [forceRefresh] (the error state's Retry button) skips the freshness
 *    check
 * `contributions` is GitHub's per-contributor commit count.
 */
object GitHubContributorsRepository {

    private const val API_URL = "https://api.github.com/repos/Utsavrajputt/xmd/contributors"
    private const val PER_PAGE = 100
    private const val MAX_PAGES = 3
    private const val CACHE_TTL_MS = 72L * 60L * 60L * 1000L
    private const val CACHE_FILE_NAME = "github_contributors.json"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private class CacheEntry(val fetchedAtMs: Long, val items: List<GitHubContributor>)

    suspend fun contributors(
        context: Context,
        forceRefresh: Boolean = false,
    ): Result<List<GitHubContributor>> = withContext(Dispatchers.IO) {
        val file = File(context.applicationContext.cacheDir, CACHE_FILE_NAME)
        val cached = readCache(file)
        if (!forceRefresh && cached != null) {
            // A negative age means the device clock moved backwards --
            // treat that as stale rather than trusting the cache forever.
            val age = System.currentTimeMillis() - cached.fetchedAtMs
            if (age in 0 until CACHE_TTL_MS) return@withContext Result.success(cached.items)
        }
        try {
            val fresh = fetchAll()
            writeCache(file, fresh)
            Result.success(fresh)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            if (cached != null) Result.success(cached.items) else Result.failure(error)
        }
    }

    private fun fetchAll(): List<GitHubContributor> {
        val all = mutableListOf<GitHubContributor>()
        for (page in 1..MAX_PAGES) {
            val request = Request.Builder()
                .url("$API_URL?per_page=$PER_PAGE&page=$page")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "Xmd-Android")
                .build()
            val array = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("GitHub contributors request failed with HTTP ${response.code}")
                }
                JSONArray(response.body?.string().orEmpty())
            }
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                if (obj.optString("type").equals("Bot", ignoreCase = true)) continue
                val login = obj.optString("login").trim()
                if (login.isEmpty()) continue
                all += GitHubContributor(
                    login = login,
                    avatarUrl = obj.optString("avatar_url").takeIf { it.startsWith("https://") },
                    profileUrl = obj.optString("html_url").takeIf { it.startsWith("https://github.com/") }
                        ?: "https://github.com/$login",
                    contributions = obj.optInt("contributions", 0).coerceAtLeast(0),
                )
            }
            if (array.length() < PER_PAGE) break
        }
        return all
            .distinctBy { it.login.lowercase(Locale.ROOT) }
            .sortedWith(
                compareByDescending<GitHubContributor> { it.contributions }
                    .thenBy { it.login.lowercase(Locale.ROOT) },
            )
    }

    private fun readCache(file: File): CacheEntry? = runCatching {
        if (!file.exists()) return null
        val json = JSONObject(file.readText())
        val array = json.getJSONArray("items")
        val items = (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            GitHubContributor(
                login = obj.getString("login"),
                avatarUrl = obj.optString("avatarUrl").takeIf { it.isNotEmpty() },
                profileUrl = obj.getString("profileUrl"),
                contributions = obj.optInt("contributions", 0),
            )
        }
        CacheEntry(json.getLong("fetchedAt"), items)
    }.getOrNull()

    private fun writeCache(file: File, items: List<GitHubContributor>) {
        runCatching {
            val array = JSONArray()
            items.forEach { c ->
                array.put(
                    JSONObject()
                        .put("login", c.login)
                        .put("avatarUrl", c.avatarUrl ?: "")
                        .put("profileUrl", c.profileUrl)
                        .put("contributions", c.contributions),
                )
            }
            file.writeText(
                JSONObject()
                    .put("fetchedAt", System.currentTimeMillis())
                    .put("items", array)
                    .toString(),
            )
        }
    }
}
