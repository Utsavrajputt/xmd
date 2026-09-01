package com.invictus.xmd.core

import android.content.Context
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File

/**
 * Wraps youtubedl-android (bundled yt-dlp + ffmpeg binaries) for the
 * YouTube download path -- "full" flavor only (see app/build.gradle.kts):
 * the python+ffmpeg binaries this needs can't be downloaded at runtime on
 * Android 10+ (apps targeting API 29+ can't execve() a file they wrote
 * themselves -- the W^X restriction -- so these have to ship bundled as
 * native libs, extracted by the OS installer, which is exempt), so YouTube
 * support is a separate, larger APK instead of an in-app download.
 *
 * Differs completely in shape from DownloadEngine/TorrentEngine:
 *  - yt-dlp resolves the video, downloads it, and (for qualities above what
 *    a single progressive stream offers) merges separate video+audio
 *    streams with ffmpeg, all in one call.
 *  - Progress is a 0-100 percentage from yt-dlp itself, not bytes.
 *  - Cancellation is by process id, not by closing an OkHttp Call / calling
 *    into a torrent engine handle.
 */
object YtDlpManager {

    private const val TAG = "YtDlpManager"

    /** Shared with DownloadService, which re-derives a QualityOption from a persisted QueueItem's formatSelector alone. */
    const val AUDIO_ONLY_SELECTOR = "bestaudio/best"

    @Volatile private var initialized = false

    /** Data for one row in the quality-picker dialog. */
    data class QualityOption(
        val label: String,
        /** yt-dlp `-f` format selector. */
        val formatSelector: String,
        val isAudioOnly: Boolean
    )

    /**
     * Fixed, simplified quality ladder (over the full raw yt-dlp format
     * list). Each video selector falls back gracefully to whatever's
     * actually available at or below that height -- yt-dlp doesn't error
     * out if e.g. a short/low-res video has no 1080p stream, it just picks
     * the closest match, so there's no need to probe the video's real
     * format list before showing this list.
     */
    /**
     * [isGenericOrHls] true for any non-YouTube link routed here (plain
     * .m3u8/.mpd manifests and other generic-extractor sites) -- these
     * frequently report formats with no `height` field at all (e.g. a
     * devstreaming-cdn HLS master playlist exposing only `hls-0`, `hls-1`
     * IDs), which makes every height-gated alternative in [videoSelector]'s
     * chain match nothing and yt-dlp fail with "Requested format is not
     * available". YouTube's extractor always reports height, so its
     * selector chain is left exactly as before.
     */
    fun standardQualityOptions(isGenericOrHls: Boolean = false): List<QualityOption> = listOf(
        QualityOption("4K (2160p)", videoSelector(2160, isGenericOrHls), isAudioOnly = false),
        QualityOption("1440p",      videoSelector(1440, isGenericOrHls), isAudioOnly = false),
        QualityOption("1080p",      videoSelector(1080, isGenericOrHls), isAudioOnly = false),
        QualityOption("720p",       videoSelector(720,  isGenericOrHls), isAudioOnly = false),
        QualityOption("360p",       videoSelector(360,  isGenericOrHls), isAudioOnly = false),
        QualityOption("144p",       videoSelector(144,  isGenericOrHls), isAudioOnly = false),
        QualityOption("Audio only (${audioFormatShortLabel()})", AUDIO_ONLY_SELECTOR, isAudioOnly = true)
    )

    /**
     * Short display label for the "Audio only (…)" row, reflecting the
     * user's saved Settings.presetAudioFormat() -- this used to be
     * hardcoded to "MP3" regardless of the preset, so the picker kept
     * showing MP3 even after switching to Opus/M4A/Original.
     */
    private fun audioFormatShortLabel(): String = when (Settings.presetAudioFormat()) {
        Settings.AudioFormatPreset.MP3 -> "MP3"
        Settings.AudioFormatPreset.M4A -> "M4A"
        Settings.AudioFormatPreset.OPUS -> "Opus"
        Settings.AudioFormatPreset.ORIGINAL -> "Original"
    }

