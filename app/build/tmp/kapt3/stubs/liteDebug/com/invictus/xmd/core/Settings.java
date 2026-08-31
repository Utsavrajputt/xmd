package com.invictus.xmd.core;

/**
 * Simple SharedPreferences-backed settings, initialized once from FfApp.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000f\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0015\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0017\n\u0002\u0010\t\n\u0002\b\r\b\u00c6\u0002\u0018\u00002\u00020\u0001:\u0005RSTUVB\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0006\u0010\u001b\u001a\u00020\u001cJ\u0006\u0010\u001d\u001a\u00020\u001eJ\u0006\u0010\u001f\u001a\u00020\u001cJ\u0006\u0010 \u001a\u00020!J\u0006\u0010\"\u001a\u00020\u0004J\u0006\u0010#\u001a\u00020$J\u000e\u0010%\u001a\u00020&2\u0006\u0010'\u001a\u00020(J\u0006\u0010)\u001a\u00020\u001cJ\u0006\u0010*\u001a\u00020!J\u0006\u0010+\u001a\u00020,J\u0006\u0010-\u001a\u00020.J\u0006\u0010/\u001a\u000200J\u0006\u00101\u001a\u000202J\u0006\u00103\u001a\u00020\u001cJ\u000e\u00104\u001a\u00020&2\u0006\u00105\u001a\u00020\u001cJ\u000e\u00106\u001a\u00020&2\u0006\u00107\u001a\u00020\u001eJ\u000e\u00108\u001a\u00020&2\u0006\u00105\u001a\u00020\u001cJ\u000e\u00109\u001a\u00020&2\u0006\u00105\u001a\u00020!J\u000e\u0010:\u001a\u00020&2\u0006\u0010;\u001a\u00020\u001cJ\u000e\u0010<\u001a\u00020&2\u0006\u00105\u001a\u00020\u0004J\u000e\u0010=\u001a\u00020&2\u0006\u00105\u001a\u00020$J\u000e\u0010>\u001a\u00020&2\u0006\u00105\u001a\u00020!J\u000e\u0010?\u001a\u00020&2\u0006\u00105\u001a\u00020,J\u000e\u0010@\u001a\u00020&2\u0006\u00105\u001a\u00020.J\u000e\u0010A\u001a\u00020&2\u0006\u00105\u001a\u000200J\u000e\u0010B\u001a\u00020&2\u0006\u00105\u001a\u000202J\u000e\u0010C\u001a\u00020&2\u0006\u00105\u001a\u00020\u001cJ\u000e\u0010D\u001a\u00020&2\u0006\u00105\u001a\u00020!J\u000e\u0010E\u001a\u00020&2\u0006\u00105\u001a\u00020\u001cJ\u000e\u0010F\u001a\u00020&2\u0006\u0010G\u001a\u00020\u0004J\u000e\u0010H\u001a\u00020&2\u0006\u00105\u001a\u00020\u001cJ\u000e\u0010I\u001a\u00020&2\u0006\u00105\u001a\u00020JJ\u000e\u0010K\u001a\u00020&2\u0006\u00105\u001a\u00020\u001cJ\u0006\u0010L\u001a\u00020!J\u0006\u0010M\u001a\u00020\u001cJ\u0006\u0010N\u001a\u00020\u0004J\u0006\u0010O\u001a\u00020\u001cJ\u0006\u0010P\u001a\u00020JJ\u0006\u0010Q\u001a\u00020\u001cR\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\f\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\r\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000e\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000f\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0010\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0011\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0012\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0013\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0014\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0015\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0016\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0017\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0018\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0019\u001a\u00020\u001aX\u0082.\u00a2\u0006\u0002\n\u0000\u00a8\u0006W"}, d2 = {"Lcom/invictus/xmd/core/Settings;", "", "()V", "KEY_ADBLOCK_ENABLED", "", "KEY_APP_THEME", "KEY_AUTO_RETRY", "KEY_CONNECTIONS", "KEY_DARK_MODE", "KEY_DNS_CUSTOM_URL", "KEY_DNS_MODE", "KEY_MAX_CONCURRENT", "KEY_PRESET_AUDIO_FORMAT", "KEY_PRESET_CODEC", "KEY_PRESET_CONTAINER", "KEY_PRESET_FPS", "KEY_SAVE_TO_DOWNLOADS", "KEY_SPEED_LIMIT_KBPS", "KEY_WIFI_ONLY", "KEY_YTDLP_DEFAULT_QUALITY", "KEY_YTDLP_INSTALLED", "KEY_YTDLP_LAST_UPDATE_MS", "KEY_YTDLP_NIGHTLY", "PREFS", "WIFI_WAIT_MARKER", "prefs", "Landroid/content/SharedPreferences;", "adblockEnabled", "", "appTheme", "Lcom/invictus/xmd/ui/theme/AppTheme;", "autoRetryEnabled", "connectionsPerDownload", "", "dnsCustomUrl", "dnsMode", "Lcom/invictus/xmd/core/Settings$DnsMode;", "init", "", "context", "Landroid/content/Context;", "isDarkMode", "maxConcurrentDownloads", "presetAudioFormat", "Lcom/invictus/xmd/core/Settings$AudioFormatPreset;", "presetCodec", "Lcom/invictus/xmd/core/Settings$CodecPreset;", "presetContainer", "Lcom/invictus/xmd/core/Settings$ContainerPreset;", "presetFps", "Lcom/invictus/xmd/core/Settings$FpsPreset;", "saveToDownloadsFolder", "setAdblockEnabled", "value", "setAppTheme", "theme", "setAutoRetryEnabled", "setConnectionsPerDownload", "setDarkMode", "isDark", "setDnsCustomUrl", "setDnsMode", "setMaxConcurrentDownloads", "setPresetAudioFormat", "setPresetCodec", "setPresetContainer", "setPresetFps", "setSaveToDownloadsFolder", "setSpeedLimitKBps", "setWifiOnlyDownloads", "setYtDlpDefaultQualityLabel", "label", "setYtDlpInstalled", "setYtDlpLastUpdateMs", "", "setYtDlpUseNightly", "speedLimitKBps", "wifiOnlyDownloads", "ytDlpDefaultQualityLabel", "ytDlpInstalled", "ytDlpLastUpdateMs", "ytDlpUseNightly", "AudioFormatPreset", "CodecPreset", "ContainerPreset", "DnsMode", "FpsPreset", "app_liteDebug"})
public final class Settings {
    
    /**
     * Sentinel [QueueItem.error] text marking a PAUSED item as auto-paused
     * by the Wi-Fi-only setting (DownloadService) rather than a manual
     * Pause -- shared so QueueAdapter can show a clearer label than the
     * generic "Paused" text, without DownloadService's pause logic and
     * QueueAdapter's display logic needing to know about each other.
     */
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String WIFI_WAIT_MARKER = "Waiting for Wi-Fi";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String PREFS = "ff_settings";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String KEY_CONNECTIONS = "connections_per_download";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String KEY_APP_THEME = "app_theme";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String KEY_DARK_MODE = "app_dark_mode";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String KEY_SPEED_LIMIT_KBPS = "speed_limit_kbps";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String KEY_MAX_CONCURRENT = "max_concurrent_downloads";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String KEY_AUTO_RETRY = "auto_retry_network_errors";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String KEY_SAVE_TO_DOWNLOADS = "save_to_downloads_folder";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String KEY_WIFI_ONLY = "wifi_only_downloads";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String KEY_ADBLOCK_ENABLED = "browser_adblock_enabled";
    private static android.content.SharedPreferences prefs;
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String KEY_DNS_MODE = "browser_dns_mode";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String KEY_DNS_CUSTOM_URL = "browser_dns_custom_url";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String KEY_YTDLP_INSTALLED = "ytdlp_installed";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String KEY_YTDLP_LAST_UPDATE_MS = "ytdlp_last_update_ms";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String KEY_YTDLP_NIGHTLY = "ytdlp_use_nightly";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String KEY_YTDLP_DEFAULT_QUALITY = "ytdlp_default_quality_label";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String KEY_PRESET_CONTAINER = "ytdlp_preset_container";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String KEY_PRESET_CODEC = "ytdlp_preset_codec";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String KEY_PRESET_FPS = "ytdlp_preset_fps";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String KEY_PRESET_AUDIO_FORMAT = "ytdlp_preset_audio_format";
    @org.jetbrains.annotations.NotNull()
    public static final com.invictus.xmd.core.Settings INSTANCE = null;
    
    private Settings() {
        super();
    }
    
    public final void init(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
    }
    
    /**
     * The active app color theme. Read `setTheme()`d onto every Activity before
     * `super.onCreate()`, so a change here needs `recreate()` to take effect.
     */
    @org.jetbrains.annotations.NotNull()
    public final com.invictus.xmd.ui.theme.AppTheme appTheme() {
        return null;
    }
    
    public final void setAppTheme(@org.jetbrains.annotations.NotNull()
    com.invictus.xmd.ui.theme.AppTheme theme) {
    }
    
    /**
     * Dark/light mode, orthogonal to [appTheme]. Defaults to dark (the app's
     * original look). Toggled by double-tapping the app header; read
     * `setTheme()`d onto every Activity before `super.onCreate()` alongside
     * the color theme, so a change here also needs `recreate()`.
     */
    public final boolean isDarkMode() {
        return false;
    }
    
    public final void setDarkMode(boolean isDark) {
    }
    
    public final int connectionsPerDownload() {
        return 0;
    }
    
    public final void setConnectionsPerDownload(int value) {
    }
    
    /**
     * KB/s per individual download; 0 means unlimited.
     */
    public final int speedLimitKBps() {
        return 0;
    }
    
    public final void setSpeedLimitKBps(int value) {
    }
    
    public final int maxConcurrentDownloads() {
        return 0;
    }
    
    public final void setMaxConcurrentDownloads(int value) {
    }
    
    /**
     * Auto-retry a failed download up to 3 times when it fails on a plain
     * network error (timeout, connection dropped, DNS failure etc.) --
     * never for server/link-level failures like an expired share link,
     * those still need a manual Retry. Default OFF.
     */
    public final boolean autoRetryEnabled() {
        return false;
    }
    
    public final void setAutoRetryEnabled(boolean value) {
    }
    
    /**
     * When true, downloads skip the app's own Xmd/<Category> subfolders and
     * land flat in the device's standard Download folder instead -- same
     * as Chrome. Default OFF (existing categorized Xmd/... behavior).
     */
    public final boolean saveToDownloadsFolder() {
        return false;
    }
    
    public final void setSaveToDownloadsFolder(boolean value) {
    }
    
    /**
     * When true, no download (HTTP, torrent, or YouTube) is allowed to start
     * or continue on cellular -- DownloadService pauses everything live the
     * moment Wi-Fi drops and resumes it automatically once Wi-Fi is back.
     * Default OFF.
     */
    public final boolean wifiOnlyDownloads() {
        return false;
    }
    
    public final void setWifiOnlyDownloads(boolean value) {
    }
    
    public final boolean adblockEnabled() {
        return false;
    }
    
    public final void setAdblockEnabled(boolean value) {
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.invictus.xmd.core.Settings.DnsMode dnsMode() {
        return null;
    }
    
    public final void setDnsMode(@org.jetbrains.annotations.NotNull()
    com.invictus.xmd.core.Settings.DnsMode value) {
    }
    
    /**
     * The DoH endpoint URL when dnsMode() == CUSTOM. Blank if never set.
     */
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String dnsCustomUrl() {
        return null;
    }
    
    public final void setDnsCustomUrl(@org.jetbrains.annotations.NotNull()
    java.lang.String value) {
    }
    
    public final boolean ytDlpInstalled() {
        return false;
    }
    
    public final void setYtDlpInstalled(boolean value) {
    }
    
    /**
     * Last time yt-dlp's self-update ran (successfully or not) -- used to throttle to roughly once a day. 0 = never.
     */
    public final long ytDlpLastUpdateMs() {
        return 0L;
    }
    
    public final void setYtDlpLastUpdateMs(long value) {
    }
    
    /**
     * Which yt-dlp release channel Settings' "Use Nightly Build" toggled to
     * (default false = stable). Persisted so ensureReady()'s daily
     * background self-update check keeps updating on whichever channel the
     * user last picked, instead of silently drifting back to stable/nightly
     * on the next process start.
     */
    public final boolean ytDlpUseNightly() {
        return false;
    }
    
    public final void setYtDlpUseNightly(boolean value) {
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String ytDlpDefaultQualityLabel() {
        return null;
    }
    
    public final void setYtDlpDefaultQualityLabel(@org.jetbrains.annotations.NotNull()
    java.lang.String label) {
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.invictus.xmd.core.Settings.ContainerPreset presetContainer() {
        return null;
    }
    
    public final void setPresetContainer(@org.jetbrains.annotations.NotNull()
    com.invictus.xmd.core.Settings.ContainerPreset value) {
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.invictus.xmd.core.Settings.CodecPreset presetCodec() {
        return null;
    }
    
    public final void setPresetCodec(@org.jetbrains.annotations.NotNull()
    com.invictus.xmd.core.Settings.CodecPreset value) {
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.invictus.xmd.core.Settings.FpsPreset presetFps() {
        return null;
    }
    
    public final void setPresetFps(@org.jetbrains.annotations.NotNull()
    com.invictus.xmd.core.Settings.FpsPreset value) {
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.invictus.xmd.core.Settings.AudioFormatPreset presetAudioFormat() {
        return null;
    }
    
    public final void setPresetAudioFormat(@org.jetbrains.annotations.NotNull()
    com.invictus.xmd.core.Settings.AudioFormatPreset value) {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0000\n\u0002\u0010\u000e\n\u0002\b\b\b\u0086\u0081\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00000\u0001B\u0011\b\u0002\u0012\b\u0010\u0002\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\u0002\u0010\u0004R\u0013\u0010\u0002\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006j\u0002\b\u0007j\u0002\b\bj\u0002\b\tj\u0002\b\n\u00a8\u0006\u000b"}, d2 = {"Lcom/invictus/xmd/core/Settings$AudioFormatPreset;", "", "ytDlpFormat", "", "(Ljava/lang/String;ILjava/lang/String;)V", "getYtDlpFormat", "()Ljava/lang/String;", "MP3", "M4A", "OPUS", "ORIGINAL", "app_liteDebug"})
    public static enum AudioFormatPreset {
        /*public static final*/ MP3 /* = new MP3(null) */,
        /*public static final*/ M4A /* = new M4A(null) */,
        /*public static final*/ OPUS /* = new OPUS(null) */,
        /*public static final*/ ORIGINAL /* = new ORIGINAL(null) */;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.String ytDlpFormat = null;
        
        AudioFormatPreset(java.lang.String ytDlpFormat) {
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String getYtDlpFormat() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public static kotlin.enums.EnumEntries<com.invictus.xmd.core.Settings.AudioFormatPreset> getEntries() {
            return null;
        }
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0000\n\u0002\u0010\u000e\n\u0002\b\b\b\u0086\u0081\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00000\u0001B\u0011\b\u0002\u0012\b\u0010\u0002\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\u0002\u0010\u0004R\u0013\u0010\u0002\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006j\u0002\b\u0007j\u0002\b\bj\u0002\b\tj\u0002\b\n\u00a8\u0006\u000b"}, d2 = {"Lcom/invictus/xmd/core/Settings$CodecPreset;", "", "vcodecPrefix", "", "(Ljava/lang/String;ILjava/lang/String;)V", "getVcodecPrefix", "()Ljava/lang/String;", "ANY", "AVC", "VP9", "AV1", "app_liteDebug"})
    public static enum CodecPreset {
        /*public static final*/ ANY /* = new ANY(null) */,
        /*public static final*/ AVC /* = new AVC(null) */,
        /*public static final*/ VP9 /* = new VP9(null) */,
        /*public static final*/ AV1 /* = new AV1(null) */;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.String vcodecPrefix = null;
        
        CodecPreset(java.lang.String vcodecPrefix) {
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String getVcodecPrefix() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public static kotlin.enums.EnumEntries<com.invictus.xmd.core.Settings.CodecPreset> getEntries() {
            return null;
        }
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0007\b\u0086\u0081\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00000\u0001B\u0011\b\u0002\u0012\b\u0010\u0002\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\u0002\u0010\u0004R\u0013\u0010\u0002\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006j\u0002\b\u0007j\u0002\b\bj\u0002\b\t\u00a8\u0006\n"}, d2 = {"Lcom/invictus/xmd/core/Settings$ContainerPreset;", "", "ytDlpExt", "", "(Ljava/lang/String;ILjava/lang/String;)V", "getYtDlpExt", "()Ljava/lang/String;", "ANY", "MP4", "WEBM", "app_liteDebug"})
    public static enum ContainerPreset {
        /*public static final*/ ANY /* = new ANY(null) */,
        /*public static final*/ MP4 /* = new MP4(null) */,
        /*public static final*/ WEBM /* = new WEBM(null) */;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.String ytDlpExt = null;
        
        ContainerPreset(java.lang.String ytDlpExt) {
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String getYtDlpExt() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public static kotlin.enums.EnumEntries<com.invictus.xmd.core.Settings.ContainerPreset> getEntries() {
            return null;
        }
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0002\b\b\b\u0086\u0081\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00000\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002j\u0002\b\u0003j\u0002\b\u0004j\u0002\b\u0005j\u0002\b\u0006j\u0002\b\u0007j\u0002\b\b\u00a8\u0006\t"}, d2 = {"Lcom/invictus/xmd/core/Settings$DnsMode;", "", "(Ljava/lang/String;I)V", "ADGUARD", "GOOGLE", "CLOUDFLARE", "CLOUDFLARE_ADBLOCK", "OFF", "CUSTOM", "app_liteDebug"})
    public static enum DnsMode {
        /*public static final*/ ADGUARD /* = new ADGUARD() */,
        /*public static final*/ GOOGLE /* = new GOOGLE() */,
        /*public static final*/ CLOUDFLARE /* = new CLOUDFLARE() */,
        /*public static final*/ CLOUDFLARE_ADBLOCK /* = new CLOUDFLARE_ADBLOCK() */,
        /*public static final*/ OFF /* = new OFF() */,
        /*public static final*/ CUSTOM /* = new CUSTOM() */;
        
        DnsMode() {
        }
        
        @org.jetbrains.annotations.NotNull()
        public static kotlin.enums.EnumEntries<com.invictus.xmd.core.Settings.DnsMode> getEntries() {
            return null;
        }
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0000\n\u0002\u0010\b\n\u0002\b\b\b\u0086\u0081\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00000\u0001B\u0011\b\u0002\u0012\b\u0010\u0002\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\u0002\u0010\u0004R\u0015\u0010\u0002\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\n\n\u0002\u0010\u0007\u001a\u0004\b\u0005\u0010\u0006j\u0002\b\bj\u0002\b\tj\u0002\b\n\u00a8\u0006\u000b"}, d2 = {"Lcom/invictus/xmd/core/Settings$FpsPreset;", "", "maxFps", "", "(Ljava/lang/String;ILjava/lang/Integer;)V", "getMaxFps", "()Ljava/lang/Integer;", "Ljava/lang/Integer;", "ANY", "FPS30", "FPS60", "app_liteDebug"})
    public static enum FpsPreset {
        /*public static final*/ ANY /* = new ANY(null) */,
        /*public static final*/ FPS30 /* = new FPS30(null) */,
        /*public static final*/ FPS60 /* = new FPS60(null) */;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.Integer maxFps = null;
        
        FpsPreset(java.lang.Integer maxFps) {
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Integer getMaxFps() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public static kotlin.enums.EnumEntries<com.invictus.xmd.core.Settings.FpsPreset> getEntries() {
            return null;
        }
    }
}