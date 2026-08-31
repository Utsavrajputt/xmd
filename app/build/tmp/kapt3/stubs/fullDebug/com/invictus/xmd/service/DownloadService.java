package com.invictus.xmd.service;

/**
 * Runs the download queue with up to [Settings.maxConcurrentDownloads] items
 * downloading in parallel, each with its own independently pause/resume/
 * cancel-able DownloadEngine, showing an aggregate progress notification.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0088\u0001\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\t\n\u0002\b\u0002\n\u0002\u0010\u0006\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u000b\u0018\u0000 E2\u00020\u0001:\u0001EB\u0005\u00a2\u0006\u0002\u0010\u0002J \u0010\u0016\u001a\u00020\u00072\u0006\u0010\u0017\u001a\u00020\u00182\u0006\u0010\u0019\u001a\u00020\u00182\u0006\u0010\u001a\u001a\u00020\u001bH\u0002J\b\u0010\u001c\u001a\u00020\u001dH\u0002J0\u0010\u001e\u001a\u00020\u001f2\u0006\u0010 \u001a\u00020\u00072\u0006\u0010!\u001a\u00020\u00072\b\u0010\"\u001a\u0004\u0018\u00010\u00072\u0006\u0010#\u001a\u00020$H\u0082@\u00a2\u0006\u0002\u0010%J2\u0010&\u001a\u00020\u001f2\u0006\u0010 \u001a\u00020\u00072\u0006\u0010!\u001a\u00020\u00072\b\u0010'\u001a\u0004\u0018\u00010\u00072\b\u0010(\u001a\u0004\u0018\u00010\u0007H\u0082@\u00a2\u0006\u0002\u0010)J\u0016\u0010*\u001a\u00020\u001f2\u0006\u0010+\u001a\u00020,H\u0082@\u00a2\u0006\u0002\u0010-J\u0010\u0010.\u001a\u00020\u00072\u0006\u0010/\u001a\u00020\u0018H\u0002J\u0010\u00100\u001a\u00020\u00072\u0006\u00101\u001a\u00020\u0018H\u0002J\u0018\u00102\u001a\u00020\u001f2\u0006\u00103\u001a\u0002042\u0006\u00105\u001a\u000204H\u0002J\b\u00106\u001a\u00020\u001fH\u0016J\b\u00107\u001a\u00020\u001fH\u0016J\"\u00108\u001a\u0002092\b\u0010:\u001a\u0004\u0018\u00010;2\u0006\u0010<\u001a\u0002092\u0006\u0010=\u001a\u000209H\u0016J\b\u0010>\u001a\u00020\u001fH\u0002J\b\u0010?\u001a\u00020\u001fH\u0002J\b\u0010@\u001a\u00020\u001fH\u0002J\b\u0010A\u001a\u00020\u001fH\u0002J\b\u0010B\u001a\u00020\u001fH\u0002J\u000e\u0010C\u001a\u00020\u001fH\u0082@\u00a2\u0006\u0002\u0010DR\u000e\u0010\u0003\u001a\u00020\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000RN\u0010\u0005\u001aB\u0012\f\u0012\n \b*\u0004\u0018\u00010\u00070\u0007\u0012\f\u0012\n \b*\u0004\u0018\u00010\t0\t \b* \u0012\f\u0012\n \b*\u0004\u0018\u00010\u00070\u0007\u0012\f\u0012\n \b*\u0004\u0018\u00010\t0\t\u0018\u00010\u00060\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u000bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001a\u0010\f\u001a\u000e\u0012\u0004\u0012\u00020\u0007\u0012\u0004\u0012\u00020\u000e0\rX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000f\u001a\u00020\u0010X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0011\u001a\u0004\u0018\u00010\u0012X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u001a\u0010\u0013\u001a\u000e\u0012\u0004\u0012\u00020\u0007\u0012\u0004\u0012\u00020\u00140\rX\u0082\u0004\u00a2\u0006\u0002\n\u0000RN\u0010\u0015\u001aB\u0012\f\u0012\n \b*\u0004\u0018\u00010\u00070\u0007\u0012\f\u0012\n \b*\u0004\u0018\u00010\t0\t \b* \u0012\f\u0012\n \b*\u0004\u0018\u00010\u00070\u0007\u0012\f\u0012\n \b*\u0004\u0018\u00010\t0\t\u0018\u00010\u00060\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006F"}, d2 = {"Lcom/invictus/xmd/service/DownloadService;", "Landroidx/lifecycle/LifecycleService;", "()V", "activeWorkers", "Ljava/util/concurrent/atomic/AtomicInteger;", "cancelledYoutubeIds", "Ljava/util/concurrent/ConcurrentHashMap$KeySetView;", "", "kotlin.jvm.PlatformType", "", "client", "Lokhttp3/OkHttpClient;", "engines", "Ljava/util/concurrent/ConcurrentHashMap;", "Lcom/invictus/xmd/core/DownloadEngine;", "lastThrottledNotifyMs", "Ljava/util/concurrent/atomic/AtomicLong;", "networkCallback", "Landroid/net/ConnectivityManager$NetworkCallback;", "torrentEngines", "Lcom/invictus/xmd/core/TorrentEngine;", "wifiWaitingYoutubeIds", "buildDetailLine", "done", "", "total", "speedBps", "", "buildNotification", "Landroid/app/Notification;", "downloadOne", "", "itemId", "sourceUrl", "directUrlAtClaim", "categoryAtClaim", "Lcom/invictus/xmd/core/DownloadCategory;", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lcom/invictus/xmd/core/DownloadCategory;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "downloadTorrentOne", "customSaveDirPath", "selectedFileIndices", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "downloadYoutube", "item", "Lcom/invictus/xmd/core/QueueItem;", "(Lcom/invictus/xmd/core/QueueItem;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "formatBytes", "bytes", "formatDuration", "totalSeconds", "moveToPublicStorage", "temp", "Ljava/io/File;", "final", "onCreate", "onDestroy", "onStartCommand", "", "intent", "Landroid/content/Intent;", "flags", "startId", "onWifiLost", "onWifiRegained", "topUpWorkers", "updateNotification", "updateNotificationThrottled", "worker", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "Companion", "app_fullDebug"})
public final class DownloadService extends androidx.lifecycle.LifecycleService {
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String ACTION_START = "com.invictus.xmd.action.START";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String ACTION_PAUSE_ITEM = "com.invictus.xmd.action.PAUSE_ITEM";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String ACTION_RESUME_ITEM = "com.invictus.xmd.action.RESUME_ITEM";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String ACTION_CANCEL_ITEM = "com.invictus.xmd.action.CANCEL_ITEM";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String ACTION_CANCEL_ALL = "com.invictus.xmd.action.CANCEL_ALL";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String ACTION_WIFI_ONLY_ENABLED = "com.invictus.xmd.action.WIFI_ONLY_ENABLED";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_ITEM_ID = "extra_item_id";
    private static final int NOTIFICATION_ID = 42;
    private static final long BETWEEN_CLAIM_DELAY_MS = 500L;
    private static final int MAX_AUTO_RETRIES = 3;
    private static final long NOTIFY_THROTTLE_MS = 500L;
    @org.jetbrains.annotations.NotNull()
    private final okhttp3.OkHttpClient client = null;
    
    /**
     * Active engines keyed by queue item id, so per-item controls can target the right download.
     */
    @org.jetbrains.annotations.NotNull()
    private final java.util.concurrent.ConcurrentHashMap<java.lang.String, com.invictus.xmd.core.DownloadEngine> engines = null;
    
    /**
     * Same idea as [engines], for magnet/.torrent items running through TorrentEngine instead.
     */
    @org.jetbrains.annotations.NotNull()
    private final java.util.concurrent.ConcurrentHashMap<java.lang.String, com.invictus.xmd.core.TorrentEngine> torrentEngines = null;
    @org.jetbrains.annotations.NotNull()
    private final java.util.concurrent.atomic.AtomicInteger activeWorkers = null;
    @org.jetbrains.annotations.Nullable()
    private android.net.ConnectivityManager.NetworkCallback networkCallback;
    
    /**
     * YouTube item ids cancelled by [onWifiLost] specifically -- distinct
     * from [cancelledYoutubeIds] (which also covers a real user Cancel and
     * routes to FAILED) so these instead land back at READY once Wi-Fi returns.
     */
    private final java.util.concurrent.ConcurrentHashMap.KeySetView<java.lang.String, java.lang.Boolean> wifiWaitingYoutubeIds = null;
    
    /**
     * Same idea as [engines], for YouTube (yt-dlp) items -- keyed by processId (== item id).
     */
    private final java.util.concurrent.ConcurrentHashMap.KeySetView<java.lang.String, java.lang.Boolean> cancelledYoutubeIds = null;
    @org.jetbrains.annotations.NotNull()
    private final java.util.concurrent.atomic.AtomicLong lastThrottledNotifyMs = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.invictus.xmd.service.DownloadService.Companion Companion = null;
    
    public DownloadService() {
        super();
    }
    
    @java.lang.Override()
    public void onCreate() {
    }
    
    @java.lang.Override()
    public void onDestroy() {
    }
    
    /**
     * Wi-Fi dropped (or vanished entirely) while Wi-Fi-only downloads is ON --
     * pause every live download in place, marking each with [Settings.WIFI_WAIT_MARKER]
     * so [onWifiRegained] knows to resume exactly these and nothing the user
     * paused by hand. YouTube has no native pause, so its items are cancelled
     * and routed back to READY instead -- same recovery path already used for
     * a dead engine in ACTION_RESUME_ITEM.
     */
    private final void onWifiLost() {
    }
    
    /**
     * Wi-Fi is back (or Wi-Fi-only was never on) -- resume anything this
     * service auto-paused for it, and top workers back up so anything still
     * READY (or just re-queued from a cancelled YouTube item above) gets picked up.
     */
    private final void onWifiRegained() {
    }
    
    @java.lang.Override()
    public int onStartCommand(@org.jetbrains.annotations.Nullable()
    android.content.Intent intent, int flags, int startId) {
        return 0;
    }
    
    /**
     * Launches enough fresh worker loops to bring the live count up to the configured max.
     */
    private final void topUpWorkers() {
    }
    
    private final java.lang.Object worker(kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    /**
     * No range downloads, no resume-on-crash, no auto-retry loop here --
     * yt-dlp owns the entire resolve+download+merge process for a YouTube
     * item, and reports plain 0-100% progress instead of bytes. Kept as its
     * own function rather than shoehorned into downloadOne() above since
     * almost nothing (temp-then-move, byte progress, Content-Disposition
     * probing) actually applies to it. Full-flavor only -- see YtDlpManager.
     */
    private final java.lang.Object downloadYoutube(com.invictus.xmd.core.QueueItem item, kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    /**
     * Magnet / .torrent items. No connections/speed-limit settings applied
     * here yet (libtorrent has its own upload/download rate limiting knobs
     * that aren't wired up to Settings) -- straightforward "download it and
     * report progress" for now, mirroring downloadOne()'s status handling.
     */
    private final java.lang.Object downloadTorrentOne(java.lang.String itemId, java.lang.String sourceUrl, java.lang.String customSaveDirPath, java.lang.String selectedFileIndices, kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    private final java.lang.Object downloadOne(java.lang.String itemId, java.lang.String sourceUrl, java.lang.String directUrlAtClaim, com.invictus.xmd.core.DownloadCategory categoryAtClaim, kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    /**
     * Moves the finished temp file into public storage. `renameTo` is instant
     * when both paths are on the same filesystem, but the private cache and
     * /sdcard/... often sit on different mount views (FUSE), so it commonly
     * fails there -- in which case we fall back to a large-buffer streamed
     * copy, which is still one continuous sequential write instead of the
     * many small interleaved writes a live multi-segment download would do.
     */
    private final void moveToPublicStorage(java.io.File temp, java.io.File p1_48718011) {
    }
    
    private final void updateNotification() {
    }
    
    /**
     * Same as [updateNotification] but rate-limited to at most once every
     * [NOTIFY_THROTTLE_MS]. The three per-download progress callbacks
     * (downloadOne/downloadYoutube/downloadTorrentOne) fire up to ~5x/sec
     * *per active download* -- buildNotification() rescans the entire queue
     * every time, and NotificationManager.notify() is a cross-process Binder
     * call, so a handful of concurrent downloads meant tens of full
     * notification rebuilds a second for a progress bar that's visually
     * indistinguishable at that rate. That's pure CPU/Binder overhead
     * competing with the actual download threads. Status-change call sites
     * (pause/resume/done/failed/etc.) are untouched and stay immediate.
     */
    private final void updateNotificationThrottled() {
    }
    
    private final android.app.Notification buildNotification() {
        return null;
    }
    
    /**
     * "12.4 MB / 45.0 MB  •  1.2 MB/s  •  ETA 0:32"
     */
    private final java.lang.String buildDetailLine(long done, long total, double speedBps) {
        return null;
    }
    
    /**
     * Bytes → human-readable string using binary prefixes (KiB, MiB, GiB).
     */
    private final java.lang.String formatBytes(long bytes) {
        return null;
    }
    
    private final java.lang.String formatDuration(long totalSeconds) {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00002\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0006\n\u0002\u0010\t\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0007\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u0011\u001a\u00020\u00122\u0006\u0010\u0013\u001a\u00020\u0014J\u0016\u0010\u0015\u001a\u00020\u00122\u0006\u0010\u0013\u001a\u00020\u00142\u0006\u0010\u0016\u001a\u00020\u0004J\u000e\u0010\u0017\u001a\u00020\u00122\u0006\u0010\u0013\u001a\u00020\u0014J\u0016\u0010\u0018\u001a\u00020\u00122\u0006\u0010\u0013\u001a\u00020\u00142\u0006\u0010\u0016\u001a\u00020\u0004J\u0016\u0010\u0019\u001a\u00020\u00122\u0006\u0010\u0013\u001a\u00020\u00142\u0006\u0010\u0016\u001a\u00020\u0004J\u000e\u0010\u001a\u001a\u00020\u00122\u0006\u0010\u0013\u001a\u00020\u0014R\u000e\u0010\u0003\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u000bX\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\f\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\r\u001a\u00020\u000eX\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000f\u001a\u00020\u000eX\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0010\u001a\u00020\u000bX\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u001b"}, d2 = {"Lcom/invictus/xmd/service/DownloadService$Companion;", "", "()V", "ACTION_CANCEL_ALL", "", "ACTION_CANCEL_ITEM", "ACTION_PAUSE_ITEM", "ACTION_RESUME_ITEM", "ACTION_START", "ACTION_WIFI_ONLY_ENABLED", "BETWEEN_CLAIM_DELAY_MS", "", "EXTRA_ITEM_ID", "MAX_AUTO_RETRIES", "", "NOTIFICATION_ID", "NOTIFY_THROTTLE_MS", "cancelAll", "", "context", "Landroid/content/Context;", "cancelItem", "itemId", "pauseForWifiOnly", "pauseItem", "resumeItem", "start", "app_fullDebug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
        
        public final void start(@org.jetbrains.annotations.NotNull()
        android.content.Context context) {
        }
        
        public final void pauseItem(@org.jetbrains.annotations.NotNull()
        android.content.Context context, @org.jetbrains.annotations.NotNull()
        java.lang.String itemId) {
        }
        
        public final void resumeItem(@org.jetbrains.annotations.NotNull()
        android.content.Context context, @org.jetbrains.annotations.NotNull()
        java.lang.String itemId) {
        }
        
        public final void cancelItem(@org.jetbrains.annotations.NotNull()
        android.content.Context context, @org.jetbrains.annotations.NotNull()
        java.lang.String itemId) {
        }
        
        public final void cancelAll(@org.jetbrains.annotations.NotNull()
        android.content.Context context) {
        }
        
        /**
         * Called right after the Wi-Fi-only setting is flipped ON from
         * Settings while already on cellular -- see [onWifiLost] for the
         * actual pause logic, this just routes to it via the running service.
         */
        public final void pauseForWifiOnly(@org.jetbrains.annotations.NotNull()
        android.content.Context context) {
        }
    }
}