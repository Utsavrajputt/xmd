package com.invictus.xmd.domain.update

import android.os.Build
import com.invictus.xmd.BuildConfig
import com.invictus.xmd.preferences.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * GitHub-Releases-backed update check + in-app APK download for the About
 * screen -- same shape as mpvRx's UpdateManager, trimmed down for Xmd's
 * simpler per-flavor/per-ABI asset layout instead of a universal one, since
 * Xmd's release workflows build "Xmd-<flavor>-<abi>-<tag>.apk" for each of
 * lite/full x arm64-v8a/armeabi-v7a rather than a single combined APK. No
 * JSON library dependency needed -- org.json ships with the platform, so
 * this avoids pulling in kotlinx.serialization just for a handful of
 * response fields.
 *
 * Two channels, matching the two release workflows: release.yml tags plain
 * "vX.Y.Z" (stable), prerelease.yml tags "vX.Y.Z-beta.N"/"-rc.N"/etc and
 * marks the GitHub release `prerelease: true`. Stable only ever sees the
 * latest non-prerelease. Preview sees the highest version among recent
 * releases of either kind, so a preview user is also offered the stable
 * release that supersedes their beta -- while a stable build is never
 * offered an older pre-release of the same version (1.0.0 > 1.0.0-beta.6).
 */
object UpdateChecker {

    data class Asset(val name: String, val downloadUrl: String, val size: Long)

    data class Release(
        val tagName: String,
        val htmlUrl: String,
        val body: String,
        val publishedAt: String,
        val assets: List<Asset>,
    )

    class CheckFailedException(message: String, cause: Throwable? = null) : Exception(message, cause)

    // /releases/latest is GitHub's own "newest non-prerelease" pointer --
    // exactly what Stable wants, and cheaper than listing+filtering.
    // Preview lists recent releases and picks the highest version among
    // them (prerelease or stable) -- not just the first in list order, which
    // is by creation date and could put an older line's hotfix on top.
    private const val RELEASES_LATEST_API_URL = "https://api.github.com/repos/Utsavrajputt/xmd/releases/latest"
    private const val RELEASES_LIST_API_URL = "https://api.github.com/repos/Utsavrajputt/xmd/releases?per_page=10"
    private const val RELEASES_FALLBACK_URL = "https://github.com/Utsavrajputt/xmd/releases"

    // 10s, not 6s -- GitHub's API can be slow to respond on weak mobile
    // signal, and a too-tight timeout here surfaces as the same generic
    // "check your connection" failure as an actual outage, which is
    // confusing when the connection is fine but just slow.
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Blocking; call from a background thread/coroutine, never the main
     * thread. Returns the latest [Release] on [channel] if it's newer than
     * [currentVersion], or null if already up to date with that channel.
     * Throws [CheckFailedException] (never a raw network/parse exception)
     * if the check itself couldn't complete, so callers can tell "no
     * update" apart from "couldn't check".
     */
    @Throws(CheckFailedException::class)
    fun checkForUpdate(
        currentVersion: String,
        channel: Settings.UpdateChannel = Settings.UpdateChannel.STABLE,
    ): Release? {
        try {
            val json = when (channel) {
                Settings.UpdateChannel.STABLE -> fetchLatestStable()
                Settings.UpdateChannel.PREVIEW -> fetchLatestPreview()
            } ?: return null // No matching release on the repo at all yet.

            val tagName = json.optString("tag_name").ifBlank {
                throw CheckFailedException("Release response missing tag_name")
            }
            if (!isNewer(tagName, currentVersion)) return null
            return parseRelease(json, tagName)
        } catch (e: CheckFailedException) {
            throw e
        } catch (e: Exception) {
            throw CheckFailedException(e.message ?: "Update check failed", e)
        }
    }

    /** GitHub's own "latest non-prerelease" pointer -- a single release
     *  object, or null if the repo has no stable release at all. */
    private fun fetchLatestStable(): JSONObject? {
        val request = Request.Builder()
            .url(RELEASES_LATEST_API_URL)
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { response ->
            // GitHub returns 404 here (not an empty list) when the repo has
            // no non-prerelease release yet -- treat that as "no release",
            // same as Preview finding nothing, not a check failure.
            if (response.code == 404) return null
            if (!response.isSuccessful) throw CheckFailedException("HTTP ${response.code}")
            val body = response.body?.string() ?: throw CheckFailedException("Empty response")
            return JSONObject(body)
        }
    }

