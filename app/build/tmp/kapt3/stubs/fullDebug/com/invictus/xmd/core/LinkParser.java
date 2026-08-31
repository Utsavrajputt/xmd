package com.invictus.xmd.core;

/**
 * Parses/validates FuckingFast links and expands fitgirl-repacks source
 * pages into the FuckingFast share links they contain.
 *
 * Kotlin port of ff_downloader/core/resolver.py's non-browser pieces
 * (_file_id, _is_direct_link, _is_share_link, extract_fitgirl_links,
 * expand_sources).
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000:\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\"\n\u0002\b\u0004\n\u0002\u0010 \n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u000b\n\u0002\b\n\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\"\u0010\r\u001a\b\u0012\u0004\u0012\u00020\u00040\u000e2\f\u0010\u000f\u001a\b\u0012\u0004\u0012\u00020\u00040\u000e2\u0006\u0010\u0010\u001a\u00020\u0011J\u001c\u0010\u0012\u001a\b\u0012\u0004\u0012\u00020\u00040\u000e2\u0006\u0010\u0013\u001a\u00020\u00042\u0006\u0010\u0010\u001a\u00020\u0011J\u000e\u0010\u0014\u001a\u00020\u00042\u0006\u0010\u0015\u001a\u00020\u0004J\u000e\u0010\u0016\u001a\u00020\u00172\u0006\u0010\u0015\u001a\u00020\u0004J\u000e\u0010\u0018\u001a\u00020\u00172\u0006\u0010\u0015\u001a\u00020\u0004J\u000e\u0010\u0019\u001a\u00020\u00172\u0006\u0010\u0015\u001a\u00020\u0004J\u000e\u0010\u001a\u001a\u00020\u00172\u0006\u0010\u0015\u001a\u00020\u0004J\u000e\u0010\u001b\u001a\u00020\u00172\u0006\u0010\u0015\u001a\u00020\u0004J\u000e\u0010\u001c\u001a\u00020\u00172\u0006\u0010\u0015\u001a\u00020\u0004J\u000e\u0010\u001d\u001a\u00020\u00172\u0006\u0010\u0015\u001a\u00020\u0004J\u000e\u0010\u001e\u001a\u00020\u00172\u0006\u0010\u0015\u001a\u00020\u0004J\u000e\u0010\u001f\u001a\u00020\u00172\u0006\u0010\u0015\u001a\u00020\u0004J\u000e\u0010 \u001a\u00020\u00172\u0006\u0010\u0015\u001a\u00020\u0004R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u0016\u0010\u0005\u001a\n \u0007*\u0004\u0018\u00010\u00060\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00040\tX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\n\u001a\b\u0012\u0004\u0012\u00020\u00040\tX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0016\u0010\u000b\u001a\n \u0007*\u0004\u0018\u00010\u00060\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\f\u001a\b\u0012\u0004\u0012\u00020\u00040\tX\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006!"}, d2 = {"Lcom/invictus/xmd/core/LinkParser;", "", "()V", "DIRECT_HOST", "", "FILE_ID_PATTERN", "Ljava/util/regex/Pattern;", "kotlin.jvm.PlatformType", "FITGIRL_HOSTS", "", "SHARE_HOSTS", "SHARE_LINK_PATTERN", "YOUTUBE_HOSTS", "expandSources", "", "links", "client", "Lokhttp3/OkHttpClient;", "extractFitgirlLinks", "url", "fileId", "link", "isDirectLink", "", "isFitgirlPage", "isGenericDownloadUrl", "isHlsOrDashLink", "isMagnetLink", "isShareLink", "isTorrentFileLink", "isTorrentLink", "isYoutubeLink", "needsYtDlp", "app_fullDebug"})
public final class LinkParser {
    @org.jetbrains.annotations.NotNull()
    private static final java.util.Set<java.lang.String> SHARE_HOSTS = null;
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String DIRECT_HOST = "dl.fuckingfast.co";
    @org.jetbrains.annotations.NotNull()
    private static final java.util.Set<java.lang.String> FITGIRL_HOSTS = null;
    @org.jetbrains.annotations.NotNull()
    private static final java.util.Set<java.lang.String> YOUTUBE_HOSTS = null;
    private static final java.util.regex.Pattern FILE_ID_PATTERN = null;
    private static final java.util.regex.Pattern SHARE_LINK_PATTERN = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.invictus.xmd.core.LinkParser INSTANCE = null;
    
    private LinkParser() {
        super();
    }
    
    public final boolean isDirectLink(@org.jetbrains.annotations.NotNull()
    java.lang.String link) {
        return false;
    }
    
    /**
     * True for a magnet: URI (magnet:?xt=urn:btih:...).
     */
    public final boolean isMagnetLink(@org.jetbrains.annotations.NotNull()
    java.lang.String link) {
        return false;
    }
    
    /**
     * True for an http(s) link that points straight at a .torrent file, or
     * a content:// URI for a .torrent file picked from local storage via
     * the system file picker (HomeFragment's "Pick .torrent file" button --
     * the picker's mime filter already restricts choices to .torrent, so
     * any content:// URI reaching here is trusted to be one).
     */
    public final boolean isTorrentFileLink(@org.jetbrains.annotations.NotNull()
    java.lang.String link) {
        return false;
    }
    
    public final boolean isTorrentLink(@org.jetbrains.annotations.NotNull()
    java.lang.String link) {
        return false;
    }
    
    /**
     * True for any well-formed http(s) URL that isn't a FuckingFast share
     * link or a fitgirl-repacks page — i.e. something already downloadable
     * as-is (dl.fuckingfast.co, but also R2/S3/other CDN direct links a
     * user might paste after resolving elsewhere). Magnet/.torrent links are
     * "generic" in the same sense — nothing to resolve, DownloadService can
     * pick them up and start immediately — even though they don't use an
     * http(s) scheme themselves (magnet: has no host at all).
     */
    public final boolean isGenericDownloadUrl(@org.jetbrains.annotations.NotNull()
    java.lang.String link) {
        return false;
    }
    
    public final boolean isShareLink(@org.jetbrains.annotations.NotNull()
    java.lang.String link) {
        return false;
    }
    
    public final boolean isFitgirlPage(@org.jetbrains.annotations.NotNull()
    java.lang.String link) {
        return false;
    }
    
    /**
     * True for a youtube.com/youtu.be video (or music.youtube.com) link -- routed to the yt-dlp quality-picker flow instead of a normal resolve.
     */
    public final boolean isYoutubeLink(@org.jetbrains.annotations.NotNull()
    java.lang.String link) {
        return false;
    }
    
    /**
     * True for a direct HLS (.m3u8) or DASH (.mpd) manifest link -- these
     * are streams, not a single file, so (like YouTube) they need yt-dlp to
     * fetch every segment and mux them into one playable file rather than a
     * plain byte-for-byte download. Reuses [MediaSniffer]'s own URL
     * classifier so a link is never treated differently here than it would
     * be by the "Find videos" sniffer sheet.
     */
    public final boolean isHlsOrDashLink(@org.jetbrains.annotations.NotNull()
    java.lang.String link) {
        return false;
    }
    
    /**
     * True for anything that needs the yt-dlp quality-picker flow instead
     * of a normal resolve -- YouTube, plus any plain HLS/DASH link pasted
     * or shared directly (not just ones caught by the in-browser sniffer).
     * Every routing decision (MainActivity, ShareReceiverActivity) should
     * check this rather than isYoutubeLink alone, or a pasted .m3u8 link
     * falls through to isGenericDownloadUrl and gets "downloaded" as the
     * raw manifest text instead of the actual video.
     */
    public final boolean needsYtDlp(@org.jetbrains.annotations.NotNull()
    java.lang.String link) {
        return false;
    }
    
    /**
     * Extracts the file id from a fuckingfast.co share URL, e.g. fuckingfast.co/f/abc123 -> abc123
     */
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String fileId(@org.jetbrains.annotations.NotNull()
    java.lang.String link) {
        return null;
    }
    
    /**
     * Scans a fitgirl-repacks page for embedded fuckingfast.co share links.
     */
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<java.lang.String> extractFitgirlLinks(@org.jetbrains.annotations.NotNull()
    java.lang.String url, @org.jetbrains.annotations.NotNull()
    okhttp3.OkHttpClient client) {
        return null;
    }
    
    /**
     * Expands a list of raw pasted links/pages into concrete fuckingfast links.
     */
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<java.lang.String> expandSources(@org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.String> links, @org.jetbrains.annotations.NotNull()
    okhttp3.OkHttpClient client) {
        return null;
    }
}