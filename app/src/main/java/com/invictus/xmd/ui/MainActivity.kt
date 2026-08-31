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
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.lifecycle.lifecycleScope
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
import android.widget.ImageView
import androidx.activity.result.ActivityResultLauncher
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class MainActivity : AppCompatActivity(), DownloadsFragment.Callbacks, BrowserFragment.Callbacks, HistoryFragment.Callbacks, BookmarkFragment.Callbacks {

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
            // The Browser fragment's own address bar is the top bar here --
            // the shared app toolbar (and its title) would just duplicate it.
            toolbar.visibility = android.view.View.GONE
            currentTabTag = TAG_BROWSER
            return
        }
        toolbar.visibility = android.view.View.VISIBLE
        toolbarTitle.text = getString(R.string.tab_downloads)
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

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        this.toolbar = toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        // Tap the header text (not the whole bar) to flip dark/light mode for
        // whichever color theme is active -- toolbarTitle is a real TextView
        // now instead of Toolbar's built-in title, so it's directly clickable
        // on its own without catching taps anywhere else across the bar.
        val toolbarTitle = findViewById<TextView>(R.id.toolbarTitle)
        this.toolbarTitle = toolbarTitle
        toolbarTitle.text = getString(R.string.tab_downloads)
        toolbarTitle.setOnClickListener { toggleDarkMode() }

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
                    toolbarTitle.text = getString(R.string.tab_downloads)
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
        //  1. Browser tab with page history / a loaded page -> step back through it.
        //  2. Browser tab -> jump to Downloads tab first before exiting.
        //  3. Already on Downloads tab -> exit the app.
        onBackPressedDispatcher.addCallback(this) {
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
        QueueRepository.items.observe(this) { list ->
            val active = list.count {
                it.status == ItemStatus.DOWNLOADING || it.status == ItemStatus.PAUSED ||
                it.status == ItemStatus.SAVING || it.status == ItemStatus.RETRYING
            }
            bottomNav.updateBadge(active)
        }

        // Watches items sent through the Retry button; pops an IDM-style
        // "Link Expired" dialog (Clear / Fetch Link) the moment a retried
        // item lands back on FAILED with an expired-link error, whether that
        // failure happened at resolve-time or later during the actual
        // download.
        QueueRepository.items.observe(this) { list ->
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
        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_add_download, null)

        val linkInput = dialogView.findViewById<EditText>(R.id.downloadLinkInput)
        val copyLinkButton = dialogView.findViewById<MaterialButton>(R.id.downloadCopyLinkButton)
        val pasteLinkButton = dialogView.findViewById<MaterialButton>(R.id.downloadPasteLinkButton)
        val pickFileText = dialogView.findViewById<TextView>(R.id.downloadPickFileText)
        val nameInput = dialogView.findViewById<EditText>(R.id.downloadNameInput)
        val advancedHeader = dialogView.findViewById<View>(R.id.downloadAdvancedHeader)
        val advancedChevron = dialogView.findViewById<ImageView>(R.id.downloadAdvancedChevron)
        val advancedContent = dialogView.findViewById<View>(R.id.downloadAdvancedContent)
        val saveToPathText = dialogView.findViewById<TextView>(R.id.downloadSaveToPathText)
        val changePathButton = dialogView.findViewById<MaterialButton>(R.id.downloadChangePathButton)
        val startButton = dialogView.findViewById<MaterialButton>(R.id.downloadStartButton)
        val cancelButton = dialogView.findViewById<MaterialButton>(R.id.downloadCancelButton)

        var customSaveDirPath: String? = null
        var nameManuallyEdited = false

        val initialLink = link?.trim().orEmpty()

        if (initialLink.isNotEmpty()) {
            linkInput.setText(initialLink)
        }
        saveToPathText.text = defaultSavePath()

        var probeJob: Job? = null
        fun updateNameForLink(currentLink: String) {
            probeJob?.cancel()
            if (nameManuallyEdited) return
            if (LinkParser.isMagnetLink(currentLink)) {
                val detected = magnetDisplayName(currentLink)
                if (!detected.isNullOrBlank()) nameInput.setText(detected)
            } else if (currentLink.isNotBlank()) {
                val guessed = DownloadEngine.filenameFromLink(currentLink).ifBlank { DownloadEngine.filenameFromUrl(currentLink) }
                if (guessed.isNotBlank()) nameInput.setText(guessed)
                probeJob = lifecycleScope.launch {
                    val probed = withContext(Dispatchers.IO) {
                        DownloadEngine.probeRealFilename(filenameClient, currentLink)
                    }
                    if (!nameManuallyEdited && !probed.isNullOrBlank()) {
                        nameInput.setText(probed)
                    }
                }
            }
        }

        if (initialLink.isNotEmpty()) {
            updateNameForLink(initialLink)
        }

        linkInput.doAfterTextChanged {
            val text = it?.toString()?.trim().orEmpty()
            updateNameForLink(text)
        }

        nameInput.doAfterTextChanged { nameManuallyEdited = true }

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.setOnDismissListener { probeJob?.cancel() }

        pickFileText.setOnClickListener {
            dialog.dismiss()
            pickTorrentFileLauncher.launch(
                arrayOf("application/x-bittorrent", "application/octet-stream")
            )
        }

        copyLinkButton.setOnClickListener {
            val text = linkInput.text?.toString()?.trim().orEmpty()
            if (text.isNotEmpty()) {
                clipboardManager.setPrimaryClip(ClipData.newPlainText("Download link", text))
                Toast.makeText(this, R.string.torrent_dialog_link_copied_toast, Toast.LENGTH_SHORT).show()
            }
        }

        pasteLinkButton.setOnClickListener {
            val clipText = clipboardManager.primaryClip?.getItemAt(0)?.text?.toString()?.trim().orEmpty()
            if (clipText.isNotEmpty()) {
                linkInput.setText(clipText)
                linkInput.setSelection(clipText.length)
                Toast.makeText(this, R.string.dialog_link_pasted_toast, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.clipboard_empty_toast, Toast.LENGTH_SHORT).show()
            }
        }

        advancedHeader.setOnClickListener {
            val expanding = advancedContent.visibility != View.VISIBLE
            advancedContent.visibility = if (expanding) View.VISIBLE else View.GONE
            advancedChevron.animate().rotation(if (expanding) 270f else 90f).setDuration(150).start()
        }

        changePathButton.setOnClickListener {
            pendingSaveDirCallback = { path ->
                customSaveDirPath = path
                saveToPathText.text = path
            }
            pickSaveDirLauncher.launch(null)
        }

        cancelButton.setOnClickListener { dialog.dismiss() }

        startButton.setOnClickListener {
            val finalLink = linkInput.text?.toString()?.trim().orEmpty()
            if (finalLink.isEmpty()) {
                Toast.makeText(this, "Enter a valid link", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val finalName = nameInput.text?.toString()?.trim().takeUnless { it.isNullOrBlank() }
            probeJob?.cancel()
            dialog.dismiss()

            if (LinkParser.isTorrentLink(finalLink)) {
                showAddTorrentDialog(prefillLink = finalLink)
            } else if (LinkParser.isShareLink(finalLink) || LinkParser.isFitgirlPage(finalLink)) {
                triggerPrepare(listOf(finalLink))
            } else {
                triggerDownloadDirectCustom(finalLink, finalName, customSaveDirPath)
            }
        }

        dialog.setOnShowListener { applyResponsiveDialogWidth(dialog) }
        dialogView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            applyResponsiveDialogWidth(dialog)
        }

        dialog.show()
        applyResponsiveDialogWidth(dialog)
    }

    fun showAddTorrentDialog(
        prefillLink: String? = null,
        prefillTorrentUri: Uri? = null,
        prefillDisplayName: String? = null
    ) {
        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_add_torrent, null)

        val linkInput = dialogView.findViewById<EditText>(R.id.torrentLinkInput)
        val copyLinkButton = dialogView.findViewById<MaterialButton>(R.id.torrentCopyLinkButton)
        val pasteLinkButton = dialogView.findViewById<MaterialButton>(R.id.torrentPasteLinkButton)
        val nameInput = dialogView.findViewById<EditText>(R.id.torrentNameInput)
        val pickFileText = dialogView.findViewById<TextView>(R.id.torrentPickFileText)
        val advancedHeader = dialogView.findViewById<View>(R.id.torrentAdvancedHeader)
        val advancedChevron = dialogView.findViewById<ImageView>(R.id.torrentAdvancedChevron)
        val advancedContent = dialogView.findViewById<View>(R.id.torrentAdvancedContent)
        val saveToPathText = dialogView.findViewById<TextView>(R.id.torrentSaveToPathText)
        val changePathButton = dialogView.findViewById<MaterialButton>(R.id.torrentChangePathButton)
        val startButton = dialogView.findViewById<MaterialButton>(R.id.torrentStartButton)
        val cancelButton = dialogView.findViewById<MaterialButton>(R.id.torrentCancelButton)

        val filesCountText = dialogView.findViewById<TextView>(R.id.torrentFilesCountText)
        val selectAllButton = dialogView.findViewById<MaterialButton>(R.id.torrentSelectAllButton)
        val filesLoadingContainer = dialogView.findViewById<View>(R.id.torrentFilesLoadingContainer)
        val filesErrorContainer = dialogView.findViewById<View>(R.id.torrentFilesErrorContainer)
        val filesListCard = dialogView.findViewById<View>(R.id.torrentFilesListCard)
        val filesRecyclerView = dialogView.findViewById<RecyclerView>(R.id.torrentFilesRecyclerView)

        var customSaveDirPath: String? = null
        var nameManuallyEdited = false
        var fetchJob: Job? = null

        saveToPathText.text = defaultSavePath()

        val adapter = TorrentFileAdapter { count, bytes ->
            val total = if (filesRecyclerView.adapter is TorrentFileAdapter) {
                (filesRecyclerView.adapter as TorrentFileAdapter).getTotalCount()
            } else count
            filesCountText.text = "• $count of $total (${TorrentFileAdapter.formatBytes(bytes)})"
            if (filesRecyclerView.adapter is TorrentFileAdapter) {
                val allSelected = (filesRecyclerView.adapter as TorrentFileAdapter).areAllSelected()
                selectAllButton.text = getString(
                    if (allSelected) R.string.torrent_dialog_deselect_all else R.string.torrent_dialog_select_all
                )
            }
            startButton.isEnabled = count > 0
        }
        filesRecyclerView.layoutManager = LinearLayoutManager(this)
        filesRecyclerView.adapter = adapter

        selectAllButton.setOnClickListener {
            val select = !adapter.areAllSelected()
            adapter.selectAll(select)
        }

        fun populateTorrentFiles(ti: TorrentInfo) {
            val count = ti.numFiles()
            val entries = (0 until count).map { idx ->
                TorrentFileEntry(
                    index = idx,
                    path = runCatching { ti.files().filePath(idx) }.getOrNull() ?: "File ${idx + 1}",
                    sizeBytes = runCatching { ti.files().fileSize(idx) }.getOrNull() ?: 0L,
                    isSelected = true
                )
            }
            adapter.setFiles(entries)
            filesLoadingContainer.visibility = View.GONE
            filesErrorContainer.visibility = View.GONE
            filesListCard.visibility = View.VISIBLE
            selectAllButton.visibility = View.VISIBLE
        }

        fun loadMetadataForMagnet(link: String) {
            fetchJob?.cancel()
            if (!LinkParser.isMagnetLink(link)) {
                filesLoadingContainer.visibility = View.GONE
                filesErrorContainer.visibility = View.GONE
                filesListCard.visibility = View.GONE
                selectAllButton.visibility = View.GONE
                filesCountText.text = ""
                return
            }

            filesLoadingContainer.visibility = View.VISIBLE
            filesErrorContainer.visibility = View.GONE
            filesListCard.visibility = View.GONE
            selectAllButton.visibility = View.GONE
            filesCountText.text = ""

            fetchJob = lifecycleScope.launch {
                val tempDir = File(cacheDir, "torrent_meta")
                val bytes = withContext(Dispatchers.IO) {
                    TorrentSession.fetchMetadata(link, timeoutSeconds = 25, tempDir)
                }
                if (bytes != null) {
                    val ti = runCatching { TorrentInfo.bdecode(bytes) }.getOrNull()
                    if (ti != null) {
                        if (!nameManuallyEdited && ti.name().isNotBlank()) {
                            nameInput.setText(ti.name())
                        }
                        populateTorrentFiles(ti)
                        return@launch
                    }
                }
                filesLoadingContainer.visibility = View.GONE
                filesErrorContainer.visibility = View.VISIBLE
                filesListCard.visibility = View.GONE
                selectAllButton.visibility = View.GONE
            }
        }

        fun updateNamePreview() {
            if (nameManuallyEdited) return
            val link = linkInput.text?.toString()?.trim().orEmpty()
            val detected = magnetDisplayName(link)
            if (!detected.isNullOrBlank()) {
                nameInput.setText(detected)
            }
        }

        linkInput.doAfterTextChanged {
            val link = linkInput.text?.toString()?.trim().orEmpty()
            updateNamePreview()
            if (prefillTorrentUri == null && LinkParser.isMagnetLink(link)) {
                loadMetadataForMagnet(link)
            }
        }
        nameInput.doAfterTextChanged { nameManuallyEdited = true }

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.setOnDismissListener { fetchJob?.cancel() }

        if (prefillTorrentUri != null) {
            linkInput.setText(prefillTorrentUri.toString())
            linkInput.isEnabled = false
            pickFileText.visibility = View.GONE
            copyLinkButton.visibility = View.GONE
            pasteLinkButton.visibility = View.GONE
            if (!prefillDisplayName.isNullOrBlank()) {
                nameInput.setText(prefillDisplayName)
            }
            runCatching {
                val bytes = contentResolver.openInputStream(prefillTorrentUri)?.use { it.readBytes() }
                if (bytes != null) {
                    val ti = TorrentInfo.bdecode(bytes)
                    if (!nameManuallyEdited && ti.name().isNotBlank()) {
                        nameInput.setText(ti.name())
                    }
                    populateTorrentFiles(ti)
                }
            }
        } else if (!prefillLink.isNullOrBlank()) {
            linkInput.setText(prefillLink)
            nameManuallyEdited = false
            updateNamePreview()
            if (LinkParser.isMagnetLink(prefillLink)) {
                loadMetadataForMagnet(prefillLink)
            }
        }

        copyLinkButton.setOnClickListener {
            val link = linkInput.text?.toString()?.trim().orEmpty()
            if (link.isEmpty()) return@setOnClickListener
            clipboardManager.setPrimaryClip(ClipData.newPlainText("Magnet link", link))
            Toast.makeText(this, R.string.torrent_dialog_link_copied_toast, Toast.LENGTH_SHORT).show()
        }

        pasteLinkButton.setOnClickListener {
            val clipText = clipboardManager.primaryClip?.getItemAt(0)?.text?.toString()?.trim().orEmpty()
            if (clipText.isNotEmpty()) {
                linkInput.setText(clipText)
                linkInput.setSelection(clipText.length)
                Toast.makeText(this, R.string.dialog_link_pasted_toast, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.clipboard_empty_toast, Toast.LENGTH_SHORT).show()
            }
        }

        advancedHeader.setOnClickListener {
            val expanding = advancedContent.visibility != View.VISIBLE
            advancedContent.visibility = if (expanding) View.VISIBLE else View.GONE
            advancedChevron.animate().rotation(if (expanding) 270f else 90f).setDuration(150).start()
        }

        changePathButton.setOnClickListener {
            pendingSaveDirCallback = { path ->
                customSaveDirPath = path
                saveToPathText.text = path
            }
            pickSaveDirLauncher.launch(null)
        }

        pickFileText.setOnClickListener {
            dialog.dismiss()
            pickTorrentFileLauncher.launch(
                arrayOf("application/x-bittorrent", "application/octet-stream")
            )
        }

        cancelButton.setOnClickListener { dialog.dismiss() }

        startButton.setOnClickListener {
            if (adapter.itemCount > 0 && adapter.getSelectedCount() == 0) {
                Toast.makeText(this, R.string.torrent_dialog_no_files_selected, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val selectedIndices = if (adapter.itemCount > 0) {
                if (adapter.areAllSelected()) null else adapter.getSelectedIndices().joinToString(",")
            } else null

            val name = nameInput.text?.toString()?.trim().takeUnless { it.isNullOrBlank() }

            if (prefillTorrentUri != null) {
                dialog.dismiss()
                triggerDownloadTorrentFile(prefillTorrentUri, name, customSaveDirPath, selectedIndices)
            } else {
                val link = linkInput.text?.toString()?.trim().orEmpty()
                if (!LinkParser.isTorrentLink(link)) {
                    Toast.makeText(this, R.string.torrent_dialog_invalid_link, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                dialog.dismiss()
                triggerDownloadTorrentMagnet(link, name, customSaveDirPath, selectedIndices)
            }
        }

        dialog.setOnShowListener { applyResponsiveDialogWidth(dialog) }
        dialogView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            applyResponsiveDialogWidth(dialog)
        }

        dialog.show()
        applyResponsiveDialogWidth(dialog)
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

    // Chrome-style overflow: a PopupMenu anchored directly under the 3-dot
    // button (right-aligned via Gravity.END) instead of a centered
    // AlertDialog, so it drops down from the icon the way Chrome's overflow
    // menu does rather than looking like a generic popup.
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

    /** Builds a two-line radio-row label: the provider name on top and its
     *  DoH address underneath in a smaller, dimmer style (mirrors Android's
     *  own Private DNS picker, which shows the resolved host under each option). */
    private fun labelWithAddress(title: String, address: String): SpannableString {
        val full = "$title\n$address"
        val spannable = SpannableString(full)
        val addressStart = title.length + 1
        spannable.setSpan(
            RelativeSizeSpan(0.8f),
            addressStart, full.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannable.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(this, R.color.m3_on_surface_variant)),
            addressStart, full.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return spannable
    }

    private fun showDnsSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_dns_settings, null)
        val group = dialogView.findViewById<RadioGroup>(R.id.dnsModeGroup)
        val optionAdguard = dialogView.findViewById<RadioButton>(R.id.dnsOptionAdguard)
        val optionGoogle = dialogView.findViewById<RadioButton>(R.id.dnsOptionGoogle)
        val optionCloudflare = dialogView.findViewById<RadioButton>(R.id.dnsOptionCloudflare)
        val optionCloudflareAdblock = dialogView.findViewById<RadioButton>(R.id.dnsOptionCloudflareAdblock)
        val optionOff = dialogView.findViewById<RadioButton>(R.id.dnsOptionOff)
        val optionCustom = dialogView.findViewById<RadioButton>(R.id.dnsOptionCustom)
        val customUrlInput = dialogView.findViewById<EditText>(R.id.dnsCustomUrlInput)
        val customUrlLayout = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.dnsCustomUrlLayout)

        optionAdguard.text = labelWithAddress(getString(R.string.dns_mode_adguard), DnsOverHttpsResolver.ADGUARD_DOH_URL)
        optionGoogle.text = labelWithAddress(getString(R.string.dns_mode_google), DnsOverHttpsResolver.GOOGLE_DOH_URL)
        optionCloudflare.text = labelWithAddress(getString(R.string.dns_mode_cloudflare), DnsOverHttpsResolver.CLOUDFLARE_DOH_URL)
        optionCloudflareAdblock.text = labelWithAddress(getString(R.string.dns_mode_cloudflare_adblock), DnsOverHttpsResolver.CLOUDFLARE_ADBLOCK_DOH_URL)

        when (Settings.dnsMode()) {
            Settings.DnsMode.ADGUARD -> optionAdguard.isChecked = true
            Settings.DnsMode.GOOGLE -> optionGoogle.isChecked = true
            Settings.DnsMode.CLOUDFLARE -> optionCloudflare.isChecked = true
            Settings.DnsMode.CLOUDFLARE_ADBLOCK -> optionCloudflareAdblock.isChecked = true
            Settings.DnsMode.OFF -> optionOff.isChecked = true
            Settings.DnsMode.CUSTOM -> optionCustom.isChecked = true
        }
        customUrlInput.setText(Settings.dnsCustomUrl())
        customUrlLayout.visibility = if (optionCustom.isChecked) android.view.View.VISIBLE else android.view.View.GONE

        group.setOnCheckedChangeListener { _, checkedId ->
            customUrlLayout.visibility =
                if (checkedId == R.id.dnsOptionCustom) android.view.View.VISIBLE else android.view.View.GONE
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dns_settings_title)
            .setView(dialogView)
            .setPositiveButton(R.string.settings_save) { _, _ ->
                when (group.checkedRadioButtonId) {
                    R.id.dnsOptionOff -> Settings.setDnsMode(Settings.DnsMode.OFF)
                    R.id.dnsOptionGoogle -> Settings.setDnsMode(Settings.DnsMode.GOOGLE)
                    R.id.dnsOptionCloudflare -> Settings.setDnsMode(Settings.DnsMode.CLOUDFLARE)
                    R.id.dnsOptionCloudflareAdblock -> Settings.setDnsMode(Settings.DnsMode.CLOUDFLARE_ADBLOCK)
                    R.id.dnsOptionCustom -> {
                        val url = customUrlInput.text?.toString()?.trim().orEmpty()
                        if (url.isEmpty() || !(url.startsWith("http://") || url.startsWith("https://"))) {
                            Toast.makeText(this, R.string.dns_custom_url_needed, Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }
                        Settings.setDnsCustomUrl(url)
                        Settings.setDnsMode(Settings.DnsMode.CUSTOM)
                    }
                    else -> Settings.setDnsMode(Settings.DnsMode.ADGUARD)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openHistoryScreen() {
        supportFragmentManager.beginTransaction()
            .add(R.id.fragmentContainer, HistoryFragment(), TAG_HISTORY)
            .addToBackStack(TAG_HISTORY)
            .commit()
    }

    // ── HistoryFragment.Callbacks ───────────────────────────────────────────

    override fun openInBrowser(url: String) {
        // History was added via addToBackStack (see openHistoryScreen), so it's
        // still layered on top of the tab fragments here -- without popping it
        // first, tapping a history entry would open the link in Browser
        // underneath while History stayed visible on top, making it look like
        // the tap "went nowhere" / landed on the wrong screen.
        supportFragmentManager.popBackStack(TAG_HISTORY, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        val browser = supportFragmentManager.findFragmentByTag(TAG_BROWSER) as? BrowserFragment
        browser?.openUrl(url)
        bottomNav.selectedItemId = R.id.nav_browser
    }

    private fun openBookmarksScreen() {
        supportFragmentManager.beginTransaction()
            .add(R.id.fragmentContainer, BookmarkFragment(), TAG_BOOKMARKS)
            .addToBackStack(TAG_BOOKMARKS)
            .commit()
    }

    // ── BookmarkFragment.Callbacks ──────────────────────────────────────────

    override fun openBookmarkInBrowser(url: String) {
        // Same reasoning as openInBrowser() above -- pop Bookmarks off the
        // back stack first so the navigation is visibly landing on Browser.
        supportFragmentManager.popBackStack(TAG_BOOKMARKS, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        val browser = supportFragmentManager.findFragmentByTag(TAG_BROWSER) as? BrowserFragment
        browser?.openUrl(url)
        bottomNav.selectedItemId = R.id.nav_browser
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
            val dialogView = layoutInflater.inflate(R.layout.dialog_quality_picker, null)
            val group = dialogView.findViewById<RadioGroup>(R.id.qualityGroup)
            val density = resources.displayMetrics.density

            // Shared row builder -- used for both the standard ladder above
            // and the advanced (real-probe) list below, so the two always
            // look identical. tag carries whichever value the positive
            // button should resolve for that row (a fixed QualityOption
            // for the standard ladder, or a ProbedFormat for advanced --
            // resolved into a QualityOption at OK-click time).
            fun buildRow(label: String, isAudioOnly: Boolean, tag: Any): AppCompatRadioButton {
                // AppCompatRadioButton, not the platform RadioButton -- it
                // handles its own compound-button tinting internally, so it
                // can be constructed programmatically like this safely. A
                // plain platform RadioButton() built with an AppCompat-
                // lineage style (Widget.Xmd.RadioRow extends
                // Widget.AppCompat.CompoundButton.RadioButton) as its
                // defStyleRes bypasses AppCompatViewInflater entirely
                // (that only runs for XML-inflated views) and crashes with
                // "requires Theme.AppCompat" the moment it's measured/drawn.
                // Styling that Widget.Xmd.RadioRow would have applied via
                // XML is replicated by hand below instead.
                val row = AppCompatRadioButton(this)
                row.id = android.view.View.generateViewId()
                row.text = label
                row.isClickable = true
                row.buttonDrawable = null
                row.setBackgroundResource(R.drawable.bg_radio_row_selector)
                row.setTextColor(ContextCompat.getColorStateList(this, R.color.text_radio_row))
                row.textSize = 14f
                row.gravity = android.view.Gravity.CENTER_VERTICAL
                row.setPadding((16 * density).toInt(), (14 * density).toInt(), (16 * density).toInt(), (14 * density).toInt())
                val startIcon = if (isAudioOnly) R.drawable.ic_music_note else R.drawable.ic_video
                row.setCompoundDrawablesWithIntrinsicBounds(startIcon, 0, R.drawable.ic_check_selector, 0)
                row.compoundDrawablePadding = (12 * density).toInt()
                row.tag = tag
                row.layoutParams = RadioGroup.LayoutParams(
                    RadioGroup.LayoutParams.MATCH_PARENT,
                    RadioGroup.LayoutParams.WRAP_CONTENT
                )
                return row
            }

            options.forEach { option -> group.addView(buildRow(option.label, option.isAudioOnly, option)) }
            // Default selection: the option one below the top of the ladder
            // (1440p) reads as a sane, non-extreme default rather than
            // pre-selecting either end of the quality range.
            (group.getChildAt(1) as? RadioButton)?.isChecked = true

            // ── Advanced settings: real probed formats (FPS/codec/exact size) ──
            val advancedHeader  = dialogView.findViewById<android.view.View>(R.id.advancedHeader)
            val advancedChevron = dialogView.findViewById<android.widget.ImageView>(R.id.advancedChevron)
            val advancedContent = dialogView.findViewById<android.view.View>(R.id.advancedContent)
            val advancedProgress = dialogView.findViewById<android.view.View>(R.id.advancedProgress)
            val advancedEmptyText = dialogView.findViewById<TextView>(R.id.advancedEmptyText)
            val advancedGroup = dialogView.findViewById<RadioGroup>(R.id.advancedGroup)

            // Cleared once the picker dialog resolves (OK/Cancel/dismiss) so
            // a slow probe landing after the user already answered doesn't
            // touch dead dialog views.
            var probeCallbackAlive = true

            // Kicked off immediately (not lazily on expand) so the result is
            // typically ready the moment the user taps "Advanced" -- this is
            // a real yt-dlp --dump-json network round-trip, so it runs on
            // Dispatchers.IO in the background while the standard ladder
            // above is already interactive.
            lifecycleScope.launch {
                val probe = withContext(Dispatchers.IO) {
                    YtDlpManager.probeFormats(item.sourceUrl, this@MainActivity)
                }
                if (!probeCallbackAlive) return@launch

                advancedProgress.visibility = android.view.View.GONE
                if (probe.formats.isEmpty()) {
                    advancedEmptyText.visibility = android.view.View.VISIBLE
                    return@launch
                }

                // Highest quality first -- video streams (by height, then
                // fps) ahead of audio-only, matching the standard ladder's
                // high-to-low ordering above.
                val sorted = probe.formats.sortedWith(
                    compareByDescending<YtDlpManager.ProbedFormat> { it.height ?: -1 }
                        .thenByDescending { it.fps ?: -1 }
                )
                sorted.forEach { format ->
                    val sizeText = YtDlpManager.formatSize(format, probe.durationSeconds)
                    val label = buildString {
                        if (format.height != null) append("${format.height}p") else append("Audio")
                        if (format.fps != null) append(" ${format.fps}fps")
                        append(" · ${format.ext.uppercase()}")
                        if (format.vcodec != null) append(" · ${format.vcodec.substringBefore('.')}")
                        if (sizeText != null) append(" · $sizeText")
                    }
                    advancedGroup.addView(buildRow(label, format.isAudioOnly, format))
                }
            }

            advancedHeader.setOnClickListener {
                val expanding = advancedContent.visibility != android.view.View.VISIBLE
                advancedContent.visibility = if (expanding) android.view.View.VISIBLE else android.view.View.GONE
                advancedChevron.animate().rotation(if (expanding) 180f else 0f).setDuration(150).start()
            }

            val dialog = MaterialAlertDialogBuilder(this)
                .setTitle(item.fileName ?: "Choose quality")
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    // Advanced list wins if the user picked a row there --
                    // its RadioGroup and the standard qualityGroup are
                    // separate groups (independent selection state), so
                    // whichever one actually has a checked row is the
                    // user's real choice; advanced is checked first since
                    // picking there is the more deliberate, overriding action.
                    val advancedChecked = advancedGroup.findViewById<RadioButton>(advancedGroup.checkedRadioButtonId)
                    val checkedTag = advancedChecked?.tag
                        ?: group.findViewById<RadioButton>(group.checkedRadioButtonId)?.tag
                    val resolved = when (checkedTag) {
                        is YtDlpManager.QualityOption -> checkedTag
                        is YtDlpManager.ProbedFormat -> YtDlpManager.QualityOption(
                            label = checkedTag.formatId,
                            formatSelector = YtDlpManager.advancedSelector(checkedTag),
                            isAudioOnly = checkedTag.isAudioOnly
                        )
                        else -> null
                    }
                    cont.resume(resolved)
                }
                .setOnCancelListener { cont.resume(null) }
                .setNegativeButton(R.string.action_cancel) { d, _ -> d.cancel() }
                .create()
            cont.invokeOnCancellation {
                probeCallbackAlive = false
                dialog.dismiss()
            }
            dialog.show()
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
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    /** Search only makes sense on the Downloads tab (filters the queue) --
     *  hidden everywhere else. Re-run via invalidateOptionsMenu() from the
     *  bottomNav item-selected listener whenever the tab changes. */
    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_search)?.isVisible = currentTabTag == TAG_DOWNLOADS
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_settings) { openSettingsScreen(); return true }
        if (item.itemId == R.id.action_search) {
            (supportFragmentManager.findFragmentByTag(TAG_DOWNLOADS) as? DownloadsFragment)?.toggleSearch()
            return true
        }
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
    }

    /**
     * Flips dark/light mode for whichever color theme is currently active --
     * triggered by tapping the toolbar title. Same pattern as the
     * duplicate in SettingsAppearanceFragment.toggleDarkMode() (used there
     * for the Dark Mode switch in Settings > Appearance): save the pick,
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
        private const val TAG_HISTORY   = "history"
        private const val TAG_BOOKMARKS = "bookmarks"
    }
}