    /** Highest-versioned release among the most recent page, prerelease or
     *  stable, skipping drafts -- null if the repo has no releases at all. */
    private fun fetchLatestPreview(): JSONObject? {
        val request = Request.Builder()
            .url(RELEASES_LIST_API_URL)
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw CheckFailedException("HTTP ${response.code}")
            val body = response.body?.string() ?: throw CheckFailedException("Empty response")
            val array = JSONArray(body)
            var best: JSONObject? = null
            for (i in 0 until array.length()) {
                val release = array.optJSONObject(i) ?: continue
                if (release.optBoolean("draft", false)) continue
                val tag = release.optString("tag_name")
                if (tag.isBlank()) continue
                val currentBest = best
                if (currentBest == null || isNewer(tag, currentBest.optString("tag_name"))) best = release
            }
            return best
        }
    }

    private fun parseRelease(json: JSONObject, tagName: String): Release {
        val htmlUrl = json.optString("html_url", RELEASES_FALLBACK_URL)
        val assetsJson = json.optJSONArray("assets")
        val assets = buildList {
            if (assetsJson != null) {
                for (i in 0 until assetsJson.length()) {
                    val a = assetsJson.optJSONObject(i) ?: continue
                    val name = a.optString("name").ifBlank { continue }
                    val downloadUrl = a.optString("browser_download_url").ifBlank { continue }
                    add(Asset(name = name, downloadUrl = downloadUrl, size = a.optLong("size", 0L)))
                }
            }
        }
        return Release(
            tagName = tagName,
            htmlUrl = htmlUrl,
            body = json.optString("body", ""),
            publishedAt = json.optString("published_at", ""),
            assets = assets,
        )
    }

    /**
     * Picks the asset matching this build's own flavor
     * ("Xmd-lite-..."/"Xmd-full-...", from [BuildConfig.FLAVOR]) and the
     * device's primary ABI, falling back to the other supported ABI if the
     * device's own isn't one of the two Xmd ships (e.g. an x86 emulator).
     * Null if the release has no compatible asset at all (e.g. it predates
     * the current flavor split, or assets are still being uploaded).
     */
    fun selectApkAsset(release: Release): Asset? {
        val flavor = BuildConfig.FLAVOR.lowercase()
        val flavorAssets = release.assets.filter {
            it.name.startsWith("Xmd-$flavor-", ignoreCase = true) && it.name.endsWith(".apk", ignoreCase = true)
        }
        val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull { it in SUPPORTED_ABIS } ?: SUPPORTED_ABIS[0]
        return flavorAssets.firstOrNull { it.name.contains(primaryAbi, ignoreCase = true) }
            ?: flavorAssets.firstOrNull { abi -> SUPPORTED_ABIS.any { it != primaryAbi && abi.name.contains(it, ignoreCase = true) } }
    }

    /**
     * Streams [asset] to [destination] (overwriting it), emitting progress
     * from 0f to 100f as bytes arrive (-1f if the server didn't send a
     * Content-Length, so the caller can show an indeterminate spinner
     * instead of a stuck bar). Runs on [Dispatchers.IO]; cancelling the
     * collecting coroutine aborts the download mid-stream.
     */
    fun downloadApk(asset: Asset, destination: File): Flow<Float> = flow {
        val request = Request.Builder().url(asset.downloadUrl).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
            val body = response.body ?: throw IOException("Empty response body")
            val contentLength = body.contentLength()
            body.byteStream().use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var totalRead = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        totalRead += read
                        emit(if (contentLength > 0) (totalRead.toFloat() / contentLength.toFloat()) * 100f else -1f)
                    }
                    output.flush()
                }
            }
        }
        emit(100f)
    }.flowOn(Dispatchers.IO)

    /** Deletes any previously-downloaded update APKs from [cacheDir] --
     *  call after a successful install, or when the user backs out of the
     *  update flow, so a stale partial/old-version APK never lingers. */
    fun clearDownloadedApks(cacheDir: File) {
        cacheDir.listFiles()?.forEach { if (it.name.endsWith(".apk")) it.delete() }
    }

    /**
     * SemVer precedence: "1.0.0" > "1.0.0-rc.1" > "1.0.0-beta.6" > "1.0.0-beta.2".
     * [currentVersion] is BuildConfig.VERSION_NAME, which carries the flavor
     * suffix ("1.0.0-beta.6-full"); that suffix is not a pre-release marker,
     * so it's stripped first -- otherwise "1.0.0-full" would rank below 1.0.0.
     */
    private fun isNewer(remoteTag: String, currentVersion: String): Boolean =
        compareVersions(remoteTag, currentVersion) > 0

    private fun compareVersions(a: String, b: String): Int {
        val (coreA, preA) = parseVersion(a)
        val (coreB, preB) = parseVersion(b)
        for (i in 0 until maxOf(coreA.size, coreB.size)) {
            val x = coreA.getOrElse(i) { 0 }
            val y = coreB.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        // Same X.Y.Z: a release (no pre-release part) outranks any pre-release.
        if (preA.isEmpty() && preB.isEmpty()) return 0
        if (preA.isEmpty()) return 1
        if (preB.isEmpty()) return -1
        for (i in 0 until maxOf(preA.size, preB.size)) {
            if (i >= preA.size) return -1 // fewer identifiers = lower precedence
            if (i >= preB.size) return 1
            val x = preA[i]
            val y = preB[i]
            val xn = x.toIntOrNull()
            val yn = y.toIntOrNull()
            val cmp = when {
                xn != null && yn != null -> xn.compareTo(yn)
                xn != null -> -1 // numeric identifiers rank below alphanumeric ones
                yn != null -> 1
                else -> x.compareTo(y)
            }
            if (cmp != 0) return cmp
        }
        return 0
    }

    /** "v1.0.0-beta.6-full" -> ([1,0,0], ["beta","6"]). */
    private fun parseVersion(raw: String): Pair<List<Int>, List<String>> {
        val cleaned = raw.trim()
            .removePrefix("v")
            .substringBefore("+") // SemVer build metadata never affects precedence
            .removeSuffix("-full")
            .removeSuffix("-lite")
        val core = cleaned.substringBefore("-")
        val pre = cleaned.substringAfter("-", "")
        return core.split(".").map { it.toIntOrNull() ?: 0 } to
            if (pre.isEmpty()) emptyList() else pre.split(".")
    }

    private val SUPPORTED_ABIS = listOf("arm64-v8a", "armeabi-v7a")
}