    /**
     * Builds the `-f` selector for one rung of [standardQualityOptions],
     * folding in the user's saved container/codec/fps preset (Settings)
     * on top of the height ceiling.
     *
     * Chains three alternatives, `/`-separated, so a preset never causes a
     * hard failure:
     *  1. Height + every preset filter that's set (exact match).
     *  2. Height alone -- today's behavior, if this video doesn't actually
     *     offer that container/codec/fps combination at this height.
     *  3. Plain `best[height<=H]` -- last-resort single-stream fallback.
     *
     * All three preset fields at ANY (nothing picked, the default) folds
     * back to exactly the original unconstrained selector.
     */
    private fun videoSelector(maxHeight: Int, isGenericOrHls: Boolean = false): String {
        val container = Settings.presetContainer()
        val codec = Settings.presetCodec()
        val fps = Settings.presetFps()

        // "<=?" instead of "<=" for generic/HLS links only: yt-dlp's "?"
        // operator matches a format if the field is within range *or* the
        // field is simply absent, instead of failing closed when it's
        // absent. Generic HLS/DASH manifests often don't report height at
        // all (e.g. a devstreaming-cdn master playlist exposing only
        // hls-0/hls-1 IDs) -- plain "<=" then matches nothing and yt-dlp
        // errors with "Requested format is not available". YouTube always
        // reports height, so its comparator is left exactly as "<=" --
        // this only loosens matching for streams that genuinely give
        // yt-dlp nothing to check the cap against, never for YouTube.
        val heightCmp = if (isGenericOrHls) "<=?" else "<="
        // Trailing safety net, generic/HLS only: if literally nothing above
        // matches (both height *and* something else about the format are
        // unusual), fall back to plain "best" rather than erroring out.
        val genericFallback = if (isGenericOrHls) "/best" else ""

        if (container == Settings.ContainerPreset.ANY &&
            codec == Settings.CodecPreset.ANY &&
            fps == Settings.FpsPreset.ANY
        ) {
            return "bestvideo[height$heightCmp$maxHeight]+bestaudio/best[height$heightCmp$maxHeight]$genericFallback"
        }

        val videoFilters = buildList {
            add("height$heightCmp$maxHeight")
            container.ytDlpExt?.let { add("ext=$it") }
            codec.vcodecPrefix?.let { add("vcodec^=$it") }
            fps.maxFps?.let { add("fps<=$it") }
        }.joinToString("][")

        // Audio track container mirrors the chosen video container where
        // possible, so the merge doesn't need an extra re-encode -- MP4
        // pairs with m4a audio, WebM pairs with the webm/opus audio track
        // YouTube actually serves alongside it.
        val audioExt = when (container) {
            Settings.ContainerPreset.MP4 -> "m4a"
            Settings.ContainerPreset.WEBM -> "webm"
            Settings.ContainerPreset.ANY -> null
        }
        val strictAudio = audioExt?.let { "bestaudio[ext=$it]" } ?: "bestaudio"

        return "bestvideo[$videoFilters]+$strictAudio/bestvideo[height$heightCmp$maxHeight]+bestaudio/best[height$heightCmp$maxHeight]$genericFallback"
    }

    /** Result of [probeFormats]: every real stream yt-dlp reports for a URL, plus the video's duration (needed to estimate size for formats where yt-dlp doesn't report filesize directly). */
    data class ProbeResult(
        val formats: List<ProbedFormat>,
        val durationSeconds: Int?
    )

    /** One raw stream as reported by yt-dlp's own format probe (`-j`/getInfo, not the fixed [standardQualityOptions] ladder). */
    data class ProbedFormat(
        val formatId: String,
        val ext: String,
        val height: Int?,
        val fps: Int?,
        val vcodec: String?,
        val acodec: String?,
        /** Bytes, from filesize (exact) or filesize_approx -- null if yt-dlp couldn't report either. */
        val sizeBytes: Long?,
        /** Total bitrate in Kbit/s (tbr), used as a filesize fallback when sizeBytes is null. */
        val tbr: Double?
    ) {
        val isVideoOnly: Boolean get() = acodec == null || acodec == "none"
        val isAudioOnly: Boolean get() = vcodec == null || vcodec == "none"
    }

