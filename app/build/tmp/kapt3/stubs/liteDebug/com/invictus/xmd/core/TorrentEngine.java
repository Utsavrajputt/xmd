package com.invictus.xmd.core;

/**
 * Downloads a single torrent (from a magnet link or raw .torrent file bytes)
 * via libtorrent4j, reporting progress the same shape as [DownloadEngine]
 * so DownloadService can treat both engines interchangeably from the
 * QueueRepository's point of view (bytesDone/bytesTotal/speedBps).
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000h\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\t\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0006\n\u0000\n\u0002\u0010\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0012\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0003\u0018\u0000 (2\u00020\u0001:\u0001(Br\u0012Q\b\u0002\u0010\u0002\u001aK\u0012\u0013\u0012\u00110\u0004\u00a2\u0006\f\b\u0005\u0012\b\b\u0006\u0012\u0004\b\b(\u0007\u0012\u0013\u0012\u00110\u0004\u00a2\u0006\f\b\u0005\u0012\b\b\u0006\u0012\u0004\b\b(\b\u0012\u0013\u0012\u00110\t\u00a2\u0006\f\b\u0005\u0012\b\b\u0006\u0012\u0004\b\b(\n\u0012\u0004\u0012\u00020\u000b0\u0003j\u0002`\f\u0012\u0018\b\u0002\u0010\r\u001a\u0012\u0012\u0004\u0012\u00020\u000f\u0012\u0004\u0012\u00020\u000b0\u000ej\u0002`\u0010\u00a2\u0006\u0002\u0010\u0011J\u0006\u0010\u0017\u001a\u00020\u000bJ\b\u0010\u0018\u001a\u00020\u000bH\u0002J\"\u0010\u0019\u001a\u00020\u001a2\u0006\u0010\u001b\u001a\u00020\u000f2\u0006\u0010\u001c\u001a\u00020\u001d2\n\b\u0002\u0010\u001e\u001a\u0004\u0018\u00010\u000fJ\"\u0010\u001f\u001a\u00020\u001a2\u0006\u0010 \u001a\u00020!2\u0006\u0010\u001c\u001a\u00020\u001d2\n\b\u0002\u0010\u001e\u001a\u0004\u0018\u00010\u000fJ\u0006\u0010\"\u001a\u00020\u000bJ\u0006\u0010#\u001a\u00020\u000bJ$\u0010$\u001a\u00020\u001a2\u0006\u0010%\u001a\u00020&2\u0006\u0010\u001c\u001a\u00020\u001d2\n\b\u0002\u0010\u001e\u001a\u0004\u0018\u00010\u000fH\u0002J\u0012\u0010'\u001a\u0004\u0018\u00010\u00152\u0006\u0010%\u001a\u00020&H\u0002R\u000e\u0010\u0012\u001a\u00020\u0013X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0014\u001a\u0004\u0018\u00010\u0015X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u001e\u0010\r\u001a\u0012\u0012\u0004\u0012\u00020\u000f\u0012\u0004\u0012\u00020\u000b0\u000ej\u0002`\u0010X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0016\u001a\u00020\u0013X\u0082\u0004\u00a2\u0006\u0002\n\u0000RW\u0010\u0002\u001aK\u0012\u0013\u0012\u00110\u0004\u00a2\u0006\f\b\u0005\u0012\b\b\u0006\u0012\u0004\b\b(\u0007\u0012\u0013\u0012\u00110\u0004\u00a2\u0006\f\b\u0005\u0012\b\b\u0006\u0012\u0004\b\b(\b\u0012\u0013\u0012\u00110\t\u00a2\u0006\f\b\u0005\u0012\b\b\u0006\u0012\u0004\b\b(\n\u0012\u0004\u0012\u00020\u000b0\u0003j\u0002`\fX\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006)"}, d2 = {"Lcom/invictus/xmd/core/TorrentEngine;", "", "progress", "Lkotlin/Function3;", "", "Lkotlin/ParameterName;", "name", "done", "total", "", "speedBps", "", "Lcom/invictus/xmd/core/ProgressFn;", "log", "Lkotlin/Function1;", "", "Lcom/invictus/xmd/core/LogFn;", "(Lkotlin/jvm/functions/Function3;Lkotlin/jvm/functions/Function1;)V", "cancelled", "Ljava/util/concurrent/atomic/AtomicBoolean;", "handle", "Lorg/libtorrent4j/TorrentHandle;", "paused", "cancel", "checkpoint", "downloadMagnet", "Lcom/invictus/xmd/core/TorrentResult;", "magnetUri", "saveDir", "Ljava/io/File;", "selectedFileIndices", "downloadTorrentFile", "torrentBytes", "", "pause", "resume", "startAndPoll", "ti", "Lorg/libtorrent4j/TorrentInfo;", "waitForHandle", "Companion", "app_liteDebug"})
public final class TorrentEngine {
    @org.jetbrains.annotations.NotNull()
    private final kotlin.jvm.functions.Function3<java.lang.Long, java.lang.Long, java.lang.Double, kotlin.Unit> progress = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.jvm.functions.Function1<java.lang.String, kotlin.Unit> log = null;
    @org.jetbrains.annotations.NotNull()
    private final java.util.concurrent.atomic.AtomicBoolean cancelled = null;
    @org.jetbrains.annotations.NotNull()
    private final java.util.concurrent.atomic.AtomicBoolean paused = null;
    @kotlin.jvm.Volatile()
    @org.jetbrains.annotations.Nullable()
    private volatile org.libtorrent4j.TorrentHandle handle;
    private static final int METADATA_TIMEOUT_SECONDS = 60;
    private static final long POLL_INTERVAL_MS = 700L;
    private static final int HANDLE_WAIT_ATTEMPTS = 50;
    private static final long HANDLE_WAIT_INTERVAL_MS = 200L;
    @org.jetbrains.annotations.NotNull()
    public static final com.invictus.xmd.core.TorrentEngine.Companion Companion = null;
    
    public TorrentEngine(@org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function3<? super java.lang.Long, ? super java.lang.Long, ? super java.lang.Double, kotlin.Unit> progress, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function1<? super java.lang.String, kotlin.Unit> log) {
        super();
    }
    
    public final void pause() {
    }
    
    public final void resume() {
    }
    
    public final void cancel() {
    }
    
    /**
     * Resolves a magnet link's metadata over DHT/peers (this is the slow,
     * sometimes-fails part -- a dead/unseeded magnet just times out here),
     * then downloads it into [saveDir]. Blocks the calling thread until the
     * torrent finishes, so call this from a background dispatcher.
     */
    @org.jetbrains.annotations.NotNull()
    public final com.invictus.xmd.core.TorrentResult downloadMagnet(@org.jetbrains.annotations.NotNull()
    java.lang.String magnetUri, @org.jetbrains.annotations.NotNull()
    java.io.File saveDir, @org.jetbrains.annotations.Nullable()
    java.lang.String selectedFileIndices) {
        return null;
    }
    
    /**
     * Same as [downloadMagnet] but for a .torrent file already fetched as bytes.
     */
    @org.jetbrains.annotations.NotNull()
    public final com.invictus.xmd.core.TorrentResult downloadTorrentFile(@org.jetbrains.annotations.NotNull()
    byte[] torrentBytes, @org.jetbrains.annotations.NotNull()
    java.io.File saveDir, @org.jetbrains.annotations.Nullable()
    java.lang.String selectedFileIndices) {
        return null;
    }
    
    private final com.invictus.xmd.core.TorrentResult startAndPoll(org.libtorrent4j.TorrentInfo ti, java.io.File saveDir, java.lang.String selectedFileIndices) {
        return null;
    }
    
    /**
     * libtorrent hands back a TorrentHandle asynchronously after download() is called.
     */
    private final org.libtorrent4j.TorrentHandle waitForHandle(org.libtorrent4j.TorrentInfo ti) {
        return null;
    }
    
    private final void checkpoint() {
    }
    
    public TorrentEngine() {
        super();
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u001a\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0010\t\n\u0002\b\u0003\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0006X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\t"}, d2 = {"Lcom/invictus/xmd/core/TorrentEngine$Companion;", "", "()V", "HANDLE_WAIT_ATTEMPTS", "", "HANDLE_WAIT_INTERVAL_MS", "", "METADATA_TIMEOUT_SECONDS", "POLL_INTERVAL_MS", "app_liteDebug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}