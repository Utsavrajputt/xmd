package com.invictus.xmd.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings as AndroidSettings
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.invictus.xmd.R
import com.invictus.xmd.BuildConfig
import java.util.UUID
import com.invictus.xmd.service.DownloadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.provider.OpenableColumns
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.ui.graphics.toArgb
import com.invictus.xmd.ui.theme.rememberThemeTransitionState
import com.invictus.xmd.ui.theme.resolveCurrentXmdColorScheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import org.libtorrent4j.TorrentInfo
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import com.invictus.xmd.FfApp
import com.invictus.xmd.database.entities.QueueItem
import com.invictus.xmd.domain.download.CategoryDetector
import com.invictus.xmd.domain.download.DownloadCategory
import com.invictus.xmd.domain.download.DownloadEngine
import com.invictus.xmd.domain.download.ItemStatus
import com.invictus.xmd.domain.download.MediaPlatform
import com.invictus.xmd.domain.download.ResolutionError
import com.invictus.xmd.domain.download.YtDlpManager
import com.invictus.xmd.domain.torrent.TorrentSession
import com.invictus.xmd.preferences.Settings
import com.invictus.xmd.repository.QueueRepository
import com.invictus.xmd.ui.browser.BrowserFragment
import com.invictus.xmd.ui.browser.BrowserMenuAction
import com.invictus.xmd.ui.browser.SavedPagesDestination
import com.invictus.xmd.ui.browser.SavedPagesOverlay
import com.invictus.xmd.ui.components.MainShell
import com.invictus.xmd.ui.components.MainDestination
import com.invictus.xmd.ui.components.MainNavigationItem
import com.invictus.xmd.ui.components.AppMessageDialog
import com.invictus.xmd.ui.components.AppMessageDialogState
import com.invictus.xmd.ui.downloads.AddDownloadDialog
import com.invictus.xmd.ui.downloads.AddTorrentDialog
import com.invictus.xmd.ui.downloads.DownloadsFragment
import com.invictus.xmd.ui.downloads.DownloadsSelectionUiState
import com.invictus.xmd.ui.downloads.TorrentFileRow
import com.invictus.xmd.ui.downloads.TorrentFilesUiState
import com.invictus.xmd.ui.home.HomeFragment
import com.invictus.xmd.ui.settings.DnsSettingsDialog
import com.invictus.xmd.ui.settings.SettingsActivity
import com.invictus.xmd.utils.LinkParser
import com.invictus.xmd.utils.storage.FileNameUtils
import com.invictus.xmd.utils.storage.OnDuplicateStrategy
import com.invictus.xmd.utils.storage.StorageUtils

