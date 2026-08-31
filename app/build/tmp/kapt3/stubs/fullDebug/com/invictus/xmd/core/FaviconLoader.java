package com.invictus.xmd.core;

/**
 * Fetches a site's favicon for the Browser tab's speed-dial tiles.
 * No third-party image library -- a direct OkHttp GET + BitmapFactory
 * decode, backed by two cache layers so re-showing the speed dial (or
 * relaunching the app) doesn't refetch the same host repeatedly:
 * - an in-memory LRU for the current process
 * - a one-day-old disk cache (see [init]) so a fresh process still hits
 *   disk instead of the network -- this used to be memory-only, so every
 *   cold app start (LruCache wiped with the process) redownloaded every
 *   tile's icon from scratch, even ones fetched minutes earlier.
 *
 * Tries, in order, apple-touch-icon.png (usually the sharpest, 120-180px),
 * Google's public favicon service at a higher requested size, then the
 * site's raw favicon.ico (often only 16-32px and the main source of the
 * blurry/pixelated tiles this used to produce) -- keeping whichever
 * candidate actually decoded to the largest bitmap rather than stopping
 * at the first one that merely succeeded.
 * Returns null (never throws) if all fail, so callers just keep showing
 * the generic link icon already in the layout.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000D\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\t\n\u0000\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u0011\u001a\u00020\u000b2\u0006\u0010\u0012\u001a\u00020\u000bH\u0002J\u0012\u0010\u0013\u001a\u0004\u0018\u00010\f2\u0006\u0010\u0014\u001a\u00020\u000bH\u0002J\u000e\u0010\u0015\u001a\u00020\u00162\u0006\u0010\u0017\u001a\u00020\u0018J\u0010\u0010\u0019\u001a\u0004\u0018\u00010\f2\u0006\u0010\u001a\u001a\u00020\u000bR\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0006X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0006X\u0082T\u00a2\u0006\u0002\n\u0000R\u001a\u0010\t\u001a\u000e\u0012\u0004\u0012\u00020\u000b\u0012\u0004\u0012\u00020\f0\nX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\r\u001a\u00020\u000eX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u000f\u001a\u0004\u0018\u00010\u0010X\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u001b"}, d2 = {"Lcom/invictus/xmd/core/FaviconLoader;", "", "()V", "DISK_CACHE_MAX_AGE_MS", "", "MAX_CACHE_ENTRIES", "", "MIN_ACCEPTABLE_PX", "TARGET_PX", "cache", "Landroid/util/LruCache;", "", "Landroid/graphics/Bitmap;", "client", "Lokhttp3/OkHttpClient;", "diskCacheDir", "Ljava/io/File;", "diskFileName", "host", "fetch", "url", "init", "", "context", "Landroid/content/Context;", "load", "pageUrl", "app_fullDebug"})
public final class FaviconLoader {
    private static final int MAX_CACHE_ENTRIES = 60;
    private static final int TARGET_PX = 128;
    private static final int MIN_ACCEPTABLE_PX = 48;
    private static final long DISK_CACHE_MAX_AGE_MS = 86400000L;
    @org.jetbrains.annotations.NotNull()
    private static final android.util.LruCache<java.lang.String, android.graphics.Bitmap> cache = null;
    @org.jetbrains.annotations.Nullable()
    private static java.io.File diskCacheDir;
    @org.jetbrains.annotations.NotNull()
    private static final okhttp3.OkHttpClient client = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.invictus.xmd.core.FaviconLoader INSTANCE = null;
    
    private FaviconLoader() {
        super();
    }
    
    /**
     * Call once from FfApp.onCreate; harmless if called again.
     */
    public final void init(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
    }
    
    /**
     * Blocking; call from a background thread/coroutine, never the main
     * thread. Tries several sources in order of typical quality (a site's
     * own apple-touch-icon is almost always a crisp 120-180px PNG, whereas
     * favicon.ico is frequently a stretched 16x16 that reads as blurry once
     * it fills a 52dp tile) and keeps the sharpest bitmap actually found
     * rather than stopping at the first response that merely succeeds.
     */
    @org.jetbrains.annotations.Nullable()
    public final android.graphics.Bitmap load(@org.jetbrains.annotations.NotNull()
    java.lang.String pageUrl) {
        return null;
    }
    
    /**
     * Filesystem-safe cache filename for a host, e.g. "www.example.com" -> "www.example.com.png".
     */
    private final java.lang.String diskFileName(java.lang.String host) {
        return null;
    }
    
    private final android.graphics.Bitmap fetch(java.lang.String url) {
        return null;
    }
}