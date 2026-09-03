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
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.invictus.xmd.core.Bookmark
import com.invictus.xmd.core.BookmarkRepository
import com.invictus.xmd.core.HistoryEntry
import com.invictus.xmd.core.HistoryRepository
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.invictus.xmd.R
import com.invictus.xmd.BuildConfig
import com.invictus.xmd.core.DnsOverHttpsResolver
import com.invictus.xmd.core.DownloadCategory
import com.invictus.xmd.core.ItemStatus
import com.invictus.xmd.core.LinkParser
import com.invictus.xmd.core.MediaPlatform
import com.invictus.xmd.core.QueueItem
import com.invictus.xmd.core.QueueRepository
import com.invictus.xmd.core.ResolutionError
import com.invictus.xmd.core.Settings
import com.invictus.xmd.core.YtDlpManager
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
import android.content.res.ColorStateList
import android.graphics.Color
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.text.TextWatcher
import android.text.Editable
import android.widget.ImageView
import androidx.activity.result.ActivityResultLauncher
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.invictus.xmd.core.DownloadEngine
import com.invictus.xmd.core.TorrentSession
import kotlinx.coroutines.Job
import org.libtorrent4j.TorrentInfo
import android.graphics.Typeface
import androidx.core.view.isVisible
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class MainActivity : AppCompatActivity(), DownloadsFragment.Callbacks, BrowserFragment.Callbacks {

    data class NavMenuItem(val itemId: Int)

    inner class ExpressiveNavBar(
        val layout: View,
        val downloadsItem: View,
        val browserItem: View,
        val downloadsIcon: ImageView,
        val downloadsLabel: TextView,
        val downloadsBadge: TextView,
        val browserIcon: ImageView,
        val browserLabel: TextView,
        val addFab: View
    ) {
        private var itemSelectedListener: ((NavMenuItem) -> Boolean)? = null

        var selectedItemId: Int = R.id.nav_downloads
            set(value) {
                val changed = field != value
                field = value
                updateVisuals(value)
                if (changed) {
                    itemSelectedListener?.invoke(NavMenuItem(value))
                }
            }

        var visibility: Int
            get() = layout.visibility
            set(value) { layout.visibility = value }

        val height: Int
            get() = layout.height

        init {
            downloadsItem.setOnClickListener {
                selectedItemId = R.id.nav_downloads
            }
            browserItem.setOnClickListener {
                selectedItemId = R.id.nav_browser
            }
            addFab.setOnClickListener {
                showAddDownloadDialog()
            }
            updateVisuals(selectedItemId)
        }

        fun setOnItemSelectedListener(listener: (NavMenuItem) -> Boolean) {
            itemSelectedListener = listener
        }

        fun updateBadge(count: Int) {
            if (count > 0) {
                downloadsBadge.visibility = View.VISIBLE
                downloadsBadge.text = if (count > 99) "99+" else count.toString()
            } else {
                downloadsBadge.visibility = View.GONE
            }
        }

        fun updateVisuals(selectedId: Int) {
            val isDownloads = selectedId == R.id.nav_downloads

            val activeBg = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_nav_item_active)
            val inactiveBg = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_nav_item_inactive)

            val colorActive = MaterialColors.getColor(
                this@MainActivity,
                com.google.android.material.R.attr.colorOnSecondaryContainer,
                Color.BLACK
            )
            val colorInactive = MaterialColors.getColor(
                this@MainActivity,
                com.google.android.material.R.attr.colorOnSurfaceVariant,
                Color.GRAY
            )

            // Only show icon on the current (active) tab, like Google Photos
            val density = resources.displayMetrics.density
            val iconMargin = (8 * density).toInt()

            downloadsIcon.visibility = if (isDownloads) View.VISIBLE else View.GONE
            (downloadsLabel.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                lp.marginStart = if (isDownloads) iconMargin else 0
                downloadsLabel.layoutParams = lp
            }

            browserIcon.visibility = if (!isDownloads) View.VISIBLE else View.GONE
            (browserLabel.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                lp.marginStart = if (!isDownloads) iconMargin else 0
                browserLabel.layoutParams = lp
            }

            downloadsItem.background = if (isDownloads) activeBg else inactiveBg
            downloadsIcon.imageTintList = ColorStateList.valueOf(if (isDownloads) colorActive else colorInactive)
            downloadsLabel.setTextColor(if (isDownloads) colorActive else colorInactive)
            downloadsLabel.setTypeface(null, if (isDownloads) Typeface.BOLD else Typeface.NORMAL)

            browserItem.background = if (!isDownloads) activeBg else inactiveBg
            browserIcon.imageTintList = ColorStateList.valueOf(if (!isDownloads) colorActive else colorInactive)
            browserLabel.setTextColor(if (!isDownloads) colorActive else colorInactive)
            browserLabel.setTypeface(null, if (!isDownloads) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    private lateinit var bottomNav: ExpressiveNavBar
    private lateinit var toolbar: androidx.appcompat.widget.Toolbar
    private lateinit var toolbarTitle: TextView
    private lateinit var headerNormalLayout: View
    private lateinit var headerSearchLayout: View
    private lateinit var headerSearchInput: EditText
    private lateinit var headerSearchClearButton: View
    // Phase 5 (Browser): composition root for Compose dialogs owned by
    // MainActivity rather than BrowserFragment -- deliberately touches this
    // Phase-6 file ahead of schedule, see COMPOSE_MIGRATION.md's
    // DnsSettingsDialog writeup for why. Same bare-composition-root pattern
    // BrowserFragment's browserDialogHost established (match_parent because
    // AlertDialog renders in its own Dialog window regardless of this
    // host's own bounds).
    private lateinit var mainDialogHost: androidx.compose.ui.platform.ComposeView
    // Compose State so mainDialogHost's setContent lambda recomposes when
    // this flips -- same `by mutableStateOf` pattern BrowserFragment uses
    // for sniffedSheetStreams/suggestionItems (see that file's comments for
    // the extension-function-import lesson this relies on).
    private var dnsSettingsDialogOpen: Boolean by mutableStateOf(false)

    // Phase D: History/Bookmarks overlay -- replaces the old
    // HistoryFragment/BookmarkFragment pushed onto fragmentContainer via
    // supportFragmentManager + addToBackStack. Own ComposeView (see
    // overlayNavHost in activity_main.xml) rather than a branch in
    // mainDialogHost, since these are full screens, not Dialog-window
    // popups -- same reasoning AddressBarSuggestions/DnsSettingsDialog
    // split followed in Phase 5/A.
    private lateinit var overlayNavHost: androidx.compose.ui.platform.ComposeView
    private lateinit var overlayNavController: androidx.navigation.NavHostController

    // Phase A: state for the 3 dialogs converted this phase, same
    // `by mutableStateOf` + null-means-closed pattern as dnsSettingsDialogOpen.
    private data class AddDownloadDialogState(val initialLink: String)

    private data class AddTorrentDialogState(
        val prefillLink: String?,
        val prefillTorrentUri: Uri?,
        val prefillDisplayName: String?,
        val filesState: TorrentFilesUiState,
    )

    private data class QualityPickerState(
        val titleText: String,
        val standardOptions: List<YtDlpManager.QualityOption>,
        val advancedFormats: List<YtDlpManager.ProbedFormat>,
        val advancedLoading: Boolean,
        val durationSeconds: Int?,
        val onConfirm: (YtDlpManager.QualityOption?) -> Unit,
        val onDismiss: () -> Unit,
    )

    private var addDownloadDialogState: AddDownloadDialogState? by mutableStateOf(null)
    private var addTorrentDialogState: AddTorrentDialogState? by mutableStateOf(null)
    private var qualityPickerState: QualityPickerState? by mutableStateOf(null)
    private var torrentMetadataJob: Job? = null

    private fun openHeaderSearch() {
        headerNormalLayout.visibility = View.GONE
        headerSearchLayout.visibility = View.VISIBLE
        headerSearchInput.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(headerSearchInput, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun closeHeaderSearch() {
        if (::headerSearchLayout.isInitialized && headerSearchLayout.visibility == View.VISIBLE) {
            headerSearchInput.text?.clear()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(headerSearchInput.windowToken, 0)
            headerSearchLayout.visibility = View.GONE
            headerNormalLayout.visibility = View.VISIBLE
            (supportFragmentManager.findFragmentByTag(TAG_DOWNLOADS) as? DownloadsFragment)?.setFilterQuery("")
        }
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

    private val bottomNavSwipeOrder = listOf(R.id.nav_downloads, R.id.nav_browser)

    private val bottomNavSwipeDetector by lazy {
        val minDistancePx = 80 * resources.displayMetrics.density
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                if (kotlin.math.abs(dx) < minDistancePx) return false
                if (kotlin.math.abs(dx) < kotlin.math.abs(dy) * 2) return false
                val currentIndex = bottomNavSwipeOrder.indexOf(bottomNav.selectedItemId)
                if (currentIndex == -1) return false
                val step = if (dx < 0) 1 else -1
                val newIndex = (currentIndex + step).coerceIn(0, bottomNavSwipeOrder.size - 1)
                if (newIndex != currentIndex) bottomNav.selectedItemId = bottomNavSwipeOrder[newIndex]
                return true
            }
        })
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (::bottomNav.isInitialized) bottomNavSwipeDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

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
                "Storage permission denied — downloads will fail.",
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
                "Notifications denied — you won't see download progress.",
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
     * hidden/shown from inside bottomNav's tap listener (see onCreate) --
     * that's fine while the process stays alive, but if Android kills this
     * activity in the background (common on low battery / low memory) and
     * the user reopens it from Recents, onCreate reruns and the toolbar's
     * view is recreated at its default (visible) state, while
     * BottomNavigationView restores its selected tab on its own without
     * ever calling that listener -- so it was possible to come back to the
     * Browser tab with the "Xmd" toolbar wrongly showing above the
     * Browser's own address bar. Calling this every onResume (not just
     * after a fresh process start) covers both cases cheaply.
     */
    override fun onResume() {
        super.onResume()
        // Settings (a separate Activity) may have changed the color theme
        // or dark/light mode while this Activity was stopped underneath
        // it -- setTheme() only applies pre-onCreate, so the only way to
        // repaint with the new theme is to recreate() once we notice it
        // no longer matches what onCreate() applied. The recreate() itself
        // re-runs onCreate() (which updates appliedThemeStyleRes) then
        // onResume() again, so the check below just passes through to
        // syncToolbarWithVisibleFragment() on that second pass.
        if (Settings.appTheme().storageKey != appliedThemeKey ||
            Settings.isDarkMode() != appliedIsDark ||
            Settings.isAmoledMode() != appliedIsAmoled) {
            recreate()
            return
        }
        syncToolbarWithVisibleFragment()
    }

    private fun syncToolbarWithVisibleFragment() {
        val fm = supportFragmentManager
        val browserVisible = fm.findFragmentByTag(TAG_BROWSER)?.isHidden == false
        if (browserVisible) {
            closeHeaderSearch()
            // The Browser fragment's own address bar is the top bar here --
            // the shared app toolbar (and its title) would just duplicate it.
            toolbar.visibility = android.view.View.GONE
            currentTabTag = TAG_BROWSER
            return
        }
        toolbar.visibility = android.view.View.VISIBLE
        toolbarTitle.text = getString(R.string.app_header_title)
        currentTabTag = TAG_DOWNLOADS
    }

    private fun applySystemBarColors() {
        val isDark = Settings.isDarkMode()
        val isAmoled = isDark && Settings.isAmoledMode()

        // Status bar must match the header (colorSurfaceContainerLow)
        val headerColor = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorSurfaceContainerLow,
            Color.BLACK
        )
        // Navigation bar matches the body background (pure black in AMOLED mode)
        val navBarColor = if (isAmoled) Color.BLACK else MaterialColors.getColor(
            this,
            android.R.attr.colorBackground,
            Color.BLACK
        )
        window.statusBarColor = headerColor
        window.navigationBarColor = navBarColor

        val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDark
        insetsController.isAppearanceLightNavigationBars = !isDark
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
        setContentView(R.layout.activity_main)
        applySystemBarColors()

        mainDialogHost = findViewById(R.id.mainDialogHost)
        mainDialogHost.setViewCompositionStrategy(
            androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        mainDialogHost.setContent {
            com.invictus.xmd.ui.theme.XmdTheme {
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
                                clipboardManager.setPrimaryClip(ClipData.newPlainText("Download link", text))
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
                        onDismiss = { addDownloadDialogState = null },
                        onStart = { link, name, saveDir, quality, audioFormat ->
                            addDownloadDialogState = null
                            when {
                                LinkParser.isTorrentLink(link) -> showAddTorrentDialog(prefillLink = link)
                                LinkParser.isShareLink(link) || LinkParser.isFitgirlPage(link) ->
                                    triggerPrepare(listOf(link))
                                LinkParser.needsYtDlp(link) ->
                                    triggerDownloadYoutubeCustom(link, name, saveDir, quality, audioFormat)
                                else -> triggerDownloadDirectCustom(link, name, saveDir)
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
                                clipboardManager.setPrimaryClip(ClipData.newPlainText("Magnet link", text))
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

                qualityPickerState?.let { state ->
                    QualityPickerDialog(
                        titleText = state.titleText,
                        standardOptions = state.standardOptions,
                        advancedFormats = state.advancedFormats,
                        advancedLoading = state.advancedLoading,
                        durationSeconds = state.durationSeconds,
                        onDismiss = state.onDismiss,
                        onConfirm = state.onConfirm,
                    )
                }
            }
        }

        setUpOverlayNavHost()

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        this.toolbar = toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        val toolbarTitle = findViewById<TextView>(R.id.toolbarTitle)
        this.toolbarTitle = toolbarTitle
        toolbarTitle.text = getString(R.string.app_header_title)
        toolbarTitle.setOnClickListener { toggleDarkMode() }

        headerNormalLayout = findViewById(R.id.headerNormalLayout)
        headerSearchLayout = findViewById(R.id.headerSearchLayout)
        headerSearchInput = findViewById(R.id.headerSearchInput)
        headerSearchClearButton = findViewById(R.id.headerSearchClearButton)

        val headerSearchButton = findViewById<View>(R.id.headerSearchButton)
        val headerSettingsButton = findViewById<View>(R.id.headerSettingsButton)
        val headerSearchBackButton = findViewById<View>(R.id.headerSearchBackButton)

        headerSearchButton.setOnClickListener { openHeaderSearch() }
        headerSettingsButton.setOnClickListener { openSettingsScreen() }
        headerSearchBackButton.setOnClickListener { closeHeaderSearch() }
        headerSearchClearButton.setOnClickListener { headerSearchInput.text?.clear() }

        headerSearchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString().orEmpty()
                headerSearchClearButton.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                (supportFragmentManager.findFragmentByTag(TAG_DOWNLOADS) as? DownloadsFragment)?.setFilterQuery(query)
            }
        })

        // Add fragments only on a fresh start (not after config-change)
        if (savedInstanceState == null) {
            val downloads = DownloadsFragment()
            val browser   = BrowserFragment()
            supportFragmentManager.beginTransaction()
                .add(R.id.fragmentContainer, downloads, TAG_DOWNLOADS)
                .add(R.id.fragmentContainer, browser,   TAG_BROWSER)
                .hide(browser)   // Downloads is the initial tab
                .commit()
        }

        val navBarLayout = findViewById<View>(R.id.navBarLayout)
        bottomNav = ExpressiveNavBar(
            layout = navBarLayout,
            downloadsItem = findViewById(R.id.navItemDownloads),
            browserItem = findViewById(R.id.navItemBrowser),
            downloadsIcon = findViewById(R.id.navDownloadsIcon),
            downloadsLabel = findViewById(R.id.navDownloadsLabel),
            downloadsBadge = findViewById(R.id.navDownloadsBadge),
            browserIcon = findViewById(R.id.navBrowserIcon),
            browserLabel = findViewById(R.id.navBrowserLabel),
            addFab = findViewById(R.id.navAddFab)
        )

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(navBarLayout) { view, insets ->
            val navBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            val basePaddingBottom = (12 * resources.displayMetrics.density).toInt()
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                navBars.bottom + basePaddingBottom
            )
            insets
        }

        // adjustResize needs somewhere for the keyboard's shrink to go so an
        // EditText near the bottom (e.g. the browser's find-in-page bar or
        // address bar) isn't left sitting underneath the keyboard -- so
        // contentColumn's own bottom padding is set to the IME height while
        // it's open, and back to 0 once it closes. (bottomNav itself is
        // handled separately below -- see the global-layout listener.)
        val contentColumn = findViewById<android.view.View>(R.id.contentColumn)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(contentColumn) { view, insets ->
            val imeHeight = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, imeHeight)
            insets
        }

        // Hide bottomNav entirely while the soft keyboard is open (paste-links
        // box, browser find-in-page, address bar, any dialog EditText, etc.)
        // instead of leaving it floating right above the keys. Covers every
        // fragment from one place since it lives on the Activity's root view.
        val rootContentView = findViewById<android.view.View>(android.R.id.content)
        val fragmentContainer = findViewById<android.view.View>(R.id.fragmentContainer)
        rootContentView.viewTreeObserver.addOnGlobalLayoutListener {
            val visibleFrame = android.graphics.Rect()
            rootContentView.getWindowVisibleDisplayFrame(visibleFrame)
            val rootHeight = rootContentView.rootView.height
            val keyboardHeight = rootHeight - visibleFrame.bottom
            val keyboardOpen = rootHeight > 0 && keyboardHeight > rootHeight * 0.15
            bottomNav.visibility = if (keyboardOpen) android.view.View.GONE else android.view.View.VISIBLE

            if (bottomNav.height > 0) {
                fragmentContainer.setPadding(
                    fragmentContainer.paddingLeft,
                    fragmentContainer.paddingTop,
                    fragmentContainer.paddingRight,
                    if (keyboardOpen) 0 else bottomNav.height
                )
            }
        }

        bottomNav.setOnItemSelectedListener { item ->
            if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
            }
            when (item.itemId) {
                R.id.nav_downloads -> {
                    currentTabTag = TAG_DOWNLOADS
                    showFragment(TAG_DOWNLOADS)
                    toolbar.visibility = android.view.View.VISIBLE
                    toolbarTitle.text = getString(R.string.app_header_title)
                    invalidateOptionsMenu()
                    true
                }
                R.id.nav_add -> {
                    showAddDownloadDialog()
                    false
                }
                R.id.nav_browser -> {
                    currentTabTag = TAG_BROWSER
                    showFragment(TAG_BROWSER)
                    toolbar.visibility = android.view.View.GONE
                    invalidateOptionsMenu()
                    true
                }
                else -> false
            }
        }

        // Back handling, gesture or button:
        //  1. History/Bookmarks overlay open (Phase D) -> pop its own stack.
        //  2. Browser tab with page history / a loaded page -> step back through it.
        //  3. Browser tab -> jump to Downloads tab first before exiting.
        //  4. Already on Downloads tab -> exit the app.
        onBackPressedDispatcher.addCallback(this) {
            if (::headerSearchLayout.isInitialized && headerSearchLayout.visibility == View.VISIBLE) {
                closeHeaderSearch()
                return@addCallback
            }
            // Overlay's own back stack first -- checked ahead of
            // supportFragmentManager's, since History/Bookmarks no longer
            // live there (Phase D).
            if (::overlayNavController.isInitialized && overlayNavHost.visibility == View.VISIBLE) {
                overlayNavController.popBackStack()
                return@addCallback
            }
            if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStack()
                return@addCallback
            }
            val browser = supportFragmentManager.findFragmentByTag(TAG_BROWSER) as? BrowserFragment
            if (browser?.isVisible == true && browser.onBackPressed()) {
                return@addCallback
            }
            if (bottomNav.selectedItemId != R.id.nav_downloads) {
                bottomNav.selectedItemId = R.id.nav_downloads
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
                    bottomNav.updateBadge(active)
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

        bottomNav.selectedItemId = R.id.nav_downloads

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

    // ── Fragment switching ─────────────────────────────────────────────────

    private fun showFragment(tag: String) {
        val fm        = supportFragmentManager
        val browser   = fm.findFragmentByTag(TAG_BROWSER)   ?: return
        val downloads = fm.findFragmentByTag(TAG_DOWNLOADS) ?: return
        fm.beginTransaction().apply {
            when (tag) {
                TAG_BROWSER -> { show(browser); hide(downloads) }
                else        -> { hide(browser); show(downloads) }
            }
        }.commit()
    }

    // ── Add Download / Torrent Dialogs ─────────────────────────────────────

    private fun defaultSavePath(): String = if (Settings.saveToDownloadsFolder()) {
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
    } else {
        File(Environment.getExternalStorageDirectory(), "Xmd").absolutePath
    }

    private fun resolveTreeUriToPath(treeUri: Uri): String? {
        return runCatching {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val parts = docId.split(":", limit = 2)
            val volume = parts.getOrNull(0)
            val relativePath = parts.getOrNull(1).orEmpty()
            if (!volume.equals("primary", ignoreCase = true)) return@runCatching null
            val base = Environment.getExternalStorageDirectory()
            (if (relativePath.isBlank()) base else File(base, relativePath)).absolutePath
        }.getOrNull()
    }

    private fun magnetDisplayName(link: String): String? {
        if (!LinkParser.isMagnetLink(link)) return null
        val dn = Regex("[?&]dn=([^&]+)").find(link)?.groupValues?.get(1) ?: return null
        return runCatching { Uri.decode(dn.replace('+', ' ')) }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun onTorrentFilePicked(uri: Uri) {
        val displayName = queryDisplayName(uri)
        if (displayName != null && !displayName.endsWith(".torrent", ignoreCase = true)) {
            Toast.makeText(this, "Please select a .torrent file", Toast.LENGTH_SHORT).show()
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

    /**
     * Sizes the popup dialog to 90% of the phone screen width in portrait,
     * while enforcing a maximum width of 500dp in landscape and on tablets
     * so it never stretches awkwardly edge-to-edge on large displays.
     */
    private fun applyResponsiveDialogWidth(dialog: androidx.appcompat.app.AlertDialog) {
        val window = dialog.window ?: return
        val dm = resources.displayMetrics
        val screenWidth = dm.widthPixels
        val screenHeight = dm.heightPixels

        val cornerRadiusPx = 28 * dm.density
        val surfaceColor = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorSurface,
            Color.WHITE
        )
        val shapeDrawable = MaterialShapeDrawable(
            ShapeAppearanceModel.builder()
                .setAllCornerSizes(cornerRadiusPx)
                .build()
        ).apply {
            fillColor = ColorStateList.valueOf(surfaceColor)
            elevation = 6 * dm.density
        }
        window.setBackgroundDrawable(shapeDrawable)
        window.decorView.clipToOutline = true

        val maxWidthPx = (500 * dm.density).toInt()

        val isLandscape = screenWidth > screenHeight
        val desiredWidth = if (isLandscape) {
            (screenWidth * 0.60f).toInt().coerceAtMost(maxWidthPx)
        } else {
            (screenWidth * 0.90f).toInt().coerceAtMost(maxWidthPx)
        }

        val lp = window.attributes
        lp.width = desiredWidth
        window.attributes = lp
        window.setLayout(desiredWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
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

    fun triggerDownloadReady() {
        DownloadService.start(this)
        showDownloadStartedSnackbar()
    }

    fun openDownloadsTab() {
        bottomNav.selectedItemId = R.id.nav_downloads
    }

    fun triggerDownloadDirect(lines: List<String>) {
        QueueRepository.setLinks(lines)
        val (youtubeLines, otherLines) = lines.partition { LinkParser.needsYtDlp(it) }

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

        // YouTube links skip directUrl/READY entirely -- they need the
        // quality-picker dialog first (see resolveYoutube), same as if they'd
        // gone through resolveAll(). DownloadService.start() for these fires
        // from inside resolveYoutube() itself, once a quality is actually
        // picked, not here.
        if (youtubeLines.isNotEmpty()) {
            lifecycleScope.launch {
                for (link in youtubeLines) {
                    val item = QueueRepository.current().firstOrNull { it.sourceUrl == link } ?: continue
                    QueueRepository.update(item.id) { it.copy(status = ItemStatus.RESOLVING) }
                    resolveOne(item)
                }
            }
        }
    }

    fun triggerDownloadDirectCustom(link: String, name: String?, customSaveDirPath: String?) {
        QueueRepository.setLinks(listOf(link))
        val item = QueueRepository.current().firstOrNull { it.sourceUrl == link }
        if (item != null) {
            QueueRepository.update(item.id) {
                it.copy(
                    directUrl = link,
                    status = ItemStatus.READY,
                    fileName = name ?: it.fileName,
                    customSaveDirPath = customSaveDirPath
                )
            }
        }
        DownloadService.start(this)
        showDownloadStartedSnackbar()
    }

    fun triggerDownloadYoutubeCustom(
        link: String,
        name: String?,
        customSaveDirPath: String?,
        chosenQuality: YtDlpManager.QualityOption?,
        chosenAudioPreset: Settings.AudioFormatPreset = Settings.presetAudioFormat()
    ) {
        if (!BuildConfig.HAS_YOUTUBE_SUPPORT) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Not supported in this build")
                .setMessage("This is the Lite build, which doesn't include the yt-dlp engine needed for YouTube, HLS (.m3u8), or DASH (.mpd) links. Download the Full build from the app's Releases page to use this.")
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        if (!YtDlpManager.isInstalled(this)) {
            MaterialAlertDialogBuilder(this)
                .setTitle("yt-dlp not installed")
                .setMessage("This link needs the yt-dlp downloader, which isn't installed yet. Install it from Settings first.")
                .setPositiveButton("Install now") { _, _ -> openSettingsScreen(SettingsActivity.CATEGORY_YOUTUBE) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }

        val quality = chosenQuality ?: run {
            val options = YtDlpManager.standardQualityOptions(isGenericOrHls = !LinkParser.isYoutubeLink(link))
            options.firstOrNull { it.label.startsWith("1080p") } ?: options.firstOrNull()
        }

        if (quality == null) {
            Toast.makeText(this, "Could not resolve quality", Toast.LENGTH_SHORT).show()
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

        QueueRepository.setLinks(listOf(link))
        val item = QueueRepository.current().firstOrNull { it.sourceUrl == link }
        if (item != null) {
            QueueRepository.update(item.id) {
                it.copy(
                    status = ItemStatus.READY,
                    platform = MediaPlatform.YOUTUBE,
                    mediaFormatSelector = quality.formatSelector,
                    mediaFormatLabel = formatLabel,
                    category = if (quality.isAudioOnly) DownloadCategory.MUSIC else DownloadCategory.VIDEOS,
                    fileName = name ?: it.fileName,
                    customSaveDirPath = customSaveDirPath
                )
            }
        }
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
        selectedFileIndices: String?
    ) {
        val link = uri.toString()
        QueueRepository.setLinks(listOf(link))
        val item = QueueRepository.current().firstOrNull { it.sourceUrl == link }
        if (item != null) {
            QueueRepository.update(item.id) {
                it.copy(
                    directUrl = link,
                    status = ItemStatus.READY,
                    fileName = displayName ?: it.fileName,
                    customSaveDirPath = customSaveDirPath,
                    selectedFileIndices = selectedFileIndices
                )
            }
        }
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
        selectedFileIndices: String?
    ) {
        QueueRepository.setLinks(listOf(link))
        val item = QueueRepository.current().firstOrNull { it.sourceUrl == link }
        if (item != null) {
            QueueRepository.update(item.id) {
                it.copy(
                    directUrl = link,
                    status = ItemStatus.READY,
                    fileName = name ?: it.fileName,
                    customSaveDirPath = customSaveDirPath,
                    selectedFileIndices = selectedFileIndices
                )
            }
        }
        DownloadService.start(this)
        showDownloadStartedSnackbar()
    }

    /**
     * Downloads kick off in the background with no screen change, so without
     * this the user has no confirmation anything happened. Mirrors the
     * "Starting download… VIEW" pattern from stock browsers, but as a
     * rounded, floating M3 card (Widget.Xmd.Snackbar shape/theme) instead
     * of the stock flat full-width bar: hugs its text instead of stretching
     * edge-to-edge, and sits clear of the bottom nav.
     *
     * fragmentContainer is a CoordinatorLayout (not a plain FrameLayout)
     * specifically so Snackbar.make() finds it while walking up the view
     * tree — otherwise Material falls back to the Activity's root content
     * view, which spans behind the bottom nav and produces a full-width bar
     * that overlaps it. With a real CoordinatorLayout anchor, the bar is
     * naturally confined above the nav with no manual bottom-margin hack
     * needed. Used for every download entry point — direct links,
     * torrents/magnets, FuckingFast, and in-app browser — since they all
     * funnel through this one helper.
     */
    private fun showDownloadStartedSnackbar() {
        // Don't show the "Starting download… VIEW" nudge if the user is
        // already sitting on the Downloads screen -- the VIEW action would
        // just be pointing them at where they already are.
        //
        // Checked against currentTabTag, not the Downloads fragment's
        // isHidden state -- isHidden only flips once its
        // FragmentTransaction.commit() actually lands, which is scheduled
        // on the next main-thread pass rather than applied immediately.
        // Any flow that switches tabs and then calls this in the same
        // frame (e.g. handleIncomingIntent jumping to Home right before
        // starting a download) was reading the *pre-switch* isHidden
        // value, which made this guard fire -- and the snackbar go
        // missing -- from every tab, not just Downloads. (This replaced an
        // earlier bottomNav.selectedItemId check that had the same kind of
        // staleness problem for a different reason.) currentTabTag is a
        // plain field written synchronously the moment a tab is chosen, so
        // there's no async gap left to race.
        if (currentTabTag == TAG_DOWNLOADS) {
            return
        }

        val snackbar = Snackbar.make(
            findViewById(R.id.fragmentContainer),
            R.string.download_started_toast,
            Snackbar.LENGTH_LONG
        ).setAction(R.string.action_view) {
            bottomNav.selectedItemId = R.id.nav_downloads
        }

        val sideMargin = (16 * resources.displayMetrics.density).toInt()
        val bottomGap = (16 * resources.displayMetrics.density).toInt()
        val snackbarView = snackbar.view
        (snackbarView.layoutParams as? androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams)?.let { params ->
            params.width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            params.gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            params.setMargins(sideMargin, params.topMargin, sideMargin, bottomGap)
            snackbarView.layoutParams = params
        }
        snackbarView.elevation = 6 * resources.displayMetrics.density

        snackbar.show()
    }

    // ── BrowserFragment.Callbacks ───────────────────────────────────────────

    // Chrome-style overflow: a PopupMenu right-aligned (Gravity.END) under
    // [anchor] instead of a centered AlertDialog, so it drops down near the
    // 3-dot icon the way Chrome's overflow menu does rather than looking
    // like a generic popup. Phase E moved the 3-dot button itself into
    // Compose (BrowserToolbarRow), so [anchor] is now the whole toolbar row's
    // ComposeView rather than the button alone -- Gravity.END still lands the
    // menu at the row's right edge, right where the button sits.
    override fun openBrowserMenu(anchor: android.view.View) {
        val browserFragment = supportFragmentManager.findFragmentByTag(TAG_BROWSER) as? BrowserFragment
        val popup = androidx.appcompat.widget.PopupMenu(this, anchor, android.view.Gravity.END)
        popup.menuInflater.inflate(R.menu.browser_overflow_menu, popup.menu)
        popup.menu.findItem(R.id.menu_desktop_site)?.isChecked = browserFragment?.isDesktopModeOn() == true
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_refresh -> { reloadBrowserTab(); true }
                R.id.menu_find_in_page -> { browserFragment?.showFindInPage(); true }
                R.id.menu_desktop_site -> { browserFragment?.toggleDesktopModeForCurrentTab(); true }
                R.id.menu_private_dns -> { showDnsSettingsDialog(); true }
                R.id.menu_bookmarks -> { openBookmarksScreen(); true }
                R.id.menu_history -> { openHistoryScreen(); true }
                R.id.menu_clear_browsing_data -> { showClearBrowsingDataDialog(); true }
                R.id.menu_settings -> { openSettingsScreen(); true }
                else -> false
            }
        }
        popup.show()
    }

    override fun triggerSniffedMedia(url: String, needsPicker: Boolean) {
        QueueRepository.setLinks(listOf(url))
        val item = QueueRepository.current().firstOrNull { it.sourceUrl == url } ?: return
        if (needsPicker) {
            QueueRepository.update(item.id) { it.copy(status = ItemStatus.RESOLVING) }
            lifecycleScope.launch { resolveYoutube(item) }
        } else {
            QueueRepository.update(item.id) { it.copy(directUrl = url, status = ItemStatus.READY) }
            DownloadService.start(this)
            showDownloadStartedSnackbar()
        }
    }

    private fun reloadBrowserTab() {
        (supportFragmentManager.findFragmentByTag(TAG_BROWSER) as? BrowserFragment)?.reloadActiveTab()
    }

    /** Overflow menu's "Clear browsing data" -- Chrome-style checklist dialog.
     *  All three boxes start checked (matches Chrome's default selection). */
    private fun showClearBrowsingDataDialog() {
        val options = arrayOf(
            getString(R.string.clear_data_history),
            getString(R.string.clear_data_cookies),
            getString(R.string.clear_data_cache)
        )
        val checked = booleanArrayOf(true, true, true)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.clear_data_title)
            .setMultiChoiceItems(options, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton(R.string.clear_data_action) { _, _ ->
                val browserFragment = supportFragmentManager.findFragmentByTag(TAG_BROWSER) as? BrowserFragment
                browserFragment?.clearBrowsingData(
                    clearHistory = checked[0],
                    clearCookies = checked[1],
                    clearCache = checked[2]
                )
                android.widget.Toast.makeText(this, R.string.clear_data_cleared_toast, android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Opens the Compose DnsSettingsDialog (see mainDialogHost's setContent
     *  in onCreate) -- this used to build+show a MaterialAlertDialogBuilder
     *  wrapping dialog_dns_settings.xml right here; all of that now lives in
     *  ui/DnsSettingsDialog.kt, this function is just the open trigger. */
    private fun showDnsSettingsDialog() {
        dnsSettingsDialogOpen = true
    }

    // ── Phase D: History/Bookmarks overlay (NavHost) ────────────────────────
    // Replaces the old openHistoryScreen()/openBookmarksScreen() Fragment
    // pushes + HistoryFragment.Callbacks/BookmarkFragment.Callbacks. See
    // overlayNavHost in activity_main.xml and the overlayNavController
    // destination listener below for how the ComposeView's GONE/VISIBLE
    // state tracks the back stack.

    private fun setUpOverlayNavHost() {
        overlayNavHost = findViewById(R.id.overlayNavHost)
        overlayNavHost.setViewCompositionStrategy(
            androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        overlayNavHost.setContent {
            val navController = rememberNavController()
            overlayNavController = navController

            // Toggle the host's own View visibility to GONE/VISIBLE in step
            // with the back stack -- an empty Compose route wouldn't itself
            // consume touches, but a stray full-bleed ComposeView left
            // VISIBLE is a trap for hit-testing/accessibility over
            // BrowserFragment's WebView underneath, so track it explicitly.
            // Registered here (inside composition, via DisposableEffect)
            // rather than right after setContent() below -- navController
            // is only assigned once composition actually runs, which isn't
            // guaranteed synchronous with the setContent() call itself.
            DisposableEffect(navController) {
                val listener = androidx.navigation.NavController.OnDestinationChangedListener { _, destination, _ ->
                    overlayNavHost.visibility = if (destination.route == OverlayRoute.EMPTY) View.GONE else View.VISIBLE
                }
                navController.addOnDestinationChangedListener(listener)
                onDispose { navController.removeOnDestinationChangedListener(listener) }
            }

            com.invictus.xmd.ui.theme.XmdTheme {
                NavHost(
                    navController = navController,
                    startDestination = OverlayRoute.EMPTY,
                ) {
                    composable(OverlayRoute.EMPTY) { /* never visible -- common root, see reasoning above */ }

                    composable(OverlayRoute.HISTORY) {
                        val allEntries by HistoryRepository.entries.collectAsStateWithLifecycle()
                        var query by remember { mutableStateOf("") }
                        var confirmingClearAll by remember { mutableStateOf(false) }

                        val trimmedQuery = query.trim()
                        val visible = if (trimmedQuery.isEmpty()) {
                            allEntries
                        } else {
                            allEntries.filter { entry ->
                                entry.title.contains(trimmedQuery, ignoreCase = true) ||
                                    entry.url.contains(trimmedQuery, ignoreCase = true)
                            }
                        }

                        HistoryScreen(
                            entries = visible,
                            query = query,
                            onQueryChange = { query = it },
                            onBack = { navController.popBackStack() },
                            onClearAll = { confirmingClearAll = true },
                            onTap = { entry: HistoryEntry ->
                                navController.popBackStack(OverlayRoute.EMPTY, false)
                                val browser = supportFragmentManager.findFragmentByTag(TAG_BROWSER) as? BrowserFragment
                                browser?.openUrl(entry.url)
                                bottomNav.selectedItemId = R.id.nav_browser
                            },
                            onDelete = { entry -> HistoryRepository.remove(entry) },
                        )

                        if (confirmingClearAll) {
                            AlertDialog(
                                onDismissRequest = { confirmingClearAll = false },
                                title = { Text(stringResource(R.string.history_clear_all)) },
                                confirmButton = {
                                    TextButton(onClick = {
                                        confirmingClearAll = false
                                        HistoryRepository.clearAll()
                                        Toast.makeText(this@MainActivity, R.string.history_cleared_toast, Toast.LENGTH_SHORT).show()
                                    }) {
                                        Text(stringResource(R.string.history_clear_all))
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { confirmingClearAll = false }) {
                                        Text(stringResource(android.R.string.cancel))
                                    }
                                },
                            )
                        }
                    }

                    composable(OverlayRoute.BOOKMARKS) {
                        val allBookmarks by BookmarkRepository.bookmarks.collectAsStateWithLifecycle()
                        var query by remember { mutableStateOf("") }
                        var confirmingClearAll by remember { mutableStateOf(false) }

                        val trimmedQuery = query.trim()
                        val visible = if (trimmedQuery.isEmpty()) {
                            allBookmarks
                        } else {
                            allBookmarks.filter { entry ->
                                entry.title.contains(trimmedQuery, ignoreCase = true) ||
                                    entry.url.contains(trimmedQuery, ignoreCase = true)
                            }
                        }

                        BookmarkScreen(
                            entries = visible,
                            query = query,
                            onQueryChange = { query = it },
                            onBack = { navController.popBackStack() },
                            onClearAll = { confirmingClearAll = true },
                            onTap = { bookmark: Bookmark ->
                                navController.popBackStack(OverlayRoute.EMPTY, false)
                                val browser = supportFragmentManager.findFragmentByTag(TAG_BROWSER) as? BrowserFragment
                                browser?.openUrl(bookmark.url)
                                bottomNav.selectedItemId = R.id.nav_browser
                            },
                            onDelete = { bookmark -> BookmarkRepository.remove(bookmark) },
                        )

                        if (confirmingClearAll) {
                            AlertDialog(
                                onDismissRequest = { confirmingClearAll = false },
                                title = { Text(stringResource(R.string.bookmarks_clear_all)) },
                                confirmButton = {
                                    TextButton(onClick = {
                                        confirmingClearAll = false
                                        BookmarkRepository.clearAll()
                                        Toast.makeText(this@MainActivity, R.string.bookmarks_cleared_toast, Toast.LENGTH_SHORT).show()
                                    }) {
                                        Text(stringResource(R.string.bookmarks_clear_all))
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { confirmingClearAll = false }) {
                                        Text(stringResource(android.R.string.cancel))
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun openHistoryScreen() {
        overlayNavController.navigate(OverlayRoute.HISTORY) { popUpTo(OverlayRoute.EMPTY) }
    }

    private fun openBookmarksScreen() {
        overlayNavController.navigate(OverlayRoute.BOOKMARKS) { popUpTo(OverlayRoute.EMPTY) }
    }

    // ── DownloadsFragment.Callbacks ─────────────────────────────────────────

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
        MaterialAlertDialogBuilder(this)
            .setTitle("Link Expired")
            .setMessage(
                "${item.fileName ?: item.sourceUrl}\n\n" +
                "This download link has expired or is no longer available."
            )
            .setPositiveButton("Fetch Link") { _, _ -> retryItem(item.id) }
            .setNegativeButton("Clear") { _, _ ->
                pendingRetryIds.remove(item.id)
                QueueRepository.removeItem(item.id)
            }
            .setCancelable(true)
            .show()
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
            pendingChallengeContinuation = { url, err -> cont.resume(url to err) }
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
            MaterialAlertDialogBuilder(this)
                .setTitle("Not supported in this build")
                .setMessage("This is the Lite build, which doesn't include the yt-dlp engine needed for YouTube, HLS (.m3u8), or DASH (.mpd) links. Download the Full build from the app's Releases page to use this.")
                .setPositiveButton(android.R.string.ok, null)
                .show()
            QueueRepository.update(item.id) {
                it.copy(status = ItemStatus.FAILED, error = "Needs the Full build")
            }
            return
        }
        if (!YtDlpManager.isInstalled(this)) {
            val openSettings = suspendCancellableCoroutine<Boolean> { cont ->
                val dialog = MaterialAlertDialogBuilder(this)
                    .setTitle("yt-dlp not installed")
                    .setMessage("This link needs the yt-dlp downloader, which isn't installed yet. Install it from Settings first.")
                    .setPositiveButton("Install now") { _, _ -> cont.resume(true) }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> cont.resume(false) }
                    .setOnCancelListener { cont.resume(false) }
                    .create()
                cont.invokeOnCancellation { dialog.dismiss() }
                dialog.show()
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

        // A saved default (anything other than blank/"Ask always") skips
        // the picker dialog entirely and downloads at that quality
        // directly -- matched back to its QualityOption by label, same
        // list the dialog itself is built from so the two never drift.
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
            suspendCancellableCoroutine<YtDlpManager.QualityOption?> { cont ->
                qualityPickerState = QualityPickerState(
                    titleText = item.fileName ?: "Choose quality",
                    standardOptions = options,
                    advancedFormats = emptyList(),
                    advancedLoading = true,
                    durationSeconds = null,
                    onConfirm = { resolved ->
                        qualityPickerState = null
                        cont.resume(resolved)
                    },
                    onDismiss = {
                        qualityPickerState = null
                        cont.resume(null)
                    },
                )
                val probeJob = lifecycleScope.launch {
                    val probe = withContext(Dispatchers.IO) {
                        YtDlpManager.probeFormats(item.sourceUrl, this@MainActivity)
                    }
                    // Highest quality first -- video streams (by height, then
                    // fps, then bitrate) ahead of audio-only, matching the
                    // standard ladder's high-to-low ordering above.
                    val sorted = probe.formats.sortedWith(
                        compareByDescending<YtDlpManager.ProbedFormat> { it.height ?: -1 }
                            .thenByDescending { it.fps ?: -1 }
                            .thenByDescending { it.tbr ?: -1.0 }
                    )
                    qualityPickerState = qualityPickerState?.copy(
                        advancedLoading = false,
                        advancedFormats = sorted,
                        durationSeconds = probe.durationSeconds,
                    )
                }
                cont.invokeOnCancellation {
                    probeJob.cancel()
                    qualityPickerState = null
                }
            }
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
                MaterialAlertDialogBuilder(this)
                    .setTitle("Storage Permission Required")
                    .setMessage(
                        "This app needs 'All files access' to save downloads to the " +
                        "\"Xmd\" folder in your internal storage.\n\nTap Allow on the next screen."
                    )
                    .setPositiveButton("Allow") { _, _ ->
                        startActivity(
                            Intent(
                                AndroidSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.fromParts("package", packageName, null)
                            )
                        )
                    }
                    .setNegativeButton("Cancel") { _, _ ->
                        Toast.makeText(
                            this,
                            "Downloads will fail without storage permission.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    .show()
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

    // ── Options menu ──────────────────────────────────────────────────────

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        return false
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        return false
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_settings) { openSettingsScreen(); return true }
        return super.onOptionsItemSelected(item)
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
        recreate()
    }


    // ── Constants ─────────────────────────────────────────────────────────

    companion object {
        private const val TAG_BROWSER   = "browser"
        private const val TAG_DOWNLOADS = "downloads"
    }
}

/** overlayNavHost's NavHost route strings (Phase D). EMPTY is the common
 *  root -- never itself rendered, see setUpOverlayNavHost()'s destination
 *  listener, which uses it to know when to hide overlayNavHost again. */
private object OverlayRoute {
    const val EMPTY = "empty"
    const val HISTORY = "history"
    const val BOOKMARKS = "bookmarks"
}
