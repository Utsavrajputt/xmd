package com.invictus.xmd.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.invictus.xmd.R
import com.invictus.xmd.core.ShortcutRepository
import com.invictus.xmd.ui.icons.Icon
import com.invictus.xmd.ui.icons.Icons
import com.invictus.xmd.ui.theme.LocalThemeTransitionState
import com.invictus.xmd.ui.theme.XmdTheme
import com.invictus.xmd.ui.theme.resolveCurrentXmdColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dedicated Settings screen -- replaces the old single-dialog Settings UI.
 * Fully Compose now: a self-drawn header (back button + title) plus a
 * navigation-compose [NavHost], one route per category -- replaces the old
 * manual-FragmentManager + addToBackStack push/pop that every other screen
 * in this codebase still uses (Settings is the first screen to move to
 * real Jetpack Navigation; Home/Downloads/Browser/History stay on the
 * manual pattern, untouched by this phase).
 *
 * Each `*Screen.kt` composable (SettingsRootScreen, SettingsAppearanceScreen,
 * etc.) is unchanged from the Fragment-hosted era -- they were already pure
 * state-in/callback-out composables with no Fragment/Activity API calls, so
 * they drop into route bodies as-is. Only their old ComposeView-hosting
 * Fragment wrappers (SettingsRootFragment, SettingsAppearanceFragment, ...)
 * are retired by this phase, along with activity_settings.xml.
 */
class SettingsActivity : ComponentActivity() {

    private lateinit var navController: NavHostController
    private var importCandidates: List<File>? by mutableStateOf(null)

