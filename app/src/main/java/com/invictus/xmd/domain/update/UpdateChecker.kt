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
 * Two real channels, matching the two release workflows:
 * release.yml tags plain "vX.Y.Z" (stable), prerelease.yml tags
 * "vX.Y.Z-beta.N"/"-rc.N"/etc and marks the GitHub release `prerelease:
 * true` (preview). [checkForUpdate] fetches strictly within
 * [Settings.UpdateChannel] -- Stable only ever sees the latest
 * non-prerelease, Preview only ever sees the latest prerelease, even if a
 * newer stable exists -- since picking Preview is an explicit opt-in to
 * pre-release builds, not "whichever tag is newest overall".
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
    // Preview has no equivalent single-object endpoint (GitHub doesn't
    // expose a "/releases/latest-prerelease"), so it lists recent releases
    // instead and picks the first one flagged prerelease -- the list is
    // already newest-first, so that's the latest preview build.
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
            } ?: return null // Preview: no prerelease exists on the repo at all yet.

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

    /** Most recent releases (already newest-first), filtered down to the
     *  first one GitHub has flagged `prerelease: true` -- null if none of
     *  the fetched page are prereleases (repo has no preview build yet, or
     *  one further back than [RELEASES_LIST_API_URL]'s page size). */
    private fun fetchLatestPreview(): JSONObject? {
        val request = Request.Builder()
            .url(RELEASES_LIST_API_URL)
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw CheckFailedException("HTTP ${response.code}")
            val body = response.body?.string() ?: throw CheckFailedException("Empty response")
            val array = JSONArray(body)
            for (i in 0 until array.length()) {
                val release = array.optJSONObject(i) ?: continue
                if (release.optBoolean("prerelease", false)) return release
            }
            return null
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
     * Dotted/numeric version comparison, e.g. "v1.0.0-beta.5" > "1.0.0-beta.4".
     * The leading "v" and the "-" before a pre-release suffix are both
     * normalized to ordinary "." separators first, so "-beta.N" compares
     * numerically like any other component instead of being dropped --
     * matters here since Xmd's own tags are all pre-release (v1.0.0-beta.*).
     */
    private fun isNewer(remoteTag: String, currentVersion: String): Boolean {
        val remote = versionComponents(remoteTag)
        val current = versionComponents(currentVersion)
        val length = maxOf(remote.size, current.size)
        for (i in 0 until length) {
            val r = remote.getOrElse(i) { 0 }
            val c = current.getOrElse(i) { 0 }
            if (r != c) return r > c
        }
        return false
    }

    private fun versionComponents(version: String): List<Int> =
        version
            .removePrefix("v")
            .replace("-", ".")
            .split(".")
            .mapNotNull { it.toIntOrNull() }

    private val SUPPORTED_ABIS = listOf("arm64-v8a", "armeabi-v7a")
}
