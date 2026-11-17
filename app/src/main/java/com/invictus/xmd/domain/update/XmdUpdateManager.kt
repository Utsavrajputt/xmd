package com.invictus.xmd.domain.update

import android.content.Context
import android.os.Build
import android.util.Log
import com.invictus.xmd.BuildConfig
import com.invictus.xmd.preferences.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLongArray

// In-app updater in the same shape as Bunko's BunkoUpdateManager (itself an
// mpvRx port): GitHub Releases for Stable, an auto-built preview manifest
// (newest successful preview.yml run on GitHub Actions, APKs hosted on GitHub
// Pages, compared by run number) for Preview, ignore-version, and a
// parallel + resumable APK download. Xmd-specific bits kept from the old
// UpdateChecker: per-flavor / per-ABI asset selection (lite/full x arm64/v7a),
// org.json instead of kotlinx.serialization (no extra dependency), and real
// check failures surfaced as CheckFailedException so "couldn't check" is never
// mistaken for "up to date".

data class UpdateAsset(val name: String, val downloadUrl: String, val size: Long)

data class XmdRelease(
    val tagName: String,
    val name: String,
    val htmlUrl: String,
    val body: String,
    val publishedAt: String,
    val prerelease: Boolean,
    val assets: List<UpdateAsset>,
    /** Auto preview only: run number of the preview.yml workflow run that built it. */
    val commitCount: Int? = null,
    val commitSha: String? = null,
    /** Preview only: user-facing summary of the commits since this build (see [summarizeCommitMessages]); null when unavailable. */
    val commitSummary: String? = null,
)

class CheckFailedException(message: String, cause: Throwable? = null) : Exception(message, cause)