class MainActivity : AppCompatActivity(), DownloadsFragment.Callbacks, BrowserFragment.Callbacks,
    HomeFragment.Callbacks {
    private var mainDestination: MainDestination by mutableStateOf(MainDestination.Downloads)
    private var navigationItems: List<MainNavigationItem> by mutableStateOf(MainNavigationItem.entries.toList())
    private var activeDownloadCount: Int by mutableIntStateOf(0)
    private var headerSearchActive: Boolean by mutableStateOf(false)
    private var headerSearchQuery: String by mutableStateOf("")
    private var savedPagesDestination: SavedPagesDestination? by mutableStateOf(null)
    private val snackbarHostState = SnackbarHostState()
    private var messageDialogState: AppMessageDialogState? by mutableStateOf(null)
    // Activity-owned dialog state is rendered by MainShell's root composition.
    private var dnsSettingsDialogOpen: Boolean by mutableStateOf(false)

    // Phase A: state for the 3 dialogs converted this phase, same
    // `by mutableStateOf` + null-means-closed pattern as dnsSettingsDialogOpen.
    private data class AddDownloadDialogState(val initialLink: String)

    private data class AddTorrentDialogState(
        val prefillLink: String?,
        val prefillTorrentUri: Uri?,
        val prefillDisplayName: String?,
        val filesState: TorrentFilesUiState,
    )

    private var addDownloadDialogState: AddDownloadDialogState? by mutableStateOf(null)
    private var addTorrentDialogState: AddTorrentDialogState? by mutableStateOf(null)
    private var torrentMetadataJob: Job? = null

    private fun openHeaderSearch() {
        headerSearchActive = true
    }

    private fun closeHeaderSearch() {
        headerSearchActive = false
        updateHeaderSearchQuery("")
    }

    private fun updateHeaderSearchQuery(query: String) {
        headerSearchQuery = query
        downloadsFragment()?.setFilterQuery(query)
    }

    private fun showMessageDialog(state: AppMessageDialogState) {
        val previous = messageDialogState
        messageDialogState = null
        previous?.onDismiss?.invoke()
        messageDialogState = state
    }

    private fun finishMessageDialog(state: AppMessageDialogState, action: () -> Unit) {
        if (messageDialogState !== state) return
        messageDialogState = null
        action()
    }

    private fun navigationItemFor(tabId: String): MainNavigationItem? = when (tabId) {
        Settings.TabId.HOME -> MainNavigationItem.Home
        Settings.TabId.DOWNLOADS -> MainNavigationItem.Downloads
        Settings.TabId.ADD -> MainNavigationItem.Add
        Settings.TabId.BROWSER -> MainNavigationItem.Browser
        else -> null
    }

    private fun destinationFor(tabId: String): MainDestination? = when (tabId) {
        Settings.TabId.HOME -> MainDestination.Home
        Settings.TabId.DOWNLOADS -> MainDestination.Downloads
        Settings.TabId.BROWSER -> MainDestination.Browser
        else -> null
    }

    private fun configuredNavigationItems(): List<MainNavigationItem> {
        val hiddenTabs = Settings.hiddenTabs()
        return Settings.tabOrder()
            .filterNot { tabId -> tabId in hiddenTabs }
            .mapNotNull(::navigationItemFor)
    }

    private fun configuredDefaultDestination(): MainDestination =
        destinationFor(Settings.defaultTab()) ?: MainDestination.Downloads

    private fun tagFor(destination: MainDestination): String = when (destination) {
        MainDestination.Home -> TAG_HOME
        MainDestination.Downloads -> TAG_DOWNLOADS
        MainDestination.Browser -> TAG_BROWSER
    }

    private var mainViewPager: ViewPager2? = null
    private var pagerPosition by mutableFloatStateOf(0f)
    private var downloadsSelectionState: DownloadsSelectionUiState? by mutableStateOf(null)
    private var isBrowserHeaderLocked: Boolean = false

    private fun updateViewPagerUserInputEnabled() {
        val disabled = (downloadsSelectionState != null) ||
            (mainDestination == MainDestination.Browser && isBrowserHeaderLocked)
        mainViewPager?.isUserInputEnabled = !disabled
    }

    private fun selectMainDestination(destination: MainDestination, smoothScroll: Boolean = false) {
        if (destination != MainDestination.Downloads) {
            downloadsSelectionState?.onClose?.invoke()
        }
        savedPagesDestination = null
        closeHeaderSearch()
        mainDestination = destination
        currentTabTag = tagFor(destination)
        val index = bottomNavSwipeOrder.indexOf(destination)
        if (index >= 0 && mainViewPager?.currentItem != index) {
            pagerPosition = index.toFloat()
            mainViewPager?.setCurrentItem(index, smoothScroll)
        }
        updateViewPagerUserInputEnabled()
    }

    private fun homeFragment(): HomeFragment? =
        supportFragmentManager.fragments.filterIsInstance<HomeFragment>().firstOrNull()

    private fun browserFragment(): BrowserFragment? =
        supportFragmentManager.fragments.filterIsInstance<BrowserFragment>().firstOrNull()

    private fun downloadsFragment(): DownloadsFragment? =
        supportFragmentManager.fragments.filterIsInstance<DownloadsFragment>().firstOrNull()

    private fun setupViewPager(viewPager: ViewPager2) {
        mainViewPager = viewPager
        val destinations = bottomNavSwipeOrder
        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = destinations.size
            override fun createFragment(position: Int): Fragment = when (destinations[position]) {
                MainDestination.Home -> HomeFragment()
                MainDestination.Downloads -> DownloadsFragment()
                MainDestination.Browser -> BrowserFragment()
            }
            override fun getItemId(position: Int): Long = destinations[position].ordinal.toLong()
            override fun containsItem(itemId: Long): Boolean = destinations.any { it.ordinal.toLong() == itemId }
        }
        viewPager.offscreenPageLimit = 2
        val initialIdx = destinations.indexOf(mainDestination).coerceAtLeast(0)
        viewPager.setCurrentItem(initialIdx, false)
        pagerPosition = initialIdx.toFloat()

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                pagerPosition = position + positionOffset
            }

            override fun onPageSelected(position: Int) {
                val dest = destinations.getOrNull(position) ?: return
                if (mainDestination != dest) {
                    // A physical swipe (unlike selectMainDestination, which
                    // only runs for bottom-nav taps) skipped this cleanup,
                    // so swiping off History/Bookmarks left the overlay
                    // stuck on top of the newly-selected tab underneath.
                    savedPagesDestination = null
                    mainDestination = dest
                    currentTabTag = tagFor(dest)
                    closeHeaderSearch()
                }
                updateViewPagerUserInputEnabled()
            }
        })
        updateViewPagerUserInputEnabled()
    }

    private val clipboardManager by lazy { getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    private val filenameClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    private var pendingSaveDirCallback: ((String) -> Unit)? = null

    private val pickTorrentFileLauncher: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) onTorrentFilePicked(uri)
        }

    private val pickSaveDirLauncher: ActivityResultLauncher<Uri?> =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            val path = resolveTreeUriToPath(uri)
            if (path != null) {
                pendingSaveDirCallback?.invoke(path)
            } else {
                Toast.makeText(
                    this,
                    R.string.torrent_dialog_save_path_failed,
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    private var appliedThemeKey: String = ""
    private var appliedIsDark: Boolean = true
    private var appliedIsAmoled: Boolean = false

    private var currentTabTag: String = TAG_DOWNLOADS

    private val bottomNavSwipeOrder: List<MainDestination>
        get() = navigationItems.mapNotNull { item -> item.destination }


    // ── HTTP client (resolve step) ────────────────────────────────────────
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // ── ChallengeActivity launcher (must be in Activity, not Fragment) ────
    private var pendingChallengeContinuation: ((directUrl: String?, error: String?) -> Unit)? = null

    private val challengeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val directUrl = result.data?.getStringExtra(ChallengeActivity.EXTRA_DIRECT_URL)
        val error     = result.data?.getStringExtra(ChallengeActivity.EXTRA_ERROR)
        pendingChallengeContinuation?.invoke(directUrl, error)
        pendingChallengeContinuation = null
    }

    // ── Storage permission (API 26-28) ────────────────────────────────────
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                this,
                getString(R.string.storage_permission_denied),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ── Notification permission (API 33+) ──────────────────────────────────
    // Declaring POST_NOTIFICATIONS in the Manifest alone does nothing on
    // Android 13+ -- it's a runtime permission like storage/location, so
    // without an explicit request here the system silently drops every
    // notification the download-progress channel tries to post (the user
    // never even sees a system prompt, downloads just run "invisibly").
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                this,
                getString(R.string.notification_permission_denied),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Items explicitly sent through the Retry button, watched until they land
    // on a terminal status. The actual download failure/success happens
    // asynchronously in DownloadService (a background coroutine, not this
    // suspend chain), so we can't just check the status right after calling
    // retrySingle() -- we have to watch QueueRepository.items for the outcome
    // and react only once, only for items the user explicitly retried.
    private val pendingRetryIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    // ── onResume ──────────────────────────────────────────────────────────

    /**
     * Re-syncs the toolbar's visibility with whichever tab fragment is
     * actually showing right now. Needed because the toolbar is only ever
    * Re-syncs Compose navigation state from restored Fragment visibility
    * after process recreation.
     */
    override fun onResume() {
        super.onResume()
        appliedThemeKey = Settings.appTheme().storageKey
        appliedIsDark = Settings.isDarkMode()
        appliedIsAmoled = Settings.isAmoledMode()
        val newNavItems = configuredNavigationItems()
        if (navigationItems != newNavItems) {
            navigationItems = newNavItems
            mainViewPager?.let { setupViewPager(it) }
        }
        syncToolbarWithVisibleFragment()
        if (mainDestination !in bottomNavSwipeOrder) {
            val fallback = configuredDefaultDestination()
                .takeIf { destination -> destination in bottomNavSwipeOrder }
                ?: bottomNavSwipeOrder.firstOrNull()
            fallback?.let { selectMainDestination(it, smoothScroll = false) }
        }
    }

    private fun syncToolbarWithVisibleFragment() {
        val currentDest = bottomNavSwipeOrder.getOrNull(mainViewPager?.currentItem ?: -1)
        if (currentDest != null) {
            mainDestination = currentDest
            currentTabTag = tagFor(currentDest)
            if (currentDest == MainDestination.Browser) closeHeaderSearch()
        }
    }

    private var appliedEdgeToEdgeDarkMode: Boolean? = null

    private fun applyEdgeToEdge(isDarkMode: Boolean) {
        if (appliedEdgeToEdgeDarkMode == isDarkMode) return

        val synchronizedBarStyle = SystemBarStyle.auto(
            lightScrim = Color(0xFFF4F6F9).toArgb(),
            darkScrim = Color(0xFF0E1521).toArgb(),
        ) { isDarkMode }
        enableEdgeToEdge(
            statusBarStyle = synchronizedBarStyle,
            navigationBarStyle = synchronizedBarStyle,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        appliedEdgeToEdgeDarkMode = isDarkMode
    }

    // ── onCreate ──────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate() -- Activity theme setup applies
        // before the window/decor is created.
        appliedThemeKey = Settings.appTheme().storageKey
        appliedIsDark = Settings.isDarkMode()
        appliedIsAmoled = Settings.isAmoledMode()
        com.invictus.xmd.ui.theme.AppTheme.applyTo(this)
        super.onCreate(savedInstanceState)
        applyEdgeToEdge(isDarkMode = Settings.isDarkMode())
        navigationItems = configuredNavigationItems()
        if (savedInstanceState == null) {
            mainDestination = configuredDefaultDestination()
        } else {
            // Process death/recreation (e.g. the app was backgrounded -- say,
            // while the user hopped into the Browser tab and hit back --
            // long enough for the system to kill it): mainDestination's
            // in-class default (Downloads) would otherwise win here, which
            // re-shows the Downloads/Home header even though the user was
            // last on the Browser tab. Restore whichever tab was actually
            // showing so the header stays correctly hidden/shown.
            mainDestination = savedInstanceState
                .getString(STATE_MAIN_DESTINATION)
                ?.let { savedName -> MainDestination.entries.firstOrNull { it.name == savedName } }
                ?: configuredDefaultDestination()
        }
        currentTabTag = tagFor(mainDestination)
        savedPagesDestination = savedInstanceState
            ?.getString(STATE_SAVED_PAGES_DESTINATION)
            ?.let { savedName ->
                SavedPagesDestination.entries.firstOrNull { destination ->
                    destination.name == savedName
                }
            }
        setContent {
            val isDark by Settings.darkModeFlow.collectAsState()
            val themeTransitionState = rememberThemeTransitionState()

            LaunchedEffect(isDark) {
                if (themeTransitionState.isAnimating) {
                    snapshotFlow {
                        themeTransitionState.animationProgress.value to themeTransitionState.isAnimating
                    }.first { (progress, isAnimating) ->
                        !isAnimating || progress >= SYSTEM_BAR_THEME_SWITCH_PROGRESS
                    }
                }
                applyEdgeToEdge(isDark)
            }

            com.invictus.xmd.ui.theme.XmdTheme(transitionState = themeTransitionState) {
                MainShell(
                    destination = mainDestination,
                    navigationItems = navigationItems,
                    activeDownloadCount = activeDownloadCount,
                    searchActive = headerSearchActive,
                    searchQuery = headerSearchQuery,
                    snackbarHostState = snackbarHostState,
                    pagerPosition = pagerPosition,
                    downloadsSelectionState = downloadsSelectionState,
                    onSearchActiveChange = { active ->
                        if (active) openHeaderSearch() else closeHeaderSearch()
                    },
                    onSearchQueryChange = ::updateHeaderSearchQuery,
                    onDestinationSelected = ::selectMainDestination,
                    onAddDownload = { showAddDownloadDialog() },
                    onOpenSettings = { openSettingsScreen() },
                    onToggleTheme = ::toggleDarkMode,
                    onViewPagerReady = ::setupViewPager,
                    overlayActive = savedPagesDestination != null,
                    onBottomBarDragStart = {
                        if (mainViewPager?.isFakeDragging == false) {
                            mainViewPager?.beginFakeDrag()
                        }
                    },
                    onBottomBarDrag = { dragAmount ->
                        if (mainViewPager?.isFakeDragging == true) {
                            mainViewPager?.fakeDragBy(-dragAmount)
                        }
                    },
                    onBottomBarDragEnd = {
                        if (mainViewPager?.isFakeDragging == true) {
                            mainViewPager?.endFakeDrag()
                        }
                    },
                    overlay = {
                        savedPagesDestination?.let { destination ->
                            SavedPagesOverlay(
                                destination = destination,
                                onBack = { savedPagesDestination = null },
                                onOpenUrl = { url ->
                                    savedPagesDestination = null
                                    browserFragment()?.openUrl(url)
                                    selectMainDestination(MainDestination.Browser)
                                },
                            )
                        }
                    },
                )

                if (dnsSettingsDialogOpen) {
                    DnsSettingsDialog(
                        currentMode = Settings.dnsMode(),
                        currentCustomUrl = Settings.dnsCustomUrl(),
                        onDismiss = { dnsSettingsDialogOpen = false },
                        onSave = { mode, customUrl ->
                            if (mode == Settings.DnsMode.CUSTOM) {
                                Settings.setDnsCustomUrl(customUrl)
                            }
                            Settings.setDnsMode(mode)
                            dnsSettingsDialogOpen = false
                        },
                        onInvalidCustomUrl = {
                            Toast.makeText(this, R.string.dns_custom_url_needed, Toast.LENGTH_SHORT).show()
                        },
                    )
                }

                addDownloadDialogState?.let { state ->
                    AddDownloadDialog(
                        initialLink = state.initialLink,
                        defaultSavePath = defaultSavePath(),
                        magnetDisplayName = { magnetDisplayName(it) },
                        extractYoutubeFallbackName = { extractYoutubeFallbackName(it) },
                        probeYoutubeTitle = { link -> withContext(Dispatchers.IO) { probeYoutubeTitle(link) } },
                        probeRealFilename = { link ->
                            withContext(Dispatchers.IO) { DownloadEngine.probeRealFilename(filenameClient, link) }
                        },
                        onDetectedTorrentLink = { link ->
                            addDownloadDialogState = null
                            showAddTorrentDialog(prefillLink = link)
                        },
                        onPickTorrentFile = {
                            addDownloadDialogState = null
                            pickTorrentFileLauncher.launch(
                                arrayOf("application/x-bittorrent", "application/octet-stream")
                            )
                        },
                        onCopyLink = { text ->
                            if (text.isNotBlank()) {
                                clipboardManager.setPrimaryClip(
                                    ClipData.newPlainText(
                                        getString(R.string.clipboard_download_link_label),
                                        text,
                                    )
                                )
                                Toast.makeText(this, R.string.torrent_dialog_link_copied_toast, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onPasteRequest = {
                            val clipText = clipboardManager.primaryClip?.getItemAt(0)?.text?.toString()?.trim().orEmpty()
                            if (clipText.isBlank()) {
                                Toast.makeText(this, R.string.clipboard_empty_toast, Toast.LENGTH_SHORT).show()
                                null
                            } else {
                                Toast.makeText(this, R.string.dialog_link_pasted_toast, Toast.LENGTH_SHORT).show()
                                clipText
                            }
                        },
                        onChangeSaveDir = { onPicked ->
                            pendingSaveDirCallback = onPicked
                            pickSaveDirLauncher.launch(null)
                        },
                        onDismiss = {
                            addDownloadDialogState?.let { state ->
                                val pending = QueueRepository.current().firstOrNull {
                                    it.sourceUrl == state.initialLink && it.status == ItemStatus.RESOLVING
                                }
                                if (pending != null) {
                                    QueueRepository.removeItem(pending.id)
                                }
                            }
                            addDownloadDialogState = null
                        },
                        onStart = { link, name, saveDir, quality, audioFormat, duplicateStrategy ->
                            addDownloadDialogState = null
                            when {
                                LinkParser.isTorrentLink(link) -> showAddTorrentDialog(prefillLink = link)
                                LinkParser.isShareLink(link) || LinkParser.isFitgirlPage(link) ->
                                    triggerPrepare(listOf(link))
                                LinkParser.needsYtDlp(link) ->
                                    triggerDownloadYoutubeCustom(link, name, saveDir, quality, audioFormat, duplicateStrategy)
                                LinkParser.isGenericDownloadUrl(link) ->
                                    triggerDownloadDirectCustom(link, name, saveDir, duplicateStrategy)
                                else ->
                                    Toast.makeText(this, getString(R.string.download_invalid_url_error, link), Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                }

                addTorrentDialogState?.let { state ->
                    AddTorrentDialog(
                        prefillLink = state.prefillLink,
                        prefillTorrentUri = state.prefillTorrentUri,
                        prefillDisplayName = state.prefillDisplayName,
                        defaultSavePath = defaultSavePath(),
                        filesState = state.filesState,
                        onLinkChanged = { newLink -> onAddTorrentLinkChanged(newLink) },
                        onCopyLink = { text ->
                            if (text.isNotBlank()) {
                                clipboardManager.setPrimaryClip(
                                    ClipData.newPlainText(
                                        getString(R.string.clipboard_magnet_link_label),
                                        text,
                                    )
                                )
                                Toast.makeText(this, R.string.torrent_dialog_link_copied_toast, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onPasteRequest = {
                            val clipText = clipboardManager.primaryClip?.getItemAt(0)?.text?.toString()?.trim().orEmpty()
                            if (clipText.isBlank()) {
                                Toast.makeText(this, R.string.clipboard_empty_toast, Toast.LENGTH_SHORT).show()
                                null
                            } else {
                                Toast.makeText(this, R.string.dialog_link_pasted_toast, Toast.LENGTH_SHORT).show()
                                clipText
                            }
                        },
                        onPickTorrentFile = {
                            addTorrentDialogState = null
                            pickTorrentFileLauncher.launch(
                                arrayOf("application/x-bittorrent", "application/octet-stream")
                            )
                        },
                        onToggleFile = { index -> toggleTorrentFileSelection(index) },
                        onToggleSelectAll = { toggleTorrentSelectAll() },
                        onChangeSaveDir = { onPicked ->
                            pendingSaveDirCallback = onPicked
                            pickSaveDirLauncher.launch(null)
                        },
                        onDismiss = {
                            torrentMetadataJob?.cancel()
                            addTorrentDialogState = null
                        },
                        onStart = onStart@{ link, name, saveDir, totalFiles, selectedCount, selectedIndices ->
                            if (totalFiles > 0 && selectedCount == 0) {
                                Toast.makeText(this, R.string.torrent_dialog_no_files_selected, Toast.LENGTH_SHORT).show()
                                return@onStart
                            }
                            val uri = state.prefillTorrentUri
                            if (uri != null) {
                                addTorrentDialogState = null
                                triggerDownloadTorrentFile(uri, name, saveDir, selectedIndices)
                            } else if (!LinkParser.isTorrentLink(link)) {
                                Toast.makeText(this, R.string.torrent_dialog_invalid_link, Toast.LENGTH_SHORT).show()
                            } else {
                                addTorrentDialogState = null
                                triggerDownloadTorrentMagnet(link, name, saveDir, selectedIndices)
                            }
                        },
                    )
                }

                messageDialogState?.let { state ->
                    AppMessageDialog(
                        state = state,
                        onConfirm = { finishMessageDialog(state, state.onConfirm) },
                        onDismissRequest = { finishMessageDialog(state, state.onDismiss) },
                        onDismissAction = {
                            finishMessageDialog(state, state.onDismissAction ?: state.onDismiss)
                        },
                    )
                }
            }
        }

        // Back handling, gesture or button:
        //  1. History/Bookmarks overlay open (Phase D) -> pop its own stack.
        //  2. Browser tab with page history / a loaded page -> step back through it.
        //  3. Any non-default tab -> jump to the configured default tab.
        //  4. Already on the default tab -> exit the app.
        onBackPressedDispatcher.addCallback(this) {
            if (headerSearchActive) {
                closeHeaderSearch()
                return@addCallback
            }
            if (savedPagesDestination != null) {
                savedPagesDestination = null
                return@addCallback
            }
            if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStack()
                return@addCallback
            }
            val browser = browserFragment()
            if (browser?.isVisible == true && browser.onBackPressed()) {
                return@addCallback
            }
            val defaultDestination = configuredDefaultDestination()
            if (mainDestination != defaultDestination) {
                selectMainDestination(defaultDestination)
                return@addCallback
            }
            isEnabled = false
            onBackPressedDispatcher.onBackPressed()
            isEnabled = true
        }

        // Active-download badge on the Downloads tab
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                QueueRepository.items.collect { list ->
                    val active = list.count {
                        it.status == ItemStatus.DOWNLOADING || it.status == ItemStatus.PAUSED ||
                        it.status == ItemStatus.SAVING || it.status == ItemStatus.RETRYING
                    }
                    activeDownloadCount = active
                }
            }
        }

        // Watches items sent through the Retry button; pops an IDM-style
        // "Link Expired" dialog (Clear / Fetch Link) the moment a retried
        // item lands back on FAILED with an expired-link error, whether that
        // failure happened at resolve-time or later during the actual
        // download.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                QueueRepository.items.collect { list ->
                    list.forEach { item ->
                        if (item.id !in pendingRetryIds) return@forEach
                        when (item.status) {
                            ItemStatus.FAILED -> {
                                pendingRetryIds.remove(item.id)
                                if (item.error?.contains("expired", ignoreCase = true) == true) {
                                    showExpiredLinkDialog(item)
                                }
                            }
                            ItemStatus.DONE -> pendingRetryIds.remove(item.id) // succeeded, stop watching
                            else -> {} // still resolving/downloading -- keep watching
                        }
                    }
                }
            }
        }

        checkStoragePermission()
        checkNotificationPermission()
        autoResumePendingDownloads()
        handleIncomingIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_MAIN_DESTINATION, mainDestination.name)
        savedPagesDestination?.let { destination ->
            outState.putString(STATE_SAVED_PAGES_DESTINATION, destination.name)
        }
        super.onSaveInstanceState(outState)
    }

    // ── Incoming links (external download-manager / share target) ──────────
    // Fires when: (a) a browser's download picker launches xmd for a VIEW
    // intent on a http(s) link (see the manifest intent-filter), or (b) a
    // link is Shared into xmd from a browser that doesn't have a
    // download-manager chooser (Chrome, Samsung Internet, Edge, ...).
    // singleTop means an already-running MainActivity gets onNewIntent
    // instead of a fresh onCreate, so both entry points are covered here.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent) {
        val url = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.toString()
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty().let { text ->
                if (text.startsWith("magnet:", ignoreCase = true)) text
                else Regex("""https?://\S+""").find(text)?.value
            }
            else -> null
        }?.trim()

        val isHttp = url != null && (url.startsWith("http://") || url.startsWith("https://"))
        val isMagnet = url != null && url.startsWith("magnet:", ignoreCase = true)
        if (url.isNullOrEmpty() || !(isHttp || isMagnet)) return

        // Consume it so rotation / re-entering onResume doesn't re-queue the
        // same link a second time.
        intent.action = null
        intent.data = null

        selectMainDestination(MainDestination.Downloads)

        // External download manager flow: show a popup allowing user to
        // copy/modify link, rename file, and change save folder in a
        // collapsible section. Torrents also show all files for selection.
        if (LinkParser.isTorrentLink(url)) {
            showAddTorrentDialog(prefillLink = url)
            return
        }

        val needsPrepare = LinkParser.isShareLink(url) || LinkParser.isFitgirlPage(url)
        if (needsPrepare) {
            triggerPrepare(listOf(url))
        } else {
            showAddDownloadDialog(url)
        }
    }

    /**
     * Items that were mid-download when the process died (phone restart,
     * app killed, etc.) get rolled back to READY by
     * QueueRepository.init()'s recovery logic -- but nothing was actually
     * re-launching DownloadService to pick them back up, so they just sat
     * at "Ready to download" forever with no live worker claiming them.
     * QueueRepository.init() (called from FfApp.onCreate, before this)
     * loads persisted state asynchronously off the main thread, so give it
     * a moment to land before checking -- this only needs to happen once
     * per process, not on every config change.
     */
    private fun autoResumePendingDownloads() {
        lifecycleScope.launch {
            delay(500)
            if (QueueRepository.current().any { it.status == ItemStatus.READY }) {
                DownloadService.start(this@MainActivity)
            }
        }
    }


    // ── Add Download / Torrent Dialogs ─────────────────────────────────────

    private fun defaultSavePath(): String = Settings.defaultSaveLocation()

    private fun resolveTreeUriToPath(treeUri: Uri): String? =
        com.invictus.xmd.utils.storage.StorageUtils.resolveTreeUriToPath(treeUri)

    private fun magnetDisplayName(link: String): String? {
        if (!LinkParser.isMagnetLink(link)) return null
        val dn = Regex("[?&]dn=([^&]+)").find(link)?.groupValues?.get(1) ?: return null
        return runCatching { Uri.decode(dn.replace('+', ' ')) }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun onTorrentFilePicked(uri: Uri) {
        val displayName = queryDisplayName(uri)
        if (displayName != null && !displayName.endsWith(".torrent", ignoreCase = true)) {
            Toast.makeText(this, R.string.torrent_file_invalid_type, Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        showAddTorrentDialog(prefillTorrentUri = uri, prefillDisplayName = displayName)
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
            }
        }.getOrNull()
    }

    fun showAddDownloadDialog(link: String? = null) {
        val trimmed = link?.trim().orEmpty()
        if (LinkParser.isTorrentLink(trimmed) && trimmed.contains("xt=", ignoreCase = true)) {
            showAddTorrentDialog(prefillLink = trimmed)
            return
        }
        addDownloadDialogState = AddDownloadDialogState(initialLink = trimmed)
    }

    fun showAddTorrentDialog(
        prefillLink: String? = null,
        prefillTorrentUri: Uri? = null,
        prefillDisplayName: String? = null
    ) {
        torrentMetadataJob?.cancel()
        addTorrentDialogState = AddTorrentDialogState(
            prefillLink = prefillLink,
            prefillTorrentUri = prefillTorrentUri,
            prefillDisplayName = prefillDisplayName,
            filesState = TorrentFilesUiState(),
        )
        when {
            prefillTorrentUri != null -> {
                lifecycleScope.launch {
                    val ti = runCatching {
                        contentResolver.openInputStream(prefillTorrentUri)?.use { it.readBytes() }
                            ?.let { TorrentInfo.bdecode(it) }
                    }.getOrNull()
                    if (ti != null) applyTorrentInfoToDialog(ti)
                }
            }
            !prefillLink.isNullOrBlank() && LinkParser.isMagnetLink(prefillLink) -> {
                loadTorrentMetadataForMagnet(prefillLink)
            }
        }
    }

    /**
     * Called on every AddTorrentDialog link-field edit (mirrors the old
     * linkInput.doAfterTextChanged). Re-fetches metadata for a newly-typed
     * magnet link, or clears any stale file list once the field no longer
     * holds one -- matches loadMetadataForMagnet()'s own early-return branch.
     */
    private fun onAddTorrentLinkChanged(link: String) {
        val state = addTorrentDialogState ?: return
        addTorrentDialogState = state.copy(prefillLink = link)
        if (state.prefillTorrentUri == null && LinkParser.isMagnetLink(link)) {
            loadTorrentMetadataForMagnet(link)
        } else if (!LinkParser.isMagnetLink(link)) {
            torrentMetadataJob?.cancel()
            addTorrentDialogState = addTorrentDialogState?.copy(filesState = TorrentFilesUiState())
        }
    }

    private fun loadTorrentMetadataForMagnet(link: String) {
        torrentMetadataJob?.cancel()
        addTorrentDialogState = addTorrentDialogState?.copy(filesState = TorrentFilesUiState(loading = true))
        torrentMetadataJob = lifecycleScope.launch {
            val tempDir = File(cacheDir, "torrent_meta")
            val bytes = withContext(Dispatchers.IO) {
                TorrentSession.fetchMetadata(link, timeoutSeconds = 25, tempDir)
            }
            val ti = bytes?.let { runCatching { TorrentInfo.bdecode(it) }.getOrNull() }
            if (ti != null) {
                applyTorrentInfoToDialog(ti)
            } else {
                addTorrentDialogState = addTorrentDialogState?.copy(filesState = TorrentFilesUiState(error = true))
            }
        }
    }

    private fun applyTorrentInfoToDialog(ti: TorrentInfo) {
        val count = ti.numFiles()
        val entries = (0 until count).map { idx ->
            TorrentFileRow(
                index = idx,
                path = runCatching { ti.files().filePath(idx) }.getOrNull() ?: "File ${idx + 1}",
                sizeBytes = runCatching { ti.files().fileSize(idx) }.getOrNull() ?: 0L,
                isSelected = true,
            )
        }
        val detectedName = ti.name().takeIf { it.isNotBlank() }
        addTorrentDialogState = addTorrentDialogState?.copy(
            filesState = TorrentFilesUiState(files = entries, magnetDetectedName = detectedName)
        )
    }

    private fun toggleTorrentFileSelection(index: Int) {
        val state = addTorrentDialogState ?: return
        val updated = state.filesState.files.map { if (it.index == index) it.copy(isSelected = !it.isSelected) else it }
        addTorrentDialogState = state.copy(filesState = state.filesState.copy(files = updated))
    }

    private fun toggleTorrentSelectAll() {
        val state = addTorrentDialogState ?: return
        val allSelected = state.filesState.files.isNotEmpty() && state.filesState.files.all { it.isSelected }
        val updated = state.filesState.files.map { it.copy(isSelected = !allSelected) }
        addTorrentDialogState = state.copy(filesState = state.filesState.copy(files = updated))
    }

    // ── Download Triggers ──────────────────────────────────────────────────

    override fun triggerPrepare(lines: List<String>) {
        lifecycleScope.launch {
            val expanded = try {
                withContext(Dispatchers.IO) { LinkParser.expandSources(lines, client) }
            } catch (e: ResolutionError) {
                Toast.makeText(this@MainActivity, e.message, Toast.LENGTH_LONG).show()
                return@launch
            }
            QueueRepository.setLinks(expanded)
            resolveAll()
        }
    }

    override fun triggerDownloadReady() {
        DownloadService.start(this)
        showDownloadStartedSnackbar()
    }

    override fun openDownloadsTab() {
        selectMainDestination(MainDestination.Downloads)
    }

    override fun triggerDownloadDirect(lines: List<String>) {
        QueueRepository.setLinks(lines)
        val (ytDlpLines, otherLines) = lines.partition { LinkParser.needsYtDlp(it) }

        otherLines.forEach { link ->
            val item = QueueRepository.current().firstOrNull { it.sourceUrl == link }
            if (item != null) {
                QueueRepository.update(item.id) { it.copy(directUrl = link, status = ItemStatus.READY) }
            }
        }
        if (otherLines.isNotEmpty()) {
            DownloadService.start(this)
            showDownloadStartedSnackbar()
        }

        if (ytDlpLines.isNotEmpty()) {
            val savedLabel = Settings.ytDlpDefaultQualityLabel()
            if (savedLabel.isNotBlank()) {
                lifecycleScope.launch {
                    for (link in ytDlpLines) {
                        val item = QueueRepository.current().firstOrNull { it.sourceUrl == link } ?: continue
                        QueueRepository.update(item.id) { it.copy(status = ItemStatus.RESOLVING) }
                        resolveOne(item)
                    }
                }
            } else {
                showAddDownloadDialog(ytDlpLines.first())
            }
        }
    }

    fun triggerDownloadDirectCustom(
        link: String,
        name: String?,
        customSaveDirPath: String?,
        duplicateStrategy: OnDuplicateStrategy? = null,
    ) {
        val category = CategoryDetector.detect(link, hint = name)
        val resolvedName = name?.takeUnless { it.isBlank() }
            ?: DownloadEngine.filenameFromLink(link).ifBlank { DownloadEngine.filenameFromUrl(link) }
        val targetFile = FileNameUtils.resolveDestinationFile(resolvedName, customSaveDirPath, category)

        val finalName = when (duplicateStrategy) {
            OnDuplicateStrategy.OverrideDownload -> {
                QueueRepository.removeDuplicatesOf(targetFile)
                if (targetFile.exists()) targetFile.delete()
                resolvedName
            }
            OnDuplicateStrategy.AddNumbered -> {
                val activeFiles = QueueRepository.current().mapNotNull { FileNameUtils.destinationFileOf(it) }.toSet()
                FileNameUtils.numberedNameIfExists(targetFile, activeFiles)
            }
            null -> {
                if (FileNameUtils.isDuplicate(targetFile, QueueRepository.current())) {
                    val activeFiles = QueueRepository.current().mapNotNull { FileNameUtils.destinationFileOf(it) }.toSet()
                    FileNameUtils.numberedNameIfExists(targetFile, activeFiles)
                } else {
                    resolvedName
                }
            }
        }

        val newItem = QueueItem(
            id = UUID.randomUUID().toString(),
            sourceUrl = link,
            directUrl = link,
            status = ItemStatus.READY,
            fileName = finalName,
            customSaveDirPath = customSaveDirPath,
            category = category,
        )
        QueueRepository.enqueue(newItem)
        DownloadService.start(this)
        showDownloadStartedSnackbar()
    }

    fun triggerDownloadYoutubeCustom(
        link: String,
        name: String?,
        customSaveDirPath: String?,
        chosenQuality: YtDlpManager.QualityOption?,
        chosenAudioPreset: Settings.AudioFormatPreset = Settings.presetAudioFormat(),
        duplicateStrategy: OnDuplicateStrategy? = null,
    ) {
        if (!BuildConfig.HAS_YOUTUBE_SUPPORT) {
            showMessageDialog(
                AppMessageDialogState(
                    title = getString(R.string.full_build_required_title),
                    message = getString(R.string.full_build_required_message),
                    confirmLabel = getString(android.R.string.ok),
                )
            )
            return
        }
        if (!YtDlpManager.isInstalled(this)) {
            showMessageDialog(
                AppMessageDialogState(
                    title = getString(R.string.ytdlp_not_installed_title),
                    message = getString(R.string.ytdlp_not_installed_message),
                    confirmLabel = getString(R.string.action_install_now),
                    dismissLabel = getString(android.R.string.cancel),
                    onConfirm = { openSettingsScreen(SettingsActivity.CATEGORY_YOUTUBE) },
                )
            )
            return
        }

        val quality = chosenQuality ?: run {
            val options = YtDlpManager.standardQualityOptions(isGenericOrHls = !LinkParser.isYoutubeLink(link))
            options.firstOrNull { it.label.startsWith("1080p") } ?: options.firstOrNull()
        }

        if (quality == null) {
            Toast.makeText(this, R.string.download_quality_unavailable, Toast.LENGTH_SHORT).show()
            return
        }

        if (quality.isAudioOnly) {
            Settings.setPresetAudioFormat(chosenAudioPreset)
        }

        val formatLabel = if (quality.isAudioOnly) {
            "Audio (${chosenAudioPreset.name})"
        } else {
            quality.label
        }

        val category = if (quality.isAudioOnly) DownloadCategory.MUSIC else DownloadCategory.VIDEOS
        val resolvedName = name?.takeUnless { it.isBlank() } ?: extractYoutubeFallbackName(link)
        val targetFile = FileNameUtils.resolveDestinationFile(resolvedName, customSaveDirPath, category)

        val finalName = when (duplicateStrategy) {
            OnDuplicateStrategy.OverrideDownload -> {
                QueueRepository.removeDuplicatesOf(targetFile)
                if (targetFile.exists()) targetFile.delete()
                resolvedName
            }
            OnDuplicateStrategy.AddNumbered -> {
                val activeFiles = QueueRepository.current().mapNotNull { FileNameUtils.destinationFileOf(it) }.toSet()
                FileNameUtils.numberedNameIfExists(targetFile, activeFiles)
            }
            null -> {
                if (FileNameUtils.isDuplicate(targetFile, QueueRepository.current())) {
                    val activeFiles = QueueRepository.current().mapNotNull { FileNameUtils.destinationFileOf(it) }.toSet()
                    FileNameUtils.numberedNameIfExists(targetFile, activeFiles)
                } else {
                    resolvedName
                }
            }
        }

        val newItem = QueueItem(
            id = UUID.randomUUID().toString(),
            sourceUrl = link,
            status = ItemStatus.READY,
            platform = MediaPlatform.YOUTUBE,
            mediaFormatSelector = quality.formatSelector,
            mediaFormatLabel = formatLabel,
            category = category,
            fileName = finalName,
            customSaveDirPath = customSaveDirPath,
        )
        QueueRepository.enqueue(newItem)
        DownloadService.start(this)
        showDownloadStartedSnackbar()
    }

    private fun extractYoutubeFallbackName(url: String): String {
        val clean = url.trim()
        val id = when {
            clean.contains("youtu.be/") -> clean.substringAfter("youtu.be/").substringBefore("?").substringBefore("/")
            clean.contains("/shorts/") -> clean.substringAfter("/shorts/").substringBefore("?").substringBefore("/")
            clean.contains("v=") -> Regex("""[?&]v=([^&]+)""").find(clean)?.groupValues?.get(1)
            else -> null
        }
        return if (!id.isNullOrBlank()) "YouTube ($id)" else "YouTube Video"
    }

    private fun probeYoutubeTitle(url: String): String? {
        return runCatching {
            val cleanUrl = url.trim()
            val encoded = URLEncoder.encode(cleanUrl, "UTF-8")
            val req = Request.Builder()
                .url("https://www.youtube.com/oembed?url=$encoded&format=json")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()
            filenameClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string() ?: return@use null
                JSONObject(body).optString("title").takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }

    fun triggerDownloadTorrentFile(
        uri: Uri,
        displayName: String?,
        customSaveDirPath: String?,
        selectedFileIndices: String?,
        duplicateStrategy: OnDuplicateStrategy? = null,
    ) {
        val link = uri.toString()
        val resolvedName = displayName?.takeUnless { it.isBlank() } ?: "Torrent Download"
        val category = CategoryDetector.detect(link, hint = resolvedName)
        val targetFile = FileNameUtils.resolveDestinationFile(resolvedName, customSaveDirPath, category)

        val finalName = when (duplicateStrategy) {
            OnDuplicateStrategy.OverrideDownload -> {
                QueueRepository.removeDuplicatesOf(targetFile)
                if (targetFile.exists()) targetFile.delete()
                resolvedName
            }
            OnDuplicateStrategy.AddNumbered -> {
                val activeFiles = QueueRepository.current().mapNotNull { FileNameUtils.destinationFileOf(it) }.toSet()
                FileNameUtils.numberedNameIfExists(targetFile, activeFiles)
            }
            null -> {
                if (FileNameUtils.isDuplicate(targetFile, QueueRepository.current())) {
                    val activeFiles = QueueRepository.current().mapNotNull { FileNameUtils.destinationFileOf(it) }.toSet()
                    FileNameUtils.numberedNameIfExists(targetFile, activeFiles)
                } else {
                    resolvedName
                }
            }
        }

        val newItem = QueueItem(
            id = UUID.randomUUID().toString(),
            sourceUrl = link,
            directUrl = link,
            status = ItemStatus.READY,
            fileName = finalName,
            customSaveDirPath = customSaveDirPath,
            selectedFileIndices = selectedFileIndices,
            category = category,
        )
        QueueRepository.enqueue(newItem)
        DownloadService.start(this)
        showDownloadStartedSnackbar()
    }

    /**
     * From the Editor dialog (showAddTorrentDialog) -- both the
     * manual "+" button and an incoming external magnet/.torrent link go
     * through here once the user hits Start, carrying whatever they
     * customized (rename, save-folder override, selected files) along with it.
     */
    fun triggerDownloadTorrentMagnet(
        link: String,
        name: String?,
        customSaveDirPath: String?,
        selectedFileIndices: String?,
        duplicateStrategy: OnDuplicateStrategy? = null,
    ) {
        val resolvedName = name?.takeUnless { it.isBlank() } ?: magnetDisplayName(link) ?: "Magnet Download"
        val category = CategoryDetector.detect(link, hint = resolvedName)
        val targetFile = FileNameUtils.resolveDestinationFile(resolvedName, customSaveDirPath, category)

        val finalName = when (duplicateStrategy) {
            OnDuplicateStrategy.OverrideDownload -> {
                QueueRepository.removeDuplicatesOf(targetFile)
                if (targetFile.exists()) targetFile.delete()
                resolvedName
            }
            OnDuplicateStrategy.AddNumbered -> {
                val activeFiles = QueueRepository.current().mapNotNull { FileNameUtils.destinationFileOf(it) }.toSet()
                FileNameUtils.numberedNameIfExists(targetFile, activeFiles)
            }
            null -> {
                if (FileNameUtils.isDuplicate(targetFile, QueueRepository.current())) {
                    val activeFiles = QueueRepository.current().mapNotNull { FileNameUtils.destinationFileOf(it) }.toSet()
                    FileNameUtils.numberedNameIfExists(targetFile, activeFiles)
                } else {
                    resolvedName
                }
            }
        }

        val newItem = QueueItem(
            id = UUID.randomUUID().toString(),
            sourceUrl = link,
            directUrl = link,
            status = ItemStatus.READY,
            fileName = finalName,
            customSaveDirPath = customSaveDirPath,
            selectedFileIndices = selectedFileIndices,
            category = category,
        )
        QueueRepository.enqueue(newItem)
        DownloadService.start(this)
        showDownloadStartedSnackbar()
    }

    /** Shows a Material 3 snackbar for download entry points outside the queue screen. */
    private fun showDownloadStartedSnackbar() {
        if (currentTabTag == TAG_DOWNLOADS) {
            return
        }
        lifecycleScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = getString(R.string.download_started_toast),
                actionLabel = getString(R.string.action_view),
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                selectMainDestination(MainDestination.Downloads)
            }
        }
    }

    // ── BrowserFragment.Callbacks ───────────────────────────────────────────

    override fun onBrowserMenuAction(action: BrowserMenuAction) {
        when (action) {
            BrowserMenuAction.PrivateDns -> showDnsSettingsDialog()
            BrowserMenuAction.Bookmarks -> openBookmarksScreen()
            BrowserMenuAction.History -> openHistoryScreen()
            BrowserMenuAction.Settings -> openSettingsScreen()
        }
    }

    override fun triggerSniffedMedia(url: String, needsPicker: Boolean) {
        if (needsPicker) {
            showAddDownloadDialog(url)
        } else {
            QueueRepository.setLinks(listOf(url))
            val item = QueueRepository.current().firstOrNull { it.sourceUrl == url } ?: return
            QueueRepository.update(item.id) { it.copy(directUrl = url, status = ItemStatus.READY) }
            DownloadService.start(this)
            showDownloadStartedSnackbar()
        }
    }

    /** Opens the Compose DnsSettingsDialog in the activity root composition.
     *  This used to build+show a MaterialAlertDialogBuilder
     *  wrapping dialog_dns_settings.xml right here; all of that now lives in
     *  ui/DnsSettingsDialog.kt, this function is just the open trigger. */
    private fun showDnsSettingsDialog() {
        dnsSettingsDialogOpen = true
    }

    private fun openHistoryScreen() {
        savedPagesDestination = SavedPagesDestination.History
    }

    private fun openBookmarksScreen() {
        savedPagesDestination = SavedPagesDestination.Bookmarks
    }

    override fun onBrowserHeaderInteractionChanged(locked: Boolean) {
        this.isBrowserHeaderLocked = locked
        updateViewPagerUserInputEnabled()
    }

    // ── DownloadsFragment.Callbacks ─────────────────────────────────────────

    override fun onDownloadsSelectionChanged(selectionState: DownloadsSelectionUiState?) {
        downloadsSelectionState = selectionState
        updateViewPagerUserInputEnabled()
    }

    override fun retryItem(itemId: String) {
        val item = QueueRepository.current().firstOrNull { it.id == itemId } ?: return
        pendingRetryIds.add(itemId)
        lifecycleScope.launch { retrySingle(item) }
    }

    override fun retryAll() {
        val failed = QueueRepository.current().filter { it.status == ItemStatus.FAILED }
        if (failed.isEmpty()) return
        lifecycleScope.launch {
            for ((index, item) in failed.withIndex()) {
                retrySingle(item)
                if (index + 1 < failed.size) delay(500)
            }
        }
    }

    /**
     * Resets a failed/cancelled item and retries it. Share links (FuckingFast
     * etc.) get a fresh resolve since the previously-resolved directUrl is a
     * short-lived CDN link that may have expired by the time Retry is tapped;
     * a plain direct URL has nothing to re-resolve, so it goes straight back
     * to READY and the download service picks it up immediately.
     */
    private suspend fun retrySingle(item: QueueItem) {
        // YouTube items only need a fresh resolve (quality picker) if a
        // quality was never actually chosen (e.g. the picker was dismissed
        // without a selection) -- once chosen, retry should just re-run
        // yt-dlp with the same quality rather than re-prompting.
        val needsResolve = LinkParser.isShareLink(item.sourceUrl) ||
            (LinkParser.needsYtDlp(item.sourceUrl) && item.mediaFormatSelector == null)
        QueueRepository.update(item.id) {
            it.copy(
                status = if (needsResolve) ItemStatus.RESOLVING else ItemStatus.READY,
                error = null,
                bytesDone = 0L,
                bytesTotal = 0L,
                speedBps = 0.0,
                directUrl = if (needsResolve) null else (it.directUrl ?: it.sourceUrl)
            )
        }
        if (needsResolve) {
            val refreshed = QueueRepository.current().first { it.id == item.id }
            resolveOne(refreshed)
        } else {
            DownloadService.start(this@MainActivity)
            showDownloadStartedSnackbar()
        }
    }

    /**
     * IDM-style prompt shown when a retried download comes back with an
     * expired/unavailable link: "Clear" drops the item entirely, "Fetch
     * Link" retries again (re-resolving from the share link if there is
     * one) -- looping back into this same check if it expires again.
     */
    private fun showExpiredLinkDialog(item: QueueItem) {
        showMessageDialog(
            AppMessageDialogState(
                title = getString(R.string.link_expired_title),
                message = getString(R.string.link_expired_message, item.fileName ?: item.sourceUrl),
                confirmLabel = getString(R.string.action_fetch_link),
                dismissLabel = getString(R.string.action_clear),
                onConfirm = { retryItem(item.id) },
                onDismissAction = {
                    pendingRetryIds.remove(item.id)
                    QueueRepository.removeItem(item.id)
                },
            )
        )
    }

    // ── Resolve logic (uses challengeLauncher — must live in Activity) ────

    private suspend fun resolveAll() {
        val items = QueueRepository.current().filter { it.status == ItemStatus.PENDING }
        for ((index, item) in items.withIndex()) {
            QueueRepository.update(item.id) { it.copy(status = ItemStatus.RESOLVING) }
            resolveOne(item)
            if (index + 1 < items.size) delay(500)
        }
    }

    private suspend fun resolveOne(item: QueueItem) {
        if (LinkParser.needsYtDlp(item.sourceUrl)) {
            resolveYoutube(item)
            return
        }
        if (LinkParser.isGenericDownloadUrl(item.sourceUrl)) {
            QueueRepository.update(item.id) {
                it.copy(directUrl = item.sourceUrl, status = ItemStatus.READY)
            }
            // Same as the share-link branch below: without this, an item that
            // becomes READY after the worker pool has already exhausted the
            // queue (or was never started) sits at READY forever -- no live
            // worker left to claim it. This branch was missing the call,
            // which is why plain direct-download links (pixeldrain, hubcloud
            // generated links, etc.) got stuck on "Ready to download" while
            // share links (which do call this) downloaded fine.
            DownloadService.start(this@MainActivity)
            showDownloadStartedSnackbar()
            return
        }
        if (!LinkParser.isShareLink(item.sourceUrl)) {
            QueueRepository.update(item.id) {
                it.copy(status = ItemStatus.FAILED, error = "Not a valid URL: ${item.sourceUrl}")
            }
            return
        }
        val fileId = try {
            LinkParser.fileId(item.sourceUrl)
        } catch (e: ResolutionError) {
            QueueRepository.update(item.id) { it.copy(status = ItemStatus.FAILED, error = e.message) }
            return
        }
        QueueRepository.update(item.id) { it.copy(status = ItemStatus.NEEDS_CHALLENGE) }

        val (directUrl, error) = suspendCancellableCoroutine<Pair<String?, String?>> { cont ->
            val continuation: (String?, String?) -> Unit = { url, err ->
                if (cont.isActive) cont.resume(url to err)
            }
            pendingChallengeContinuation = continuation
            cont.invokeOnCancellation {
                if (pendingChallengeContinuation === continuation) {
                    pendingChallengeContinuation = null
                }
            }
            val intent = Intent(this@MainActivity, ChallengeActivity::class.java)
                .putExtra(ChallengeActivity.EXTRA_SHARE_URL, item.sourceUrl)
                .putExtra(ChallengeActivity.EXTRA_FILE_ID,  fileId)
            challengeLauncher.launch(intent)
        }
        if (directUrl != null) {
            QueueRepository.update(item.id) { it.copy(directUrl = directUrl, status = ItemStatus.READY) }
            // If downloads are already running (or were started earlier and ran out of
            // READY items), this item would otherwise sit at READY with no worker left
            // to claim it. Re-poking the service tops workers back up to the configured
            // max so a newly-resolved link starts downloading right away.
            DownloadService.start(this@MainActivity)
            showDownloadStartedSnackbar()
        } else {
            QueueRepository.update(item.id) {
                it.copy(status = ItemStatus.FAILED, error = error ?: "Could not resolve link")
            }
        }
    }

    // ── yt-dlp resolve (quality picker; also handles direct HLS/DASH links,
    // not just YouTube -- see LinkParser.needsYtDlp) ──────────────────────

    /**
     * YouTube (and plain HLS/DASH manifest) items skip the FuckingFast
     * challenge/resolve pipeline entirely -- instead of a directUrl, the
     * user picks a quality here and yt-dlp (DownloadService) resolves +
     * downloads + merges it itself later. Named for its original
     * YouTube-only case; LinkParser.needsYtDlp now also routes plain
     * .m3u8/.mpd links here since yt-dlp handles those the same way,
     * segments fetched and muxed into one mp4 rather than downloaded as
     * the raw manifest text.
     */
    private suspend fun resolveYoutube(item: QueueItem) {
        if (!BuildConfig.HAS_YOUTUBE_SUPPORT) {
            showMessageDialog(
                AppMessageDialogState(
                    title = getString(R.string.full_build_required_title),
                    message = getString(R.string.full_build_required_message),
                    confirmLabel = getString(android.R.string.ok),
                )
            )
            QueueRepository.update(item.id) {
                it.copy(status = ItemStatus.FAILED, error = "Needs the Full build")
            }
            return
        }
        if (!YtDlpManager.isInstalled(this)) {
            val openSettings = suspendCancellableCoroutine<Boolean> { cont ->
                val complete: (Boolean) -> Unit = { result ->
                    if (cont.isActive) cont.resume(result)
                }
                val state = AppMessageDialogState(
                    title = getString(R.string.ytdlp_not_installed_title),
                    message = getString(R.string.ytdlp_not_installed_message),
                    confirmLabel = getString(R.string.action_install_now),
                    dismissLabel = getString(android.R.string.cancel),
                    onConfirm = { complete(true) },
                    onDismiss = { complete(false) },
                )
                showMessageDialog(state)
                cont.invokeOnCancellation {
                    if (messageDialogState === state) messageDialogState = null
                }
            }
            QueueRepository.update(item.id) {
                it.copy(status = ItemStatus.FAILED, error = "yt-dlp not installed")
            }
            if (openSettings) openSettingsScreen(SettingsActivity.CATEGORY_YOUTUBE)
            return
        }

        val options = YtDlpManager.standardQualityOptions(
            isGenericOrHls = !LinkParser.isYoutubeLink(item.sourceUrl)
        )

        val savedLabel = Settings.ytDlpDefaultQualityLabel()
        val chosen = if (savedLabel.isNotBlank()) {
            // Exact match first; the "Audio only (…)" entry's suffix now
            // tracks Settings.presetAudioFormat() (used to be hardcoded to
            // "(MP3)"), so a default saved before switching format presets
            // won't match verbatim -- fall back to isAudioOnly so it still
            // resolves to the (now-relabeled) audio-only rung instead of
            // silently reverting to "Ask always".
            options.firstOrNull { it.label == savedLabel }
                ?: options.firstOrNull { it.isAudioOnly && savedLabel.startsWith("Audio only") }
        } else {
            showAddDownloadDialog(item.sourceUrl)
            return
        }

        if (chosen == null) {
            QueueRepository.update(item.id) {
                it.copy(status = ItemStatus.FAILED, error = "Cancelled")
            }
            return
        }

        QueueRepository.update(item.id) {
            it.copy(
                status = ItemStatus.READY,
                platform = MediaPlatform.YOUTUBE,
                mediaFormatSelector = chosen.formatSelector,
                mediaFormatLabel = chosen.label,
                category = if (chosen.isAudioOnly) DownloadCategory.MUSIC else DownloadCategory.VIDEOS
            )
        }
        // Same as the other resolve branches: top workers back up so this
        // starts downloading right away instead of sitting at READY until
        // the next unrelated ACTION_START.
        DownloadService.start(this@MainActivity)
    }

    // ── Storage permission ────────────────────────────────────────────────

    private fun checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                showMessageDialog(
                    AppMessageDialogState(
                        title = getString(R.string.storage_permission_title),
                        message = getString(R.string.storage_permission_message),
                        confirmLabel = getString(R.string.action_allow),
                        dismissLabel = getString(android.R.string.cancel),
                        onConfirm = {
                            startActivity(
                                Intent(
                                    AndroidSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.fromParts("package", packageName, null)
                                )
                            )
                        },
                        onDismissAction = {
                            Toast.makeText(
                                this,
                                R.string.storage_permission_denied,
                                Toast.LENGTH_LONG,
                            ).show()
                        },
                    )
                )
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    // ── Notification permission ─────────────────────────────────────────

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /**
     * Opens the dedicated Settings screen (replaces the old in-place
     * dialog). All the *screen* logic that used to live here -- connections
     * & speed, download behavior, YouTube quality presets, yt-dlp
     * install/update, and website import -- now lives in SettingsActivity
     * and its category fragments. Dark mode and the theme picker still have
     * a presence here too (see toggleDarkMode() below), since the toolbar
     * title tap needs to flip dark mode on *this* Activity directly.
     */
    private fun openSettingsScreen(category: String? = null) {
        startActivity(Intent(this, SettingsActivity::class.java).apply {
            if (category != null) putExtra(SettingsActivity.EXTRA_OPEN_CATEGORY, category)
        })
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    /**
     * Flips dark/light mode for whichever color theme is currently active --
     * triggered by tapping the toolbar title. Same pattern as the
     * duplicate in SettingsActivity's AppearanceRoute onDarkModeChanged
     * (used there for the Dark Mode switch in Settings > Appearance): save the pick,
     * toast the new mode, then recreate() since a theme is only read in
     * onCreate(), before super.onCreate(). Two copies exist because each
     * needs to recreate() its *own* Activity instance.
     */
    private fun toggleDarkMode() {
        val nowDark = !Settings.isDarkMode()
        Settings.setDarkMode(nowDark)
    }


    // ── Constants ─────────────────────────────────────────────────────────

    companion object {
        private const val SYSTEM_BAR_THEME_SWITCH_PROGRESS = 0.55f
        private const val TAG_HOME      = "home"
        private const val TAG_BROWSER   = "browser"
        private const val TAG_DOWNLOADS = "downloads"
        private const val STATE_SAVED_PAGES_DESTINATION = "saved_pages_destination"
        private const val STATE_MAIN_DESTINATION = "main_destination"
    }
}
