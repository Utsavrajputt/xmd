package com.invictus.xmd.core

import android.content.Context
import android.content.SharedPreferences
import com.invictus.xmd.ui.theme.AppTheme

/**
 * Simple SharedPreferences-backed settings, initialized once from FfApp.
 */
object Settings {
    /** Sentinel [QueueItem.error] text marking a PAUSED item as auto-paused
     *  by the Wi-Fi-only setting (DownloadService) rather than a manual
     *  Pause -- shared so QueueItemRow (DownloadsScreen.kt) can show a clearer label than the
     *  generic "Paused" text, without DownloadService's pause logic and
     *  QueueItemRow's display logic needing to know about each other. */
    const val WIFI_WAIT_MARKER = "Waiting for Wi-Fi"

    private const val PREFS = "ff_settings"
    private const val KEY_CONNECTIONS = "connections_per_download"
    private const val KEY_APP_THEME = "app_theme"
    private const val KEY_DARK_MODE = "app_dark_mode"
    private const val KEY_AMOLED_MODE = "app_amoled_mode"
    private const val KEY_SPEED_LIMIT_KBPS = "speed_limit_kbps"
    private const val KEY_MAX_CONCURRENT = "max_concurrent_downloads"
    private const val KEY_AUTO_RETRY = "auto_retry_network_errors"
    private const val KEY_SAVE_TO_DOWNLOADS = "save_to_downloads_folder"
    private const val KEY_WIFI_ONLY = "wifi_only_downloads"
    private const val KEY_ADBLOCK_ENABLED = "browser_adblock_enabled"

    private lateinit var prefs: SharedPreferences

    private val _themeFlow = kotlinx.coroutines.flow.MutableStateFlow(AppTheme.Default)
    val themeFlow: kotlinx.coroutines.flow.StateFlow<AppTheme> = _themeFlow

    private val _darkModeFlow = kotlinx.coroutines.flow.MutableStateFlow(true)
    val darkModeFlow: kotlinx.coroutines.flow.StateFlow<Boolean> = _darkModeFlow