class XmdUpdateManager(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    /** Everything this class downloads lives here, so clearing the cache can
     *  never touch unrelated files (torrent staging etc.) in cacheDir. */
    private val updatesDir: File
        get() = File(appContext.cacheDir, "updates").apply { mkdirs() }

    /**
     * Returns the newest release on [channel] if it is newer than this build
     * (and not ignored, unless [forceShow]), or null when up to date.
     * Throws [CheckFailedException] when the check itself could not complete.
     */
    @Throws(CheckFailedException::class)
    suspend fun checkForUpdate(
        channel: Settings.UpdateChannel,
        forceShow: Boolean = false,
    ): XmdRelease? = withContext(Dispatchers.IO) {
        val release = try {
            when (channel) {
                Settings.UpdateChannel.STABLE -> getLatestRelease()
                Settings.UpdateChannel.PREVIEW -> getLatestPrerelease()
            }
        } catch (e: CheckFailedException) {
            throw e
        } catch (e: Exception) {
            Log.w(Tag, "Could not check GitHub releases.", e)
            throw CheckFailedException(e.message ?: "Update check failed", e)
        } ?: return@withContext null

        val current = BuildConfig.VERSION_NAME
        val newer = when (channel) {
            Settings.UpdateChannel.STABLE -> isVersionNewer(release.tagName, current)
            Settings.UpdateChannel.PREVIEW ->
                isPreviewReleaseNewer(release, BuildConfig.PREVIEW_RUN, current) && !currentBuildCoversPreview(release)
        }
        if (!newer) return@withContext null

        val ignored = prefs.getString(ignoredVersionKey(channel), null)
        if (!forceShow && (ignored == release.tagName || ignored == release.tagName.removePrefix("v"))) {
            return@withContext null
        }
        // A newer release without an APK for this flavor/ABI can't be
        // installed in-app -- report it instead of pretending we're current.
        if (selectApkAsset(release) == null) {
            throw CheckFailedException("No compatible APK in ${release.tagName}")
        }
        if (channel == Settings.UpdateChannel.PREVIEW && release.commitSha != null) {
            val summary = previewCommitSummary(release.commitSha)
            if (summary != null) return@withContext release.copy(commitSummary = summary)
        }
        release
    }

    /**
     * True when this build is at or past the preview, i.e. offering the preview would be a downgrade.
     *
     * Builds that did not come from preview.yml (the "Android build" workflow, a local build) have
     * PREVIEW_RUN == 0, so the run-number check alone calls *every* preview newer. For those builds:
     *  1. ask GitHub how our commit relates to the preview's ("ahead"/"identical" = covered,
     *     "behind" = the preview really is newer);
     *  2. if that can't be answered (offline, rate limit, preview commit gone after a history rewrite,
     *     "diverged"), fall back to time: a build compiled after the preview run finished is newer.
     * Unknown on both counts keeps the old behaviour (offer the preview).
     */
    private fun currentBuildCoversPreview(release: XmdRelease): Boolean {
        if (BuildConfig.PREVIEW_RUN > 0) return false
        val previewSha = release.commitSha
        val ownSha = BuildConfig.GIT_SHA.takeIf { it.isNotBlank() }
        if (previewSha != null && ownSha != null) {
            if (previewSha.equals(ownSha, ignoreCase = true)) return true
            when (compareStatus(previewSha, ownSha)) {
                "ahead", "identical" -> return true
                "behind" -> return false
                else -> Unit // diverged / unknown: decide by time below
            }
        }
        val previewTime = parseIsoUtc(release.publishedAt) ?: return false
        val covered = BuildConfig.BUILD_TIME_MS > previewTime
        Log.i(Tag, "Preview ${release.tagName}: build time ${BuildConfig.BUILD_TIME_MS} vs preview $previewTime -> covered=$covered")
        return covered
    }

    /** GitHub's `status` of [head] relative to [base] ("ahead", "behind", "identical", "diverged"); null if unavailable. */
    private fun compareStatus(base: String, head: String): String? = try {
        commitsClient.newCall(releaseRequest("https://api.github.com/repos/$Repo/compare/$base...$head"))
            .execute().use { response ->
                if (!response.isSuccessful) return@use null
                JSONObject(response.body?.string() ?: return@use null).optString("status").ifBlank { null }
            }
    } catch (e: Exception) {
        Log.w(Tag, "Could not compare this build with the preview.", e)
        null
    }

    /** `2026-11-14T12:34:56Z` -> epoch millis, or null. */
    private fun parseIsoUtc(value: String): Long? = runCatching {
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .parse(value)?.time
    }.getOrNull()

    /**
     * Size of the APK for [release]: the feed's own size when it has one, else
     * (auto previews list sizes as 0) the server's Content-Length via HEAD. 0 if unknown.
     */
    suspend fun fetchApkSize(release: XmdRelease): Long = withContext(Dispatchers.IO) {
        val asset = selectApkAsset(release) ?: return@withContext 0L
        if (asset.size > 0L) return@withContext asset.size
        try {
            val request = Request.Builder().url(asset.downloadUrl).head().header("User-Agent", userAgent()).build()
            apiClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.header("Content-Length")?.toLongOrNull() ?: 0L else 0L
            }
        } catch (_: Exception) {
            0L
        }
    }

    // ---- Preview "What's new": commits since this build, summarised --------

    private suspend fun previewCommitSummary(headSha: String): String? = withContext(Dispatchers.IO) {
        try {
            val baseSha = BuildConfig.GIT_SHA.takeIf { it.isNotBlank() } ?: lookupPreviewRunSha(BuildConfig.PREVIEW_RUN)
            val fromCompare = if (baseSha != null && !baseSha.equals(headSha, ignoreCase = true)) {
                fetchCompareMessages(baseSha, headSha)
            } else {
                null
            }
            summarizeCommitMessages(fromCompare ?: fetchRecentMessages(headSha))
        } catch (e: Exception) {
            Log.w(Tag, "Could not build the preview change summary.", e)
            null
        }
    }

    /** head_sha of the successful preview.yml run [runNumber] -- for builds that predate BuildConfig.GIT_SHA. */
    private fun lookupPreviewRunSha(runNumber: Int): String? {
        if (runNumber <= 0) return null
        val url = PreviewRunsUrl.removeSuffix("&per_page=1") + "&per_page=100"
        return commitsClient.newCall(releaseRequest(url)).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val body = response.body?.string() ?: return@use null
            val runs = JSONObject(body).optJSONArray("workflow_runs") ?: return@use null
            (0 until runs.length())
                .mapNotNull { runs.optJSONObject(it) }
                .firstOrNull { it.optInt("run_number", -1) == runNumber }
                ?.optString("head_sha")
                ?.ifBlank { null }
        }
    }

    /** Commit messages between [base] and [head], newest first; null if GitHub can't compare them (e.g. rewritten history). */
    private fun fetchCompareMessages(base: String, head: String): List<String>? =
        commitsClient.newCall(releaseRequest("https://api.github.com/repos/$Repo/compare/$base...$head")).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val body = response.body?.string() ?: return@use null
            val commits = JSONObject(body).optJSONArray("commits") ?: return@use null
            (0 until commits.length())
                .mapNotNull { commits.optJSONObject(it)?.optJSONObject("commit")?.optString("message") }
                .asReversed()
        }

    /** Fallback when there is no base to compare against: the latest commits leading to [head]. */
    private fun fetchRecentMessages(head: String): List<String> =
        commitsClient.newCall(releaseRequest("https://api.github.com/repos/$Repo/commits?sha=$head&per_page=15")).execute().use { response ->
            if (!response.isSuccessful) return@use emptyList()
            val body = response.body?.string() ?: return@use emptyList()
            val commits = JSONArray(body)
            (0 until commits.length())
                .mapNotNull { commits.optJSONObject(it)?.optJSONObject("commit")?.optString("message") }
        }

    fun ignoreVersion(version: String, channel: Settings.UpdateChannel) {
        prefs.edit().putString(ignoredVersionKey(channel), version).apply()
    }

    private fun ignoredVersionKey(channel: Settings.UpdateChannel): String =
        "ignored_version_${channel.name.lowercase()}"

    fun selectApkAsset(release: XmdRelease): UpdateAsset? =
        selectXmdApkAsset(release.assets, BuildConfig.FLAVOR, Build.SUPPORTED_ABIS.toList())

    fun downloadUpdate(release: XmdRelease): Flow<Float> {
        val asset = selectApkAsset(release) ?: throw IOException("No compatible APK asset found")
        return downloadApkParallel(asset.downloadUrl, File(updatesDir, asset.name), asset.name, asset.size)
    }

    fun getApkFile(release: XmdRelease): File? {
        val asset = selectApkAsset(release) ?: return null
        val destination = File(updatesDir, asset.name)
        val hasParts = updatesDir.listFiles()?.any { it.name.startsWith("${asset.name}.part") } == true
        return if (destination.exists() && destination.length() > 0L && !hasParts) destination else null
    }

    fun getExistingProgress(release: XmdRelease): Float {
        val asset = selectApkAsset(release) ?: return 0f
        val destination = File(updatesDir, asset.name)
        if (destination.exists() && destination.length() > 0L) return 100f
        if (asset.size <= 0L) return 0f
        val partBytes = updatesDir.listFiles()
            ?.filter { it.name.startsWith("${asset.name}.part") }
            ?.sumOf { it.length() } ?: 0L
        if (partBytes > 0L) {
            return ((partBytes.toFloat() / asset.size.toFloat()) * 100f).coerceIn(0f, 99f)
        }
        return 0f
    }

    fun clearCache() {
        updatesDir.listFiles()?.forEach { it.delete() }
        // APKs the pre-manager updater left directly in cacheDir.
        appContext.cacheDir.listFiles()?.forEach {
            if (it.isFile && it.name.startsWith("Xmd-") && it.name.endsWith(".apk")) it.delete()
        }
    }

    // ---- Network: release feeds ------------------------------------------

    /** GitHub's own "newest non-prerelease" pointer; null when the repo has none (404). */
    private suspend fun getLatestRelease(): XmdRelease? = withContext(Dispatchers.IO) {
        apiClient.newCall(releaseRequest(LatestReleaseUrl)).execute().use { response ->
            if (response.code == 404) return@withContext null
            if (!response.isSuccessful) throw CheckFailedException("HTTP ${response.code}")
            val body = response.body?.string() ?: throw CheckFailedException("Empty response")
            parseRelease(JSONObject(body))
                ?: throw CheckFailedException("Release response missing tag_name")
        }
    }

    private suspend fun getLatestPrerelease(): XmdRelease? = withContext(Dispatchers.IO) {
        // Primary feed: the newest successful preview.yml workflow run (APKs on Pages).
        fetchLatestPreviewRun()?.let { return@withContext it }
        // Fallback: the newest hand-cut GitHub prerelease (prerelease.yml).
        apiClient.newCall(releaseRequest(ReleasesListUrl)).execute().use { response ->
            if (!response.isSuccessful) throw CheckFailedException("HTTP ${response.code}")
            val body = response.body?.string() ?: throw CheckFailedException("Empty response")
            val array = JSONArray(body)
            for (i in 0 until array.length()) {
                val release = array.optJSONObject(i)?.let(::parseRelease) ?: continue
                // The old rolling "preview" release is not a feed any more.
                if (release.prerelease && release.tagName != PreviewReleaseTag) return@withContext release
            }
            null
        }
    }

    /**
     * Newest successful run of preview.yml. A run only succeeds after its APKs
     * were deployed to Pages, so the files below exist once the run is listed.
     * Null when there is no run yet or the feed is unreachable (the caller then
     * falls back to hand-cut prereleases).
     */
    private suspend fun fetchLatestPreviewRun(): XmdRelease? = withContext(Dispatchers.IO) {
        try {
            apiClient.newCall(releaseRequest(PreviewRunsUrl)).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val run = runCatching { JSONObject(body).optJSONArray("workflow_runs")?.optJSONObject(0) }
                    .getOrNull() ?: return@withContext null
                previewReleaseFromRun(
                    runNumber = run.optInt("run_number", 0),
                    headSha = run.optString("head_sha"),
                    updatedAt = run.optString("updated_at"),
                    runUrl = run.optString("html_url", ReleasesFallbackUrl),
                    siteBase = PreviewSiteBase,
                )
            }
        } catch (_: IOException) {
            null
        }
    }

    private fun parseRelease(json: JSONObject): XmdRelease? {
        val tagName = json.optString("tag_name").ifBlank { return null }
        val assetsJson = json.optJSONArray("assets")
        val assets = buildList {
            if (assetsJson != null) {
                for (i in 0 until assetsJson.length()) {
                    val a = assetsJson.optJSONObject(i) ?: continue
                    val name = a.optString("name").ifBlank { continue }
                    val url = a.optString("browser_download_url").ifBlank { continue }
                    add(UpdateAsset(name = name, downloadUrl = url, size = a.optLong("size", 0L)))
                }
            }
        }
        return XmdRelease(
            tagName = tagName,
            name = json.optString("name", tagName),
            htmlUrl = json.optString("html_url", ReleasesFallbackUrl),
            body = json.optString("body", ""),
            publishedAt = json.optString("published_at", ""),
            prerelease = json.optBoolean("prerelease", false),
            assets = assets,
            commitCount = if (json.has("commit_count")) json.optInt("commit_count") else null,
            commitSha = json.optString("commit_sha").ifBlank { null },
        )
    }

    private fun releaseRequest(url: String): Request =
        Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", userAgent())
            .header("Cache-Control", "no-cache")
            .build()

    private fun userAgent() = "Xmd/${BuildConfig.VERSION_NAME}"

    // ---- Network: parallel + resumable APK download ----------------------

    private fun downloadApkParallel(
        url: String,
        destination: File,
        assetName: String,
        expectedTotalBytes: Long,
    ): Flow<Float> = channelFlow {
        var totalBytes = expectedTotalBytes

        // Unknown size: ask the server via HEAD.
        if (totalBytes <= 0L) {
            val headReq = Request.Builder().url(url).head().header("User-Agent", userAgent()).build()
            try {
                downloadClient.newCall(headReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        totalBytes = resp.header("Content-Length")?.toLongOrNull() ?: 0L
                    }
                }
            } catch (_: Exception) {
            }
        }

        val numChunks = if (totalBytes >= MinParallelBytes) ParallelChunkCount else 1
        val singlePartFile = File(updatesDir, "$assetName.part")

        if (numChunks == 1) {
            downloadSingleStream(url, destination, singlePartFile, totalBytes) { p -> send(p) }
            send(100f)
            return@channelFlow
        }

        val chunkSize = totalBytes / numChunks
        val partFiles = (0 until numChunks).map { i -> File(updatesDir, "$assetName.part$i") }

        val allCompleted = partFiles.mapIndexed { i, file ->
            val startByte = i * chunkSize
            val endByte = if (i == numChunks - 1) totalBytes - 1 else (i + 1) * chunkSize - 1
            file.exists() && file.length() >= endByte - startByte + 1
        }.all { it }

        if (allCompleted) {
            mergeChunkFiles(partFiles, destination)
            send(100f)
            return@channelFlow
        }

        val writtenPerChunk = AtomicLongArray(numChunks)
        for (i in 0 until numChunks) {
            writtenPerChunk.set(i, if (partFiles[i].exists()) partFiles[i].length() else 0L)
        }
        val lastEmittedPercent = AtomicInteger(-1)

        fun checkAndSendProgress() {
            var sum = 0L
            for (i in 0 until numChunks) sum += writtenPerChunk.get(i)
            if (totalBytes > 0L) {
                val percent = ((sum.toDouble() / totalBytes.toDouble()) * 100.0).toFloat().coerceIn(0f, 99.9f)
                val pInt = percent.toInt()
                val prev = lastEmittedPercent.get()
                if (pInt != prev && lastEmittedPercent.compareAndSet(prev, pInt)) trySend(percent)
            }
        }

        checkAndSendProgress() // resuming: show what's already on disk

        coroutineScope {
            val jobs = (0 until numChunks).map { i ->
                val startByte = i * chunkSize
                val endByte = if (i == numChunks - 1) totalBytes - 1 else (i + 1) * chunkSize - 1
                val targetSize = endByte - startByte + 1
                val partFile = partFiles[i]

                async(Dispatchers.IO) {
                    var existingLen = if (partFile.exists()) partFile.length() else 0L
                    if (existingLen >= targetSize) {
                        writtenPerChunk.set(i, targetSize)
                        return@async
                    }

                    var response = downloadClient.newCall(
                        rangeRequest(url, startByte + existingLen, endByte),
                    ).execute()

                    if (response.code == 416) {
                        response.close()
                        partFile.delete()
                        existingLen = 0L
                        writtenPerChunk.set(i, 0L)
                        response = downloadClient.newCall(rangeRequest(url, startByte, endByte)).execute()
                    }

                    if (!response.isSuccessful) {
                        val code = response.code
                        response.close()
                        throw IOException("HTTP error $code for chunk $i")
                    }

                    val body = response.body ?: throw IOException("Empty body for chunk $i")
                    val appendMode = response.code == 206 && existingLen > 0L
                    if (!appendMode) {
                        existingLen = 0L
                        if (partFile.exists()) partFile.delete()
                    }

                    body.byteStream().use { input ->
                        FileOutputStream(partFile, appendMode).use { output ->
                            val buffer = ByteArray(32 * 1024)
                            var read: Int
                            var currentWritten = existingLen
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                currentWritten += read
                                writtenPerChunk.set(i, currentWritten)
                                checkAndSendProgress()
                            }
                            output.flush()
                        }
                    }
                }
            }
            jobs.awaitAll()
        }

        mergeChunkFiles(partFiles, destination)
        send(100f)
    }.flowOn(Dispatchers.IO)

    private fun rangeRequest(url: String, from: Long, to: Long): Request =
        Request.Builder()
            .url(url)
            .header("Range", "bytes=$from-$to")
            .header("User-Agent", userAgent())
            .build()

    private fun mergeChunkFiles(partFiles: List<File>, destination: File) {
        if (destination.exists()) destination.delete()
        val tempDest = File(destination.parentFile, "${destination.name}.merge_tmp")
        if (tempDest.exists()) tempDest.delete()

        FileOutputStream(tempDest).use { out ->
            for (file in partFiles) file.inputStream().use { it.copyTo(out) }
            out.flush()
        }
        if (!tempDest.renameTo(destination)) {
            tempDest.copyTo(destination, overwrite = true)
            tempDest.delete()
        }
        partFiles.forEach { it.delete() }
    }

    private suspend fun downloadSingleStream(
        url: String,
        destination: File,
        tempFile: File,
        expectedTotalBytes: Long,
        onProgress: suspend (Float) -> Unit,
    ) {
        var existingLength = if (tempFile.exists()) tempFile.length() else 0L

        if (expectedTotalBytes > 0L && existingLength >= expectedTotalBytes) {
            promote(tempFile, destination)
            return
        }

        val requestBuilder = Request.Builder().url(url).header("User-Agent", userAgent())
        if (existingLength > 0L) requestBuilder.header("Range", "bytes=$existingLength-")

        var response = downloadClient.newCall(requestBuilder.build()).execute()

        if (response.code == 416) {
            response.close()
            tempFile.delete()
            existingLength = 0L
            response = downloadClient.newCall(
                Request.Builder().url(url).header("User-Agent", userAgent()).build(),
            ).execute()
        }

        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw IOException("Unexpected download code: $code")
        }

        val body = response.body ?: throw IOException("Empty download response")
        val appendMode = response.code == 206 && existingLength > 0L
        if (!appendMode) {
            existingLength = 0L
            if (tempFile.exists()) tempFile.delete()
        }

        val contentLength = body.contentLength()
        val totalBytes = if (contentLength > 0L) {
            if (appendMode) existingLength + contentLength else contentLength
        } else {
            expectedTotalBytes
        }

        body.byteStream().use { input ->
            FileOutputStream(tempFile, appendMode).use { output ->
                val buffer = ByteArray(32 * 1024)
                var bytesRead: Int
                var written = existingLength
                var lastEmittedPercent = -1

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    written += bytesRead
                    if (totalBytes > 0L) {
                        val percent = ((written.toDouble() / totalBytes.toDouble()) * 100.0)
                            .toFloat().coerceIn(0f, 99.9f)
                        val pInt = percent.toInt()
                        if (pInt != lastEmittedPercent) {
                            lastEmittedPercent = pInt
                            onProgress(percent)
                        }
                    } else {
                        onProgress(-1f)
                    }
                }
                output.flush()
            }
        }
        promote(tempFile, destination)
    }

    private fun promote(tempFile: File, destination: File) {
        if (destination.exists()) destination.delete()
        if (!tempFile.renameTo(destination)) {
            tempFile.copyTo(destination, overwrite = true)
            tempFile.delete()
        }
    }

    private companion object {
        const val Tag = "XmdUpdate"
        const val PreferencesName = "xmd_update"
        const val Repo = "Utsavrajputt/xmd"
        const val PreviewReleaseTag = "preview"
        const val LatestReleaseUrl = "https://api.github.com/repos/$Repo/releases/latest"
        const val ReleasesListUrl = "https://api.github.com/repos/$Repo/releases?per_page=20"
        const val ReleasesFallbackUrl = "https://github.com/$Repo/releases"

        /** Newest successful run of .github/workflows/preview.yml (no token needed, repo is public). */
        const val PreviewRunsUrl =
            "https://api.github.com/repos/$Repo/actions/workflows/preview.yml/runs?status=success&per_page=1"

        /** GitHub Pages site; preview APKs live under /previews/r<run>/ (see scripts/stage_pages.py). */
        const val PreviewSiteBase = "https://utsavrajputt.github.io/xmd"

        const val ParallelChunkCount = 4
        const val MinParallelBytes = 2 * 1024 * 1024L // 2MB

        val apiClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
        /** Change-summary lookups are optional garnish: never let them hold the sheet up for long. */
        val commitsClient: OkHttpClient = apiClient.newBuilder()
            .callTimeout(6, TimeUnit.SECONDS)
            .build()
        val downloadClient: OkHttpClient = OkHttpClient.Builder()
            .dispatcher(Dispatcher().apply {
                maxRequests = 16
                maxRequestsPerHost = 8
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }
}

// ---- Pure helpers (unit-tested) ------------------------------------------

private val SupportedAbis = listOf("arm64-v8a", "armeabi-v7a")

/**
 * Picks this build's own flavor asset ("Xmd-lite-..." / "Xmd-full-...") for
 * the device's primary supported ABI, falling back to the other ABI Xmd
 * ships (e.g. an x86 emulator). Null if the release has nothing compatible.
 */
internal fun selectXmdApkAsset(
    assets: List<UpdateAsset>,
    flavor: String,
    deviceAbis: List<String>,
): UpdateAsset? {
    val flavorAssets = assets.filter {
        it.downloadUrl.isNotBlank() &&
            it.name.endsWith(".apk", ignoreCase = true) &&
            it.name.startsWith("Xmd-$flavor-", ignoreCase = true)
    }
    val primary = deviceAbis.firstOrNull { it in SupportedAbis } ?: SupportedAbis[0]
    return flavorAssets.firstOrNull { it.name.hasAssetToken(primary) }
        ?: flavorAssets.firstOrNull { asset ->
            SupportedAbis.any { it != primary && asset.name.hasAssetToken(it) }
        }
}

private fun String.hasAssetToken(token: String): Boolean =
    Regex("(?:^|-)${Regex.escape(token)}(?:-|\\.apk$)", RegexOption.IGNORE_CASE).containsMatchIn(this)

/**
 * Semver-style parse of an Xmd version string. Build-flavor suffixes
 * ("-lite", "-full" on installed versionNames) are ignored; only a real
 * pre-release marker (alpha / beta / rc / preview, optionally with a number,
 * e.g. "beta.6" or the auto-preview "beta.r324") makes a version a pre-release.
 */
private data class ParsedVersion(val base: List<Int>, val stage: Int?, val number: Int)

private val PreReleaseRegex =
    Regex("""(?:^|[-.])(alpha|beta|rc|preview)(?:[.-]?r?)(\d+)?""", RegexOption.IGNORE_CASE)

private fun String.parseVersion(): ParsedVersion? {
    val base = versionParts() ?: return null
    val suffix = trim().removePrefix("v").removePrefix("V").substringAfter('-', "")
    val match = PreReleaseRegex.find(suffix) ?: return ParsedVersion(base, stage = null, number = 0)
    val stage = when (match.groupValues[1].lowercase()) {
        "alpha" -> 0
        "beta", "preview" -> 1
        else -> 2 // rc
    }
    return ParsedVersion(base, stage, match.groupValues[2].toIntOrNull() ?: 0)
}

private fun String.versionParts(): List<Int>? {
    val normalized = trim().removePrefix("v").removePrefix("V").substringBefore('-')
    if (normalized.isBlank()) return null
    return normalized.split('.').map { part ->
        val digits = part.takeWhile(Char::isDigit)
        if (digits.isBlank()) return null
        digits.toIntOrNull() ?: return null
    }
}

private fun compareBase(a: List<Int>, b: List<Int>): Int {
    val size = maxOf(a.size, b.size)
    for (i in 0 until size) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x.compareTo(y)
    }
    return 0
}

