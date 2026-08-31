package com.invictus.xmd.core;

/**
 * User-facing download category. Each maps to its own subfolder under the
 * app's downloads directory (auto-created on first download in that
 * category). Auto-detected per link from its file extension by
 * [com.invictus.xmd.core.CategoryDetector] -- not user-picked
 * anymore (IDM-style auto-categorization), stored on each [QueueItem] so
 * DownloadService knows where to save it.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0000\n\u0002\u0010\u000e\n\u0002\b\f\b\u0086\u0081\u0002\u0018\u0000 \u000e2\b\u0012\u0004\u0012\u00020\u00000\u0001:\u0001\u000eB\u0017\b\u0002\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0005R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0011\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\u0007j\u0002\b\tj\u0002\b\nj\u0002\b\u000bj\u0002\b\fj\u0002\b\r\u00a8\u0006\u000f"}, d2 = {"Lcom/invictus/xmd/core/DownloadCategory;", "", "folderName", "", "label", "(Ljava/lang/String;ILjava/lang/String;Ljava/lang/String;)V", "getFolderName", "()Ljava/lang/String;", "getLabel", "VIDEOS", "MUSIC", "DOCUMENTS", "APPS", "OTHERS", "Companion", "app_fullDebug"})
public enum DownloadCategory {
    /*public static final*/ VIDEOS /* = new VIDEOS(null, null) */,
    /*public static final*/ MUSIC /* = new MUSIC(null, null) */,
    /*public static final*/ DOCUMENTS /* = new DOCUMENTS(null, null) */,
    /*public static final*/ APPS /* = new APPS(null, null) */,
    /*public static final*/ OTHERS /* = new OTHERS(null, null) */;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.String folderName = null;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.String label = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.invictus.xmd.core.DownloadCategory.Companion Companion = null;
    
    DownloadCategory(java.lang.String folderName, java.lang.String label) {
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getFolderName() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getLabel() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public static kotlin.enums.EnumEntries<com.invictus.xmd.core.DownloadCategory> getEntries() {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0006\u0010\u0003\u001a\u00020\u0004\u00a8\u0006\u0005"}, d2 = {"Lcom/invictus/xmd/core/DownloadCategory$Companion;", "", "()V", "default", "Lcom/invictus/xmd/core/DownloadCategory;", "app_fullDebug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}