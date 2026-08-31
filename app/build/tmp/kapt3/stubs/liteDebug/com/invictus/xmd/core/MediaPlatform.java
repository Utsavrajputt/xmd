package com.invictus.xmd.core;

/**
 * Where a queue item's bytes actually come from. DIRECT covers everything
 * the app already handled (FuckingFast share links, generic direct URLs,
 * fitgirl-expanded links, magnet/.torrent via TorrentEngine) via their own
 * existing engines. YOUTUBE is downloaded/merged by yt-dlp itself (see
 * YtDlpManager) instead -- no directUrl, its own percent-based progress.
 * Deliberately left room to grow (INSTAGRAM, TERABOX, ...) since they'll
 * likely follow the same "external extractor, percent progress" shape.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0002\b\u0004\b\u0086\u0081\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00000\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002j\u0002\b\u0003j\u0002\b\u0004\u00a8\u0006\u0005"}, d2 = {"Lcom/invictus/xmd/core/MediaPlatform;", "", "(Ljava/lang/String;I)V", "DIRECT", "YOUTUBE", "app_liteDebug"})
public enum MediaPlatform {
    /*public static final*/ DIRECT /* = new DIRECT() */,
    /*public static final*/ YOUTUBE /* = new YOUTUBE() */;
    
    MediaPlatform() {
    }
    
    @org.jetbrains.annotations.NotNull()
    public static kotlin.enums.EnumEntries<com.invictus.xmd.core.MediaPlatform> getEntries() {
        return null;
    }
}