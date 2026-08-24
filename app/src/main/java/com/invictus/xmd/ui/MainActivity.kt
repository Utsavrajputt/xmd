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
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.invictus.xmd.R
import com.invictus.xmd.BuildConfig
import com.invictus.xmd.core.BookmarkRepository
import com.invictus.xmd.core.DownloadCategory
import com.invictus.xmd.core.ItemStatus
import com.invictus.xmd.core.LinkParser
import com.invictus.xmd.core.MediaPlatform
import com.invictus.xmd.core.QueueItem
import com.invictus.xmd.core.QueueRepository
import com.invictus.xmd.core.ResolutionError
import com.invictus.xmd.core.Settings
import com.invictus.xmd.core.YtDlpManager
import com.invictus.xmd.ui.theme.AppTheme
import com.invictus.xmd.service.DownloadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class MainActivity : AppCompatActivity(), HomeFragment.Callbacks, DownloadsFragment.Callbacks, BrowserFragment.Callbacks, HistoryFragment.Callbacks {

    private lateinit var bottomNav: BottomNavigationView
    private lateinit var toolbar: androidx.appcompat.widget.Toolbar

    // ── Swipe-to-switch-tabs (bottom nav) ───────────────────────────────
    // Was previously wired into the Browser fragment's WebView (switching
    // between open website tabs there); moved here so a fast horizontal
    // swipe anywhere in the app instead switches between the Home/Browser/
    // Downloads bottom nav destinations. Living in dispatchTouchEvent (as
    // opposed to a per-view touch listener) is what lets it see swipes
    // that happen over a WebView or RecyclerView -- those views consume
    // their own touch events before a listener on a parent view would ever
    // see them, but the Activity sees every touch event first regardless.
    // Deliberately conservative (2x horizontal-over-vertical dominance + a
    // minimum distance) so it doesn't fire on ordinary vertical scrolling,
    // and it never consumes the event (super.dispatchTouchEvent still runs
    // unconditionally below), so normal scrolling/tapping/zooming is
    // completely unaffected.
    private val bottomNavSwipeOrder = listOf(R.id.nav_home, R.id.nav_browser, R.id.nav_downloads)

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
        syncToolbarWithVisibleFragment()
    }

    private fun syncToolbarWithVisibleFragment() {
        val fm = supportFragmentManager
        val browserVisible = fm.findFragmentByTag(TAG_BROWSER)?.isHidden == false
        if (browserVisible) {
            // The Browser fragment's own address bar is the top bar here --
            // the shared app toolbar (and its title) would just duplicate it.
            toolbar.visibility = android.view.View.GONE
            return
        }
        toolbar.visibility = android.view.View.VISIBLE
        val downloadsVisible = fm.findFragmentByTag(TAG_DOWNLOADS)?.isHidden == false
        supportActionBar?.title = if (downloadsVisible) "Downloads" else getString(R.string.app_header_title)
    }

    // ── onCreate ──────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate() -- Activity.setTheme() only takes
        // effect if called before the window/decor is created.
        setTheme(Settings.appTheme().resolvedStyleRes(Settings.isDarkMode()))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        this.toolbar = toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.title = getString(R.string.app_header_title)

        // Tap the header to flip dark/light mode for whichever color theme
        // is active. The Toolbar's title isn't a separately clickable view,
        // so a plain OnClickListener on the Toolbar itself already catches
        // taps anywhere across it (including over the title text).
        toolbar.setOnClickListener { toggleDarkMode() }

        // Add fragments only on a fresh start (not after config-change)
        if (savedInstanceState == null) {
            val home      = HomeFragment()
            val browser   = BrowserFragment()
            val downloads = DownloadsFragment()
            supportFragmentManager.beginTransaction()
                .add(R.id.fragmentContainer, home,      TAG_HOME)
                .add(R.id.fragmentContainer, browser,   TAG_BROWSER)
                .add(R.id.fragmentContainer, downloads, TAG_DOWNLOADS)
                .hide(browser)
                .hide(downloads)   // Home is the initial tab
                .commit()
        }

        bottomNav = findViewById(R.id.bottomNav)
        bottomNav.setOnItemSelectedListener { item ->
            // History is layered on top via addToBackStack (see openHistoryScreen)
            // -- showFragment() below only shows/hides the three base tab
            // fragments underneath it, so without popping first, switching
            // tabs (by tap OR the swipe gesture) left History visibly stuck
            // on top no matter which tab got selected underneath.
            if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
            }
            when (item.itemId) {
                R.id.nav_home -> {
                    showFragment(TAG_HOME)
                    toolbar.visibility = android.view.View.VISIBLE
                    supportActionBar?.title = getString(R.string.app_header_title)
                }
                R.id.nav_browser -> {
                    showFragment(TAG_BROWSER)
                    // The Browser fragment's own address bar is the top bar here
                    // (with its own reload/tabs/overflow controls) -- the shared
                    // app toolbar (and its "Browser" title) would just duplicate it.
                    toolbar.visibility = android.view.View.GONE
                }
                R.id.nav_downloads -> {
                    showFragment(TAG_DOWNLOADS)
                    toolbar.visibility = android.view.View.VISIBLE
                    supportActionBar?.title = "Downloads"
                }
            }
            true
        }

        // Back handling, gesture or button:
        //  1. Browser tab with page history / a loaded page -> step back
        //     through it (or back to the speed dial). Handled entirely by
        //     BrowserFragment.onBackPressed().
        //  2. Browser tab already on the speed dial (or any other tab) ->
        //     jump to the Home tab, rather than falling straight through to
        //     the default behavior and closing the app. This is what used to
        //     be missing: searching or opening a site, then going back, used
        //     to exit the app outright instead of landing on Home.
        //  3. Already on the Home tab -> default behavior (exits the app).
        onBackPressedDispatcher.addCallback(this) {
            // History (or any other overlay screen added via addToBackStack)
            // is on top -- pop it first, same as it would in any other app.
            if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStack()
                return@addCallback
            }
            val browser = supportFragmentManager.findFragmentByTag(TAG_BROWSER) as? BrowserFragment
            if (browser?.isVisible == true && browser.onBackPressed()) {
                return@addCallback
            }
            if (bottomNav.selectedItemId != R.id.nav_home) {
                bottomNav.selectedItemId = R.id.nav_home
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
            val badge = bottomNav.getOrCreateBadge(R.id.nav_downloads)
            if (active > 0) {
                badge.isVisible = true
                badge.number    = active
            } else {
                badge.isVisible = false
            }
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

        bottomNav.selectedItemId = R.id.nav_home

        val needsPrepare = LinkParser.isShareLink(url) || LinkParser.isFitgirlPage(url)
        if (needsPrepare) {
            triggerPrepare(listOf(url))
        } else {
            triggerDownloadDirect(listOf(url))
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
        val home      = fm.findFragmentByTag(TAG_HOME)      ?: return
        val browser   = fm.findFragmentByTag(TAG_BROWSER)   ?: return
        val downloads = fm.findFragmentByTag(TAG_DOWNLOADS) ?: return
        fm.beginTransaction().apply {
            when (tag) {
                TAG_HOME      -> { show(home); hide(browser); hide(downloads) }
                TAG_BROWSER   -> { hide(home); show(browser); hide(downloads) }
                else          -> { hide(home); hide(browser); show(downloads) }
            }
        }.commit()
    }

    // ── HomeFragment.Callbacks ────────────────────────────────────────────

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

    override fun triggerDownloadDirect(lines: List<String>) {
        QueueRepository.setLinks(lines)
        val (youtubeLines, otherLines) = lines.partition { LinkParser.isYoutubeLink(it) }

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

    override fun triggerDownloadTorrentFile(uri: Uri, displayName: String?) {
        val link = uri.toString()
        QueueRepository.setLinks(listOf(link))
        val item = QueueRepository.current().firstOrNull { it.sourceUrl == link }
        if (item != null) {
            QueueRepository.update(item.id) {
                it.copy(directUrl = link, status = ItemStatus.READY, fileName = displayName ?: it.fileName)
            }
        }
        DownloadService.start(this)
        showDownloadStartedSnackbar()
    }

    /**
     * Downloads kick off in the background with no screen change, so without
     * this the user has no confirmation anything happened. Mirrors the
     * "Starting download… VIEW" pattern from stock browsers: a brief
     * Snackbar with a VIEW action that jumps straight to the Downloads tab.
     */
    private fun showDownloadStartedSnackbar() {
        Snackbar.make(
            findViewById(R.id.fragmentContainer),
            R.string.download_started_toast,
            Snackbar.LENGTH_LONG
        ).setAction(R.string.action_view) {
            findViewById<BottomNavigationView>(R.id.bottomNav).selectedItemId = R.id.nav_downloads
        }.show()
    }

    // ── BrowserFragment.Callbacks ───────────────────────────────────────────

    // Chrome-style overflow: a PopupMenu anchored directly under the 3-dot
    // button (right-aligned via Gravity.END) instead of a centered
    // AlertDialog, so it drops down from the icon the way Chrome's overflow
    // menu does rather than looking like a generic popup.
    override fun openBrowserMenu(anchor: android.view.View) {
        val popup = androidx.appcompat.widget.PopupMenu(this, anchor, android.view.Gravity.END)
        popup.menuInflater.inflate(R.menu.browser_overflow_menu, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_refresh -> { reloadBrowserTab(); true }
                R.id.menu_private_dns -> { showDnsSettingsDialog(); true }
                R.id.menu_history -> { openHistoryScreen(); true }
                else -> false
            }
        }
        popup.show()
    }

    private fun reloadBrowserTab() {
        (supportFragmentManager.findFragmentByTag(TAG_BROWSER) as? BrowserFragment)?.reloadActiveTab()
    }

    private fun showDnsSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_dns_settings, null)
        val group = dialogView.findViewById<RadioGroup>(R.id.dnsModeGroup)
        val optionAdguard = dialogView.findViewById<RadioButton>(R.id.dnsOptionAdguard)
        val optionOff = dialogView.findViewById<RadioButton>(R.id.dnsOptionOff)
        val optionCustom = dialogView.findViewById<RadioButton>(R.id.dnsOptionCustom)
        val customUrlInput = dialogView.findViewById<EditText>(R.id.dnsCustomUrlInput)
        val customUrlLayout = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.dnsCustomUrlLayout)

        when (Settings.dnsMode()) {
            Settings.DnsMode.ADGUARD -> optionAdguard.isChecked = true
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
        findViewById<BottomNavigationView>(R.id.bottomNav).selectedItemId = R.id.nav_browser
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
            (LinkParser.isYoutubeLink(item.sourceUrl) && item.mediaFormatSelector == null)
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
        if (LinkParser.isYoutubeLink(item.sourceUrl)) {
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
        } else {
            QueueRepository.update(item.id) {
                it.copy(status = ItemStatus.FAILED, error = error ?: "Could not resolve link")
            }
        }
    }

    // ── YouTube resolve (quality picker, no challenge/webview involved) ────

    /**
     * YouTube items skip the FuckingFast challenge/resolve pipeline
     * entirely -- instead of a directUrl, the user picks a quality here and
     * yt-dlp (DownloadService) resolves + downloads + merges it itself later.
     */
    private suspend fun resolveYoutube(item: QueueItem) {
        if (!BuildConfig.HAS_YOUTUBE_SUPPORT) {
            MaterialAlertDialogBuilder(this)
                .setTitle("YouTube not supported in this build")
                .setMessage("This is the Lite build, which doesn't include YouTube downloads. Download the Full build from the app's Releases page to use this.")
                .setPositiveButton(android.R.string.ok, null)
                .show()
            QueueRepository.update(item.id) {
                it.copy(status = ItemStatus.FAILED, error = "YouTube needs the Full build")
            }
            return
        }
        if (!YtDlpManager.isInstalled(this)) {
            val openSettings = suspendCancellableCoroutine<Boolean> { cont ->
                val dialog = MaterialAlertDialogBuilder(this)
                    .setTitle("yt-dlp not installed")
                    .setMessage("YouTube downloads need the yt-dlp downloader, which isn't installed yet. Install it from Settings first.")
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
            if (openSettings) showSettingsDialog()
            return
        }

        val options = YtDlpManager.standardQualityOptions()
        val chosen = suspendCancellableCoroutine<YtDlpManager.QualityOption?> { cont ->
            val dialogView = layoutInflater.inflate(R.layout.dialog_quality_picker, null)
            val group = dialogView.findViewById<RadioGroup>(R.id.qualityGroup)

            options.forEach { option ->
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
                row.text = option.label
                row.isClickable = true
                row.buttonDrawable = null
                row.setBackgroundResource(R.drawable.bg_radio_row_selector)
                row.setTextColor(ContextCompat.getColorStateList(this, R.color.text_radio_row))
                row.textSize = 14f
                row.gravity = android.view.Gravity.CENTER_VERTICAL
                val density = resources.displayMetrics.density
                row.setPadding((16 * density).toInt(), (14 * density).toInt(), (16 * density).toInt(), (14 * density).toInt())
                val startIcon = if (option.isAudioOnly) R.drawable.ic_music_note else R.drawable.ic_video
                row.setCompoundDrawablesWithIntrinsicBounds(startIcon, 0, R.drawable.ic_check_selector, 0)
                row.compoundDrawablePadding = (12 * density).toInt()
                row.tag = option
                row.layoutParams = RadioGroup.LayoutParams(
                    RadioGroup.LayoutParams.MATCH_PARENT,
                    RadioGroup.LayoutParams.WRAP_CONTENT
                )
                group.addView(row)
            }
            // Default selection: the option one below the top of the ladder
            // (1440p) reads as a sane, non-extreme default rather than
            // pre-selecting either end of the quality range.
            (group.getChildAt(1) as? RadioButton)?.isChecked = true

            val dialog = MaterialAlertDialogBuilder(this)
                .setTitle(item.fileName ?: "Choose quality")
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val checked = group.findViewById<RadioButton>(group.checkedRadioButtonId)
                    cont.resume(checked?.tag as? YtDlpManager.QualityOption)
                }
                .setOnCancelListener { cont.resume(null) }
                .setNegativeButton(R.string.action_cancel) { d, _ -> d.cancel() }
                .create()
            cont.invokeOnCancellation { dialog.dismiss() }
            dialog.show()
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_settings) { showSettingsDialog(); return true }
        return super.onOptionsItemSelected(item)
    }

    private fun showSettingsDialog() {
        val view            = layoutInflater.inflate(R.layout.dialog_settings, null)
        setupThemePicker(view.findViewById(R.id.themeSwatchContainer))
        val group           = view.findViewById<RadioGroup>(R.id.connectionsGroup)
        val speedInput      = view.findViewById<EditText>(R.id.speedLimitInput)
        val concurrentInput = view.findViewById<EditText>(R.id.maxConcurrentInput)
        val darkModeSwitch  = view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.darkModeSwitch)
        val autoRetrySwitch = view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.autoRetrySwitch)
        val saveToDownloadsSwitch = view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.saveToDownloadsSwitch)
        val importWebsitesButton = view.findViewById<MaterialButton>(R.id.importWebsitesButton)
        val ytdlpDivider    = view.findViewById<android.view.View>(R.id.ytdlpDivider)
        val ytdlpSection    = view.findViewById<android.view.View>(R.id.ytdlpSection)
        val ytdlpStatus     = view.findViewById<android.widget.TextView>(R.id.ytdlpStatus)
        val ytdlpProgress   = view.findViewById<android.widget.ProgressBar>(R.id.ytdlpProgress)
        val ytdlpButton     = view.findViewById<android.widget.Button>(R.id.ytdlpActionButton)
        val ytdlpUpdateButton  = view.findViewById<android.widget.Button>(R.id.ytdlpUpdateButton)
        val ytdlpNightlyButton = view.findViewById<android.widget.Button>(R.id.ytdlpNightlyButton)

        if (!BuildConfig.HAS_YOUTUBE_SUPPORT) {
            // Lite build has no YtDlpManager to back this section with --
            // hide it entirely rather than show controls that can't do anything.
            ytdlpDivider.visibility = android.view.View.GONE
            ytdlpSection.visibility = android.view.View.GONE
        } else {
            fun refreshYtDlpRow() {
                val installed = YtDlpManager.isInstalled(this)
                ytdlpStatus.text = if (installed) {
                    val channel = getString(
                        if (Settings.ytDlpUseNightly()) R.string.settings_ytdlp_channel_nightly
                        else R.string.settings_ytdlp_channel_stable
                    )
                    "${getString(R.string.settings_ytdlp_status_installed)}  •  $channel"
                } else {
                    getString(R.string.settings_ytdlp_status_not_installed)
                }
                ytdlpButton.setText(if (installed) R.string.settings_ytdlp_delete else R.string.settings_ytdlp_install)
                ytdlpButton.isEnabled = true
                ytdlpUpdateButton.visibility = if (installed) android.view.View.VISIBLE else android.view.View.GONE
                ytdlpUpdateButton.isEnabled = true
                ytdlpUpdateButton.setText(R.string.settings_ytdlp_update)
                ytdlpNightlyButton.visibility = if (installed) android.view.View.VISIBLE else android.view.View.GONE
                ytdlpNightlyButton.isEnabled = true
                // Button always offers switching to the *other* channel --
                // once on nightly, it becomes "back to stable" instead of
                // staying labeled "Use Nightly Build" forever.
                ytdlpNightlyButton.setText(
                    if (Settings.ytDlpUseNightly()) R.string.settings_ytdlp_switch_stable
                    else R.string.settings_ytdlp_use_nightly
                )
                ytdlpProgress.visibility = android.view.View.GONE
            }
            refreshYtDlpRow()

            ytdlpButton.setOnClickListener {
                if (YtDlpManager.isInstalled(this)) {
                    YtDlpManager.delete(this)
                    Toast.makeText(this, "yt-dlp removed", Toast.LENGTH_SHORT).show()
                    refreshYtDlpRow()
                } else {
                    ytdlpButton.isEnabled = false
                    ytdlpProgress.visibility = android.view.View.VISIBLE
                    ytdlpStatus.setText(R.string.settings_ytdlp_installing)
                    lifecycleScope.launch {
                        val error = withContext(Dispatchers.IO) { YtDlpManager.install(this@MainActivity) }
                        // Show the exact failure reason instead of a generic message --
                        // init() only unpacks bundled assets, no network involved, so a
                        // guessed "check your connection" message would usually be wrong.
                        Toast.makeText(
                            this@MainActivity,
                            error?.let { "Install failed: $it" } ?: "yt-dlp installed",
                            Toast.LENGTH_LONG
                        ).show()
                        refreshYtDlpRow()
                    }
                }
            }

            ytdlpUpdateButton.setOnClickListener {
                ytdlpUpdateButton.isEnabled = false
                ytdlpNightlyButton.isEnabled = false
                ytdlpProgress.visibility = android.view.View.VISIBLE
                ytdlpStatus.setText(R.string.settings_ytdlp_updating)
                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) { YtDlpManager.update(this@MainActivity) }
                    Toast.makeText(
                        this@MainActivity,
                        result?.let { "yt-dlp: $it" } ?: "Update failed — check your connection",
                        Toast.LENGTH_LONG
                    ).show()
                    refreshYtDlpRow()
                }
            }

            ytdlpNightlyButton.setOnClickListener {
                val switchingToNightly = !Settings.ytDlpUseNightly()
                ytdlpUpdateButton.isEnabled = false
                ytdlpNightlyButton.isEnabled = false
                ytdlpProgress.visibility = android.view.View.VISIBLE
                ytdlpStatus.setText(
                    if (switchingToNightly) R.string.settings_ytdlp_switching_nightly
                    else R.string.settings_ytdlp_updating
                )
                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        YtDlpManager.switchChannel(this@MainActivity, switchingToNightly)
                    }
                    Toast.makeText(
                        this@MainActivity,
                        result?.let { "yt-dlp: $it" } ?: "Switch failed — check your connection",
                        Toast.LENGTH_LONG
                    ).show()
                    refreshYtDlpRow()
                }
            }
        }

        val idForConnections = mapOf(
            2 to R.id.conn2, 4 to R.id.conn4, 8 to R.id.conn8, 16 to R.id.conn16
        )
        (view.findViewById<RadioButton>(
            idForConnections[Settings.connectionsPerDownload()] ?: R.id.conn4
        )).isChecked = true
        speedInput.setText(Settings.speedLimitKBps().toString())
        concurrentInput.setText(Settings.maxConcurrentDownloads().toString())
        autoRetrySwitch.isChecked = Settings.autoRetryEnabled()
        saveToDownloadsSwitch.isChecked = Settings.saveToDownloadsFolder()

        // Applies immediately (like the color swatches above it) instead of
        // waiting for Save, since flipping it needs a recreate() anyway --
        // the guard against the initial isChecked assignment re-triggering
        // itself is redundant here (setChecked before the listener is
        // attached doesn't fire it), kept only for safety.
        darkModeSwitch.isChecked = Settings.isDarkMode()
        darkModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != Settings.isDarkMode()) toggleDarkMode()
        }

        importWebsitesButton.setOnClickListener { startWebImportFlow() }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_title)
            .setView(view)
            .setPositiveButton(R.string.settings_save) { _, _ ->
                val checkedId = group.checkedRadioButtonId
                val connections = idForConnections.entries
                    .firstOrNull { it.value == checkedId }?.key ?: 4
                Settings.setConnectionsPerDownload(connections)
                Settings.setSpeedLimitKBps(speedInput.text?.toString()?.toIntOrNull() ?: 0)
                Settings.setMaxConcurrentDownloads(concurrentInput.text?.toString()?.toIntOrNull() ?: 2)
                Settings.setAutoRetryEnabled(autoRetrySwitch.isChecked)
                Settings.setSaveToDownloadsFolder(saveToDownloadsSwitch.isChecked)
                Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Fills the horizontal theme picker row in the settings dialog with one
     * swatch per [AppTheme]. Tapping a swatch applies it immediately --
     * saves the pick, dismisses the settings dialog (it belongs to this
     * Activity instance and would be torn down by recreate() anyway), and
     * recreates the Activity so the new colorPrimary/colorSurface/etc.
     * actually take effect (a theme is only read in onCreate, before
     * super.onCreate()).
     */
    /**
     * Flips dark/light mode for whichever [AppTheme] color theme is
     * currently active -- tap the app header, or use the Dark Mode switch
     * in Settings > Appearance. Same pattern as picking a new color theme:
     * save the pick, then `recreate()` since a theme is only read in
     * `onCreate()`, before `super.onCreate()`.
     */
    private fun toggleDarkMode() {
        val nowDark = !Settings.isDarkMode()
        Settings.setDarkMode(nowDark)
        Toast.makeText(
            this,
            if (nowDark) getString(R.string.theme_mode_dark) else getString(R.string.theme_mode_light),
            Toast.LENGTH_SHORT,
        ).show()
        recreate()
    }

    private fun setupThemePicker(container: android.widget.LinearLayout) {
        container.removeAllViews()
        val current = Settings.appTheme()
        val dp8 = (8 * resources.displayMetrics.density).toInt()

        fun circleDrawable(colorHex: String) = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(android.graphics.Color.parseColor(colorHex))
        }

        fun roundRectDrawable(colorHex: String, radiusDp: Float, strokeColor: Int? = null, strokeWidthPx: Int = 0) =
            android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = radiusDp * resources.displayMetrics.density
                setColor(android.graphics.Color.parseColor(colorHex))
                if (strokeColor != null) setStroke(strokeWidthPx, strokeColor)
            }

        AppTheme.entries.forEach { theme ->
            val item = layoutInflater.inflate(R.layout.item_theme_swatch, container, false)
            val ring = item.findViewById<android.widget.FrameLayout>(R.id.swatchRing)
            val box = item.findViewById<android.widget.FrameLayout>(R.id.swatchBox)
            val dotPrimary = item.findViewById<android.view.View>(R.id.dotPrimary)
            val dotSecondary = item.findViewById<android.view.View>(R.id.dotSecondary)
            val dotTertiary = item.findViewById<android.view.View>(R.id.dotTertiary)
            val checkIcon = item.findViewById<android.widget.ImageView>(R.id.checkIcon)
            val nameView = item.findViewById<android.widget.TextView>(R.id.themeName)

            val isSelected = theme == current
            val ringStrokePx = (2 * resources.displayMetrics.density).toInt()
            ring.background = roundRectDrawable(
                colorHex = "#00000000",
                radiusDp = 16f,
                strokeColor = if (isSelected) android.graphics.Color.parseColor(theme.swatchPrimary) else android.graphics.Color.TRANSPARENT,
                strokeWidthPx = ringStrokePx,
            )
            box.background = roundRectDrawable(theme.swatchBackground, 13f)
            dotPrimary.background = circleDrawable(theme.swatchPrimary)
            dotSecondary.background = circleDrawable(theme.swatchSecondary)
            dotTertiary.background = circleDrawable(theme.swatchTertiary)
            checkIcon.visibility = if (isSelected) android.view.View.VISIBLE else android.view.View.GONE
            checkIcon.setColorFilter(android.graphics.Color.parseColor(theme.swatchPrimary))

            nameView.text = getString(theme.titleRes)
            // Was hardcoded to R.color.m3_on_surface (a light-on-dark gray),
            // so it went near-invisible against a light-theme dialog
            // background. Resolve colorOnSurface from whichever theme is
            // actually active instead, same as everything else in this
            // dialog.
            nameView.setTextColor(MaterialColors.getColor(nameView, com.google.android.material.R.attr.colorOnSurface))
            nameView.setTypeface(nameView.typeface, if (isSelected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)

            item.setOnClickListener {
                if (theme != Settings.appTheme()) {
                    Settings.setAppTheme(theme)
                    Toast.makeText(this, getString(theme.titleRes), Toast.LENGTH_SHORT).show()
                    recreate()
                }
            }

            container.addView(item, android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = dp8 })
        }
    }

    // ── Website source pack import (Settings -> Import Websites) ────────

    /**
     * Scans for any xmdweb source-pack file and lets the user pick which
     * one to import -- no auto-popup on launch, and no file picker either;
     * just a scan + list. Called only from the "Import Now" button in
     * Settings. Scoped to Downloads, Xmd, and WhatsApp Documents (incl.
     * subfolders) rather than all of storage, so it can take a moment on a
     * phone with a lot of WhatsApp history -- a quick toast sets that
     * expectation before the scan starts.
     */
    private fun startWebImportFlow() {
        Toast.makeText(this, R.string.import_websites_scanning, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val files = withContext(Dispatchers.IO) { BookmarkRepository.findImportCandidates() }
            if (files.isEmpty()) {
                Toast.makeText(this@MainActivity, R.string.import_websites_not_found, Toast.LENGTH_LONG).show()
            } else {
                showImportCandidatesDialog(files)
            }
        }
    }

    private fun showImportCandidatesDialog(files: List<File>) {
        val storageRoot = Environment.getExternalStorageDirectory().path
        val labels = files.map { it.path.removePrefix(storageRoot).trimStart('/') }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.import_websites_title)
            .setItems(labels) { _, which -> runWebImport(files[which]) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun runWebImport(file: File) {
        lifecycleScope.launch {
            val result = BookmarkRepository.importWebsites(file)
            val message = if (result.imported > 0) {
                getString(R.string.import_websites_success, result.imported)
            } else {
                getString(R.string.import_websites_none_new)
            }
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
        }
    }

    // ── Constants ─────────────────────────────────────────────────────────

    companion object {
        private const val TAG_HOME      = "home"
        private const val TAG_BROWSER   = "browser"
        private const val TAG_DOWNLOADS = "downloads"
        private const val TAG_HISTORY   = "history"
    }
}