    /**
     * Real probe of every stream YouTube actually serves for [url] (the
     * advanced-settings tab in the quality picker), as opposed to
     * [standardQualityOptions]'s fixed simplified ladder.
     *
     * Runs yt-dlp with `--dump-json --no-download` as a plain [execute]
     * call (same request/response shape as [download] above) and parses
     * the raw JSON with org.json rather than going through the library's
     * typed getInfo()/VideoInfo wrapper -- org.json ships with Android, so
     * this needs no extra dependency, and reading the well-documented
     * yt-dlp JSON schema directly (yt-dlp's own `-j` field names:
     * format_id, height, fps, vcodec, acodec, filesize, filesize_approx,
     * tbr) is more robust than depending on a third-party wrapper's bean
     * field/getter names, which aren't part of any documented contract.
     *
     * Off the main thread, real network round-trip -- callers should
     * launch it fire-and-forget alongside showing the dialog, not block
     * dialog construction on it.
     *
     * Filters out storyboard/thumbnail pseudo-formats (mhtml, no vcodec
     * and no acodec) since those aren't downloadable video/audio streams.
     * Returns empty on any failure (network, extraction, not installed,
     * unparseable output) rather than throwing -- the advanced tab just
     * shows "couldn't load extra formats" and the standard ladder above
     * still works regardless.
     */
    fun probeFormats(url: String, context: Context): ProbeResult {
        if (!ensureReady(context)) return ProbeResult(emptyList(), null)
        return try {
            val request = YoutubeDLRequest(url)
            request.addOption("--dump-json")
            request.addOption("--no-download")
            request.addOption("--no-playlist")
            val response = YoutubeDL.getInstance().execute(request, "probe-" + System.nanoTime()) { _, _, _ -> }

            // --dump-json prints exactly one JSON object per line (one
            // line here, since --no-playlist forces a single video) --
            // lastOrNull skips over any [info]/[youtube] noise lines that
            // aren't JSON.
            val jsonLine = response.out
                .lineSequence()
                .map { it.trim() }
                .lastOrNull { it.startsWith("{") }
                ?: return ProbeResult(emptyList(), null)

            val root = org.json.JSONObject(jsonLine)
            val duration = root.optInt("duration", -1).takeIf { it > 0 }
            val formats = root.optJSONArray("formats") ?: return ProbeResult(emptyList(), duration)

            val parsed = (0 until formats.length()).mapNotNull { i ->
                val f = formats.optJSONObject(i) ?: return@mapNotNull null
                val vcodec = f.optString("vcodec", "none").takeIf { it != "none" }
                val acodec = f.optString("acodec", "none").takeIf { it != "none" }
                if (vcodec == null && acodec == null) return@mapNotNull null
                val formatId = f.optString("format_id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val filesize = f.optLong("filesize", -1L).takeIf { it > 0 }
                val filesizeApprox = f.optLong("filesize_approx", -1L).takeIf { it > 0 }
                ProbedFormat(
                    formatId = formatId,
                    ext = f.optString("ext").takeIf { it.isNotBlank() } ?: "mp4",
                    height = f.optInt("height", -1).takeIf { it > 0 },
                    fps = f.optInt("fps", -1).takeIf { it > 0 },
                    vcodec = vcodec,
                    acodec = acodec,
                    sizeBytes = filesize ?: filesizeApprox,
                    tbr = f.optDouble("tbr", -1.0).takeIf { it > 0 }
                )
            }
            ProbeResult(parsed, duration)
        } catch (e: Throwable) {
            Log.w(TAG, "Format probe failed for $url", e)
            ProbeResult(emptyList(), null)
        }
    }

    /**
     * Builds a `-f` selector for one specific video-only [ProbedFormat]
     * merged with the best matching audio -- mirrors [videoSelector] but
     * pins the exact formatId instead of a height ceiling, so advanced
     * picks download the exact stream shown (exact fps/codec/bitrate)
     * rather than whatever yt-dlp would otherwise pick at that height.
     */
    fun advancedSelector(format: ProbedFormat): String = when {
        format.isAudioOnly -> format.formatId
        format.isVideoOnly -> "${format.formatId}+bestaudio"
        else -> format.formatId // already muxed (progressive) stream
    }

    /** Human-readable "~45.2 MB" from bytes, or a bitrate-derived estimate, or null if neither is known. */
    fun formatSize(format: ProbedFormat, durationSeconds: Int?): String? {
        val bytes = format.sizeBytes
            ?: format.tbr?.takeIf { durationSeconds != null && durationSeconds > 0 }
                ?.let { tbrKbps -> (tbrKbps * 1000 / 8 * durationSeconds!!).toLong() }
            ?: return null
        val approx = format.sizeBytes == null
        val mb = bytes / (1024.0 * 1024.0)
        val gb = mb / 1024.0
        val text = if (gb >= 1.0) "%.2f GB".format(gb) else "%.1f MB".format(mb)
        return if (approx) "~$text" else text
    }

    /**
     * True once the user has tapped Install in Settings and it succeeded.
     * Nothing is unpacked automatically on app start -- [ensureReady] does
     * the (cheap, already-unpacked) re-init per process lifetime only if
     * this is true.
     */
    fun isInstalled(context: Context): Boolean = Settings.ytDlpInstalled()

    /**
     * Unpacks the bundled yt-dlp + ffmpeg binaries to internal storage.
     * Slow-ish the first time; call off the main thread. Persists the
     * "installed" flag on success so [isInstalled] survives process death.
     *
     * Returns null on success, or the failure's message/class name on
     * failure -- shown directly in Settings so a failure is diagnosable
     * without needing logcat. Catches Throwable, not just Exception -- the
     * underlying library unpacks a bundled python interpreter + native
     * ffmpeg/ffprobe binaries via internal reflection/JNI plumbing, which
     * can surface as an Error subtype (UnsatisfiedLinkError,
     * NoClassDefFoundError) rather than a plain Exception if anything about
     * that goes wrong (missing ProGuard keep rule, corrupted unpack,
     * unsupported ABI, low storage).
     */
    @Synchronized
    fun install(context: Context): String? {
        return try {
            YoutubeDL.getInstance().init(context)
            FFmpeg.getInstance().init(context)
            initialized = true
            Settings.setYtDlpInstalled(true)
            null
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to install yt-dlp/ffmpeg", e)
            initialized = false
            Settings.setYtDlpInstalled(false)
            "${e.javaClass.simpleName}: ${e.message ?: "no message"}"
        }
    }

    /**
     * Removes the unpacked binaries to reclaim storage and flips
     * [isInstalled] back to false. The bundled assets inside the APK
     * itself aren't affected -- tapping Install again just re-unpacks them.
     */
    @Synchronized
    fun delete(context: Context) {
        initialized = false
        Settings.setYtDlpInstalled(false)
        // youtubedl-android/ffmpeg-kit unpack under the app's internal
        // files dir; matched heuristically by name rather than a hardcoded
        // path since the exact folder name isn't a stable public API.
        runCatching {
            context.filesDir?.listFiles()?.forEach { f ->
                val n = f.name.lowercase()
                if (f.isDirectory && ("youtubedl" in n || "python" in n || "ffmpeg" in n)) {
                    f.deleteRecursively()
                }
            }
        }
    }

    /**
     * Re-attaches to already-unpacked binaries at the start of a fresh
     * process (the in-memory [initialized] flag doesn't survive process
     * death, but the unpacked files on disk do) -- cheap/near-instant when
     * [isInstalled] is true, since there's nothing left to unpack.
     * Returns false without doing anything if the user never installed it.
     *
     * Also throttled-checks for a newer yt-dlp release (roughly once a day)
     * -- the bundled yt-dlp version goes stale within weeks since YouTube
     * changes its page structure often, and yt-dlp itself warns loudly (and
     * downloads can start failing) once it's more than ~90 days old. This
     * is a plain script download (yt-dlp is Python, not a compiled
     * binary), so it doesn't hit the same Android 10+ W^X restriction that
     * rules out downloading the interpreter/ffmpeg themselves at runtime.
     * Best-effort: a failed update check (e.g. no internet) doesn't block
     * the download, it just means yt-dlp isn't yet as fresh as it could be.
     */
    @Synchronized
    fun ensureReady(context: Context): Boolean {
        if (!isInstalled(context)) return false
        if (!initialized) {
            val installError = install(context)
            if (installError != null) return false
        }

        val oneDayMs = 24L * 60 * 60 * 1000
        if (System.currentTimeMillis() - Settings.ytDlpLastUpdateMs() > oneDayMs) {
            val channel = if (Settings.ytDlpUseNightly()) YoutubeDL.UpdateChannel._NIGHTLY else YoutubeDL.UpdateChannel._STABLE
            runCatching {
                YoutubeDL.getInstance().updateYoutubeDL(context, channel)
            }.onFailure { Log.w(TAG, "yt-dlp self-update check failed (will retry later)", it) }
            Settings.setYtDlpLastUpdateMs(System.currentTimeMillis())
        }
        return true
    }

    /**
     * Manual "Update" button in Settings -- explicit, immediate check on
     * whichever channel [Settings.ytDlpUseNightly] currently points at,
     * instead of waiting for [ensureReady]'s once-a-day background check.
     * Only makes sure yt-dlp is initialized first (cheap re-init from
     * already-unpacked files, not a real network call) rather than routing
     * through [ensureReady] itself, since that has its own 24h throttle that
     * would otherwise silently no-op a manual tap that happens to land
     * inside the same day as the last background check.
     *
     * Returns a status string combining yt-dlp's own UpdateStatus (e.g.
     * "ALREADY_UP_TO_DATE" / "DONE") with the resulting version name, so
     * Settings can show something more useful than a bare success toast, or
     * null on failure.
     */
    @Synchronized
    fun update(context: Context): String? {
        if (!isInstalled(context)) return null
        if (!initialized) {
            val installError = install(context)
            if (installError != null) return null
        }
        val channel = if (Settings.ytDlpUseNightly()) YoutubeDL.UpdateChannel._NIGHTLY else YoutubeDL.UpdateChannel._STABLE
        return try {
            val status = YoutubeDL.getInstance().updateYoutubeDL(context, channel)
            Settings.setYtDlpLastUpdateMs(System.currentTimeMillis())
            val version = runCatching { YoutubeDL.getInstance().versionName(context) }.getOrNull()
            if (version != null) "$status ($version)" else status.toString()
        } catch (e: Throwable) {
            Log.e(TAG, "Manual yt-dlp update failed", e)
            null
        }
    }

    /**
     * Switches the release channel and immediately updates onto it (a bare
     * channel-preference flip with no accompanying update would leave
     * whatever version was already installed running until the next daily
     * background check, up to 24h away). [toNightly] false switches back to
     * stable and updates onto that instead -- same button/preference drives
     * both directions, matching the toggle in the reference (mpv-rx) this
     * was modeled on where flipping to stable is just "Update" with nightly
     * off.
     *
     * Returns the same status string as [update], or null on failure -- on
     * failure the channel preference is rolled back too, so a failed switch
     * doesn't leave Settings claiming a channel that was never actually
     * fetched.
     */
    @Synchronized
    fun switchChannel(context: Context, toNightly: Boolean): String? {
        val previousChannel = Settings.ytDlpUseNightly()
        Settings.setYtDlpUseNightly(toNightly)
        val result = update(context)
        if (result == null) Settings.setYtDlpUseNightly(previousChannel)
        return result
    }

    fun isReady(): Boolean = initialized

    /** One progress tick, parsed from yt-dlp's own stdout rather than trusting the library's percent-only callback alone (see parseProgressLine). */
    data class DownloadProgress(
        val percent: Int,
        /** e.g. "12.4MiB/s, ETA 00:32" -- null once nothing matches (postprocessing stages: merging, extracting audio, embedding thumbnail, etc). */
        val statusText: String?
    )

    // Matches yt-dlp's standard download-progress line, e.g.:
    // "[download]  42.5% of   10.32MiB at    1.21MiB/s ETA 00:07"
    // "[download] 100% of 3.45MiB in 00:02"
    private val ANSI_REGEX = Regex("""\u001B\[[0-?]*[ -/]*[@-~]""")
    private val DOWNLOAD_PERCENT = Regex("""\[download\]\s+([\d.]+)%""")
    private val DOWNLOAD_SIZE = Regex("""\bof\s+(~?\s*[\d.]+\s*[a-zA-Z]+)""")
    private val DOWNLOAD_SPEED = Regex("""\bat\s+([\d.]+\s*[a-zA-Z/]+)""")
    private val DOWNLOAD_ETA = Regex("""\bETA\s+(\S+)""")

    // Postprocessing stage markers -- these lines have no percentage at all,
    // so they only feed statusText (percent stays at whatever it last was).
    private val STAGE_LINE = Regex("""^\[(Merger|ExtractAudio|Metadata|EmbedThumbnail|ThumbnailsConvertor|VideoConvertor)]\s*(.*)$""")

    /**
     * Parses one line of yt-dlp's stdout into a [DownloadProgress], or null
     * if the line has nothing progress-related in it.
     */
    private fun parseProgressLine(rawLine: String, lastPercent: Int): DownloadProgress? {
        val line = rawLine.replace(ANSI_REGEX, "").trim()
        val percentMatch = DOWNLOAD_PERCENT.find(line)
        if (percentMatch != null) {
            val percent = percentMatch.groupValues[1].toFloatOrNull()?.toInt()?.coerceIn(0, 100) ?: lastPercent
            val size = DOWNLOAD_SIZE.find(line)?.groupValues?.get(1)?.trim()
            val speed = DOWNLOAD_SPEED.find(line)?.groupValues?.get(1)?.trim()?.takeUnless { it.startsWith("Unknown", ignoreCase = true) }
            val eta = DOWNLOAD_ETA.find(line)?.groupValues?.get(1)?.trim()?.takeUnless { it.equals("Unknown", ignoreCase = true) }
            val status = buildString {
                if (!speed.isNullOrEmpty()) append(speed)
                if (!size.isNullOrEmpty()) {
                    if (isNotEmpty()) append(" · ")
                    append(size)
                }
                if (!eta.isNullOrEmpty()) {
                    if (isNotEmpty()) append(" · ")
                    append("ETA $eta")
                }
            }.ifEmpty { null }
            return DownloadProgress(percent, status)
        }
        if (line.startsWith("[download] Destination:")) {
            return DownloadProgress(lastPercent.coerceAtLeast(0), "Starting download…")
        }
        STAGE_LINE.find(line)?.let { m ->
            val stageLabel = when (m.groupValues[1]) {
                "Merger" -> "Merging video + audio…"
                "ExtractAudio" -> "Extracting audio…"
                "ThumbnailsConvertor" -> "Converting thumbnail…"
                "EmbedThumbnail" -> "Embedding thumbnail…"
                "Metadata" -> "Writing metadata…"
                "VideoConvertor" -> "Converting video…"
                else -> null
            }
            return DownloadProgress(lastPercent.coerceAtLeast(0), stageLabel)
        }
        if (line.startsWith("[youtube]") || line.startsWith("[info]")) {
            return DownloadProgress(lastPercent.coerceAtLeast(0), "Connecting & preparing…")
        }
        return null
    }

    /**
     * Downloads (and, for merged qualities, muxes) the given YouTube URL
     * straight into [outputDir] using yt-dlp's own output template, so
     * there's no separate temp-then-move step like the DIRECT path -- yt-dlp
     * already writes/renames atomically itself.
     *
     * [processId] lets [cancel] target this specific download later.
     * [onProgress] receives a percent + human-readable status parsed from
     * yt-dlp's own stdout (see [parseProgressLine]) -- more reliable across
     * the whole download+postprocess run than the library's bare percent
     * callback, which goes quiet during postprocessing stages.
     *
     * Returns the final downloaded file, discovered via yt-dlp's
     * `--print after_move:filepath`, which prints the exact on-disk path
     * once any post-processing (merge/audio-extract) is done -- more
     * reliable than trying to reconstruct the filename ourselves from the
     * video title (which can contain characters yt-dlp itself sanitizes
     * differently than our own sanitize()).
     */
    fun download(
        url: String,
        option: QualityOption,
        outputDir: File,
        processId: String,
        context: Context,
        onProgress: (DownloadProgress) -> Unit
    ): File {
        if (!ensureReady(context)) throw IllegalStateException("yt-dlp not installed")
        outputDir.mkdirs()

        val request = YoutubeDLRequest(url)
        request.addOption("-o", File(outputDir, "%(title).200B [%(id)s].%(ext)s").absolutePath)
        request.addOption("--no-mtime")
        request.addOption("--no-playlist")
        request.addOption("--newline")
        request.addOption("--no-colors")
        request.addOption("--no-quiet")
        request.addOption("--progress")
        request.addOption("--extractor-args", "youtube:player_client=android,web")
        request.addOption("--print", "after_move:filepath")

        if (option.isAudioOnly) {
            // Settings.AudioFormatPreset.ORIGINAL (ytDlpFormat == null) skips
            // -x/--audio-format entirely and keeps whatever container/codec
            // YouTube actually serves for the best audio-only stream (m4a or
            // webm/opus) instead of forcing a re-encode; every other preset
            // extracts+converts to that exact format, same as the old
            // hardcoded mp3 behavior.
            val audioFormat = Settings.presetAudioFormat()
            if (audioFormat.ytDlpFormat != null) {
                request.addOption("-x")
                request.addOption("--audio-format", audioFormat.ytDlpFormat)
            } else {
                request.addOption("-f", "bestaudio/best")
            }
            // ID3 tags: title/uploader come from yt-dlp's own metadata for
            // free via --embed-metadata, but it maps uploader -> "artist"
            // only loosely and never sets album -- --parse-metadata fills
            // both explicitly so the file shows real Artist/Album in a
            // player instead of blank/mismatched tags. "%(artist,creator,
            // uploader)s" falls back through whichever field YouTube's
            // metadata actually has (music uploads set artist/creator;
            // regular videos usually only have uploader).
            request.addOption("--embed-metadata")
            request.addOption("--embed-thumbnail")
            // Embedded art must be a JPEG (ID3v2 APIC for mp3; harmless and
            // still widely compatible for m4a/opus/original too), not
            // yt-dlp's default webp thumbnail -- ffmpeg (bundled) converts.
            request.addOption("--convert-thumbnails", "jpg")
            request.addOption(
                "--parse-metadata",
                "%(artist,creator,uploader,channel)s:%(meta_artist)s"
            )
            request.addOption(
                "--parse-metadata",
                "%(album,playlist_title,channel)s:%(meta_album)s"
            )
        } else {
            request.addOption("-f", option.formatSelector)
            // Merge container for the video+audio case above.
            request.addOption("--merge-output-format", "mp4")
            request.addOption("--embed-thumbnail")
            request.addOption("--embed-metadata")
        }

        var lastPercent = 0
        // Correct signature: execute(request, processId, callback) with
        // callback = (progress: Float, etaInSeconds: Long, line: String) -> Unit.
        // The library's own `progress` float is used only as a last-resort
        // fallback (it can be -1/stale during postprocessing) -- the raw
        // `line` is what actually drives percent+status via parseProgressLine.
        val response = YoutubeDL.getInstance().execute(request, processId) { progress, _, line ->
            val parsed = parseProgressLine(line, lastPercent)
            if (parsed != null) {
                lastPercent = parsed.percent
                onProgress(parsed)
            } else if (progress >= 0f) {
                val fallbackPercent = progress.toInt().coerceIn(0, 100)
                lastPercent = fallbackPercent
                onProgress(DownloadProgress(fallbackPercent, "Downloading…"))
            }
        }

        val resolved = response.out
            .lineSequence()
            .map { it.trim().replace(ANSI_REGEX, "") }
            .firstOrNull { line ->
                line.isNotEmpty() && !line.startsWith("[") && File(line).isFile
            }?.let { File(it) }
            ?: response.out
                .lineSequence()
                .map { it.trim().replace(ANSI_REGEX, "") }
                .lastOrNull { it.isNotEmpty() && !it.startsWith("[") && File(it).isFile }
                ?.let { File(it) }
            ?: outputDir.listFiles()
                ?.filter { it.isFile }
                ?.maxByOrNull { it.lastModified() }

        return resolved ?: throw RuntimeException("Download finished but the output file couldn't be located")
    }

    /** Force-stops an in-flight download started with the same [processId]. */
    fun cancel(processId: String) {
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
    }
}
