package com.invictus.xmd.core;

/**
 * Classifies a request URL (+ optional Content-Type, when the response
 * headers are available) as a sniffable media stream for BrowserFragment's
 * "Find videos" chip -- HLS/DASH manifests, which need yt-dlp to actually
 * fetch (resolveYoutube's path, reused as-is for these), and direct
 * video/audio files, which are just a normal DownloadEngine download.
 *
 * Deliberately pure/stateless (no Context, no I/O) so it's cheap to call on
 * every single sub-resource request a page makes, from WebView's own
 * background thread in BrowserFragment.shouldInterceptRequest.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000.\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0004\n\u0002\u0010\u000b\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u00c6\u0002\u0018\u00002\u00020\u0001:\u0002\u0013\u0014B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u001a\u0010\t\u001a\u0004\u0018\u00010\n2\u0006\u0010\u000b\u001a\u00020\f2\b\u0010\r\u001a\u0004\u0018\u00010\fJ\u0010\u0010\u000e\u001a\u0004\u0018\u00010\n2\u0006\u0010\u000b\u001a\u00020\fJ\u000e\u0010\u000f\u001a\u00020\f2\u0006\u0010\u000b\u001a\u00020\fJ\n\u0010\u0010\u001a\u00020\u0011*\u00020\u0012R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0015"}, d2 = {"Lcom/invictus/xmd/core/MediaSniffer;", "", "()V", "AUDIO_EXT", "Lkotlin/text/Regex;", "DASH_EXT", "HLS_EXT", "HLS_PATH_HINT", "VIDEO_EXT", "classifyContentType", "Lcom/invictus/xmd/core/MediaSniffer$Sniffed;", "url", "", "contentType", "classifyUrl", "guessLabel", "needsQualityPicker", "", "Lcom/invictus/xmd/core/MediaSniffer$Kind;", "Kind", "Sniffed", "app_liteDebug"})
public final class MediaSniffer {
    @org.jetbrains.annotations.NotNull()
    private static final kotlin.text.Regex HLS_EXT = null;
    @org.jetbrains.annotations.NotNull()
    private static final kotlin.text.Regex DASH_EXT = null;
    @org.jetbrains.annotations.NotNull()
    private static final kotlin.text.Regex VIDEO_EXT = null;
    @org.jetbrains.annotations.NotNull()
    private static final kotlin.text.Regex AUDIO_EXT = null;
    @org.jetbrains.annotations.NotNull()
    private static final kotlin.text.Regex HLS_PATH_HINT = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.invictus.xmd.core.MediaSniffer INSTANCE = null;
    
    private MediaSniffer() {
        super();
    }
    
    /**
     * Classifies purely from the URL -- used on every request since headers
     * usually aren't available without a full round trip. Returns null for
     * anything that isn't clearly media (the common case, so this stays
     * cheap: two regex passes on a String, no allocation beyond that).
     */
    @org.jetbrains.annotations.Nullable()
    public final com.invictus.xmd.core.MediaSniffer.Sniffed classifyUrl(@org.jetbrains.annotations.NotNull()
    java.lang.String url) {
        return null;
    }
    
    /**
     * Refines (or produces) a classification once a response Content-Type
     * is actually known -- catches extensionless/signed CDN URLs the pure
     * URL pass above would miss. Only called from paths that already have
     * the header cheaply available (never worth a dedicated network probe
     * per request just for this).
     */
    @org.jetbrains.annotations.Nullable()
    public final com.invictus.xmd.core.MediaSniffer.Sniffed classifyContentType(@org.jetbrains.annotations.NotNull()
    java.lang.String url, @org.jetbrains.annotations.Nullable()
    java.lang.String contentType) {
        return null;
    }
    
    /**
     * True for [Kind]s that need yt-dlp (manifest, not a single file).
     */
    public final boolean needsQualityPicker(@org.jetbrains.annotations.NotNull()
    com.invictus.xmd.core.MediaSniffer.Kind $this$needsQualityPicker) {
        return false;
    }
    
    /**
     * Best-effort display name from the URL's last path segment, falling
     * back to the host when the path is empty/opaque (e.g. a bare "/").
     */
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String guessLabel(@org.jetbrains.annotations.NotNull()
    java.lang.String url) {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0002\b\u0006\b\u0086\u0081\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00000\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002j\u0002\b\u0003j\u0002\b\u0004j\u0002\b\u0005j\u0002\b\u0006\u00a8\u0006\u0007"}, d2 = {"Lcom/invictus/xmd/core/MediaSniffer$Kind;", "", "(Ljava/lang/String;I)V", "HLS", "DASH", "DIRECT_VIDEO", "DIRECT_AUDIO", "app_liteDebug"})
    public static enum Kind {
        /*public static final*/ HLS /* = new HLS() */,
        /*public static final*/ DASH /* = new DASH() */,
        /*public static final*/ DIRECT_VIDEO /* = new DIRECT_VIDEO() */,
        /*public static final*/ DIRECT_AUDIO /* = new DIRECT_AUDIO() */;
        
        Kind() {
        }
        
        @org.jetbrains.annotations.NotNull()
        public static kotlin.enums.EnumEntries<com.invictus.xmd.core.MediaSniffer.Kind> getEntries() {
            return null;
        }
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000(\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\b\t\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006J\t\u0010\u000b\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\f\u001a\u00020\u0005H\u00c6\u0003J\u001d\u0010\r\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u0005H\u00c6\u0001J\u0013\u0010\u000e\u001a\u00020\u000f2\b\u0010\u0010\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0011\u001a\u00020\u0012H\u00d6\u0001J\t\u0010\u0013\u001a\u00020\u0003H\u00d6\u0001R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\n\u00a8\u0006\u0014"}, d2 = {"Lcom/invictus/xmd/core/MediaSniffer$Sniffed;", "", "url", "", "kind", "Lcom/invictus/xmd/core/MediaSniffer$Kind;", "(Ljava/lang/String;Lcom/invictus/xmd/core/MediaSniffer$Kind;)V", "getKind", "()Lcom/invictus/xmd/core/MediaSniffer$Kind;", "getUrl", "()Ljava/lang/String;", "component1", "component2", "copy", "equals", "", "other", "hashCode", "", "toString", "app_liteDebug"})
    public static final class Sniffed {
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String url = null;
        @org.jetbrains.annotations.NotNull()
        private final com.invictus.xmd.core.MediaSniffer.Kind kind = null;
        
        public Sniffed(@org.jetbrains.annotations.NotNull()
        java.lang.String url, @org.jetbrains.annotations.NotNull()
        com.invictus.xmd.core.MediaSniffer.Kind kind) {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getUrl() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.invictus.xmd.core.MediaSniffer.Kind getKind() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component1() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.invictus.xmd.core.MediaSniffer.Kind component2() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.invictus.xmd.core.MediaSniffer.Sniffed copy(@org.jetbrains.annotations.NotNull()
        java.lang.String url, @org.jetbrains.annotations.NotNull()
        com.invictus.xmd.core.MediaSniffer.Kind kind) {
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