    private val _amoledModeFlow = kotlinx.coroutines.flow.MutableStateFlow(false)
    val amoledModeFlow: kotlinx.coroutines.flow.StateFlow<Boolean> = _amoledModeFlow

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _themeFlow.value = appTheme()
        _darkModeFlow.value = isDarkMode()
        _amoledModeFlow.value = isAmoledMode()
    }

    /** The active app color theme. */
    fun appTheme(): AppTheme = AppTheme.fromKey(prefs.getString(KEY_APP_THEME, null))
    fun setAppTheme(theme: AppTheme) {
        prefs.edit().putString(KEY_APP_THEME, theme.storageKey).apply()
        _themeFlow.value = theme
    }

    /** Dark/light mode, orthogonal to [appTheme]. */
    fun isDarkMode(): Boolean = prefs.getBoolean(KEY_DARK_MODE, true)
    fun setDarkMode(isDark: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, isDark).apply()
        _darkModeFlow.value = isDark
    }

    /** AMOLED pure black dark mode. */
    fun isAmoledMode(): Boolean = prefs.getBoolean(KEY_AMOLED_MODE, false)
    fun setAmoledMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AMOLED_MODE, enabled).apply()
        _amoledModeFlow.value = enabled
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

    /** When true, no download (HTTP, torrent, or YouTube) is allowed to start
     *  or continue on cellular -- DownloadService pauses everything live the
     *  moment Wi-Fi drops and resumes it automatically once Wi-Fi is back.
     *  Default OFF. */
    fun wifiOnlyDownloads(): Boolean = prefs.getBoolean(KEY_WIFI_ONLY, false)
    fun setWifiOnlyDownloads(value: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply()
    }

    // ── Browser: Adblock (domain-blocklist ad/tracker blocking) ───────────
    // Global switch only, no per-site whitelist. Default ON -- this is an
    // opt-out feature, not opt-in, matching how ad-blocking browsers
    // (Brave, 1DM+) ship it.
    fun adblockEnabled(): Boolean = prefs.getBoolean(KEY_ADBLOCK_ENABLED, true)
    fun setAdblockEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ADBLOCK_ENABLED, value).apply()
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

    // ── YouTube download preset (video container/codec/fps + audio format) ─
    // Independent of the height ladder in YtDlpManager.standardQualityOptions
    // -- these narrow *which* stream at that height gets picked. Left at
    // yt-dlp's own default (whichever's highest bitrate) today, quick picks
    // tend to land on plain 30fps MP4/AVC; setting these lets that ladder
    // prefer e.g. 60fps WebM/VP9 instead. ANY/MP3 are the pre-preset
    // defaults, functionally identical to today's unconstrained behavior.
    enum class ContainerPreset(val ytDlpExt: String?) { ANY(null), MP4("mp4"), WEBM("webm") }
    enum class CodecPreset(val vcodecPrefix: String?) { ANY(null), AVC("avc1"), VP9("vp09"), AV1("av01") }
    enum class FpsPreset(val maxFps: Int?) { ANY(null), FPS30(30), FPS60(60) }
    enum class AudioFormatPreset(val ytDlpFormat: String?) { MP3("mp3"), M4A("m4a"), OPUS("opus"), ORIGINAL(null) }

    private const val KEY_PRESET_CONTAINER = "ytdlp_preset_container"
    private const val KEY_PRESET_CODEC = "ytdlp_preset_codec"
    private const val KEY_PRESET_FPS = "ytdlp_preset_fps"
    private const val KEY_PRESET_AUDIO_FORMAT = "ytdlp_preset_audio_format"

    fun presetContainer(): ContainerPreset =
        when (prefs.getString(KEY_PRESET_CONTAINER, ContainerPreset.ANY.name)) {
            ContainerPreset.MP4.name -> ContainerPreset.MP4
            ContainerPreset.WEBM.name -> ContainerPreset.WEBM
            else -> ContainerPreset.ANY
        }
    fun setPresetContainer(value: ContainerPreset) {
        prefs.edit().putString(KEY_PRESET_CONTAINER, value.name).apply()
    }

    fun presetCodec(): CodecPreset =
        when (prefs.getString(KEY_PRESET_CODEC, CodecPreset.ANY.name)) {
            CodecPreset.AVC.name -> CodecPreset.AVC
            CodecPreset.VP9.name -> CodecPreset.VP9
            CodecPreset.AV1.name -> CodecPreset.AV1
            else -> CodecPreset.ANY
        }
    fun setPresetCodec(value: CodecPreset) {
        prefs.edit().putString(KEY_PRESET_CODEC, value.name).apply()
    }

    fun presetFps(): FpsPreset =
        when (prefs.getString(KEY_PRESET_FPS, FpsPreset.ANY.name)) {
            FpsPreset.FPS30.name -> FpsPreset.FPS30
            FpsPreset.FPS60.name -> FpsPreset.FPS60
            else -> FpsPreset.ANY
        }
    fun setPresetFps(value: FpsPreset) {
        prefs.edit().putString(KEY_PRESET_FPS, value.name).apply()
    }

    fun presetAudioFormat(): AudioFormatPreset =
        when (prefs.getString(KEY_PRESET_AUDIO_FORMAT, AudioFormatPreset.MP3.name)) {
            AudioFormatPreset.M4A.name -> AudioFormatPreset.M4A
            AudioFormatPreset.OPUS.name -> AudioFormatPreset.OPUS
            AudioFormatPreset.ORIGINAL.name -> AudioFormatPreset.ORIGINAL
            else -> AudioFormatPreset.MP3
        }
    fun setPresetAudioFormat(value: AudioFormatPreset) {
        prefs.edit().putString(KEY_PRESET_AUDIO_FORMAT, value.name).apply()
    }

    // ── Bottom nav: tab order / hidden tabs / default tab ──────────────────
    // Four slots total: three real page tabs (home/downloads/browser) plus
    // "add" (the center FAB -- not a page, just an action, so it's never a
    // valid default tab). Order and hidden-set are stored as CSV of these
    // ids; unknown/missing ids are healed on read so a future app update
    // that adds/removes a tab id doesn't leave a stale or incomplete list.
    object TabId {
        const val HOME = "home"
        const val DOWNLOADS = "downloads"
        const val ADD = "add"
        const val BROWSER = "browser"
        val ALL = listOf(HOME, DOWNLOADS, ADD, BROWSER)
        val PAGES = listOf(HOME, DOWNLOADS, BROWSER)
    }

    private const val KEY_TAB_ORDER = "nav_tab_order"
    private const val KEY_HIDDEN_TABS = "nav_hidden_tabs"
    private const val KEY_DEFAULT_TAB = "nav_default_tab"

    /** Left-to-right order of all four tab slots. Any id missing from a
     *  stored (older/corrupted) list is appended at the end; any unknown
     *  stored id is dropped -- keeps this always a valid permutation of
     *  [TabId.ALL]. */
    fun tabOrder(): List<String> {
        val stored = prefs.getString(KEY_TAB_ORDER, null)
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.filter { it in TabId.ALL }
            ?.distinct()
            .orEmpty()
        val missing = TabId.ALL.filterNot { it in stored }
        return if (stored.isEmpty()) TabId.ALL else stored + missing
    }

    fun setTabOrder(order: List<String>) {
        prefs.edit().putString(KEY_TAB_ORDER, order.joinToString(",")).apply()
    }

    /** Tabs the user has hidden from the bottom nav. At least one entry in
     *  [TabId.PAGES] is always kept visible -- callers should refuse to hide
     *  the last visible page tab rather than relying on this to fix it up,
     *  but as a last-resort guard, a stored set that would hide every page
     *  tab has that constraint dropped on read. */
    fun hiddenTabs(): Set<String> {
        val stored = prefs.getString(KEY_HIDDEN_TABS, "")
            ?.split(",")
            ?.filter { it.isNotBlank() && it in TabId.ALL }
            ?.toSet()
            .orEmpty()
        val wouldHideAllPages = TabId.PAGES.all { it in stored }
        return if (wouldHideAllPages) stored - TabId.PAGES.toSet() else stored
    }

    fun setHiddenTabs(hidden: Set<String>) {
        prefs.edit().putString(KEY_HIDDEN_TABS, hidden.joinToString(",")).apply()
    }

    /** Which page tab the app opens on. Falls back to the first visible page
     *  tab (in [tabOrder] order) if the stored choice is invalid, hidden, or
     *  unset, and to [TabId.DOWNLOADS] if nothing is visible at all. */
    fun defaultTab(): String {
        val stored = prefs.getString(KEY_DEFAULT_TAB, null)
        val hidden = hiddenTabs()
        if (stored != null && stored in TabId.PAGES && stored !in hidden) return stored
        return tabOrder().firstOrNull { it in TabId.PAGES && it !in hidden } ?: TabId.DOWNLOADS
    }

    fun setDefaultTab(value: String) {
        if (value !in TabId.PAGES) return
        prefs.edit().putString(KEY_DEFAULT_TAB, value).apply()
    }
}