    // Must be registered before onStart -- declared as a property so it's
    // set up during Activity construction, same requirement as any other
    // registerForActivityResult() call.
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) writeAndShareExport(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate() -- Activity.setTheme() only
        // takes effect if called before the window/decor is created. Same
        // theme/dark-mode resolution as MainActivity/ChallengeActivity, so
        // this screen (and SettingsAppearanceScreen's recreate() calls)
        // actually repaint instead of recreating with the default theme.
        com.invictus.xmd.ui.theme.AppTheme.applyTo(this)
        super.onCreate(savedInstanceState)

        val isDark = com.invictus.xmd.core.Settings.isDarkMode()
        val colorScheme = resolveCurrentXmdColorScheme(this)
        window.statusBarColor = colorScheme.surfaceContainerLow.toArgb()
        window.navigationBarColor = colorScheme.background.toArgb()
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDark
        insetsController.isAppearanceLightNavigationBars = !isDark

        // Deep-link straight into a category (e.g. the "Install now" button
        // on the yt-dlp-not-installed dialog) instead of always landing on
        // the root list -- see EXTRA_OPEN_CATEGORY. Resolved once up front
        // (rather than via LaunchedEffect after first composition) so the
        // NavHost's startDestination is correct on the very first frame --
        // avoids a root->youtube flash that a post-composition navigate()
        // would cause.
        val startRoute = if (intent.getStringExtra(EXTRA_OPEN_CATEGORY) == CATEGORY_YOUTUBE) {
            Route.YOUTUBE
        } else {
            Route.ROOT
        }

        setContent {
            navController = rememberNavController()
            XmdTheme {
                SettingsScreenRoot(
                    navController = navController,
                    startRoute = startRoute,
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    onImportWebsites = ::startWebImportFlow,
                    onExportWebsites = ::startWebExportFlow,
                )
                importCandidates?.let { files ->
                    val storageRoot = Environment.getExternalStorageDirectory().path
                    AppChoiceDialog(
                        title = stringResource(R.string.import_websites_title),
                        choices = files.map { it.path.removePrefix(storageRoot).trimStart('/') },
                        dismissLabel = stringResource(android.R.string.cancel),
                        onChoice = { index ->
                            val selected = files.getOrNull(index)
                            importCandidates = null
                            if (selected != null) runWebImport(selected)
                        },
                        onDismiss = { importCandidates = null },
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SettingsScreenRoot(
        navController: NavHostController,
        startRoute: String,
        onBack: () -> Unit,
        onImportWebsites: () -> Unit,
        onExportWebsites: () -> Unit,
    ) {
        val configuration = LocalConfiguration.current
        val isTablet = configuration.smallestScreenWidthDp >= 600

        if (isTablet) {
            var selectedRoute by remember {
                mutableStateOf(if (startRoute == Route.ROOT) Route.APPEARANCE else startRoute)
            }

            Surface(color = MaterialTheme.colorScheme.background) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left Pane (Master: Category List)
                    Column(
                        modifier = Modifier
                            .weight(0.38f)
                            .fillMaxHeight(),
                    ) {
                        TopAppBar(
                            title = {
                                Text(
                                    text = stringResource(R.string.settings_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = onBack) {
                                    Icon(
                                        imageVector = Icons.ArrowBack,
                                        contentDescription = stringResource(R.string.action_back),
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                        )
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            SettingsRootScreen(
                                showYoutubeRow = com.invictus.xmd.BuildConfig.HAS_YOUTUBE_SUPPORT,
                                selectedRoute = selectedRoute,
                                onOpenAppearance = { selectedRoute = Route.APPEARANCE },
                                onOpenConnections = { selectedRoute = Route.CONNECTIONS },
                                onOpenBrowser = { selectedRoute = Route.BROWSER },
                                onOpenDownloads = { selectedRoute = Route.DOWNLOADS },
                                onOpenYoutube = { selectedRoute = Route.YOUTUBE },
                                onOpenAbout = { selectedRoute = Route.ABOUT },
                            )
                        }
                    }

                    // Vertical Divider between Master and Detail panes
                    VerticalDivider(
                        modifier = Modifier.fillMaxHeight(),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        thickness = 1.dp,
                    )

                    // Right Pane (Detail: Active Settings Screen)
                    Column(
                        modifier = Modifier
                            .weight(0.62f)
                            .fillMaxHeight(),
                    ) {
                        val detailTitleRes = routeTitles[selectedRoute] ?: R.string.settings_title
                        TopAppBar(
                            title = {
                                Text(
                                    text = stringResource(detailTitleRes),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                        )
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            when (selectedRoute) {
                                Route.APPEARANCE -> AppearanceRoute()
                                Route.CONNECTIONS -> ConnectionsRoute()
                                Route.DOWNLOADS -> DownloadsRoute()
                                Route.BROWSER -> BrowserRoute(
                                    onImportWebsites = onImportWebsites,
                                    onExportWebsites = onExportWebsites,
                                )
                                Route.YOUTUBE -> YoutubeRoute()
                                Route.ABOUT -> AboutRoute()
                                else -> AppearanceRoute()
                            }
                        }
                    }
                }
            }
        } else {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route ?: startRoute
            val titleRes = routeTitles[currentRoute] ?: R.string.settings_title

            Surface(color = MaterialTheme.colorScheme.background) {
                Column {
                    TopAppBar(
                        title = { Text(stringResource(titleRes)) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.ArrowBack,
                                    contentDescription = stringResource(R.string.action_back),
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    )

                    NavHost(
                        navController = navController,
                        startDestination = startRoute,
                        modifier = Modifier.weight(1f, fill = true).fillMaxWidth(),
                    ) {
                        composable(Route.ROOT) {
                            SettingsRootScreen(
                                showYoutubeRow = com.invictus.xmd.BuildConfig.HAS_YOUTUBE_SUPPORT,
                                selectedRoute = null,
                                onOpenAppearance = { navController.navigate(Route.APPEARANCE) },
                                onOpenConnections = { navController.navigate(Route.CONNECTIONS) },
                                onOpenBrowser = { navController.navigate(Route.BROWSER) },
                                onOpenDownloads = { navController.navigate(Route.DOWNLOADS) },
                                onOpenYoutube = { navController.navigate(Route.YOUTUBE) },
                                onOpenAbout = { navController.navigate(Route.ABOUT) },
                            )
                        }
                        composable(Route.APPEARANCE) { AppearanceRoute() }
                        composable(Route.CONNECTIONS) { ConnectionsRoute() }
                        composable(Route.DOWNLOADS) { DownloadsRoute() }
                        composable(Route.BROWSER) {
                            BrowserRoute(onImportWebsites = onImportWebsites, onExportWebsites = onExportWebsites)
                        }
                        composable(Route.YOUTUBE) { YoutubeRoute() }
                        composable(Route.ABOUT) { AboutRoute() }
                    }
                }
            }
        }
    }

    // ── website source-pack import ────────────────────────────────────
    // Moved verbatim from MainActivity.startWebImportFlow() / friends -- the
    // Import Websites action lives in the Downloads settings screen now, and
    // this logic is fully self-contained (ShortcutRepository only), so it's
    // relocated here rather than delegated back across Activities.

    private fun startWebImportFlow() {
        Toast.makeText(this, R.string.import_websites_scanning, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val files = withContext(Dispatchers.IO) { ShortcutRepository.findImportCandidates() }
            if (files.isEmpty()) {
                Toast.makeText(this@SettingsActivity, R.string.import_websites_not_found, Toast.LENGTH_LONG).show()
            } else {
                showImportCandidatesDialog(files)
            }
        }
    }

    private fun showImportCandidatesDialog(files: List<File>) {
        importCandidates = files
    }

    private fun runWebImport(file: File) {
        lifecycleScope.launch {
            val result = ShortcutRepository.importWebsites(file)
            val message = if (result.imported > 0) {
                getString(R.string.import_websites_success, result.imported)
            } else {
                getString(R.string.import_websites_none_new)
            }
            Toast.makeText(this@SettingsActivity, message, Toast.LENGTH_LONG).show()
        }
    }

    // ── website source-pack export ────────────────────────────────────
    // User picks the save location via SAF (Save As) rather than a fixed
    // Downloads/Xmd path, then the file is shared immediately after saving
    // so it's one tap from "Export Now" to sending it to someone.

    private fun startWebExportFlow() {
        lifecycleScope.launch {
            val count = ShortcutRepository.count()
            if (count == 0) {
                Toast.makeText(this@SettingsActivity, R.string.export_websites_empty, Toast.LENGTH_SHORT).show()
            } else {
                exportLauncher.launch(defaultExportFileName())
            }
        }
    }

    private fun defaultExportFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "xmd_web_$stamp.json"
    }

    private fun writeAndShareExport(uri: Uri) {
        lifecycleScope.launch {
            val json = ShortcutRepository.exportWebsitesJson()
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(json.toByteArray())
                        true
                    } ?: false
                }.getOrDefault(false)
            }
            if (!written) {
                Toast.makeText(this@SettingsActivity, R.string.export_websites_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            Toast.makeText(this@SettingsActivity, R.string.export_websites_success, Toast.LENGTH_SHORT).show()
            shareExportedFile(uri)
        }
    }

    private fun shareExportedFile(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.export_websites_share_title)))
    }

    override fun finish() {
        super.finish()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    companion object {
        /** Intent extra: which category to land on directly, skipping the
         *  root list. See [CATEGORY_YOUTUBE]. */
        const val EXTRA_OPEN_CATEGORY = "open_category"
        const val CATEGORY_YOUTUBE = "youtube"
    }
}

/** NavHost route strings, one per Settings category screen. */
internal object Route {
    const val ROOT = "root"
    const val APPEARANCE = "appearance"
    const val CONNECTIONS = "connections"
    const val DOWNLOADS = "downloads"
    const val BROWSER = "browser"
    const val YOUTUBE = "youtube"
    const val ABOUT = "about"
}

/** Route -> header title, replaces the old syncHeaderTitle()'s Fragment-type switch. */
internal val routeTitles: Map<String, Int> = mapOf(
    Route.APPEARANCE to R.string.settings_category_appearance,
    Route.CONNECTIONS to R.string.settings_category_connections,
    Route.BROWSER to R.string.settings_category_browser,
    Route.DOWNLOADS to R.string.settings_category_downloads,
    Route.YOUTUBE to R.string.settings_category_youtube,
    Route.ABOUT to R.string.settings_category_about,
)

// ── Route bodies ──────────────────────────────────────────────────────────
// Each wraps the matching *Screen.kt composable exactly as its retired
// Fragment did, replacing Fragment-scoped calls (getString/requireContext/
// requireActivity/lifecycleScope/startActivity) with their Compose
// equivalents (stringResource/LocalContext.current/rememberCoroutineScope).
// The *Screen.kt composables themselves are untouched -- same signatures.

@Composable
private fun AppearanceRoute() {
    val themeTransition = LocalThemeTransitionState.current
    val coroutineScope = rememberCoroutineScope()

    val currentTheme by com.invictus.xmd.core.Settings.themeFlow.collectAsState()
    val isDark by com.invictus.xmd.core.Settings.darkModeFlow.collectAsState()
    val isAmoled by com.invictus.xmd.core.Settings.amoledModeFlow.collectAsState()

    var tabOrder by remember {
        mutableStateOf(com.invictus.xmd.core.Settings.tabOrder())
    }
    var hiddenTabs by remember {
        mutableStateOf(com.invictus.xmd.core.Settings.hiddenTabs())
    }
    var defaultTab by remember {
        mutableStateOf(com.invictus.xmd.core.Settings.defaultTab())
    }

    SettingsAppearanceScreen(
        currentTheme = currentTheme,
        isDark = isDark,
        isAmoled = isAmoled,
        onThemeSelected = { theme, position ->
            if (theme != currentTheme && themeTransition?.isAnimating != true) {
                themeTransition?.startTransition(position)
                coroutineScope.launch {
                    kotlinx.coroutines.delay(50)
                    com.invictus.xmd.core.Settings.setAppTheme(theme)
                }
            }
        },
        onDarkModeChanged = { checked ->
            if (themeTransition?.isAnimating != true) {
                themeTransition?.startTransition(androidx.compose.ui.geometry.Offset.Zero)
                coroutineScope.launch {
                    kotlinx.coroutines.delay(50)
                    com.invictus.xmd.core.Settings.setDarkMode(checked)
                }
            }
        },
        onAmoledModeChanged = { checked ->
            if (themeTransition?.isAnimating != true) {
                themeTransition?.startTransition(androidx.compose.ui.geometry.Offset.Zero)
                coroutineScope.launch {
                    kotlinx.coroutines.delay(50)
                    com.invictus.xmd.core.Settings.setAmoledMode(checked)
                }
            }
        },
        tabOrder = tabOrder,
        hiddenTabs = hiddenTabs,
        defaultTab = defaultTab,
        onMoveTab = { fromIndex, toIndex ->
            val updated = tabOrder.toMutableList()
            val moved = updated.removeAt(fromIndex)
            updated.add(toIndex, moved)
            tabOrder = updated
            com.invictus.xmd.core.Settings.setTabOrder(updated)
        },
        onToggleTabVisible = { tabId, visible ->
            val updated = hiddenTabs.toMutableSet()
            if (visible) updated -= tabId else updated += tabId
            hiddenTabs = updated
            com.invictus.xmd.core.Settings.setHiddenTabs(updated)
            // The now-hidden (or newly-visible) tab might have been --
            // or might become -- the default; re-read it the same way
            // Settings.defaultTab() would self-heal on its own next read.
            defaultTab = com.invictus.xmd.core.Settings.defaultTab()
        },
        onDefaultTabSelected = { tabId ->
            defaultTab = tabId
            com.invictus.xmd.core.Settings.setDefaultTab(tabId)
        },
    )
}

@Composable
private fun ConnectionsRoute() {
    val context = LocalContext.current
    SettingsConnectionsScreen(
        initialConnections = com.invictus.xmd.core.Settings.connectionsPerDownload(),
        initialSpeedLimitKBps = com.invictus.xmd.core.Settings.speedLimitKBps(),
        initialMaxConcurrent = com.invictus.xmd.core.Settings.maxConcurrentDownloads(),
        onSave = { connections, speedLimitKBps, maxConcurrent ->
            com.invictus.xmd.core.Settings.setConnectionsPerDownload(connections)
            com.invictus.xmd.core.Settings.setSpeedLimitKBps(speedLimitKBps)
            com.invictus.xmd.core.Settings.setMaxConcurrentDownloads(maxConcurrent)
            Toast.makeText(context, R.string.settings_saved, Toast.LENGTH_SHORT).show()
        },
    )
}

@Composable
private fun DownloadsRoute() {
    val context = LocalContext.current
    var autoRetry by remember {
        mutableStateOf(com.invictus.xmd.core.Settings.autoRetryEnabled())
    }
    var saveToDownloads by remember {
        mutableStateOf(com.invictus.xmd.core.Settings.saveToDownloadsFolder())
    }
    var wifiOnly by remember {
        mutableStateOf(com.invictus.xmd.core.Settings.wifiOnlyDownloads())
    }

    SettingsDownloadsScreen(
        autoRetry = autoRetry,
        saveToDownloads = saveToDownloads,
        wifiOnly = wifiOnly,
        onAutoRetryChanged = { checked ->
            autoRetry = checked
            com.invictus.xmd.core.Settings.setAutoRetryEnabled(checked)
        },
        onSaveToDownloadsChanged = { checked ->
            saveToDownloads = checked
            com.invictus.xmd.core.Settings.setSaveToDownloadsFolder(checked)
        },
        onWifiOnlyChanged = { checked ->
            val wifiOnlyJustEnabled = checked && !com.invictus.xmd.core.Settings.wifiOnlyDownloads()
            wifiOnly = checked
            com.invictus.xmd.core.Settings.setWifiOnlyDownloads(checked)
            if (wifiOnlyJustEnabled && !com.invictus.xmd.core.NetworkMonitor.isOnWifi(context)) {
                // Turned ON while already on cellular -- the setting only
                // reacts to a live network *transition* otherwise, so
                // without this any download already in flight would keep
                // running on cellular until the next Wi-Fi drop/regain.
                com.invictus.xmd.service.DownloadService.pauseForWifiOnly(context)
            }
        },
    )
}

@Composable
private fun BrowserRoute(onImportWebsites: () -> Unit, onExportWebsites: () -> Unit) {
    var adblockEnabled by remember {
        mutableStateOf(com.invictus.xmd.core.Settings.adblockEnabled())
    }
    SettingsBrowserScreen(
        adblockEnabled = adblockEnabled,
        onAdblockChanged = { checked ->
            adblockEnabled = checked
            com.invictus.xmd.core.Settings.setAdblockEnabled(checked)
        },
        onImportWebsites = onImportWebsites,
        onExportWebsites = onExportWebsites,
    )
}

@Composable
private fun YoutubeRoute() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val containerOptions = listOf(
        stringResource(R.string.preset_any) to com.invictus.xmd.core.Settings.ContainerPreset.ANY,
        stringResource(R.string.preset_container_mp4) to com.invictus.xmd.core.Settings.ContainerPreset.MP4,
        stringResource(R.string.preset_container_webm) to com.invictus.xmd.core.Settings.ContainerPreset.WEBM
    )
    val fpsOptions = listOf(
        stringResource(R.string.preset_any) to com.invictus.xmd.core.Settings.FpsPreset.ANY,
        stringResource(R.string.preset_fps_30) to com.invictus.xmd.core.Settings.FpsPreset.FPS30,
        stringResource(R.string.preset_fps_60) to com.invictus.xmd.core.Settings.FpsPreset.FPS60
    )
    val codecOptions = listOf(
        stringResource(R.string.preset_any) to com.invictus.xmd.core.Settings.CodecPreset.ANY,
        stringResource(R.string.preset_codec_avc) to com.invictus.xmd.core.Settings.CodecPreset.AVC,
        stringResource(R.string.preset_codec_vp9) to com.invictus.xmd.core.Settings.CodecPreset.VP9,
        stringResource(R.string.preset_codec_av1) to com.invictus.xmd.core.Settings.CodecPreset.AV1
    )
    val audioFormatOptions = listOf(
        stringResource(R.string.audio_format_mp3) to com.invictus.xmd.core.Settings.AudioFormatPreset.MP3,
        stringResource(R.string.audio_format_m4a) to com.invictus.xmd.core.Settings.AudioFormatPreset.M4A,
        stringResource(R.string.audio_format_opus) to com.invictus.xmd.core.Settings.AudioFormatPreset.OPUS,
        stringResource(R.string.audio_format_original) to com.invictus.xmd.core.Settings.AudioFormatPreset.ORIGINAL
    )

    // "Ask always" (blank stored value) first, then one entry per
    // standardQualityOptions() label, same order as the picker dialog
    // itself so the two stay visually consistent.
    val askAlwaysLabel = stringResource(R.string.quality_ask_always)
    val qualityLabels = listOf(askAlwaysLabel) +
        com.invictus.xmd.core.YtDlpManager.standardQualityOptions().map { it.label }

    fun resolveInitialQualityLabel(): String {
        val savedLabel = com.invictus.xmd.core.Settings.ytDlpDefaultQualityLabel()
        return when {
            savedLabel.isBlank() -> askAlwaysLabel
            qualityLabels.contains(savedLabel) -> savedLabel
            // Saved before the audio format preset changed (see the
            // matching resolveYoutube() fallback) -- show the current
            // audio-only label instead of a stale "(MP3)" that's no longer
            // in the list.
            savedLabel.startsWith("Audio only") ->
                qualityLabels.firstOrNull { it.startsWith("Audio only") } ?: savedLabel
            else -> savedLabel
        }
    }

    var selectedQualityLabel by remember {
        mutableStateOf(resolveInitialQualityLabel())
    }
    var selectedContainerLabel by remember {
        mutableStateOf(containerOptions.first { it.second == com.invictus.xmd.core.Settings.presetContainer() }.first)
    }
    var selectedFpsLabel by remember {
        mutableStateOf(fpsOptions.first { it.second == com.invictus.xmd.core.Settings.presetFps() }.first)
    }
    var selectedCodecLabel by remember {
        mutableStateOf(codecOptions.first { it.second == com.invictus.xmd.core.Settings.presetCodec() }.first)
    }
    var selectedAudioFormatLabel by remember {
        mutableStateOf(audioFormatOptions.first { it.second == com.invictus.xmd.core.Settings.presetAudioFormat() }.first)
    }

    var ytDlpInstalled by remember {
        mutableStateOf(com.invictus.xmd.core.YtDlpManager.isInstalled(context))
    }
    var ytDlpUsingNightly by remember {
        mutableStateOf(com.invictus.xmd.core.Settings.ytDlpUseNightly())
    }
    var ytDlpOpState by remember {
        mutableStateOf<YtDlpOpState>(YtDlpOpState.Idle)
    }

    fun refreshYtDlpStatus() {
        ytDlpInstalled = com.invictus.xmd.core.YtDlpManager.isInstalled(context)
        ytDlpUsingNightly = com.invictus.xmd.core.Settings.ytDlpUseNightly()
    }

    SettingsYoutubeScreen(
        liteMode = !com.invictus.xmd.BuildConfig.HAS_YOUTUBE_SUPPORT,
        hintText = stringResource(R.string.settings_ytdlp_hint),
        qualityLabels = qualityLabels,
        selectedQualityLabel = selectedQualityLabel,
        onQualityChanged = { index ->
            val chosenLabel = qualityLabels[index]
            selectedQualityLabel = chosenLabel
            com.invictus.xmd.core.Settings.setYtDlpDefaultQualityLabel(
                if (chosenLabel == askAlwaysLabel) "" else chosenLabel
            )
        },
        containerOptions = containerOptions.map { it.first },
        selectedContainer = selectedContainerLabel,
        onContainerChanged = { index ->
            selectedContainerLabel = containerOptions[index].first
            com.invictus.xmd.core.Settings.setPresetContainer(containerOptions[index].second)
        },
        fpsOptions = fpsOptions.map { it.first },
        selectedFps = selectedFpsLabel,
        onFpsChanged = { index ->
            selectedFpsLabel = fpsOptions[index].first
            com.invictus.xmd.core.Settings.setPresetFps(fpsOptions[index].second)
        },
        codecOptions = codecOptions.map { it.first },
        selectedCodec = selectedCodecLabel,
        onCodecChanged = { index ->
            selectedCodecLabel = codecOptions[index].first
            com.invictus.xmd.core.Settings.setPresetCodec(codecOptions[index].second)
        },
        audioFormatOptions = audioFormatOptions.map { it.first },
        selectedAudioFormat = selectedAudioFormatLabel,
        onAudioFormatChanged = { index ->
            selectedAudioFormatLabel = audioFormatOptions[index].first
            com.invictus.xmd.core.Settings.setPresetAudioFormat(audioFormatOptions[index].second)
        },
        ytDlpInstalled = ytDlpInstalled,
        ytDlpUsingNightly = ytDlpUsingNightly,
        ytDlpOpState = ytDlpOpState,
        onInstallOrDeleteClick = {
            if (ytDlpInstalled) {
                com.invictus.xmd.core.YtDlpManager.delete(context)
                Toast.makeText(context, R.string.settings_ytdlp_removed, Toast.LENGTH_SHORT).show()
                refreshYtDlpStatus()
            } else {
                ytDlpOpState = YtDlpOpState.Installing
                scope.launch {
                    val error = withContext(Dispatchers.IO) { com.invictus.xmd.core.YtDlpManager.install(context) }
                    // Show the exact failure reason instead of a generic
                    // message -- install() only unpacks bundled assets, no
                    // network involved, so a guessed "check your
                    // connection" message would usually be wrong.
                    Toast.makeText(
                        context,
                        error?.let { "Install failed: $it" } ?: "yt-dlp installed",
                        Toast.LENGTH_LONG
                    ).show()
                    refreshYtDlpStatus()
                    ytDlpOpState = YtDlpOpState.Idle
                }
            }
        },
        onUpdateClick = {
            ytDlpOpState = YtDlpOpState.Updating
            scope.launch {
                val result = withContext(Dispatchers.IO) { com.invictus.xmd.core.YtDlpManager.update(context) }
                Toast.makeText(
                    context,
                    result?.let { "yt-dlp: $it" } ?: "Update failed — check your connection",
                    Toast.LENGTH_LONG
                ).show()
                refreshYtDlpStatus()
                ytDlpOpState = YtDlpOpState.Idle
            }
        },
        onNightlyToggleClick = {
            val switchingToNightly = !ytDlpUsingNightly
            ytDlpOpState = YtDlpOpState.SwitchingChannel(switchingToNightly)
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    com.invictus.xmd.core.YtDlpManager.switchChannel(context, switchingToNightly)
                }
                Toast.makeText(
                    context,
                    result?.let { "yt-dlp: $it" } ?: "Switch failed — check your connection",
                    Toast.LENGTH_LONG
                ).show()
                refreshYtDlpStatus()
                ytDlpOpState = YtDlpOpState.Idle
            }
        },
    )
}

@Composable
private fun AboutRoute() {
    val context = LocalContext.current
    val developers = listOf(
        "Utsav Rajput" to "Developer",
        "Arnab Sadhukhan" to "Developer",
        "Ritesh Pandit" to "Developer",
    )
    val credits = buildList {
        add("libtorrent4j" to stringResource(R.string.about_credit_libtorrent_desc))
        if (com.invictus.xmd.BuildConfig.HAS_YOUTUBE_SUPPORT) {
            add("yt-dlp (youtubedl-android)" to stringResource(R.string.about_credit_ytdlp_desc))
        }
        add("OkHttp" to stringResource(R.string.about_credit_okhttp_desc))
        add("jsoup" to stringResource(R.string.about_credit_jsoup_desc))
        add("Room" to stringResource(R.string.about_credit_room_desc))
        add("Kotlin Coroutines" to stringResource(R.string.about_credit_coroutines_desc))
    }

    AboutScreen(
        versionText = stringResource(R.string.about_version_format, com.invictus.xmd.BuildConfig.VERSION_NAME),
        onGithubClick = {
            val url = context.getString(R.string.about_github_url)
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        },
        developers = developers,
        credits = credits,
    )
}
