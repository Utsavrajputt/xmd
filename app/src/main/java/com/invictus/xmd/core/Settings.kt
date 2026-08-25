package com.invictus.xmd.core

import android.content.Context
import android.content.SharedPreferences
import com.invictus.xmd.ui.theme.AppTheme

/**
 * Simple SharedPreferences-backed settings, initialized once from FfApp.
 */
object Settings {
    private const val PREFS = "ff_settings"
    private const val KEY_CONNECTIONS = "connections_per_download"
    private const val KEY_APP_THEME = "app_theme"
    private const val KEY_DARK_MODE = "app_dark_mode"
    private const val KEY_SPEED_LIMIT_KBPS = "speed_limit_kbps"
    private const val KEY_MAX_CONCURRENT = "max_concurrent_downloads"
    private const val KEY_AUTO_RETRY = "auto_retry_network_errors"
    private const val KEY_SAVE_TO_DOWNLOADS = "save_to_downloads_folder"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /** The active app color theme. Read `setTheme()`d onto every Activity before
     *  `super.onCreate()`, so a change here needs `recreate()` to take effect. */
    fun appTheme(): AppTheme = AppTheme.fromKey(prefs.getString(KEY_APP_THEME, null))
    fun setAppTheme(theme: AppTheme) {
        prefs.edit().putString(KEY_APP_THEME, theme.storageKey).apply()
    }

