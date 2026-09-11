package com.invictus.xmd.domain.update

import android.os.Build
import com.invictus.xmd.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * GitHub-Releases-backed update check + in-app APK download for the About
 * screen -- same shape as mpvRx's UpdateManager, trimmed down for Xmd's
 * simpler release setup: a single GitHub Releases channel (no stable/
 * preview split) and per-flavor/per-ABI APK assets instead of a universal
 * one, since Xmd's release workflow (.github/workflows/release.yml) builds
 * "Xmd-<flavor>-<abi>-<tag>.apk" for each of lite/full x arm64-v8a/
 * armeabi-v7a rather than a single combined APK. No JSON library dependency
 * needed -- org.json ships with the platform, so this avoids pulling in
 * kotlinx.serialization just for two response fields.
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

    private const val RELEASES_API_URL = "https://api.github.com/repos/Utsavrajputt/xmd/releases/latest"
    private const val RELEASES_FALLBACK_URL = "https://github.com/Utsavrajputt/xmd/releases/latest"

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    /**
     * Blocking; call from a background thread/coroutine, never the main
     * thread. Returns the latest [Release] if it's newer than
     * [currentVersion], or null if already up to date. Throws
     * [CheckFailedException] (never a raw network/parse exception) if the
     * check itself couldn't complete, so callers can tell "no update"
     * apart from "couldn't check".
     */
    @Throws(CheckFailedException::class)
    fun checkForUpdate(currentVersion: String): Release? {
        val request = Request.Builder()
            .url(RELEASES_API_URL)
            .header("Accept", "application/vnd.github+json")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw CheckFailedException("HTTP ${response.code}")
                val body = response.body?.string()
                    ?: throw CheckFailedException("Empty response")
                val json = JSONObject(body)
                val tagName = json.optString("tag_name").ifBlank {
                    throw CheckFailedException("Release response missing tag_name")
                }
                val htmlUrl = json.optString("html_url", RELEASES_FALLBACK_URL)
                if (!isNewer(tagName, currentVersion)) return null

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
        } catch (e: CheckFailedException) {
            throw e
        } catch (e: Exception) {
            throw CheckFailedException(e.message ?: "Update check failed", e)
        }
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