/**
 * True when [candidate] is newer than [current] by semver rules: a higher
 * base version wins; on an equal base, a stable release beats any
 * pre-release of it (1.0.0 > 1.0.0-beta.6) and pre-releases order by
 * stage (alpha < beta < rc) then number.
 */
fun isVersionNewer(candidate: String, current: String): Boolean {
    val c = candidate.parseVersion() ?: return false
    val k = current.parseVersion() ?: return false
    val base = compareBase(c.base, k.base)
    if (base != 0) return base > 0
    return when {
        c.stage == null && k.stage == null -> false
        c.stage == null -> true // stable vs installed pre-release of the same base
        k.stage == null -> false // pre-release is never newer than the installed stable
        c.stage != k.stage -> c.stage > k.stage
        else -> c.number > k.number
    }
}

/** Hand-cut pre-release tags (`v1.1.0-beta.2`, `-rc.1`): same semver rules as [isVersionNewer]. */
fun isPreviewNewer(candidate: String, current: String): Boolean = isVersionNewer(candidate, current)

/**
 * Auto previews carry the preview.yml run number, compared against this
 * build's BuildConfig.PREVIEW_RUN (passed as [currentGitCount]; 0 for non-preview
 * builds, which are offered any preview); hand-cut prereleases fall back to tag comparison.
 */