    /** Dark/light mode, orthogonal to [appTheme]. Defaults to dark (the app's
     *  original look). Toggled by double-tapping the app header; read
     *  `setTheme()`d onto every Activity before `super.onCreate()` alongside
     *  the color theme, so a change here also needs `recreate()`. */
    fun isDarkMode(): Boolean = prefs.getBoolean(KEY_DARK_MODE, true)
    fun setDarkMode(isDark: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, isDark).apply()
    }

    fun connectionsPerDownload(): Int = prefs.getInt(KEY_CONNECTIONS, 16)
    fun setConnectionsPerDownload(value: Int) {
        prefs.edit().putInt(KEY_CONNECTIONS, value).apply()
    }

    /** KB/s per individual download; 0 means unlimited. */
    fun speedLimitKBps(): Int = prefs.getInt(KEY_SPEED_LIMIT_KBPS, 0)
    fun setSpeedLimitKBps(value: Int) {
        prefs.edit().putInt(KEY_SPEED_LIMIT_KBPS, value.coerceAtLeast(0)).apply()
    }

    fun maxConcurrentDownloads(): Int = prefs.getInt(KEY_MAX_CONCURRENT, 2)
    fun setMaxConcurrentDownloads(value: Int) {
        prefs.edit().putInt(KEY_MAX_CONCURRENT, value.coerceIn(1, 5)).apply()
    }

    /** Auto-retry a failed download up to 3 times when it fails on a plain
     *  network error (timeout, connection dropped, DNS failure etc.) --
     *  never for server/link-level failures like an expired share link,
     *  those still need a manual Retry. Default OFF. */
    fun autoRetryEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_RETRY, false)
    fun setAutoRetryEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_RETRY, value).apply()
    }

    /** When true, downloads skip the app's own Xmd/<Category> subfolders and
     *  land flat in the device's standard Download folder instead -- same
     *  as Chrome. Default OFF (existing categorized Xmd/... behavior). */
    fun saveToDownloadsFolder(): Boolean = prefs.getBoolean(KEY_SAVE_TO_DOWNLOADS, false)
    fun setSaveToDownloadsFolder(value: Boolean) {
        prefs.edit().putBoolean(KEY_SAVE_TO_DOWNLOADS, value).apply()
    }

    // ── Browser: Private DNS (DNS-over-HTTPS for in-app browsing only) ────
    enum class DnsMode { ADGUARD, GOOGLE, CLOUDFLARE, CLOUDFLARE_ADBLOCK, OFF, CUSTOM }

    private const val KEY_DNS_MODE = "browser_dns_mode"
    private const val KEY_DNS_CUSTOM_URL = "browser_dns_custom_url"

    // Defaults to OFF (system DNS) -- previously defaulted to ADGUARD, which
    // silently routed every in-app browsing request through a third-party DoH
    // resolver on first launch with no explicit opt-in from the user.
    fun dnsMode(): DnsMode =
        when (prefs.getString(KEY_DNS_MODE, DnsMode.OFF.name)) {
            DnsMode.ADGUARD.name -> DnsMode.ADGUARD
            DnsMode.GOOGLE.name -> DnsMode.GOOGLE
            DnsMode.CLOUDFLARE.name -> DnsMode.CLOUDFLARE
            DnsMode.CLOUDFLARE_ADBLOCK.name -> DnsMode.CLOUDFLARE_ADBLOCK
            DnsMode.CUSTOM.name -> DnsMode.CUSTOM
            else -> DnsMode.OFF
        }

    fun setDnsMode(value: DnsMode) {
        prefs.edit().putString(KEY_DNS_MODE, value.name).apply()
    }

    /** The DoH endpoint URL when dnsMode() == CUSTOM. Blank if never set. */
    fun dnsCustomUrl(): String = prefs.getString(KEY_DNS_CUSTOM_URL, "").orEmpty()
    fun setDnsCustomUrl(value: String) {
        prefs.edit().putString(KEY_DNS_CUSTOM_URL, value.trim()).apply()
    }

    // ── YouTube downloader (yt-dlp) install/update state (Full build only) ─
    private const val KEY_YTDLP_INSTALLED = "ytdlp_installed"
    private const val KEY_YTDLP_LAST_UPDATE_MS = "ytdlp_last_update_ms"
    private const val KEY_YTDLP_NIGHTLY = "ytdlp_use_nightly"

    fun ytDlpInstalled(): Boolean = prefs.getBoolean(KEY_YTDLP_INSTALLED, false)
    fun setYtDlpInstalled(value: Boolean) {
        prefs.edit().putBoolean(KEY_YTDLP_INSTALLED, value).apply()
    }

    /** Last time yt-dlp's self-update ran (successfully or not) -- used to throttle to roughly once a day. 0 = never. */
    fun ytDlpLastUpdateMs(): Long = prefs.getLong(KEY_YTDLP_LAST_UPDATE_MS, 0L)
    fun setYtDlpLastUpdateMs(value: Long) {
        prefs.edit().putLong(KEY_YTDLP_LAST_UPDATE_MS, value).apply()
    }

    /**
     * Which yt-dlp release channel Settings' "Use Nightly Build" toggled to
     * (default false = stable). Persisted so ensureReady()'s daily
     * background self-update check keeps updating on whichever channel the
     * user last picked, instead of silently drifting back to stable/nightly
     * on the next process start.
     */
    fun ytDlpUseNightly(): Boolean = prefs.getBoolean(KEY_YTDLP_NIGHTLY, false)
    fun setYtDlpUseNightly(value: Boolean) {
        prefs.edit().putBoolean(KEY_YTDLP_NIGHTLY, value).apply()
    }

    // ── YouTube default quality ─────────────────────────────────────────
    // Blank (the default) means "Ask always" -- resolveYoutube shows the
    // quality picker dialog on every download. Any other value is the
    // exact label of a YtDlpManager.standardQualityOptions() entry (e.g.
    // "1080p", "Audio only (MP3)"), matched back to its QualityOption by
    // label at resolve time, skipping the dialog.
    private const val KEY_YTDLP_DEFAULT_QUALITY = "ytdlp_default_quality_label"

    fun ytDlpDefaultQualityLabel(): String = prefs.getString(KEY_YTDLP_DEFAULT_QUALITY, "").orEmpty()
    fun setYtDlpDefaultQualityLabel(label: String) {
        prefs.edit().putString(KEY_YTDLP_DEFAULT_QUALITY, label).apply()
    }
}
