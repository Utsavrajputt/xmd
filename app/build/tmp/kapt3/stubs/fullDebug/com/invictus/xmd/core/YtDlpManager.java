package com.invictus.xmd.core;

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
 * - yt-dlp resolves the video, downloads it, and (for qualities above what
 *   a single progressive stream offers) merges separate video+audio
 *   streams with ffmpeg, all in one call.
 * - Progress is a 0-100 percentage from yt-dlp itself, not bytes.
 * - Cancellation is by process id, not by closing an OkHttp Call / calling
 *   into a torrent engine handle.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000j\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\b\n\u0002\b\b\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\b\u000b\b\u00c6\u0002\u0018\u00002\u00020\u0001:\u00043456B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u000b\u001a\u00020\u00042\u0006\u0010\f\u001a\u00020\rJ\b\u0010\u000e\u001a\u00020\u0004H\u0002J\u000e\u0010\u000f\u001a\u00020\u00102\u0006\u0010\u0011\u001a\u00020\u0004J\u000e\u0010\u0012\u001a\u00020\u00102\u0006\u0010\u0013\u001a\u00020\u0014JB\u0010\u0015\u001a\u00020\u00162\u0006\u0010\u0017\u001a\u00020\u00042\u0006\u0010\u0018\u001a\u00020\u00192\u0006\u0010\u001a\u001a\u00020\u00162\u0006\u0010\u0011\u001a\u00020\u00042\u0006\u0010\u0013\u001a\u00020\u00142\u0012\u0010\u001b\u001a\u000e\u0012\u0004\u0012\u00020\u001d\u0012\u0004\u0012\u00020\u00100\u001cJ\u000e\u0010\u001e\u001a\u00020\n2\u0006\u0010\u0013\u001a\u00020\u0014J\u001f\u0010\u001f\u001a\u0004\u0018\u00010\u00042\u0006\u0010\f\u001a\u00020\r2\b\u0010 \u001a\u0004\u0018\u00010!\u00a2\u0006\u0002\u0010\"J\u0010\u0010#\u001a\u0004\u0018\u00010\u00042\u0006\u0010\u0013\u001a\u00020\u0014J\u000e\u0010$\u001a\u00020\n2\u0006\u0010\u0013\u001a\u00020\u0014J\u0006\u0010%\u001a\u00020\nJ\u001a\u0010&\u001a\u0004\u0018\u00010\u001d2\u0006\u0010'\u001a\u00020\u00042\u0006\u0010(\u001a\u00020!H\u0002J\u0016\u0010)\u001a\u00020*2\u0006\u0010\u0017\u001a\u00020\u00042\u0006\u0010\u0013\u001a\u00020\u0014J\u0016\u0010+\u001a\b\u0012\u0004\u0012\u00020\u00190,2\b\b\u0002\u0010-\u001a\u00020\nJ\u0018\u0010.\u001a\u0004\u0018\u00010\u00042\u0006\u0010\u0013\u001a\u00020\u00142\u0006\u0010/\u001a\u00020\nJ\u0010\u00100\u001a\u0004\u0018\u00010\u00042\u0006\u0010\u0013\u001a\u00020\u0014J\u001a\u00101\u001a\u00020\u00042\u0006\u00102\u001a\u00020!2\b\b\u0002\u0010-\u001a\u00020\nH\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\nX\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u00067"}, d2 = {"Lcom/invictus/xmd/core/YtDlpManager;", "", "()V", "AUDIO_ONLY_SELECTOR", "", "DOWNLOAD_LINE", "Lkotlin/text/Regex;", "STAGE_LINE", "TAG", "initialized", "", "advancedSelector", "format", "Lcom/invictus/xmd/core/YtDlpManager$ProbedFormat;", "audioFormatShortLabel", "cancel", "", "processId", "delete", "context", "Landroid/content/Context;", "download", "Ljava/io/File;", "url", "option", "Lcom/invictus/xmd/core/YtDlpManager$QualityOption;", "outputDir", "onProgress", "Lkotlin/Function1;", "Lcom/invictus/xmd/core/YtDlpManager$DownloadProgress;", "ensureReady", "formatSize", "durationSeconds", "", "(Lcom/invictus/xmd/core/YtDlpManager$ProbedFormat;Ljava/lang/Integer;)Ljava/lang/String;", "install", "isInstalled", "isReady", "parseProgressLine", "line", "lastPercent", "probeFormats", "Lcom/invictus/xmd/core/YtDlpManager$ProbeResult;", "standardQualityOptions", "", "isGenericOrHls", "switchChannel", "toNightly", "update", "videoSelector", "maxHeight", "DownloadProgress", "ProbeResult", "ProbedFormat", "QualityOption", "app_fullDebug"})
