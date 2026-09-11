package com.invictus.xmd.preferences

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import com.invictus.xmd.ui.theme.AppTheme
import java.io.File
import com.invictus.xmd.FfApp
import com.invictus.xmd.database.entities.QueueItem
import com.invictus.xmd.domain.browser.AdblockFilter
import com.invictus.xmd.domain.browser.AdblockListUpdater
import com.invictus.xmd.domain.download.YtDlpManager
import com.invictus.xmd.service.DownloadService
import com.invictus.xmd.ui.browser.BrowserViewModel
import com.invictus.xmd.ui.downloads.DownloadsScreen
import com.invictus.xmd.ui.downloads.QueueItemRow

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

    /** Same idea as [WIFI_WAIT_MARKER] but for a total internet outage
     *  (any transport, not just Wi-Fi) -- lets a PAUSED item auto-resume
     *  once connectivity of any kind comes back, mirroring how Chrome/the
     *  system Downloads app shows "Waiting for network" instead of failing
     *  outright the instant a connection drops. */
    const val NETWORK_WAIT_MARKER = "Waiting for network"

    private const val PREFS = "ff_settings"
    private const val KEY_CONNECTIONS = "connections_per_download"
    private const val KEY_APP_THEME = "app_theme"
    private const val KEY_DARK_MODE = "app_dark_mode"
    private const val KEY_AMOLED_MODE = "app_amoled_mode"
    private const val KEY_SPEED_LIMIT_KBPS = "speed_limit_kbps"
    private const val KEY_MAX_CONCURRENT = "max_concurrent_downloads"
    private const val KEY_AUTO_RETRY = "auto_retry_network_errors"
    private const val KEY_DEFAULT_SAVE_LOCATION = "default_save_location_path"
    private const val KEY_DISABLE_CATEGORIZATION = "disable_folder_categorization"
    private const val KEY_WIFI_ONLY = "wifi_only_downloads"
    private const val KEY_ADBLOCK_ENABLED = "browser_adblock_enabled"
    private const val KEY_BACKGROUND_PLAYBACK_ENABLED = "browser_background_playback_enabled"
    private const val KEY_TABS_GRID_MODE = "browser_tabs_grid_mode"
    private const val KEY_AUTO_CHECK_UPDATES = "about_auto_check_for_updates"

    private lateinit var prefs: SharedPreferences

    /** Application context, kept around for callers (e.g. the Browser's DoH
     *  HTTP disk cache in BrowserViewModel) that need a cache/files dir but
     *  aren't themselves an Activity/Fragment/AndroidViewModel. */
    private lateinit var appContext: Context
    fun appContext(): Context = appContext

    private val _themeFlow = kotlinx.coroutines.flow.MutableStateFlow(AppTheme.Default)
    val themeFlow: kotlinx.coroutines.flow.StateFlow<AppTheme> = _themeFlow

    private val _darkModeFlow = kotlinx.coroutines.flow.MutableStateFlow(true)
    val darkModeFlow: kotlinx.coroutines.flow.StateFlow<Boolean> = _darkModeFlow

    private val _amoledModeFlow = kotlinx.coroutines.flow.MutableStateFlow(false)
    val amoledModeFlow: kotlinx.coroutines.flow.StateFlow<Boolean> = _amoledModeFlow

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
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

    /** The base folder new downloads are saved under when no per-download
     *  custom save dir was picked (see [com.invictus.xmd.database.entities.QueueItem.customSaveDirPath]).
     *  Falls back to the original <sdcard>/Xmd folder until the user picks
     *  something else via the SAF folder picker in Settings. Whether
     *  [categorizationDisabled] is on or off, this is always the *root* --
     *  category subfolders (Videos/Music/.../Torrents) are appended under it,
     *  never baked into the stored value itself. */
    fun defaultSaveLocation(): String =
        prefs.getString(KEY_DEFAULT_SAVE_LOCATION, null)
            ?: File(Environment.getExternalStorageDirectory(), "Xmd").absolutePath

    fun setDefaultSaveLocation(path: String) {
        prefs.edit().putString(KEY_DEFAULT_SAVE_LOCATION, path).apply()
    }

    /** When true, downloads skip the category subfolder (Videos/Music/
     *  Documents/Apps/Others/Torrents) entirely and land flat in
     *  [defaultSaveLocation] instead -- same as Chrome. Default OFF
     *  (existing categorized <location>/<Category> behavior). */
    fun categorizationDisabled(): Boolean = prefs.getBoolean(KEY_DISABLE_CATEGORIZATION, false)
    fun setCategorizationDisabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_DISABLE_CATEGORIZATION, value).apply()
    }

    /** When true, no download (HTTP, torrent, or YouTube) is allowed to start
     *  or continue on cellular -- DownloadService pauses everything live the
     *  moment Wi-Fi drops and resumes it automatically once Wi-Fi is back.
     *  Default OFF. */
    fun wifiOnlyDownloads(): Boolean = prefs.getBoolean(KEY_WIFI_ONLY, false)
    fun setWifiOnlyDownloads(value: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply()
    }

    // ── Browser: Adblock (Brave-style Shields: level + per-site allowlist) ─
    // Three levels, like Brave's Standard/Aggressive/Allow-all:
    //  - STANDARD: ad/tracker domain + URL-pattern blocking (AdblockFilter's
    //    main host list). Safe for basically any site.
    //  - AGGRESSIVE: STANDARD plus a second tier of borderline trackers
    //    (comment widgets, live-chat bubbles, social embed SDKs) that can
    //    visibly break a widget on some pages -- an intentional trade-off,
    //    same one Brave's Aggressive mode makes.
    //  - OFF: no blocking at all.
    // Default STANDARD -- opt-out, not opt-in, matching how ad-blocking
    // browsers (Brave, 1DM+) ship it.
    enum class AdblockLevel { STANDARD, AGGRESSIVE, OFF }

    private const val KEY_ADBLOCK_LEVEL = "browser_adblock_level"

    fun adblockLevel(): AdblockLevel {
        val stored = prefs.getString(KEY_ADBLOCK_LEVEL, null)
        if (stored != null) {
            return runCatching { AdblockLevel.valueOf(stored) }.getOrDefault(AdblockLevel.STANDARD)
        }
        // No level saved yet -- either a fresh install, or an upgrade from
        // the old on/off-only KEY_ADBLOCK_ENABLED toggle. Read that instead
        // of defaulting to STANDARD outright, so upgrading users keep
        // "adblock off" if that's what they'd chosen, rather than having it
        // silently turn back on.
        return if (prefs.getBoolean(KEY_ADBLOCK_ENABLED, true)) AdblockLevel.STANDARD else AdblockLevel.OFF
    }

    fun setAdblockLevel(level: AdblockLevel) {
        prefs.edit().putString(KEY_ADBLOCK_LEVEL, level.name).apply()
    }

    // Lifetime count of individually blocked requests, shown on the
    // Settings screen (Brave shows the same kind of running total).
    // Incremented from WebView's own background thread(s), potentially
    // concurrently across tabs, so reads-then-writes are serialized under
    // [adblockCountLock] rather than risking lost increments from two
    // threads reading the same stale value. Cached in memory after first
    // load so every increment isn't a disk read plus a write -- just the
    // (async) write.
    private const val KEY_ADBLOCK_LIFETIME_BLOCKED = "browser_adblock_lifetime_blocked_count"
    @Volatile private var adblockLifetimeCountCache: Long = -1L
    private val adblockCountLock = Any()

    fun adblockLifetimeBlockedCount(): Long {
        adblockLifetimeCountCache.let { if (it >= 0L) return it }
        synchronized(adblockCountLock) {
            if (adblockLifetimeCountCache < 0L) {
                adblockLifetimeCountCache = prefs.getLong(KEY_ADBLOCK_LIFETIME_BLOCKED, 0L)
            }
        }
        return adblockLifetimeCountCache
    }

    fun incrementAdblockLifetimeBlockedCount() {
        synchronized(adblockCountLock) {
            val next = adblockLifetimeBlockedCount() + 1
            adblockLifetimeCountCache = next
            prefs.edit().putLong(KEY_ADBLOCK_LIFETIME_BLOCKED, next).apply()
        }
    }

    // Per-site "shields down" allowlist -- sites where blocking is off
    // regardless of the global level, toggled from the Browser's overflow
    // menu and manageable (view/remove) from the Settings screen. Keyed by
    // registrable-ish host with any "www." prefix stripped, so
    // "example.com" and "www.example.com" share one entry the way a user
    // would expect "this site" to mean.
    private const val KEY_ADBLOCK_SITE_ALLOWLIST = "browser_adblock_site_allowlist"

    private fun normalizeSiteHost(host: String): String =
        host.lowercase().removePrefix("www.")

    /** Sites currently allowlisted (shields down), for display in Settings. */
    fun adblockAllowlistedSites(): Set<String> =
        HashSet(prefs.getStringSet(KEY_ADBLOCK_SITE_ALLOWLIST, emptySet()).orEmpty())

    fun isAdblockAllowlisted(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        return adblockAllowlistedSites().contains(normalizeSiteHost(host))
    }

    fun setAdblockAllowlisted(host: String, allowed: Boolean) {
        val normalized = normalizeSiteHost(host)
        // getStringSet's returned Set must be treated as immutable (Android
        // docs warn against mutating it in place and expecting persistence
        // to notice) -- copy into a fresh HashSet before changing it.
        val updated = HashSet(adblockAllowlistedSites())
        if (allowed) updated.add(normalized) else updated.remove(normalized)
        prefs.edit().putStringSet(KEY_ADBLOCK_SITE_ALLOWLIST, updated).apply()
    }

    // ── Browser: Background playback ───────────────────────────────────
    // Off by default -- this overrides the page's own Page Visibility API
    // (document.hidden/visibilityState + visibilitychange), which is an
    // opt-in behavior change to how sites see the tab, not a passive
    // toggle like adblock.
    fun backgroundPlaybackEnabled(): Boolean =
        prefs.getBoolean(KEY_BACKGROUND_PLAYBACK_ENABLED, false)
    fun setBackgroundPlaybackEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_BACKGROUND_PLAYBACK_ENABLED, value).apply()
    }

    // ── Browser: Tab switcher layout mode ──────────────────────────────
    fun isTabsGridMode(): Boolean = prefs.getBoolean(KEY_TABS_GRID_MODE, true)
    fun setTabsGridMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TABS_GRID_MODE, enabled).apply()
    }

    // ── About: Update checks ───────────────────────────────────────────
    // Whether the About screen should silently check GitHub Releases for a
    // newer version each time it's opened. Default OFF -- unlike mpvRx this
    // is a purely network-initiated, opt-in check (no background WorkManager
    // job), so the switch starts false until the user turns it on.
    fun autoCheckForUpdatesEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_CHECK_UPDATES, false)
    fun setAutoCheckForUpdatesEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CHECK_UPDATES, value).apply()
    }

    // ── Browser: Search Engine ─────────────────────────────────────────
    enum class SearchEngine(
        val id: String,
        val displayName: String,
        val queryUrlTemplate: String,
        val domain: String,
    ) {
        GOOGLE("google", "Google", "https://www.google.com/search?q=%s", "google.com"),
        DUCKDUCKGO("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q=%s", "duckduckgo.com"),
        BING("bing", "Bing", "https://www.bing.com/search?q=%s", "bing.com"),
        BRAVE("brave", "Brave", "https://search.brave.com/search?q=%s", "search.brave.com"),
        YAHOO("yahoo", "Yahoo", "https://search.yahoo.com/search?p=%s", "search.yahoo.com"),
        ECOSIA("ecosia", "Ecosia", "https://www.ecosia.org/search?q=%s", "ecosia.org"),
        CUSTOM("custom", "Custom", "", "Custom URL");

        companion object {
            fun fromId(id: String?): SearchEngine =
                entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: GOOGLE
        }
    }

    private const val KEY_SEARCH_ENGINE = "browser_search_engine"
    private const val KEY_CUSTOM_SEARCH_URL = "browser_custom_search_url"
    private const val KEY_CUSTOM_SEARCH_NAME = "browser_custom_search_name"

    fun searchEngine(): SearchEngine =
        SearchEngine.fromId(prefs.getString(KEY_SEARCH_ENGINE, SearchEngine.GOOGLE.id))

    fun setSearchEngine(engine: SearchEngine) {
        prefs.edit().putString(KEY_SEARCH_ENGINE, engine.id).apply()
    }

    fun customSearchUrl(): String = prefs.getString(KEY_CUSTOM_SEARCH_URL, "").orEmpty()

    fun setCustomSearchUrl(url: String) {
        prefs.edit().putString(KEY_CUSTOM_SEARCH_URL, url.trim()).apply()
    }

    fun customSearchName(): String = prefs.getString(KEY_CUSTOM_SEARCH_NAME, "").orEmpty()

    fun setCustomSearchName(name: String) {
        prefs.edit().putString(KEY_CUSTOM_SEARCH_NAME, name.trim()).apply()
    }

    /**
     * Builds a full search URL for the given query using the configured search engine.
     * Supports %s or {q} placeholders in custom search URLs, with fallback to Google.
     */
    fun buildSearchUrl(query: String): String {
        val encoded = android.net.Uri.encode(query.trim())
        val engine = searchEngine()
        if (engine == SearchEngine.CUSTOM) {
            val customUrl = customSearchUrl().trim()
            if (customUrl.isNotBlank()) {
                return when {
                    customUrl.contains("%s") -> customUrl.replace("%s", encoded)
                    customUrl.contains("{q}") -> customUrl.replace("{q}", encoded)
                    customUrl.endsWith("=") || customUrl.endsWith("/") || customUrl.endsWith("?") -> "$customUrl$encoded"
                    customUrl.contains("?") -> "$customUrl&$encoded"
                    else -> "$customUrl?q=$encoded"
                }
            }
        }
        val template = engine.queryUrlTemplate.ifBlank { SearchEngine.GOOGLE.queryUrlTemplate }
        return template.replace("%s", encoded)
    }

    // Epoch millis of the last successful AdblockListUpdater.refresh() --
    // 0L means "never" (first run, or every attempted refresh has failed
    // so far), which AdblockFilter treats as always-stale so it keeps
    // retrying on subsequent Browser opens rather than giving up forever.
    private const val KEY_ADBLOCK_LIST_UPDATED_AT = "browser_adblock_list_updated_at"
    fun adblockListUpdatedAt(): Long = prefs.getLong(KEY_ADBLOCK_LIST_UPDATED_AT, 0L)
    fun setAdblockListUpdatedAt(value: Long) {
        prefs.edit().putLong(KEY_ADBLOCK_LIST_UPDATED_AT, value).apply()
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
        val pages = (stored.filter { it in TabId.PAGES } + TabId.PAGES).distinct()
        return pages + TabId.ADD
    }

    fun setTabOrder(order: List<String>) {
        val pages = (order.filter { it in TabId.PAGES } + TabId.PAGES).distinct()
        prefs.edit().putString(KEY_TAB_ORDER, (pages + TabId.ADD).joinToString(",")).apply()
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
