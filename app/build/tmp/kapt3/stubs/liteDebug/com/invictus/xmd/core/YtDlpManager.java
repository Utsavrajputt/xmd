package com.invictus.xmd.core;

/**
 * "lite" flavor stub -- this build has no youtubedl-android dependency at
 * all (see app/build.gradle.kts), so there's nothing here to wrap. Kept
 * with the exact same public API as the "full" flavor's real
 * YtDlpManager.kt so MainActivity/DownloadService (which live in the
 * shared main/ source set, built into both flavors) compile against
 * either one without any flavor-specific branching of their own beyond
 * the BuildConfig.HAS_YOUTUBE_SUPPORT check that gates ever calling these.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000`\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\b\t\b\u00c6\u0002\u0018\u00002\u00020\u0001:\u0004()*+B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u0005\u001a\u00020\u00042\u0006\u0010\u0006\u001a\u00020\u0007J\u000e\u0010\b\u001a\u00020\t2\u0006\u0010\n\u001a\u00020\u0004J\u000e\u0010\u000b\u001a\u00020\t2\u0006\u0010\f\u001a\u00020\rJB\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\u00042\u0006\u0010\u0011\u001a\u00020\u00122\u0006\u0010\u0013\u001a\u00020\u000f2\u0006\u0010\n\u001a\u00020\u00042\u0006\u0010\f\u001a\u00020\r2\u0012\u0010\u0014\u001a\u000e\u0012\u0004\u0012\u00020\u0016\u0012\u0004\u0012\u00020\t0\u0015J\u000e\u0010\u0017\u001a\u00020\u00182\u0006\u0010\f\u001a\u00020\rJ\u001f\u0010\u0019\u001a\u0004\u0018\u00010\u00042\u0006\u0010\u0006\u001a\u00020\u00072\b\u0010\u001a\u001a\u0004\u0018\u00010\u001b\u00a2\u0006\u0002\u0010\u001cJ\u0010\u0010\u001d\u001a\u0004\u0018\u00010\u00042\u0006\u0010\f\u001a\u00020\rJ\u000e\u0010\u001e\u001a\u00020\u00182\u0006\u0010\f\u001a\u00020\rJ\u0006\u0010\u001f\u001a\u00020\u0018J\u0016\u0010 \u001a\u00020!2\u0006\u0010\u0010\u001a\u00020\u00042\u0006\u0010\f\u001a\u00020\rJ\u0016\u0010\"\u001a\b\u0012\u0004\u0012\u00020\u00120#2\b\b\u0002\u0010$\u001a\u00020\u0018J\u0018\u0010%\u001a\u0004\u0018\u00010\u00042\u0006\u0010\f\u001a\u00020\r2\u0006\u0010&\u001a\u00020\u0018J\u0010\u0010'\u001a\u0004\u0018\u00010\u00042\u0006\u0010\f\u001a\u00020\rR\u000e\u0010\u0003\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000\u00a8\u0006,"}, d2 = {"Lcom/invictus/xmd/core/YtDlpManager;", "", "()V", "AUDIO_ONLY_SELECTOR", "", "advancedSelector", "format", "Lcom/invictus/xmd/core/YtDlpManager$ProbedFormat;", "cancel", "", "processId", "delete", "context", "Landroid/content/Context;", "download", "Ljava/io/File;", "url", "option", "Lcom/invictus/xmd/core/YtDlpManager$QualityOption;", "outputDir", "onProgress", "Lkotlin/Function1;", "Lcom/invictus/xmd/core/YtDlpManager$DownloadProgress;", "ensureReady", "", "formatSize", "durationSeconds", "", "(Lcom/invictus/xmd/core/YtDlpManager$ProbedFormat;Ljava/lang/Integer;)Ljava/lang/String;", "install", "isInstalled", "isReady", "probeFormats", "Lcom/invictus/xmd/core/YtDlpManager$ProbeResult;", "standardQualityOptions", "", "isGenericOrHls", "switchChannel", "toNightly", "update", "DownloadProgress", "ProbeResult", "ProbedFormat", "QualityOption", "app_liteDebug"})
public final class YtDlpManager {
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String AUDIO_ONLY_SELECTOR = "bestaudio/best";
    @org.jetbrains.annotations.NotNull()
    public static final com.invictus.xmd.core.YtDlpManager INSTANCE = null;
    
    private YtDlpManager() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<com.invictus.xmd.core.YtDlpManager.QualityOption> standardQualityOptions(boolean isGenericOrHls) {
        return null;
    }
    
    /**
     * Always empty in this flavor -- gated behind BuildConfig.HAS_YOUTUBE_SUPPORT at the call site, same as everything else here.
     */
    @org.jetbrains.annotations.NotNull()
    public final com.invictus.xmd.core.YtDlpManager.ProbeResult probeFormats(@org.jetbrains.annotations.NotNull()
    java.lang.String url, @org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String advancedSelector(@org.jetbrains.annotations.NotNull()
    com.invictus.xmd.core.YtDlpManager.ProbedFormat format) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String formatSize(@org.jetbrains.annotations.NotNull()
    com.invictus.xmd.core.YtDlpManager.ProbedFormat format, @org.jetbrains.annotations.Nullable()
    java.lang.Integer durationSeconds) {
        return null;
    }
    
    public final boolean isInstalled(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        return false;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String install(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        return null;
    }
    
    public final void delete(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
    }
    
    public final boolean ensureReady(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        return false;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String update(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String switchChannel(@org.jetbrains.annotations.NotNull()
    android.content.Context context, boolean toNightly) {
        return null;
    }
    
    public final boolean isReady() {
        return false;
    }
    
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
    
    public final void cancel(@org.jetbrains.annotations.NotNull()
    java.lang.String processId) {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0002\b\t\n\u0002\u0010\u000b\n\u0002\b\u0004\b\u0086\b\u0018\u00002\u00020\u0001B\u0017\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\b\u0010\u0004\u001a\u0004\u0018\u00010\u0005\u00a2\u0006\u0002\u0010\u0006J\t\u0010\u000b\u001a\u00020\u0003H\u00c6\u0003J\u000b\u0010\f\u001a\u0004\u0018\u00010\u0005H\u00c6\u0003J\u001f\u0010\r\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\n\b\u0002\u0010\u0004\u001a\u0004\u0018\u00010\u0005H\u00c6\u0001J\u0013\u0010\u000e\u001a\u00020\u000f2\b\u0010\u0010\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0011\u001a\u00020\u0003H\u00d6\u0001J\t\u0010\u0012\u001a\u00020\u0005H\u00d6\u0001R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0013\u0010\u0004\u001a\u0004\u0018\u00010\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\n\u00a8\u0006\u0013"}, d2 = {"Lcom/invictus/xmd/core/YtDlpManager$DownloadProgress;", "", "percent", "", "statusText", "", "(ILjava/lang/String;)V", "getPercent", "()I", "getStatusText", "()Ljava/lang/String;", "component1", "component2", "copy", "equals", "", "other", "hashCode", "toString", "app_liteDebug"})
    public static final class DownloadProgress {
        private final int percent = 0;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.String statusText = null;
        
        public DownloadProgress(int percent, @org.jetbrains.annotations.Nullable()
        java.lang.String statusText) {
            super();
        }
        
        public final int getPercent() {
            return 0;
        }
        
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
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000*\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\u000b\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0000\b\u0086\b\u0018\u00002\u00020\u0001B\u001d\u0012\f\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003\u0012\b\u0010\u0005\u001a\u0004\u0018\u00010\u0006\u00a2\u0006\u0002\u0010\u0007J\u000f\u0010\r\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003H\u00c6\u0003J\u0010\u0010\u000e\u001a\u0004\u0018\u00010\u0006H\u00c6\u0003\u00a2\u0006\u0002\u0010\tJ*\u0010\u000f\u001a\u00020\u00002\u000e\b\u0002\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u00032\n\b\u0002\u0010\u0005\u001a\u0004\u0018\u00010\u0006H\u00c6\u0001\u00a2\u0006\u0002\u0010\u0010J\u0013\u0010\u0011\u001a\u00020\u00122\b\u0010\u0013\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0014\u001a\u00020\u0006H\u00d6\u0001J\t\u0010\u0015\u001a\u00020\u0016H\u00d6\u0001R\u0015\u0010\u0005\u001a\u0004\u0018\u00010\u0006\u00a2\u0006\n\n\u0002\u0010\n\u001a\u0004\b\b\u0010\tR\u0017\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\f\u00a8\u0006\u0017"}, d2 = {"Lcom/invictus/xmd/core/YtDlpManager$ProbeResult;", "", "formats", "", "Lcom/invictus/xmd/core/YtDlpManager$ProbedFormat;", "durationSeconds", "", "(Ljava/util/List;Ljava/lang/Integer;)V", "getDurationSeconds", "()Ljava/lang/Integer;", "Ljava/lang/Integer;", "getFormats", "()Ljava/util/List;", "component1", "component2", "copy", "(Ljava/util/List;Ljava/lang/Integer;)Lcom/invictus/xmd/core/YtDlpManager$ProbeResult;", "equals", "", "other", "hashCode", "toString", "", "app_liteDebug"})
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
     * One raw stream as reported by the real format probe -- see the full flavor's YtDlpManager.kt for field meanings.
     */
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00000\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0004\n\u0002\u0010\t\n\u0000\n\u0002\u0010\u0006\n\u0002\b\n\n\u0002\u0010\u000b\n\u0002\b\u0018\b\u0086\b\u0018\u00002\u00020\u0001BQ\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u0012\b\u0010\u0005\u001a\u0004\u0018\u00010\u0006\u0012\b\u0010\u0007\u001a\u0004\u0018\u00010\u0006\u0012\b\u0010\b\u001a\u0004\u0018\u00010\u0003\u0012\b\u0010\t\u001a\u0004\u0018\u00010\u0003\u0012\b\u0010\n\u001a\u0004\u0018\u00010\u000b\u0012\b\u0010\f\u001a\u0004\u0018\u00010\r\u00a2\u0006\u0002\u0010\u000eJ\t\u0010\"\u001a\u00020\u0003H\u00c6\u0003J\t\u0010#\u001a\u00020\u0003H\u00c6\u0003J\u0010\u0010$\u001a\u0004\u0018\u00010\u0006H\u00c6\u0003\u00a2\u0006\u0002\u0010\u0014J\u0010\u0010%\u001a\u0004\u0018\u00010\u0006H\u00c6\u0003\u00a2\u0006\u0002\u0010\u0014J\u000b\u0010&\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\u000b\u0010'\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\u0010\u0010(\u001a\u0004\u0018\u00010\u000bH\u00c6\u0003\u00a2\u0006\u0002\u0010\u001cJ\u0010\u0010)\u001a\u0004\u0018\u00010\rH\u00c6\u0003\u00a2\u0006\u0002\u0010\u001fJj\u0010*\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00032\n\b\u0002\u0010\u0005\u001a\u0004\u0018\u00010\u00062\n\b\u0002\u0010\u0007\u001a\u0004\u0018\u00010\u00062\n\b\u0002\u0010\b\u001a\u0004\u0018\u00010\u00032\n\b\u0002\u0010\t\u001a\u0004\u0018\u00010\u00032\n\b\u0002\u0010\n\u001a\u0004\u0018\u00010\u000b2\n\b\u0002\u0010\f\u001a\u0004\u0018\u00010\rH\u00c6\u0001\u00a2\u0006\u0002\u0010+J\u0013\u0010,\u001a\u00020\u00182\b\u0010-\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010.\u001a\u00020\u0006H\u00d6\u0001J\t\u0010/\u001a\u00020\u0003H\u00d6\u0001R\u0013\u0010\t\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u0011\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0010R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0012\u0010\u0010R\u0015\u0010\u0007\u001a\u0004\u0018\u00010\u0006\u00a2\u0006\n\n\u0002\u0010\u0015\u001a\u0004\b\u0013\u0010\u0014R\u0015\u0010\u0005\u001a\u0004\u0018\u00010\u0006\u00a2\u0006\n\n\u0002\u0010\u0015\u001a\u0004\b\u0016\u0010\u0014R\u0011\u0010\u0017\u001a\u00020\u00188F\u00a2\u0006\u0006\u001a\u0004\b\u0017\u0010\u0019R\u0011\u0010\u001a\u001a\u00020\u00188F\u00a2\u0006\u0006\u001a\u0004\b\u001a\u0010\u0019R\u0015\u0010\n\u001a\u0004\u0018\u00010\u000b\u00a2\u0006\n\n\u0002\u0010\u001d\u001a\u0004\b\u001b\u0010\u001cR\u0015\u0010\f\u001a\u0004\u0018\u00010\r\u00a2\u0006\n\n\u0002\u0010 \u001a\u0004\b\u001e\u0010\u001fR\u0013\u0010\b\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b!\u0010\u0010\u00a8\u00060"}, d2 = {"Lcom/invictus/xmd/core/YtDlpManager$ProbedFormat;", "", "formatId", "", "ext", "height", "", "fps", "vcodec", "acodec", "sizeBytes", "", "tbr", "", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Integer;Ljava/lang/Integer;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Long;Ljava/lang/Double;)V", "getAcodec", "()Ljava/lang/String;", "getExt", "getFormatId", "getFps", "()Ljava/lang/Integer;", "Ljava/lang/Integer;", "getHeight", "isAudioOnly", "", "()Z", "isVideoOnly", "getSizeBytes", "()Ljava/lang/Long;", "Ljava/lang/Long;", "getTbr", "()Ljava/lang/Double;", "Ljava/lang/Double;", "getVcodec", "component1", "component2", "component3", "component4", "component5", "component6", "component7", "component8", "copy", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Integer;Ljava/lang/Integer;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Long;Ljava/lang/Double;)Lcom/invictus/xmd/core/YtDlpManager$ProbedFormat;", "equals", "other", "hashCode", "toString", "app_liteDebug"})
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
        @org.jetbrains.annotations.Nullable()
        private final java.lang.Long sizeBytes = null;
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
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Long getSizeBytes() {
            return null;
        }
        
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
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\f\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001B\u001d\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\u0002\u0010\u0007J\t\u0010\f\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\r\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u000e\u001a\u00020\u0006H\u00c6\u0003J'\u0010\u000f\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00032\b\b\u0002\u0010\u0005\u001a\u00020\u0006H\u00c6\u0001J\u0013\u0010\u0010\u001a\u00020\u00062\b\u0010\u0011\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0012\u001a\u00020\u0013H\u00d6\u0001J\t\u0010\u0014\u001a\u00020\u0003H\u00d6\u0001R\u0011\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u0011\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\nR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\t\u00a8\u0006\u0015"}, d2 = {"Lcom/invictus/xmd/core/YtDlpManager$QualityOption;", "", "label", "", "formatSelector", "isAudioOnly", "", "(Ljava/lang/String;Ljava/lang/String;Z)V", "getFormatSelector", "()Ljava/lang/String;", "()Z", "getLabel", "component1", "component2", "component3", "copy", "equals", "other", "hashCode", "", "toString", "app_liteDebug"})
    public static final class QualityOption {
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String label = null;
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