public final class YtDlpManager {
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String TAG = "YtDlpManager";
    
    /**
     * Shared with DownloadService, which re-derives a QualityOption from a persisted QueueItem's formatSelector alone.
     */
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String AUDIO_ONLY_SELECTOR = "bestaudio/best";
    @kotlin.jvm.Volatile()
    private static volatile boolean initialized = false;
    @org.jetbrains.annotations.NotNull()
    private static final kotlin.text.Regex DOWNLOAD_LINE = null;
    @org.jetbrains.annotations.NotNull()
    private static final kotlin.text.Regex STAGE_LINE = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.invictus.xmd.core.YtDlpManager INSTANCE = null;
    
    private YtDlpManager() {
        super();
    }
    
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
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<com.invictus.xmd.core.YtDlpManager.QualityOption> standardQualityOptions(boolean isGenericOrHls) {
        return null;
    }
    
    /**
     * Short display label for the "Audio only (…)" row, reflecting the
     * user's saved Settings.presetAudioFormat() -- this used to be
     * hardcoded to "MP3" regardless of the preset, so the picker kept
     * showing MP3 even after switching to Opus/M4A/Original.
     */
    private final java.lang.String audioFormatShortLabel() {
        return null;
    }
    
    /**
     * Builds the `-f` selector for one rung of [standardQualityOptions],
     * folding in the user's saved container/codec/fps preset (Settings)
     * on top of the height ceiling.
     *
     * Chains three alternatives, `/`-separated, so a preset never causes a
     * hard failure:
     * 1. Height + every preset filter that's set (exact match).
     * 2. Height alone -- today's behavior, if this video doesn't actually
     *    offer that container/codec/fps combination at this height.
     * 3. Plain `best[height<=H]` -- last-resort single-stream fallback.
     *
     * All three preset fields at ANY (nothing picked, the default) folds
     * back to exactly the original unconstrained selector.
     */
    private final java.lang.String videoSelector(int maxHeight, boolean isGenericOrHls) {
        return null;
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
    @org.jetbrains.annotations.NotNull()
    public final com.invictus.xmd.core.YtDlpManager.ProbeResult probeFormats(@org.jetbrains.annotations.NotNull()
    java.lang.String url, @org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        return null;
    }
    
    /**
     * Builds a `-f` selector for one specific video-only [ProbedFormat]
     * merged with the best matching audio -- mirrors [videoSelector] but
     * pins the exact formatId instead of a height ceiling, so advanced
     * picks download the exact stream shown (exact fps/codec/bitrate)
     * rather than whatever yt-dlp would otherwise pick at that height.
     */
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String advancedSelector(@org.jetbrains.annotations.NotNull()
    com.invictus.xmd.core.YtDlpManager.ProbedFormat format) {
        return null;
    }
    
    /**
     * Human-readable "~45.2 MB" from bytes, or a bitrate-derived estimate, or null if neither is known.
     */
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String formatSize(@org.jetbrains.annotations.NotNull()
    com.invictus.xmd.core.YtDlpManager.ProbedFormat format, @org.jetbrains.annotations.Nullable()
    java.lang.Integer durationSeconds) {
        return null;
    }
    
    /**
     * True once the user has tapped Install in Settings and it succeeded.
     * Nothing is unpacked automatically on app start -- [ensureReady] does
     * the (cheap, already-unpacked) re-init per process lifetime only if
     * this is true.
     */
    public final boolean isInstalled(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        return false;
    }
    
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
    @kotlin.jvm.Synchronized()
    @org.jetbrains.annotations.Nullable()
    public final synchronized java.lang.String install(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        return null;
    }
    
    /**
     * Removes the unpacked binaries to reclaim storage and flips
     * [isInstalled] back to false. The bundled assets inside the APK
     * itself aren't affected -- tapping Install again just re-unpacks them.
     */
    @kotlin.jvm.Synchronized()
    public final synchronized void delete(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
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
    @kotlin.jvm.Synchronized()
    public final synchronized boolean ensureReady(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        return false;
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
    @kotlin.jvm.Synchronized()
    @org.jetbrains.annotations.Nullable()
    public final synchronized java.lang.String update(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        return null;
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
    @kotlin.jvm.Synchronized()
    @org.jetbrains.annotations.Nullable()
    public final synchronized java.lang.String switchChannel(@org.jetbrains.annotations.NotNull()
    android.content.Context context, boolean toNightly) {
        return null;
    }
    
    public final boolean isReady() {
        return false;
    }
    
    /**
     * Parses one line of yt-dlp's stdout into a [DownloadProgress], or null
     * if the line has nothing progress-related in it (most lines don't --
     * yt-dlp prints a lot of [info]/[youtube] noise per run).
     *
     * This exists because youtubedl-android's own progress callback only
     * reliably fires during the raw download phase -- once yt-dlp moves into
     * postprocessing (merging video+audio, extracting/converting audio,
     * embedding the thumbnail, writing metadata), the library's regex
     * doesn't recognize those lines as progress at all, so relying on it
     * alone leaves the UI stuck showing stale/no percentage for however long
     * that phase takes. Parsing the raw line ourselves means every stage
     * shows *something* instead of a frozen "Downloading…".
     */
    private final com.invictus.xmd.core.YtDlpManager.DownloadProgress parseProgressLine(java.lang.String line, int lastPercent) {
        return null;
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
    @org.jetbrains.annotations.NotNull()
    public final java.io.File download(@org.jetbrains.annotations.NotNull()
    java.lang.String url, @org.jetbrains.annotations.NotNull()
    com.invictus.xmd.core.YtDlpManager.QualityOption option, @org.jetbrains.annotations.NotNull()
    java.io.File outputDir, @org.jetbrains.annotations.NotNull()
    java.lang.String processId, @org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function1<? super com.invictus.xmd.core.YtDlpManager.DownloadProgress, kotlin.Unit> onProgress) {
        return null;
    }
    
    /**
     * Force-stops an in-flight download started with the same [processId].
     */
    public final void cancel(@org.jetbrains.annotations.NotNull()
    java.lang.String processId) {
    }
    
    /**
     * One progress tick, parsed from yt-dlp's own stdout rather than trusting the library's percent-only callback alone (see parseProgressLine).
     */
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0002\b\t\n\u0002\u0010\u000b\n\u0002\b\u0004\b\u0086\b\u0018\u00002\u00020\u0001B\u0017\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\b\u0010\u0004\u001a\u0004\u0018\u00010\u0005\u00a2\u0006\u0002\u0010\u0006J\t\u0010\u000b\u001a\u00020\u0003H\u00c6\u0003J\u000b\u0010\f\u001a\u0004\u0018\u00010\u0005H\u00c6\u0003J\u001f\u0010\r\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\n\b\u0002\u0010\u0004\u001a\u0004\u0018\u00010\u0005H\u00c6\u0001J\u0013\u0010\u000e\u001a\u00020\u000f2\b\u0010\u0010\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0011\u001a\u00020\u0003H\u00d6\u0001J\t\u0010\u0012\u001a\u00020\u0005H\u00d6\u0001R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0013\u0010\u0004\u001a\u0004\u0018\u00010\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\n\u00a8\u0006\u0013"}, d2 = {"Lcom/invictus/xmd/core/YtDlpManager$DownloadProgress;", "", "percent", "", "statusText", "", "(ILjava/lang/String;)V", "getPercent", "()I", "getStatusText", "()Ljava/lang/String;", "component1", "component2", "copy", "equals", "", "other", "hashCode", "toString", "app_fullDebug"})
    public static final class DownloadProgress {
        private final int percent = 0;
        
        /**
         * e.g. "12.4MiB/s, ETA 00:32" -- null once nothing matches (postprocessing stages: merging, extracting audio, embedding thumbnail, etc).
         */
        @org.jetbrains.annotations.Nullable()
        private final java.lang.String statusText = null;
        
        public DownloadProgress(int percent, @org.jetbrains.annotations.Nullable()
        java.lang.String statusText) {
            super();
        }
        
        public final int getPercent() {
            return 0;
        }
        
        /**
         * e.g. "12.4MiB/s, ETA 00:32" -- null once nothing matches (postprocessing stages: merging, extracting audio, embedding thumbnail, etc).
         */
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String getStatusText() {
            return null;
        }
        
        public final int component1() {
            return 0;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String component2() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.invictus.xmd.core.YtDlpManager.DownloadProgress copy(int percent, @org.jetbrains.annotations.Nullable()
        java.lang.String statusText) {
            return null;
        }
        
        @java.lang.Override()
        public boolean equals(@org.jetbrains.annotations.Nullable()
        java.lang.Object other) {
            return false;
        }
        
        @java.lang.Override()
        public int hashCode() {
            return 0;
        }
        
        @java.lang.Override()
        @org.jetbrains.annotations.NotNull()
        public java.lang.String toString() {
            return null;
        }
    }
    
    /**
     * Result of [probeFormats]: every real stream yt-dlp reports for a URL, plus the video's duration (needed to estimate size for formats where yt-dlp doesn't report filesize directly).
     */
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000*\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\u000b\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0000\b\u0086\b\u0018\u00002\u00020\u0001B\u001d\u0012\f\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003\u0012\b\u0010\u0005\u001a\u0004\u0018\u00010\u0006\u00a2\u0006\u0002\u0010\u0007J\u000f\u0010\r\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003H\u00c6\u0003J\u0010\u0010\u000e\u001a\u0004\u0018\u00010\u0006H\u00c6\u0003\u00a2\u0006\u0002\u0010\tJ*\u0010\u000f\u001a\u00020\u00002\u000e\b\u0002\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u00032\n\b\u0002\u0010\u0005\u001a\u0004\u0018\u00010\u0006H\u00c6\u0001\u00a2\u0006\u0002\u0010\u0010J\u0013\u0010\u0011\u001a\u00020\u00122\b\u0010\u0013\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0014\u001a\u00020\u0006H\u00d6\u0001J\t\u0010\u0015\u001a\u00020\u0016H\u00d6\u0001R\u0015\u0010\u0005\u001a\u0004\u0018\u00010\u0006\u00a2\u0006\n\n\u0002\u0010\n\u001a\u0004\b\b\u0010\tR\u0017\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\f\u00a8\u0006\u0017"}, d2 = {"Lcom/invictus/xmd/core/YtDlpManager$ProbeResult;", "", "formats", "", "Lcom/invictus/xmd/core/YtDlpManager$ProbedFormat;", "durationSeconds", "", "(Ljava/util/List;Ljava/lang/Integer;)V", "getDurationSeconds", "()Ljava/lang/Integer;", "Ljava/lang/Integer;", "getFormats", "()Ljava/util/List;", "component1", "component2", "copy", "(Ljava/util/List;Ljava/lang/Integer;)Lcom/invictus/xmd/core/YtDlpManager$ProbeResult;", "equals", "", "other", "hashCode", "toString", "", "app_fullDebug"})
    public static final class ProbeResult {
        @org.jetbrains.annotations.NotNull()
        private final java.util.List<com.invictus.xmd.core.YtDlpManager.ProbedFormat> formats = null;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.Integer durationSeconds = null;
        
        public ProbeResult(@org.jetbrains.annotations.NotNull()
        java.util.List<com.invictus.xmd.core.YtDlpManager.ProbedFormat> formats, @org.jetbrains.annotations.Nullable()
        java.lang.Integer durationSeconds) {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.util.List<com.invictus.xmd.core.YtDlpManager.ProbedFormat> getFormats() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Integer getDurationSeconds() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.util.List<com.invictus.xmd.core.YtDlpManager.ProbedFormat> component1() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Integer component2() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.invictus.xmd.core.YtDlpManager.ProbeResult copy(@org.jetbrains.annotations.NotNull()
        java.util.List<com.invictus.xmd.core.YtDlpManager.ProbedFormat> formats, @org.jetbrains.annotations.Nullable()
        java.lang.Integer durationSeconds) {
            return null;
        }
        
        @java.lang.Override()
        public boolean equals(@org.jetbrains.annotations.Nullable()
        java.lang.Object other) {
            return false;
        }
        
        @java.lang.Override()
        public int hashCode() {
            return 0;
        }
        
        @java.lang.Override()
        @org.jetbrains.annotations.NotNull()
        public java.lang.String toString() {
            return null;
        }
    }
    
    /**
     * One raw stream as reported by yt-dlp's own format probe (`-j`/getInfo, not the fixed [standardQualityOptions] ladder).
     */
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00000\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0004\n\u0002\u0010\t\n\u0000\n\u0002\u0010\u0006\n\u0002\b\n\n\u0002\u0010\u000b\n\u0002\b\u0018\b\u0086\b\u0018\u00002\u00020\u0001BQ\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u0012\b\u0010\u0005\u001a\u0004\u0018\u00010\u0006\u0012\b\u0010\u0007\u001a\u0004\u0018\u00010\u0006\u0012\b\u0010\b\u001a\u0004\u0018\u00010\u0003\u0012\b\u0010\t\u001a\u0004\u0018\u00010\u0003\u0012\b\u0010\n\u001a\u0004\u0018\u00010\u000b\u0012\b\u0010\f\u001a\u0004\u0018\u00010\r\u00a2\u0006\u0002\u0010\u000eJ\t\u0010\"\u001a\u00020\u0003H\u00c6\u0003J\t\u0010#\u001a\u00020\u0003H\u00c6\u0003J\u0010\u0010$\u001a\u0004\u0018\u00010\u0006H\u00c6\u0003\u00a2\u0006\u0002\u0010\u0014J\u0010\u0010%\u001a\u0004\u0018\u00010\u0006H\u00c6\u0003\u00a2\u0006\u0002\u0010\u0014J\u000b\u0010&\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\u000b\u0010'\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\u0010\u0010(\u001a\u0004\u0018\u00010\u000bH\u00c6\u0003\u00a2\u0006\u0002\u0010\u001cJ\u0010\u0010)\u001a\u0004\u0018\u00010\rH\u00c6\u0003\u00a2\u0006\u0002\u0010\u001fJj\u0010*\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00032\n\b\u0002\u0010\u0005\u001a\u0004\u0018\u00010\u00062\n\b\u0002\u0010\u0007\u001a\u0004\u0018\u00010\u00062\n\b\u0002\u0010\b\u001a\u0004\u0018\u00010\u00032\n\b\u0002\u0010\t\u001a\u0004\u0018\u00010\u00032\n\b\u0002\u0010\n\u001a\u0004\u0018\u00010\u000b2\n\b\u0002\u0010\f\u001a\u0004\u0018\u00010\rH\u00c6\u0001\u00a2\u0006\u0002\u0010+J\u0013\u0010,\u001a\u00020\u00182\b\u0010-\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010.\u001a\u00020\u0006H\u00d6\u0001J\t\u0010/\u001a\u00020\u0003H\u00d6\u0001R\u0013\u0010\t\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u0011\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0010R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0012\u0010\u0010R\u0015\u0010\u0007\u001a\u0004\u0018\u00010\u0006\u00a2\u0006\n\n\u0002\u0010\u0015\u001a\u0004\b\u0013\u0010\u0014R\u0015\u0010\u0005\u001a\u0004\u0018\u00010\u0006\u00a2\u0006\n\n\u0002\u0010\u0015\u001a\u0004\b\u0016\u0010\u0014R\u0011\u0010\u0017\u001a\u00020\u00188F\u00a2\u0006\u0006\u001a\u0004\b\u0017\u0010\u0019R\u0011\u0010\u001a\u001a\u00020\u00188F\u00a2\u0006\u0006\u001a\u0004\b\u001a\u0010\u0019R\u0015\u0010\n\u001a\u0004\u0018\u00010\u000b\u00a2\u0006\n\n\u0002\u0010\u001d\u001a\u0004\b\u001b\u0010\u001cR\u0015\u0010\f\u001a\u0004\u0018\u00010\r\u00a2\u0006\n\n\u0002\u0010 \u001a\u0004\b\u001e\u0010\u001fR\u0013\u0010\b\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b!\u0010\u0010\u00a8\u00060"}, d2 = {"Lcom/invictus/xmd/core/YtDlpManager$ProbedFormat;", "", "formatId", "", "ext", "height", "", "fps", "vcodec", "acodec", "sizeBytes", "", "tbr", "", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Integer;Ljava/lang/Integer;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Long;Ljava/lang/Double;)V", "getAcodec", "()Ljava/lang/String;", "getExt", "getFormatId", "getFps", "()Ljava/lang/Integer;", "Ljava/lang/Integer;", "getHeight", "isAudioOnly", "", "()Z", "isVideoOnly", "getSizeBytes", "()Ljava/lang/Long;", "Ljava/lang/Long;", "getTbr", "()Ljava/lang/Double;", "Ljava/lang/Double;", "getVcodec", "component1", "component2", "component3", "component4", "component5", "component6", "component7", "component8", "copy", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Integer;Ljava/lang/Integer;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Long;Ljava/lang/Double;)Lcom/invictus/xmd/core/YtDlpManager$ProbedFormat;", "equals", "other", "hashCode", "toString", "app_fullDebug"})
    public static final class ProbedFormat {
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String formatId = null;
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String ext = null;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.Integer height = null;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.Integer fps = null;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.String vcodec = null;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.String acodec = null;
        
        /**
         * Bytes, from filesize (exact) or filesize_approx -- null if yt-dlp couldn't report either.
         */
        @org.jetbrains.annotations.Nullable()
        private final java.lang.Long sizeBytes = null;
        
        /**
         * Total bitrate in Kbit/s (tbr), used as a filesize fallback when sizeBytes is null.
         */
        @org.jetbrains.annotations.Nullable()
        private final java.lang.Double tbr = null;
        
        public ProbedFormat(@org.jetbrains.annotations.NotNull()
        java.lang.String formatId, @org.jetbrains.annotations.NotNull()
        java.lang.String ext, @org.jetbrains.annotations.Nullable()
        java.lang.Integer height, @org.jetbrains.annotations.Nullable()
        java.lang.Integer fps, @org.jetbrains.annotations.Nullable()
        java.lang.String vcodec, @org.jetbrains.annotations.Nullable()
        java.lang.String acodec, @org.jetbrains.annotations.Nullable()
        java.lang.Long sizeBytes, @org.jetbrains.annotations.Nullable()
        java.lang.Double tbr) {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getFormatId() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getExt() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Integer getHeight() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Integer getFps() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String getVcodec() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String getAcodec() {
            return null;
        }
        
        /**
         * Bytes, from filesize (exact) or filesize_approx -- null if yt-dlp couldn't report either.
         */
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Long getSizeBytes() {
            return null;
        }
        
        /**
         * Total bitrate in Kbit/s (tbr), used as a filesize fallback when sizeBytes is null.
         */
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Double getTbr() {
            return null;
        }
        
        public final boolean isVideoOnly() {
            return false;
        }
        
        public final boolean isAudioOnly() {
            return false;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component1() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component2() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Integer component3() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Integer component4() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String component5() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String component6() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Long component7() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Double component8() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.invictus.xmd.core.YtDlpManager.ProbedFormat copy(@org.jetbrains.annotations.NotNull()
        java.lang.String formatId, @org.jetbrains.annotations.NotNull()
        java.lang.String ext, @org.jetbrains.annotations.Nullable()
        java.lang.Integer height, @org.jetbrains.annotations.Nullable()
        java.lang.Integer fps, @org.jetbrains.annotations.Nullable()
        java.lang.String vcodec, @org.jetbrains.annotations.Nullable()
        java.lang.String acodec, @org.jetbrains.annotations.Nullable()
        java.lang.Long sizeBytes, @org.jetbrains.annotations.Nullable()
        java.lang.Double tbr) {
            return null;
        }
        
        @java.lang.Override()
        public boolean equals(@org.jetbrains.annotations.Nullable()
        java.lang.Object other) {
            return false;
        }
        
        @java.lang.Override()
        public int hashCode() {
            return 0;
        }
        
        @java.lang.Override()
        @org.jetbrains.annotations.NotNull()
        public java.lang.String toString() {
            return null;
        }
    }
    
    /**
     * Data for one row in the quality-picker dialog.
     */
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\f\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001B\u001d\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\u0002\u0010\u0007J\t\u0010\f\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\r\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u000e\u001a\u00020\u0006H\u00c6\u0003J'\u0010\u000f\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00032\b\b\u0002\u0010\u0005\u001a\u00020\u0006H\u00c6\u0001J\u0013\u0010\u0010\u001a\u00020\u00062\b\u0010\u0011\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0012\u001a\u00020\u0013H\u00d6\u0001J\t\u0010\u0014\u001a\u00020\u0003H\u00d6\u0001R\u0011\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u0011\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\nR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\t\u00a8\u0006\u0015"}, d2 = {"Lcom/invictus/xmd/core/YtDlpManager$QualityOption;", "", "label", "", "formatSelector", "isAudioOnly", "", "(Ljava/lang/String;Ljava/lang/String;Z)V", "getFormatSelector", "()Ljava/lang/String;", "()Z", "getLabel", "component1", "component2", "component3", "copy", "equals", "other", "hashCode", "", "toString", "app_fullDebug"})
    public static final class QualityOption {
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String label = null;
        
        /**
         * yt-dlp `-f` format selector.
         */
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String formatSelector = null;
        private final boolean isAudioOnly = false;
        
        public QualityOption(@org.jetbrains.annotations.NotNull()
        java.lang.String label, @org.jetbrains.annotations.NotNull()
        java.lang.String formatSelector, boolean isAudioOnly) {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getLabel() {
            return null;
        }
        
        /**
         * yt-dlp `-f` format selector.
         */
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getFormatSelector() {
            return null;
        }
        
        public final boolean isAudioOnly() {
            return false;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component1() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component2() {
            return null;
        }
        
        public final boolean component3() {
            return false;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.invictus.xmd.core.YtDlpManager.QualityOption copy(@org.jetbrains.annotations.NotNull()
        java.lang.String label, @org.jetbrains.annotations.NotNull()
        java.lang.String formatSelector, boolean isAudioOnly) {
            return null;
        }
        
        @java.lang.Override()
        public boolean equals(@org.jetbrains.annotations.Nullable()
        java.lang.Object other) {
            return false;
        }
        
        @java.lang.Override()
        public int hashCode() {
            return 0;
        }
        
        @java.lang.Override()
        @org.jetbrains.annotations.NotNull()
        public java.lang.String toString() {
            return null;
        }
    }
}