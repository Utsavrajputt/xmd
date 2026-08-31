package com.invictus.xmd.core;

/**
 * One entry in the queue. [sourceUrl] is what the user pasted (or a link
 * discovered on a fitgirl-repacks page); [directUrl] is filled in once
 * resolved to a dl.fuckingfast.co URL.
 *
 * Persisted to disk via Room (see core/db/AppDatabase.kt) so the queue
 * survives the app process being killed/restarted -- QueueRepository used
 * to hold this purely in memory, which meant every item vanished on
 * restart even though the downloaded files themselves were fine.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000J\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\t\n\u0002\b\u0002\n\u0002\u0010\u0006\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\b\n\u0002\bJ\n\u0002\u0010\u000b\n\u0002\b\u0004\b\u0087\b\u0018\u00002\u00020\u0001B\u00d1\u0001\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u0012\n\b\u0002\u0010\u0005\u001a\u0004\u0018\u00010\u0003\u0012\b\b\u0002\u0010\u0006\u001a\u00020\u0007\u0012\n\b\u0002\u0010\b\u001a\u0004\u0018\u00010\u0003\u0012\n\b\u0002\u0010\t\u001a\u0004\u0018\u00010\u0003\u0012\n\b\u0002\u0010\n\u001a\u0004\u0018\u00010\u0003\u0012\b\b\u0002\u0010\u000b\u001a\u00020\f\u0012\b\b\u0002\u0010\r\u001a\u00020\f\u0012\b\b\u0002\u0010\u000e\u001a\u00020\u000f\u0012\b\b\u0002\u0010\u0010\u001a\u00020\f\u0012\b\b\u0002\u0010\u0011\u001a\u00020\u0012\u0012\n\b\u0002\u0010\u0013\u001a\u0004\u0018\u00010\u0003\u0012\b\b\u0002\u0010\u0014\u001a\u00020\u0015\u0012\n\b\u0002\u0010\u0016\u001a\u0004\u0018\u00010\u0003\u0012\n\b\u0002\u0010\u0017\u001a\u0004\u0018\u00010\u0003\u0012\b\b\u0002\u0010\u0018\u001a\u00020\u0019\u0012\n\b\u0002\u0010\u001a\u001a\u0004\u0018\u00010\u0003\u0012\n\b\u0002\u0010\u001b\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\u0002\u0010\u001cJ\t\u0010O\u001a\u00020\u0003H\u00c6\u0003J\t\u0010P\u001a\u00020\u000fH\u00c6\u0003J\t\u0010Q\u001a\u00020\fH\u00c6\u0003J\t\u0010R\u001a\u00020\u0012H\u00c6\u0003J\u000b\u0010S\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\t\u0010T\u001a\u00020\u0015H\u00c6\u0003J\u000b\u0010U\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\u000b\u0010V\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\t\u0010W\u001a\u00020\u0019H\u00c6\u0003J\u000b\u0010X\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\u000b\u0010Y\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\t\u0010Z\u001a\u00020\u0003H\u00c6\u0003J\u000b\u0010[\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\t\u0010\\\u001a\u00020\u0007H\u00c6\u0003J\u000b\u0010]\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\u000b\u0010^\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\u000b\u0010_\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\t\u0010`\u001a\u00020\fH\u00c6\u0003J\t\u0010a\u001a\u00020\fH\u00c6\u0003J\u00d9\u0001\u0010b\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00032\n\b\u0002\u0010\u0005\u001a\u0004\u0018\u00010\u00032\b\b\u0002\u0010\u0006\u001a\u00020\u00072\n\b\u0002\u0010\b\u001a\u0004\u0018\u00010\u00032\n\b\u0002\u0010\t\u001a\u0004\u0018\u00010\u00032\n\b\u0002\u0010\n\u001a\u0004\u0018\u00010\u00032\b\b\u0002\u0010\u000b\u001a\u00020\f2\b\b\u0002\u0010\r\u001a\u00020\f2\b\b\u0002\u0010\u000e\u001a\u00020\u000f2\b\b\u0002\u0010\u0010\u001a\u00020\f2\b\b\u0002\u0010\u0011\u001a\u00020\u00122\n\b\u0002\u0010\u0013\u001a\u0004\u0018\u00010\u00032\b\b\u0002\u0010\u0014\u001a\u00020\u00152\n\b\u0002\u0010\u0016\u001a\u0004\u0018\u00010\u00032\n\b\u0002\u0010\u0017\u001a\u0004\u0018\u00010\u00032\b\b\u0002\u0010\u0018\u001a\u00020\u00192\n\b\u0002\u0010\u001a\u001a\u0004\u0018\u00010\u00032\n\b\u0002\u0010\u001b\u001a\u0004\u0018\u00010\u0003H\u00c6\u0001J\u0013\u0010c\u001a\u00020d2\b\u0010e\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010f\u001a\u00020\u0019H\u00d6\u0001J\t\u0010g\u001a\u00020\u0003H\u00d6\u0001R\u001a\u0010\u000b\u001a\u00020\fX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u001d\u0010\u001e\"\u0004\b\u001f\u0010 R\u001a\u0010\r\u001a\u00020\fX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b!\u0010\u001e\"\u0004\b\"\u0010 R\u001a\u0010\u0011\u001a\u00020\u0012X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b#\u0010$\"\u0004\b%\u0010&R\u001c\u0010\u0013\u001a\u0004\u0018\u00010\u0003X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b'\u0010(\"\u0004\b)\u0010*R\u001c\u0010\u0005\u001a\u0004\u0018\u00010\u0003X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b+\u0010(\"\u0004\b,\u0010*R\u001a\u0010\u0010\u001a\u00020\fX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b-\u0010\u001e\"\u0004\b.\u0010 R\u001c\u0010\n\u001a\u0004\u0018\u00010\u0003X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b/\u0010(\"\u0004\b0\u0010*R\u001c\u0010\b\u001a\u0004\u0018\u00010\u0003X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b1\u0010(\"\u0004\b2\u0010*R\u001c\u0010\t\u001a\u0004\u0018\u00010\u0003X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b3\u0010(\"\u0004\b4\u0010*R\u0016\u0010\u0002\u001a\u00020\u00038\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b5\u0010(R\u001c\u0010\u0017\u001a\u0004\u0018\u00010\u0003X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b6\u0010(\"\u0004\b7\u0010*R\u001c\u0010\u0016\u001a\u0004\u0018\u00010\u0003X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b8\u0010(\"\u0004\b9\u0010*R\u001c\u0010\u001a\u001a\u0004\u0018\u00010\u0003X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b:\u0010(\"\u0004\b;\u0010*R\u001a\u0010\u0014\u001a\u00020\u0015X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b<\u0010=\"\u0004\b>\u0010?R\u001a\u0010\u0018\u001a\u00020\u0019X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b@\u0010A\"\u0004\bB\u0010CR\u001c\u0010\u001b\u001a\u0004\u0018\u00010\u0003X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\bD\u0010(\"\u0004\bE\u0010*R\u0011\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\bF\u0010(R\u001a\u0010\u000e\u001a\u00020\u000fX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\bG\u0010H\"\u0004\bI\u0010JR\u001a\u0010\u0006\u001a\u00020\u0007X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\bK\u0010L\"\u0004\bM\u0010N\u00a8\u0006h"}, d2 = {"Lcom/invictus/xmd/core/QueueItem;", "", "id", "", "sourceUrl", "directUrl", "status", "Lcom/invictus/xmd/core/ItemStatus;", "fileName", "filePath", "error", "bytesDone", "", "bytesTotal", "speedBps", "", "downloadStartedAtMs", "category", "Lcom/invictus/xmd/core/DownloadCategory;", "customSaveDirPath", "platform", "Lcom/invictus/xmd/core/MediaPlatform;", "mediaFormatSelector", "mediaFormatLabel", "progressPercent", "", "mediaStatusText", "selectedFileIndices", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lcom/invictus/xmd/core/ItemStatus;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;JJDJLcom/invictus/xmd/core/DownloadCategory;Ljava/lang/String;Lcom/invictus/xmd/core/MediaPlatform;Ljava/lang/String;Ljava/lang/String;ILjava/lang/String;Ljava/lang/String;)V", "getBytesDone", "()J", "setBytesDone", "(J)V", "getBytesTotal", "setBytesTotal", "getCategory", "()Lcom/invictus/xmd/core/DownloadCategory;", "setCategory", "(Lcom/invictus/xmd/core/DownloadCategory;)V", "getCustomSaveDirPath", "()Ljava/lang/String;", "setCustomSaveDirPath", "(Ljava/lang/String;)V", "getDirectUrl", "setDirectUrl", "getDownloadStartedAtMs", "setDownloadStartedAtMs", "getError", "setError", "getFileName", "setFileName", "getFilePath", "setFilePath", "getId", "getMediaFormatLabel", "setMediaFormatLabel", "getMediaFormatSelector", "setMediaFormatSelector", "getMediaStatusText", "setMediaStatusText", "getPlatform", "()Lcom/invictus/xmd/core/MediaPlatform;", "setPlatform", "(Lcom/invictus/xmd/core/MediaPlatform;)V", "getProgressPercent", "()I", "setProgressPercent", "(I)V", "getSelectedFileIndices", "setSelectedFileIndices", "getSourceUrl", "getSpeedBps", "()D", "setSpeedBps", "(D)V", "getStatus", "()Lcom/invictus/xmd/core/ItemStatus;", "setStatus", "(Lcom/invictus/xmd/core/ItemStatus;)V", "component1", "component10", "component11", "component12", "component13", "component14", "component15", "component16", "component17", "component18", "component19", "component2", "component3", "component4", "component5", "component6", "component7", "component8", "component9", "copy", "equals", "", "other", "hashCode", "toString", "app_liteDebug"})
@androidx.room.Entity(tableName = "queue_items")
public final class QueueItem {
    @androidx.room.PrimaryKey()
    @org.jetbrains.annotations.NotNull()
    private final java.lang.String id = null;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.String sourceUrl = null;
    @org.jetbrains.annotations.Nullable()
    private java.lang.String directUrl;
    @org.jetbrains.annotations.NotNull()
    private com.invictus.xmd.core.ItemStatus status;
    @org.jetbrains.annotations.Nullable()
    private java.lang.String fileName;
    
    /**
     * Absolute on-disk path, set only once [status] reaches DONE -- used by
     * the "Open" action to hand the file to an external app via
     * FileProvider. Reconstructing this from category/fileName/settings at
     * open-time would be fragile (three different download paths compute
     * their own base dir -- see DownloadService's downloadOne/
     * downloadTorrentOne/downloadYoutube -- and Settings.
     * saveToDownloadsFolder() could change between download and open), so
     * it's captured directly from the actual File the download wrote.
     */
    @org.jetbrains.annotations.Nullable()
    private java.lang.String filePath;
    @org.jetbrains.annotations.Nullable()
    private java.lang.String error;
    private long bytesDone;
    private long bytesTotal;
    private double speedBps;
    private long downloadStartedAtMs;
    @org.jetbrains.annotations.NotNull()
    private com.invictus.xmd.core.DownloadCategory category;
    
    /**
     * Torrent-only: an absolute save-folder path picked via the "Editor"
     * dialog's Advanced -> Change (see HomeFragment.showAddTorrentDialog,
     * MainActivity.triggerDownloadTorrentMagnet) that overrides the normal
     * Settings-driven default when set. Resolved from the SAF folder-picker
     * tree URI to a real filesystem path at pick time -- see
     * HomeFragment.resolveTreeUriToPath -- since libtorrent4j needs an
     * actual path, not a content:// tree URI. Null means "use the default".
     */
    @org.jetbrains.annotations.Nullable()
    private java.lang.String customSaveDirPath;
    @org.jetbrains.annotations.NotNull()
    private com.invictus.xmd.core.MediaPlatform platform;
    
    /**
     * yt-dlp `-f` format selector chosen in the quality picker, e.g. "bestvideo[height<=1080]+bestaudio/best[height<=1080]".
     */
    @org.jetbrains.annotations.Nullable()
    private java.lang.String mediaFormatSelector;
    
    /**
     * Display label for the chosen quality, e.g. "1080p" or "Audio (MP3)".
     */
    @org.jetbrains.annotations.Nullable()
    private java.lang.String mediaFormatLabel;
    
    /**
     * yt-dlp reports progress as a 0-100 percentage, not bytes -- -1 means
     * "not applicable, use bytesDone/bytesTotal instead" (the DIRECT path).
     */
    private int progressPercent;
    
    /**
     * Speed/ETA/size for the current yt-dlp stage, parsed directly from its
     * stdout line (e.g. "12.4MiB/s, ETA 00:32") -- youtubedl-android's own
     * progress callback only reports a bare percentage during postprocessing
     * stages (audio extract, thumbnail embed, metadata write), so this is
     * filled in by our own regex over the raw line instead of relying on the
     * library for anything beyond the percent. Null when there's nothing
     * parseable yet (right after the download starts) or not applicable
     * (DIRECT path, which already has its own speed via speedBps).
     */
    @org.jetbrains.annotations.Nullable()
    private java.lang.String mediaStatusText;
    
    /**
     * Comma-separated list of selected 0-indexed file indices for torrent downloads. Null means download all files.
     */
    @org.jetbrains.annotations.Nullable()
    private java.lang.String selectedFileIndices;
    
    public QueueItem(@org.jetbrains.annotations.NotNull()
    java.lang.String id, @org.jetbrains.annotations.NotNull()
    java.lang.String sourceUrl, @org.jetbrains.annotations.Nullable()
    java.lang.String directUrl, @org.jetbrains.annotations.NotNull()
    com.invictus.xmd.core.ItemStatus status, @org.jetbrains.annotations.Nullable()
    java.lang.String fileName, @org.jetbrains.annotations.Nullable()
    java.lang.String filePath, @org.jetbrains.annotations.Nullable()
    java.lang.String error, long bytesDone, long bytesTotal, double speedBps, long downloadStartedAtMs, @org.jetbrains.annotations.NotNull()
    com.invictus.xmd.core.DownloadCategory category, @org.jetbrains.annotations.Nullable()
    java.lang.String customSaveDirPath, @org.jetbrains.annotations.NotNull()
    com.invictus.xmd.core.MediaPlatform platform, @org.jetbrains.annotations.Nullable()
    java.lang.String mediaFormatSelector, @org.jetbrains.annotations.Nullable()
    java.lang.String mediaFormatLabel, int progressPercent, @org.jetbrains.annotations.Nullable()
    java.lang.String mediaStatusText, @org.jetbrains.annotations.Nullable()
    java.lang.String selectedFileIndices) {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getId() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getSourceUrl() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getDirectUrl() {
        return null;
    }
    
    public final void setDirectUrl(@org.jetbrains.annotations.Nullable()
    java.lang.String p0) {
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.invictus.xmd.core.ItemStatus getStatus() {
        return null;
    }
    
    public final void setStatus(@org.jetbrains.annotations.NotNull()
    com.invictus.xmd.core.ItemStatus p0) {
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getFileName() {
        return null;
    }
    
    public final void setFileName(@org.jetbrains.annotations.Nullable()
    java.lang.String p0) {
    }
    
    /**
     * Absolute on-disk path, set only once [status] reaches DONE -- used by
     * the "Open" action to hand the file to an external app via
     * FileProvider. Reconstructing this from category/fileName/settings at
     * open-time would be fragile (three different download paths compute
     * their own base dir -- see DownloadService's downloadOne/
     * downloadTorrentOne/downloadYoutube -- and Settings.
     * saveToDownloadsFolder() could change between download and open), so
     * it's captured directly from the actual File the download wrote.
     */
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getFilePath() {
        return null;
    }
    
    /**
     * Absolute on-disk path, set only once [status] reaches DONE -- used by
     * the "Open" action to hand the file to an external app via
     * FileProvider. Reconstructing this from category/fileName/settings at
     * open-time would be fragile (three different download paths compute
     * their own base dir -- see DownloadService's downloadOne/
     * downloadTorrentOne/downloadYoutube -- and Settings.
     * saveToDownloadsFolder() could change between download and open), so
     * it's captured directly from the actual File the download wrote.
     */
    public final void setFilePath(@org.jetbrains.annotations.Nullable()
    java.lang.String p0) {
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getError() {
        return null;
    }
    
    public final void setError(@org.jetbrains.annotations.Nullable()
    java.lang.String p0) {
    }
    
    public final long getBytesDone() {
        return 0L;
    }
    
    public final void setBytesDone(long p0) {
    }
    
    public final long getBytesTotal() {
        return 0L;
    }
    
    public final void setBytesTotal(long p0) {
    }
    
    public final double getSpeedBps() {
        return 0.0;
    }
    
    public final void setSpeedBps(double p0) {
    }
    
    public final long getDownloadStartedAtMs() {
        return 0L;
    }
    
    public final void setDownloadStartedAtMs(long p0) {
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.invictus.xmd.core.DownloadCategory getCategory() {
        return null;
    }
    
    public final void setCategory(@org.jetbrains.annotations.NotNull()
    com.invictus.xmd.core.DownloadCategory p0) {
    }
    
    /**
     * Torrent-only: an absolute save-folder path picked via the "Editor"
     * dialog's Advanced -> Change (see HomeFragment.showAddTorrentDialog,
     * MainActivity.triggerDownloadTorrentMagnet) that overrides the normal
     * Settings-driven default when set. Resolved from the SAF folder-picker
     * tree URI to a real filesystem path at pick time -- see
     * HomeFragment.resolveTreeUriToPath -- since libtorrent4j needs an
     * actual path, not a content:// tree URI. Null means "use the default".
     */
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getCustomSaveDirPath() {
        return null;
    }
    
    /**
     * Torrent-only: an absolute save-folder path picked via the "Editor"
     * dialog's Advanced -> Change (see HomeFragment.showAddTorrentDialog,
     * MainActivity.triggerDownloadTorrentMagnet) that overrides the normal
     * Settings-driven default when set. Resolved from the SAF folder-picker
     * tree URI to a real filesystem path at pick time -- see
     * HomeFragment.resolveTreeUriToPath -- since libtorrent4j needs an
     * actual path, not a content:// tree URI. Null means "use the default".
     */
    public final void setCustomSaveDirPath(@org.jetbrains.annotations.Nullable()
    java.lang.String p0) {
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.invictus.xmd.core.MediaPlatform getPlatform() {
        return null;
    }
    
    public final void setPlatform(@org.jetbrains.annotations.NotNull()
    com.invictus.xmd.core.MediaPlatform p0) {
    }
    
    /**
     * yt-dlp `-f` format selector chosen in the quality picker, e.g. "bestvideo[height<=1080]+bestaudio/best[height<=1080]".
     */
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getMediaFormatSelector() {
        return null;
    }
    
    /**
     * yt-dlp `-f` format selector chosen in the quality picker, e.g. "bestvideo[height<=1080]+bestaudio/best[height<=1080]".
     */
    public final void setMediaFormatSelector(@org.jetbrains.annotations.Nullable()
    java.lang.String p0) {
    }
    
    /**
     * Display label for the chosen quality, e.g. "1080p" or "Audio (MP3)".
     */
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getMediaFormatLabel() {
        return null;
    }
    
    /**
     * Display label for the chosen quality, e.g. "1080p" or "Audio (MP3)".
     */
    public final void setMediaFormatLabel(@org.jetbrains.annotations.Nullable()
    java.lang.String p0) {
    }
    
    /**
     * yt-dlp reports progress as a 0-100 percentage, not bytes -- -1 means
     * "not applicable, use bytesDone/bytesTotal instead" (the DIRECT path).
     */
    public final int getProgressPercent() {
        return 0;
    }
    
    /**
     * yt-dlp reports progress as a 0-100 percentage, not bytes -- -1 means
     * "not applicable, use bytesDone/bytesTotal instead" (the DIRECT path).
     */
    public final void setProgressPercent(int p0) {
    }
    
    /**
     * Speed/ETA/size for the current yt-dlp stage, parsed directly from its
     * stdout line (e.g. "12.4MiB/s, ETA 00:32") -- youtubedl-android's own
     * progress callback only reports a bare percentage during postprocessing
     * stages (audio extract, thumbnail embed, metadata write), so this is
     * filled in by our own regex over the raw line instead of relying on the
     * library for anything beyond the percent. Null when there's nothing
     * parseable yet (right after the download starts) or not applicable
     * (DIRECT path, which already has its own speed via speedBps).
     */
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getMediaStatusText() {
        return null;
    }
    
    /**
     * Speed/ETA/size for the current yt-dlp stage, parsed directly from its
     * stdout line (e.g. "12.4MiB/s, ETA 00:32") -- youtubedl-android's own
     * progress callback only reports a bare percentage during postprocessing
     * stages (audio extract, thumbnail embed, metadata write), so this is
     * filled in by our own regex over the raw line instead of relying on the
     * library for anything beyond the percent. Null when there's nothing
     * parseable yet (right after the download starts) or not applicable
     * (DIRECT path, which already has its own speed via speedBps).
     */
    public final void setMediaStatusText(@org.jetbrains.annotations.Nullable()
    java.lang.String p0) {
    }
    
    /**
     * Comma-separated list of selected 0-indexed file indices for torrent downloads. Null means download all files.
     */
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getSelectedFileIndices() {
        return null;
    }
    
    /**
     * Comma-separated list of selected 0-indexed file indices for torrent downloads. Null means download all files.
     */
    public final void setSelectedFileIndices(@org.jetbrains.annotations.Nullable()
    java.lang.String p0) {
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String component1() {
        return null;
    }
    
    public final double component10() {
        return 0.0;
    }
    
    public final long component11() {
        return 0L;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.invictus.xmd.core.DownloadCategory component12() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String component13() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.invictus.xmd.core.MediaPlatform component14() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String component15() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String component16() {
        return null;
    }
    
    public final int component17() {
        return 0;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String component18() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String component19() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String component2() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String component3() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.invictus.xmd.core.ItemStatus component4() {
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
    public final java.lang.String component7() {
        return null;
    }
    
    public final long component8() {
        return 0L;
    }
    
    public final long component9() {
        return 0L;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.invictus.xmd.core.QueueItem copy(@org.jetbrains.annotations.NotNull()
    java.lang.String id, @org.jetbrains.annotations.NotNull()
    java.lang.String sourceUrl, @org.jetbrains.annotations.Nullable()
    java.lang.String directUrl, @org.jetbrains.annotations.NotNull()
    com.invictus.xmd.core.ItemStatus status, @org.jetbrains.annotations.Nullable()
    java.lang.String fileName, @org.jetbrains.annotations.Nullable()
    java.lang.String filePath, @org.jetbrains.annotations.Nullable()
    java.lang.String error, long bytesDone, long bytesTotal, double speedBps, long downloadStartedAtMs, @org.jetbrains.annotations.NotNull()
    com.invictus.xmd.core.DownloadCategory category, @org.jetbrains.annotations.Nullable()
    java.lang.String customSaveDirPath, @org.jetbrains.annotations.NotNull()
    com.invictus.xmd.core.MediaPlatform platform, @org.jetbrains.annotations.Nullable()
    java.lang.String mediaFormatSelector, @org.jetbrains.annotations.Nullable()
    java.lang.String mediaFormatLabel, int progressPercent, @org.jetbrains.annotations.Nullable()
    java.lang.String mediaStatusText, @org.jetbrains.annotations.Nullable()
    java.lang.String selectedFileIndices) {
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