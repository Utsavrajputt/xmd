package com.invictus.xmd.core;

/**
 * Auto-categorizes a link/filename into a [DownloadCategory] by file
 * extension, IDM-style -- no manual picker. Checked against the pasted
 * URL's path first; DownloadService re-checks against the resolved
 * filename (Content-Disposition / final URL) once that's known, in case
 * the extension wasn't visible in the original share/short link.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\"\n\u0002\u0010\u000e\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0005\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u001a\u0010\t\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\u00052\n\b\u0002\u0010\f\u001a\u0004\u0018\u00010\u0005J\u0014\u0010\r\u001a\u0004\u0018\u00010\u00052\b\u0010\u000e\u001a\u0004\u0018\u00010\u0005H\u0002R\u0014\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u0006\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u000f"}, d2 = {"Lcom/invictus/xmd/core/CategoryDetector;", "", "()V", "APP_EXT", "", "", "DOCUMENT_EXT", "MUSIC_EXT", "VIDEO_EXT", "detect", "Lcom/invictus/xmd/core/DownloadCategory;", "url", "hint", "extensionOf", "value", "app_liteDebug"})
public final class CategoryDetector {
    @org.jetbrains.annotations.NotNull()
    private static final java.util.Set<java.lang.String> VIDEO_EXT = null;
    @org.jetbrains.annotations.NotNull()
    private static final java.util.Set<java.lang.String> MUSIC_EXT = null;
    @org.jetbrains.annotations.NotNull()
    private static final java.util.Set<java.lang.String> DOCUMENT_EXT = null;
    @org.jetbrains.annotations.NotNull()
    private static final java.util.Set<java.lang.String> APP_EXT = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.invictus.xmd.core.CategoryDetector INSTANCE = null;
    
    private CategoryDetector() {
        super();
    }
    
    /**
     * [hint] is checked first when given (e.g. a resolved filename from
     * Content-Disposition), falling back to [url]'s own path -- covers
     * short/share links whose extension only becomes known after resolve.
     */
    @org.jetbrains.annotations.NotNull()
    public final com.invictus.xmd.core.DownloadCategory detect(@org.jetbrains.annotations.NotNull()
    java.lang.String url, @org.jetbrains.annotations.Nullable()
    java.lang.String hint) {
        return null;
    }
    
    private final java.lang.String extensionOf(java.lang.String value) {
        return null;
    }
}