internal fun isPreviewReleaseNewer(release: XmdRelease, currentGitCount: Int, currentVersion: String): Boolean {
    val remoteCount = release.previewBuildNumber()
    if (remoteCount != null) return remoteCount > currentGitCount
    return isPreviewNewer(release.tagName, currentVersion)
}

private val ManifestTagRegex = Regex("""preview-r(\d+)""", RegexOption.IGNORE_CASE)

/** Build number of an auto-built preview (workflow run number, `preview-r123` tag); null for other releases. */
internal fun XmdRelease.previewBuildNumber(): Int? =
    commitCount ?: ManifestTagRegex.find(tagName)?.groupValues?.getOrNull(1)?.toIntOrNull()

private val PreviewFlavors = listOf("lite", "full")
private val PreviewAbis = listOf("arm64-v8a", "armeabi-v7a")

/**
 * Builds the [XmdRelease] for a preview.yml run. The APK URLs are predictable
 * (scripts/stage_pages.py): `<site>/previews/r<run>/Xmd-<flavor>-<abi>-preview-r<run>.apk`.
 * Sizes are unknown here (0); the downloader asks the server via HEAD.
 * Returns null for a run without a usable number.
 */
internal fun previewReleaseFromRun(
    runNumber: Int,
    headSha: String,
    updatedAt: String,
    runUrl: String,
    siteBase: String,
): XmdRelease? {
    if (runNumber <= 0) return null
    val base = siteBase.trimEnd('/')
    val assets = PreviewFlavors.flatMap { flavor ->
        PreviewAbis.map { abi ->
            val name = "Xmd-$flavor-$abi-preview-r$runNumber.apk"
            UpdateAsset(name = name, downloadUrl = "$base/previews/r$runNumber/$name", size = 0L)
        }
    }
    return XmdRelease(
        tagName = "preview-r$runNumber",
        name = "Xmd Preview r$runNumber",
        htmlUrl = runUrl,
        body = if (headSha.isNotBlank()) "Automatic preview from ${headSha.take(7)}." else "",
        publishedAt = updatedAt,
        prerelease = true,
        assets = assets,
        commitCount = runNumber,
        commitSha = headSha.ifBlank { null },
    )
}
