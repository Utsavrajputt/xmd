package com.invictus.xmd.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.invictus.xmd.FfApp
import com.invictus.xmd.R
import com.composables.icons.materialsymbols.roundedfilled.R as MaterialSymbols
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import com.invictus.xmd.database.entities.QueueItem
import com.invictus.xmd.domain.download.CategoryDetector
import com.invictus.xmd.domain.download.DownloadCancelledException
import com.invictus.xmd.domain.download.DownloadCategory
import com.invictus.xmd.domain.download.DownloadEngine
import com.invictus.xmd.domain.download.ItemStatus
import com.invictus.xmd.domain.download.MediaPlatform
import com.invictus.xmd.domain.download.YtDlpManager
import com.invictus.xmd.domain.torrent.TorrentEngine
import com.invictus.xmd.network.NetworkMonitor
import com.invictus.xmd.preferences.Settings
import com.invictus.xmd.repository.QueueRepository
import com.invictus.xmd.ui.MainActivity
import com.invictus.xmd.ui.downloads.DownloadsScreen
import com.invictus.xmd.ui.downloads.QueueItemRow
import com.invictus.xmd.ui.home.HomeFragment
import com.invictus.xmd.utils.ErrorUtils
import com.invictus.xmd.utils.LinkParser
import com.invictus.xmd.utils.formatRemainingTimeChrome

/**
 * Runs the download queue with up to [Settings.maxConcurrentDownloads] items
 * downloading in parallel, each with its own independently pause/resume/
 * cancel-able DownloadEngine, showing an aggregate progress notification.
 */
class DownloadService : LifecycleService() {

    companion object {
        const val ACTION_START = "com.invictus.xmd.action.START"
        const val ACTION_PAUSE_ITEM = "com.invictus.xmd.action.PAUSE_ITEM"
        const val ACTION_RESUME_ITEM = "com.invictus.xmd.action.RESUME_ITEM"
        const val ACTION_PAUSE_ALL = "com.invictus.xmd.action.PAUSE_ALL"
        const val ACTION_RESUME_ALL = "com.invictus.xmd.action.RESUME_ALL"
        const val ACTION_CANCEL_ITEM = "com.invictus.xmd.action.CANCEL_ITEM"
        const val ACTION_CANCEL_ALL = "com.invictus.xmd.action.CANCEL_ALL"
        const val ACTION_WIFI_ONLY_ENABLED = "com.invictus.xmd.action.WIFI_ONLY_ENABLED"
        const val EXTRA_ITEM_ID = "extra_item_id"
        const val EXTRA_ITEM_IDS = "extra_item_ids"
        private const val NOTIFICATION_ID = 42
        private const val DATA_LIMIT_NOTIFICATION_ID = 43
        private const val BETWEEN_CLAIM_DELAY_MS = 500L
        private const val MAX_AUTO_RETRIES = 3
        private const val NOTIFY_THROTTLE_MS = 500L
        private const val ETA_HOLD_MS = 1_000L

        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java).setAction(ACTION_START)
            context.startForegroundService(intent)
        }

        fun pauseItem(context: Context, itemId: String) {
            context.startService(
                Intent(context, DownloadService::class.java)
                    .setAction(ACTION_PAUSE_ITEM)
                    .putExtra(EXTRA_ITEM_ID, itemId)
            )
        }

        fun resumeItem(context: Context, itemId: String) {
            context.startService(
                Intent(context, DownloadService::class.java)
                    .setAction(ACTION_RESUME_ITEM)
                    .putExtra(EXTRA_ITEM_ID, itemId)
            )
        }

        /** Pauses every id in [itemIds] -- caller (DownloadsScreen's overflow
         *  menu) decides the scope, e.g. only the items visible under the
         *  currently selected filter tab + search query. */
        fun pauseAll(context: Context, itemIds: List<String>) {
            if (itemIds.isEmpty()) return
            context.startService(
                Intent(context, DownloadService::class.java)
                    .setAction(ACTION_PAUSE_ALL)
                    .putStringArrayListExtra(EXTRA_ITEM_IDS, ArrayList(itemIds))
            )
        }

        /** Resumes every id in [itemIds] -- same scoping story as [pauseAll]. */
        fun resumeAll(context: Context, itemIds: List<String>) {
            if (itemIds.isEmpty()) return
            context.startService(
                Intent(context, DownloadService::class.java)
                    .setAction(ACTION_RESUME_ALL)
                    .putStringArrayListExtra(EXTRA_ITEM_IDS, ArrayList(itemIds))
            )
        }

        fun cancelItem(context: Context, itemId: String) {
            context.startService(
                Intent(context, DownloadService::class.java)
                    .setAction(ACTION_CANCEL_ITEM)
                    .putExtra(EXTRA_ITEM_ID, itemId)
            )
        }

        fun cancelAll(context: Context) {
            context.startService(Intent(context, DownloadService::class.java).setAction(ACTION_CANCEL_ALL))
        }

        /** Called right after the Wi-Fi-only setting is flipped ON from
         *  Settings while already on cellular -- see [onWifiLost] for the
         *  actual pause logic, this just routes to it via the running service. */
        fun pauseForWifiOnly(context: Context) {
            context.startService(Intent(context, DownloadService::class.java).setAction(ACTION_WIFI_ONLY_ENABLED))
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        // Force HTTP/1.1. If the server (often Cloudflare/CDN-backed, like
        // dl.fuckingfast.co) speaks HTTP/2, OkHttp will silently multiplex ALL
        // of our "parallel" segment requests over ONE physical TCP connection
        // -- so raising `connections` to 8/16 did nothing for real throughput,
        // it was still one TCP flow with one congestion window. Disabling H2
        // forces each segment onto its own genuine TCP connection, which is
        // what actually unlocks parallel bandwidth on cellular networks (this
        // is the same trick IDM / Chrome's own parallel downloader rely on).
        .protocols(listOf(Protocol.HTTP_1_1))
        // OkHttp's default Dispatcher caps concurrent requests to the SAME
        // host at 5. With up to 16 segments hitting one host, the extras
        // would queue behind the default limit instead of running in
        // parallel -- this raises the ceiling so all segments actually run
        // concurrently now that they're on separate HTTP/1.1 connections.
        .dispatcher(Dispatcher().apply {
            maxRequestsPerHost = 32
            maxRequests = 64
        })
        // Bigger pool of kept-alive connections so segment requests reuse
        // warm sockets instead of paying a fresh TCP+TLS handshake each time.
        .connectionPool(ConnectionPool(32, 5, TimeUnit.MINUTES))
        .build()

    /** Active engines keyed by queue item id, so per-item controls can target the right download. */
    private val engines = ConcurrentHashMap<String, DownloadEngine>()

    /** Same idea as [engines], for magnet/.torrent items running through TorrentEngine instead. */
    private val torrentEngines = ConcurrentHashMap<String, TorrentEngine>()

    // Number of worker loops currently alive. Workers exit their loop the
    // moment claimNextReady() returns null (nothing READY *right now*) --
    // previously that meant a single ACTION_START only ever spun up workers
    // once, so an item that became READY *after* the workers had already
    // exhausted the queue (e.g. it was still resolving) would sit at READY
    // forever: no live worker left to claim it, and onStartCommand refused
    // to launch more because a stale `runJob` still looked "active" while
    // the other worker(s) were mid-download.
    //
    // Fix: track live worker count directly, and let every ACTION_START
    // top the count back up to Settings.maxConcurrentDownloads() -- so
    // pressing "Download ready files" again (or any other ACTION_START,
    // e.g. right after a link finishes resolving) always has a chance to
    // spawn a fresh worker for anything newly READY, even while other
    // downloads are still in flight.
    private val activeWorkers = java.util.concurrent.atomic.AtomicInteger(0)

    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null
    private var internetCallback: android.net.ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        networkCallback = NetworkMonitor.register(
            context = this,
            onWifiAvailable = { onWifiRegained() },
            onWifiLost = { onWifiLost() }
        )
        internetCallback = NetworkMonitor.registerInternet(
            context = this,
            onAvailable = { onInternetRegained() },
            onLost = { onInternetLost() }
        )
    }

    override fun onDestroy() {
        networkCallback?.let { NetworkMonitor.unregister(this, it) }
        networkCallback = null
        internetCallback?.let { NetworkMonitor.unregister(this, it) }
        internetCallback = null
        super.onDestroy()
    }

    /** Total internet outage (airplane mode, no signal, router down --
     *  regardless of the Wi-Fi-only setting, which only cares about Wi-Fi
     *  specifically). Pause every live download in place as "Waiting for
     *  network", the same shape as [onWifiLost] but for any transport. */
    private fun onInternetLost() {
        val live = QueueRepository.current()
            .filter { it.status == ItemStatus.DOWNLOADING || it.status == ItemStatus.RETRYING }
        live.forEach { item ->
            if (item.platform == MediaPlatform.YOUTUBE) {
                networkWaitingYoutubeIds.add(item.id)
                cancelledYoutubeIds.add(item.id)
                YtDlpManager.cancel(item.id)
            } else {
                engines[item.id]?.pause()
                torrentEngines[item.id]?.pause()
                QueueRepository.update(item.id) {
                    it.copy(status = ItemStatus.PAUSED, error = Settings.NETWORK_WAIT_MARKER)
                }
            }
        }
        if (live.isNotEmpty()) updateNotification()
    }

    /** Internet is back (any transport) -- resume anything auto-paused for
     *  a total outage and top workers back up so anything still READY gets
     *  picked up automatically, no manual Retry needed (mirrors Chrome). */
    private fun onInternetRegained() {
        val autoPaused = QueueRepository.current()
            .filter { it.status == ItemStatus.PAUSED && it.error == Settings.NETWORK_WAIT_MARKER }
        autoPaused.forEach { item ->
            val liveEngine = engines[item.id] != null || torrentEngines[item.id] != null
            if (liveEngine) {
                engines[item.id]?.resume()
                torrentEngines[item.id]?.resume()
                QueueRepository.update(item.id) { it.copy(status = ItemStatus.DOWNLOADING, error = null) }
            } else {
                QueueRepository.update(item.id) { it.copy(status = ItemStatus.READY, error = null) }
            }
        }
        val hadWaitingYoutube = networkWaitingYoutubeIds.isNotEmpty()
        if (autoPaused.isNotEmpty() || hadWaitingYoutube) {
            startForeground(NOTIFICATION_ID, buildNotification())
            topUpWorkers()
            updateNotification()
        }
    }

    /** YouTube item ids cancelled by [onInternetLost] specifically -- same
     *  idea as [wifiWaitingYoutubeIds] but for a total outage, so their
     *  catch block in [downloadYoutube] knows to land on READY, not FAILED. */
    private val networkWaitingYoutubeIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** Wi-Fi dropped (or vanished entirely) while Wi-Fi-only downloads is ON --
     *  pause every live download in place, marking each with [Settings.WIFI_WAIT_MARKER]
     *  so [onWifiRegained] knows to resume exactly these and nothing the user
     *  paused by hand. YouTube has no native pause, so its items are cancelled
     *  and routed back to READY instead -- same recovery path already used for
     *  a dead engine in ACTION_RESUME_ITEM. */
    private fun onWifiLost() {
        if (!Settings.wifiOnlyDownloads()) return
        val live = QueueRepository.current().filter { it.status == ItemStatus.DOWNLOADING }
        live.forEach { item ->
            if (item.platform == MediaPlatform.YOUTUBE) {
                wifiWaitingYoutubeIds.add(item.id)
                cancelledYoutubeIds.add(item.id)
                YtDlpManager.cancel(item.id)
            } else {
                engines[item.id]?.pause()
                torrentEngines[item.id]?.pause()
                QueueRepository.update(item.id) {
                    it.copy(status = ItemStatus.PAUSED, error = Settings.WIFI_WAIT_MARKER)
                }
            }
        }
        if (live.isNotEmpty()) updateNotification()
    }

    /** True if the daily data limit is enabled and today's usage for its
     *  configured scope (mobile-only, Wi-Fi-only, or total) has already
     *  reached it. Cheap SharedPreferences reads only -- no TrafficStats
     *  syscall unless [DataUsageTracker.onTick] has been called at least
     *  once this session, which [checkDataLimit] does on every throttled
     *  progress tick. */
    private fun isDataLimitReached(): Boolean {
        if (!Settings.dataLimitEnabled()) return false
        val usage = when (Settings.dataLimitScope()) {
            Settings.DataLimitScope.MOBILE -> com.invictus.xmd.network.DataUsageTracker.todayMobileBytes()
            Settings.DataLimitScope.WIFI -> com.invictus.xmd.network.DataUsageTracker.todayWifiBytes()
            Settings.DataLimitScope.TOTAL -> com.invictus.xmd.network.DataUsageTracker.todayTotalBytes()
        }
        return usage >= Settings.dataLimitBytes()
    }

    /** Marker so [checkDataLimit] only fires the pause-everything routine
     *  once per limit breach, not on every throttled tick while paused. */
    private val dataLimitTriggered = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * Advances [DataUsageTracker]'s mobile-usage accumulator and, the
     * moment the limit is newly reached, pauses every live download in
     * place (marked [Settings.DATA_LIMIT_WAIT_MARKER], same shape as
     * [onWifiLost]/[onInternetLost] but with no auto-resume -- the user is
     * only notified) and stops any further worker from claiming new items
     * via [isDataLimitReached] in [worker]. Called from the same throttled
     * spot as [updateNotificationThrottled] so it shares that ~2x/sec cap
     * instead of running on every raw progress callback.
     */
    private fun checkDataLimit() {
        com.invictus.xmd.network.DataUsageTracker.onTick(NetworkMonitor.isMetered(this))
        if (!isDataLimitReached()) {
            dataLimitTriggered.set(false)
            return
        }
        if (!dataLimitTriggered.compareAndSet(false, true)) return

        val live = QueueRepository.current()
            .filter { it.status == ItemStatus.DOWNLOADING || it.status == ItemStatus.RETRYING }
        live.forEach { item ->
            if (item.platform == MediaPlatform.YOUTUBE) {
                wifiWaitingYoutubeIds.remove(item.id)
                networkWaitingYoutubeIds.remove(item.id)
                dataLimitYoutubeIds.add(item.id)
                cancelledYoutubeIds.add(item.id)
                YtDlpManager.cancel(item.id)
            } else {
                engines[item.id]?.pause()
                torrentEngines[item.id]?.pause()
                QueueRepository.update(item.id) {
                    it.copy(status = ItemStatus.PAUSED, error = Settings.DATA_LIMIT_WAIT_MARKER)
                }
            }
        }
        if (live.isNotEmpty()) {
            updateNotification()
            showDataLimitReachedNotification()
        }
    }

    /** Same idea as [wifiWaitingYoutubeIds] but for the data-limit pause --
     *  kept separate so a YouTube item's catch block (in [downloadYoutube])
     *  can tell which of the three "we cancelled you, not a real failure"
     *  reasons applies and land on the right status/marker. */
    private val dataLimitYoutubeIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** One-shot heads-up notification (separate from the persistent
     *  download-progress notification) telling the user why everything
     *  just stopped -- there's no auto-resume for this one, so a silent
     *  pause would look like a stall/hang with no explanation. */
    private fun showDataLimitReachedNotification() {
        val manager = getSystemService(android.app.NotificationManager::class.java) ?: return
        val notification = NotificationCompat.Builder(this, FfApp.DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_pause)
            .setContentTitle(getString(R.string.settings_data_limit_reached_title))
            .setContentText(getString(R.string.settings_data_limit_reached_body))
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
        manager.notify(DATA_LIMIT_NOTIFICATION_ID, notification)
    }

    /** Wi-Fi is back (or Wi-Fi-only was never on) -- resume anything this
     *  service auto-paused for it, and top workers back up so anything still
     *  READY (or just re-queued from a cancelled YouTube item above) gets picked up. */
    private fun onWifiRegained() {
        val autoPaused = QueueRepository.current()
            .filter { it.status == ItemStatus.PAUSED && it.error == Settings.WIFI_WAIT_MARKER }
        autoPaused.forEach { item ->
            val liveEngine = engines[item.id] != null || torrentEngines[item.id] != null
            if (liveEngine) {
                engines[item.id]?.resume()
                torrentEngines[item.id]?.resume()
                QueueRepository.update(item.id) { it.copy(status = ItemStatus.DOWNLOADING, error = null) }
            } else {
                // Process died while waiting -- same fallback as a dead-engine
                // resume: back to READY so a fresh worker re-claims it and
                // downloadOne()'s Range header picks up the partial file.
                QueueRepository.update(item.id) { it.copy(status = ItemStatus.READY, error = null) }
            }
        }
        // YouTube items: their cancel() call from onWifiLost() is async and
        // lands in downloadYoutube()'s catch block, which handles the
        // READY transition itself (see wifiWaitingYoutubeIds there) --
        // nothing to requeue here, just make sure a worker exists to pick
        // them up once that catch block runs.
        val hadWifiWaitingYoutube = wifiWaitingYoutubeIds.isNotEmpty()
        if (autoPaused.isNotEmpty() || hadWifiWaitingYoutube) {
            startForeground(NOTIFICATION_ID, buildNotification())
            topUpWorkers()
            updateNotification()
        }
    }

    /** YouTube item ids cancelled by [onWifiLost] specifically -- distinct
     *  from [cancelledYoutubeIds] (which also covers a real user Cancel and
     *  routes to FAILED) so these instead land back at READY once Wi-Fi returns. */
    private val wifiWaitingYoutubeIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                topUpWorkers()
            }
            ACTION_PAUSE_ITEM -> intent.getStringExtra(EXTRA_ITEM_ID)?.let { id ->
                pauseSingleItem(id)
                updateNotification()
            }
            ACTION_RESUME_ITEM -> intent.getStringExtra(EXTRA_ITEM_ID)?.let { id ->
                if (resumeSingleItem(id)) {
                    startForeground(NOTIFICATION_ID, buildNotification())
                    topUpWorkers()
                }
                updateNotification()
            }
            ACTION_PAUSE_ALL -> {
                intent.getStringArrayListExtra(EXTRA_ITEM_IDS)?.forEach { id -> pauseSingleItem(id) }
                updateNotification()
            }
            ACTION_RESUME_ALL -> {
                val ids = intent.getStringArrayListExtra(EXTRA_ITEM_IDS).orEmpty()
                // Only call startForeground/topUpWorkers once for the whole
                // batch, not per id -- same effect, no redundant churn.
                val needsTopUp = ids.fold(false) { acc, id -> resumeSingleItem(id) || acc }
                if (needsTopUp) {
                    startForeground(NOTIFICATION_ID, buildNotification())
                    topUpWorkers()
                }
                updateNotification()
            }
            ACTION_CANCEL_ITEM -> intent.getStringExtra(EXTRA_ITEM_ID)?.let { id ->
                val current = QueueRepository.current().firstOrNull { it.id == id }
                if (current?.platform == MediaPlatform.YOUTUBE) {
                    cancelledYoutubeIds.add(id)
                    YtDlpManager.cancel(id)
                } else {
                    engines[id]?.cancel()
                    torrentEngines[id]?.cancel()
                }
                // No live engine to interrupt above -- either mid an auto-retry
                // backoff wait (engine was removed before the delay), or a
                // PAUSED item whose process was killed hours ago and never
                // came back. Either way .cancel() above was a no-op, so mark
                // it cancelled directly here instead of leaving it stuck with
                // a Cancel button that visibly does nothing.
                val noLiveEngine = engines[id] == null && torrentEngines[id] == null
                if (current != null && current.platform != MediaPlatform.YOUTUBE && noLiveEngine &&
                    current.status != ItemStatus.DONE && current.status != ItemStatus.FAILED &&
                    current.status != ItemStatus.READY
                ) {
                    QueueRepository.update(id) { it.copy(status = ItemStatus.FAILED, error = "Cancelled") }
                }
                updateNotification()
            }
            ACTION_WIFI_ONLY_ENABLED -> onWifiLost()
            ACTION_CANCEL_ALL -> {
                engines.values.forEach { it.cancel() }
                torrentEngines.values.forEach { it.cancel() }
                QueueRepository.current()
                    .filter { it.platform == MediaPlatform.YOUTUBE && it.status == ItemStatus.DOWNLOADING }
                    .forEach {
                        cancelledYoutubeIds.add(it.id)
                        YtDlpManager.cancel(it.id)
                    }
                QueueRepository.current().filter { it.status == ItemStatus.RETRYING }.forEach { item ->
                    QueueRepository.update(item.id) { it.copy(status = ItemStatus.FAILED, error = "Cancelled") }
                }
                updateNotification()
            }
        }
        return START_NOT_STICKY
    }

    /** Pauses one item by id -- shared by [ACTION_PAUSE_ITEM] and [ACTION_PAUSE_ALL]. */
    private fun pauseSingleItem(id: String) {
        val current = QueueRepository.current().firstOrNull { it.id == id } ?: return
        if (current.platform == MediaPlatform.YOUTUBE) {
            pausedYoutubeIds.add(id)
            cancelledYoutubeIds.add(id)
            YtDlpManager.cancel(id)
            QueueRepository.update(id) { it.copy(status = ItemStatus.PAUSED, mediaStatusText = null) }
        } else {
            engines[id]?.pause()
            torrentEngines[id]?.pause()
            QueueRepository.update(id) { it.copy(status = ItemStatus.PAUSED) }
        }
    }

    /** Resumes one item by id -- shared by [ACTION_RESUME_ITEM] and [ACTION_RESUME_ALL].
     *  Returns true if the caller needs to (re-)start the foreground
     *  notification and top up workers, i.e. this item had no live engine
     *  left and was routed back through READY/PENDING instead. */
    private fun resumeSingleItem(id: String): Boolean {
        val liveEngine = engines[id] != null || torrentEngines[id] != null
        if (liveEngine) {
            // Same app session, engine's coroutine is still alive and
            // just spinning in its pause-checkpoint loop -- flip the
            // flag and it picks the exact same connection back up.
            engines[id]?.resume()
            torrentEngines[id]?.resume()
            QueueRepository.update(id) { it.copy(status = ItemStatus.DOWNLOADING) }
            return false
        }
        // No live engine -- the process was killed while this item
        // sat paused (very possible over "long hours": Doze,
        // battery optimization, user swipe-kill). There's no
        // coroutine left to un-pause. Route it back through READY
        // instead of marking DOWNLOADING with nothing behind it --
        // a fresh worker claims it and downloadAuto() picks the
        // temp file back up via Range: bytes=<existingSize>-, so
        // already-downloaded bytes aren't wasted.
        val current = QueueRepository.current().firstOrNull { it.id == id }
        return if (current?.directUrl != null || current?.platform == MediaPlatform.YOUTUBE ||
            LinkParser.isTorrentLink(current?.sourceUrl.orEmpty())) {
            QueueRepository.update(id) { it.copy(status = ItemStatus.READY, error = null, mediaStatusText = null) }
            true
        } else {
            // No resolved direct link cached either -- needs a
            // full re-resolve, not just a restarted download.
            QueueRepository.update(id) { it.copy(status = ItemStatus.PENDING, error = null) }
            false
        }
    }

    /** Launches enough fresh worker loops to bring the live count up to the configured max. */
    private fun topUpWorkers() {
        val maxWorkers = Settings.maxConcurrentDownloads().coerceIn(1, 5)
        val toLaunch = maxWorkers - activeWorkers.get()
        if (toLaunch <= 0) return
        repeat(toLaunch) {
            activeWorkers.incrementAndGet()
            lifecycleScope.launch(Dispatchers.IO) {
                worker()
                if (activeWorkers.decrementAndGet() == 0) {
                    withContext(Dispatchers.Main) {
                        ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
    }

    /** Same idea as [engines], for YouTube (yt-dlp) items -- keyed by processId (== item id). */
    private val cancelledYoutubeIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val pausedYoutubeIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private suspend fun worker() {
        while (true) {
            if (Settings.wifiOnlyDownloads() && !NetworkMonitor.isOnWifi(this)) break
            // No internet at all -- don't even attempt to start a fresh item
            // just to have it immediately fail; leave it READY and let
            // onInternetRegained()'s topUpWorkers() spin a worker back up
            // the moment connectivity returns.
            if (!NetworkMonitor.hasInternet(this)) break
            // Daily data limit already reached -- don't start a fresh item;
            // it stays READY, same as the two checks above. No auto-resume
            // path for this one (unlike Wi-Fi/internet): the item sits READY
            // until the user manually retries, which is fine once tomorrow's
            // reset makes isDataLimitReached() false again.
            if (isDataLimitReached()) break
            val item = QueueRepository.claimNextReady() ?: break
            when {
                item.platform == MediaPlatform.YOUTUBE -> downloadYoutube(item)
                LinkParser.isTorrentLink(item.sourceUrl) -> downloadTorrentOne(item.id, item.sourceUrl, item.customSaveDirPath, item.selectedFileIndices)
                else -> downloadOne(item.id, item.sourceUrl, item.directUrl, item.category)
            }
            kotlinx.coroutines.delay(BETWEEN_CLAIM_DELAY_MS)
        }
    }

    // ── YouTube (yt-dlp) download path ──────────────────────────────────
    /**
     * No range downloads, no resume-on-crash, no auto-retry loop here --
     * yt-dlp owns the entire resolve+download+merge process for a YouTube
     * item, and reports plain 0-100% progress instead of bytes. Kept as its
     * own function rather than shoehorned into downloadOne() above since
     * almost nothing (temp-then-move, byte progress, Content-Disposition
     * probing) actually applies to it. Full-flavor only -- see YtDlpManager.
     */
    private suspend fun downloadYoutube(item: QueueItem) {
        val itemId = item.id
        val formatSelector = item.mediaFormatSelector
        val formatLabel = item.mediaFormatLabel
        if (formatSelector == null || formatLabel == null) {
            QueueRepository.update(itemId) {
                it.copy(status = ItemStatus.FAILED, error = "No quality selected")
            }
            return
        }
        if (!YtDlpManager.isInstalled(this)) {
            // Shouldn't normally reach here since MainActivity checks this
            // before ever showing the quality picker -- but guard anyway
            // (e.g. user deleted it from Settings after the item was queued).
            QueueRepository.update(itemId) {
                it.copy(status = ItemStatus.FAILED, error = "yt-dlp not installed — install it from Settings")
            }
            return
        }

        val option = YtDlpManager.QualityOption(
            label = formatLabel,
            formatSelector = formatSelector,
            isAudioOnly = formatSelector == YtDlpManager.AUDIO_ONLY_SELECTOR
        )

        val saveRoot = File(Settings.defaultSaveLocation())
        val outputDir = if (Settings.categorizationDisabled()) {
            saveRoot
        } else {
            File(saveRoot, item.category.folderName)
        }

        try {
            val file = withContext(Dispatchers.IO) {
                YtDlpManager.download(
                    url = item.sourceUrl,
                    option = option,
                    outputDir = outputDir,
                    processId = itemId,
                    context = this@DownloadService
                ) { progress ->
                    QueueRepository.update(itemId) {
                        it.copy(
                            status = ItemStatus.DOWNLOADING,
                            progressPercent = progress.percent,
                            mediaStatusText = progress.statusText
                        )
                    }
                    updateNotificationThrottled()
                }
            }
            QueueRepository.update(itemId) {
                it.copy(
                    status = ItemStatus.DONE,
                    fileName = file.name,
                    filePath = file.absolutePath,
                    progressPercent = 100,
                    mediaStatusText = null,
                    downloadFinishedAtMs = System.currentTimeMillis()
                )
            }
        } catch (e: Throwable) {
            // Throwable (not just Exception) for the same reason as
            // YtDlpManager.install() -- the underlying library's native
            // binary invocation can surface as an Error subtype.
            val cancelled = cancelledYoutubeIds.remove(itemId)
            val wifiWait = wifiWaitingYoutubeIds.remove(itemId)
            val networkWait = networkWaitingYoutubeIds.remove(itemId)
            val dataLimitWait = dataLimitYoutubeIds.remove(itemId)
            val userPaused = pausedYoutubeIds.remove(itemId)
            QueueRepository.update(itemId) {
                when {
                    // User explicitly paused this YouTube download
                    userPaused -> it.copy(
                        status = ItemStatus.PAUSED,
                        error = null,
                        progressPercent = -1,
                        mediaStatusText = null
                    )
                    // Cancelled specifically for a Wi-Fi or total-outage wait --
                    // land on READY (not FAILED) so a fresh worker re-claims it
                    // once connectivity is back, mirroring the non-YouTube path.
                    wifiWait || networkWait -> it.copy(
                        status = ItemStatus.READY,
                        error = null,
                        progressPercent = -1,
                        mediaStatusText = null
                    )
                    // Cancelled for the daily data limit -- unlike Wi-Fi/
                    // network waits, this does NOT go back to READY: there's
                    // no auto-resume for the data limit, so it stays PAUSED
                    // with the marker until the user retries by hand.
                    dataLimitWait -> it.copy(
                        status = ItemStatus.PAUSED,
                        error = Settings.DATA_LIMIT_WAIT_MARKER,
                        progressPercent = -1,
                        mediaStatusText = null
                    )
                    // Not an explicit cancel/wait, but there's genuinely no
                    // internet right now -- pause as "Waiting for network"
                    // instead of failing outright; onInternetRegained() will
                    // put it back to READY once connectivity returns.
                    !cancelled && !NetworkMonitor.hasInternet(this@DownloadService) -> it.copy(
                        status = ItemStatus.PAUSED,
                        error = Settings.NETWORK_WAIT_MARKER,
                        progressPercent = -1,
                        mediaStatusText = null
                    )
                    else -> {
                        val cleanMsg = e.message?.let { msg -> ErrorUtils.cleanErrorText(msg).takeIf { it.isNotBlank() } }
                        it.copy(
                            status = ItemStatus.FAILED,
                            error = if (cancelled) "Cancelled" else (cleanMsg ?: "YouTube download failed"),
                            progressPercent = -1,
                            mediaStatusText = null
                        )
                    }
                }
            }
        } finally {
            cancelledYoutubeIds.remove(itemId)
            wifiWaitingYoutubeIds.remove(itemId)
            networkWaitingYoutubeIds.remove(itemId)
            dataLimitYoutubeIds.remove(itemId)
            pausedYoutubeIds.remove(itemId)
            updateNotification()
        }
    }

    /**
     * Magnet / .torrent items. No connections/speed-limit settings applied
     * here yet (libtorrent has its own upload/download rate limiting knobs
     * that aren't wired up to Settings) -- straightforward "download it and
     * report progress" for now, mirroring downloadOne()'s status handling.
     */
    private suspend fun downloadTorrentOne(itemId: String, sourceUrl: String, customSaveDirPath: String?, selectedFileIndices: String?) {
        val engine = TorrentEngine(
            progress = { done, total, speed ->
                QueueRepository.update(itemId) { it.copy(bytesDone = done, bytesTotal = total, speedBps = speed) }
                updateNotificationThrottled()
            },
            log = { }
        )
        torrentEngines[itemId] = engine

        try {
            val baseDir = if (!customSaveDirPath.isNullOrBlank()) {
                // Picked via the Editor dialog's Advanced -> Change (see
                // HomeFragment/MainActivity) -- overrides both the settings
                // default and the Torrents-subfolder convention below.
                File(customSaveDirPath)
            } else {
                val saveRoot = File(Settings.defaultSaveLocation())
                if (Settings.categorizationDisabled()) {
                    saveRoot
                } else {
                    // Own subfolder rather than DownloadCategory.folderName -- a
                    // torrent is very often a multi-file batch (a whole season,
                    // an album, a repack's several parts) that belongs together
                    // as one folder rather than split across Videos/Music/Others.
                    File(saveRoot, "Torrents")
                }
            }

            val result = withContext(Dispatchers.IO) {
                if (LinkParser.isMagnetLink(sourceUrl)) {
                    engine.downloadMagnet(sourceUrl, baseDir, selectedFileIndices)
                } else if (sourceUrl.startsWith("content://")) {
                    // A .torrent file picked from local storage via the system file
                    // picker (HomeFragment's "Pick .torrent file" button) -- read its
                    // bytes through the ContentResolver rather than fetching over HTTP.
                    val bytes = applicationContext.contentResolver
                        .openInputStream(Uri.parse(sourceUrl))
                        ?.use { it.readBytes() }
                        ?: throw RuntimeException("Could not read the selected .torrent file")
                    engine.downloadTorrentFile(bytes, baseDir, selectedFileIndices)
                } else {
                    val bytes = client.newCall(okhttp3.Request.Builder().url(sourceUrl).build())
                        .execute().use { resp ->
                            if (!resp.isSuccessful) {
                                throw RuntimeException("Could not fetch .torrent file (HTTP ${resp.code})")
                            }
                            resp.body?.bytes() ?: throw RuntimeException("Empty .torrent file")
                        }
                    engine.downloadTorrentFile(bytes, baseDir, selectedFileIndices)
                }
            }

            QueueRepository.update(itemId) {
                it.copy(
                    fileName = result.name,
                    // Single-file torrent: point straight at the file so
                    // "Open" can hand it to an external app. Multi-file
                    // torrents don't have one sensible "the file" to open --
                    // filePath is left null unless exactly 1 file was selected.
                    filePath = if (result.numFiles == 1) {
                        result.singleFilePath ?: File(result.saveDir, result.name).absolutePath
                    } else null,
                    status = ItemStatus.DONE,
                    downloadFinishedAtMs = System.currentTimeMillis()
                )
            }
        } catch (e: DownloadCancelledException) {
            QueueRepository.update(itemId) { it.copy(status = ItemStatus.FAILED, error = "Cancelled") }
        } catch (e: Exception) {
            QueueRepository.update(itemId) { it.copy(status = ItemStatus.FAILED, error = e.message ?: "Torrent download failed") }
        } finally {
            torrentEngines.remove(itemId)
            updateNotification()
        }
    }

    private suspend fun downloadOne(
        itemId: String,
        sourceUrl: String,
        directUrlAtClaim: String?,
        categoryAtClaim: DownloadCategory
    ) {
        var attempt = 0

        while (true) {
            var destinationFile: File? = null

            val engine = DownloadEngine(
                client = client,
                progress = { done, total, speed ->
                    QueueRepository.update(itemId) { it.copy(bytesDone = done, bytesTotal = total, speedBps = speed) }
                    updateNotificationThrottled()
                },
                log = { },
                connections = Settings.connectionsPerDownload(),
                speedLimitBytesPerSec = Settings.speedLimitKBps().toLong() * 1024L
            )
            engines[itemId] = engine

            try {
                val directUrl = directUrlAtClaim ?: throw RuntimeException("No resolved URL")

                val currentItem = QueueRepository.current().firstOrNull { it.id == itemId }
                val customName = currentItem?.fileName?.takeUnless { it.isBlank() }
                val realName = if (customName != null) customName else withContext(Dispatchers.IO) { DownloadEngine.probeRealFilename(client, directUrl) }
                val fileName = customName
                    ?: realName
                    ?: DownloadEngine.filenameFromLink(sourceUrl).ifBlank { DownloadEngine.filenameFromUrl(directUrl) }

                // The source URL alone (e.g. a FuckingFast share link) often has no visible
                // extension -- re-detect the category now that the real filename is resolved,
                // so it doesn't wrongly land in Others just because the share link was opaque.
                val category = CategoryDetector.detect(directUrl, hint = fileName)
                    .takeIf { it != DownloadCategory.default() } ?: categoryAtClaim
                QueueRepository.update(itemId) { it.copy(fileName = fileName, category = category) }

                // Download into the app's private cache first. Public/shared storage
                // (/sdcard/...) is served through Android's FUSE emulation layer, where
                // every read/write syscall carries extra overhead -- that overhead is
                // what was capping speed well below Chrome's. The private cache sits on
                // the real filesystem with none of that overhead, so the download itself
                // runs at full network speed. The finished file is then moved to
                // /sdcard/Xmd/ in one continuous copy, which is far faster than paying
                // the FUSE tax on every chunk of the download.
                val tempDir = File(cacheDir, "xmd_temp/${category.folderName}")
                val tempFile = File(tempDir, fileName)
                destinationFile = tempFile

                val customDir = currentItem?.customSaveDirPath
                val finalDir = if (!customDir.isNullOrBlank()) {
                    File(customDir)
                } else {
                    val saveRoot = File(Settings.defaultSaveLocation())
                    if (Settings.categorizationDisabled()) {
                        // Chrome-style: flat, straight into the default save
                        // location, no <location>/<Category> subfolder at all.
                        saveRoot
                    } else {
                        File(saveRoot, category.folderName)
                    }
                }
                val finalFile = File(finalDir, fileName)

                // Pause (engine.pause()) blocks in-place inside downloadAuto and never throws here --
                // the engine stays registered in `engines` so Resume can call engine.resume() on the
                // very same in-flight connection. Only a genuine Cancel throws, ending this coroutine.
                engine.downloadAuto(directUrl, tempFile)

                // downloadAuto can return normally without throwing even when the
                // server truncates the stream early (connection reset mid-body,
                // proxy cuts off, etc. -- read() just returns -1 sooner than
                // expected, which looks identical to a clean EOF from here). Without
                // this check that half-downloaded temp file gets happily moved to
                // public storage and marked DONE, showing a "completed" file the
                // user can't actually play/open in full. Verify against the known
                // total (from Content-Length/Range probe, tracked via bytesTotal) —
                // when the size was unknown up front (bytesTotal still 0) fall back
                // to just requiring a non-empty file.
                val knownTotal = QueueRepository.current().firstOrNull { it.id == itemId }?.bytesTotal ?: 0L
                val actualSize = tempFile.length()
                if (!tempFile.isFile || actualSize == 0L || (knownTotal > 0 && actualSize < knownTotal)) {
                    tempFile.delete()
                    throw RuntimeException("Incomplete download (got ${actualSize}B" +
                        (if (knownTotal > 0) " of ${knownTotal}B" else "") + ")")
                }

                QueueRepository.update(itemId) { it.copy(status = ItemStatus.SAVING) }
                withContext(Dispatchers.IO) { moveToPublicStorage(tempFile, finalFile) }
                destinationFile = finalFile

                QueueRepository.update(itemId) {
                    it.copy(
                        status = ItemStatus.DONE,
                        filePath = finalFile.absolutePath,
                        downloadFinishedAtMs = System.currentTimeMillis()
                    )
                }
                return
            } catch (e: DownloadCancelledException) {
                destinationFile?.let { DownloadEngine.deletePartialFiles(it) }
                QueueRepository.update(itemId) { it.copy(status = ItemStatus.FAILED, error = "Cancelled") }
                return
            } catch (e: Exception) {
                // Only a plain network-level failure (timeout, connection dropped, DNS
                // failure, TLS handshake failure -- all surface as IOException from
                // OkHttp) is eligible for auto-retry. Server/link-level failures --
                // expired share link, bad HTTP status, incomplete segment -- are our
                // own explicit RuntimeExceptions, not IOExceptions, and deliberately
                // fall straight through to FAILED since retrying the same dead link
                // automatically would just burn battery/data for nothing; those need
                // the user's manual Retry (which can re-resolve a fresh link).
                val isNetworkError = e is IOException
                if (isNetworkError && !NetworkMonitor.hasInternet(this)) {
                    // Not a flaky link -- there's genuinely no internet at all
                    // right now (Wi-Fi/data dropped, airplane mode, router
                    // down...). Sit as "Waiting for network" like Chrome does
                    // instead of burning retries or failing outright;
                    // onInternetRegained() flips it back to READY/DOWNLOADING
                    // the instant connectivity returns, no manual Retry needed.
                    engines.remove(itemId)
                    QueueRepository.update(itemId) {
                        it.copy(status = ItemStatus.PAUSED, error = Settings.NETWORK_WAIT_MARKER)
                    }
                    updateNotification()
                    return
                }
                if (isNetworkError && Settings.autoRetryEnabled() && attempt < MAX_AUTO_RETRIES) {
                    attempt++
                    engines.remove(itemId)
                    QueueRepository.update(itemId) {
                        it.copy(
                            status = ItemStatus.RETRYING,
                            error = "Network error — retrying ($attempt/$MAX_AUTO_RETRIES)…"
                        )
                    }
                    updateNotification()
                    kotlinx.coroutines.delay(2_000L * attempt) // 2s, 4s, 6s backoff

                    // Cancel during the wait (no live engine to interrupt at that
                    // point) is handled by ACTION_CANCEL_ITEM/ALL setting the item
                    // to FAILED directly -- check for that here instead of blindly
                    // retrying a download the user already cancelled.
                    val stillPending = QueueRepository.current().firstOrNull { it.id == itemId }
                    if (stillPending == null || stillPending.status != ItemStatus.RETRYING) return

                    continue
                }
                QueueRepository.update(itemId) { it.copy(status = ItemStatus.FAILED, error = e.message) }
                return
            } finally {
                engines.remove(itemId)
                updateNotification()
            }
        }
    }

    /**
     * Moves the finished temp file into public storage. `renameTo` is instant
     * when both paths are on the same filesystem, but the private cache and
     * /sdcard/... often sit on different mount views (FUSE), so it commonly
     * fails there -- in which case we fall back to a large-buffer streamed
     * copy, which is still one continuous sequential write instead of the
     * many small interleaved writes a live multi-segment download would do.
     */
    private fun moveToPublicStorage(temp: File, final: File) {
        final.parentFile?.mkdirs()
        if (final.exists()) final.delete()

        if (temp.renameTo(final)) return

        FileInputStream(temp).use { input ->
            FileOutputStream(final).use { output ->
                val buffer = ByteArray(4 * 1024 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                }
                output.fd.sync()
            }
        }
        temp.delete()
    }

    private fun updateNotification() {
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification())
    }

    private val lastThrottledNotifyMs = java.util.concurrent.atomic.AtomicLong(0L)

    /**
     * Holds each notification detail line's ETA steady for ~1s at a time,
     * the same cadence [rememberThrottledSpeedEtaText] uses on the
     * DownloadsScreen row (see DownloadsScreen.kt). Without this, ETA here
     * would recompute from instantaneous speedBps on every throttled
     * notification rebuild (every [NOTIFY_THROTTLE_MS] = 500ms), making the
     * number flicker twice as fast as the in-app UI for the same download.
     * Keyed by item id, or "__aggregate__" for the multi-item summary line.
     */
    private val etaHoldCache = ConcurrentHashMap<String, Pair<Long, Long>>() // key -> (heldAtMs, etaSec)

    /** Returns [etaSec] unless a value for [key] was cached within the last second, in which case that stale value is returned instead. */
    private fun holdSteadyEtaSec(key: String, etaSec: Long): Long {
        val now = System.currentTimeMillis()
        val cached = etaHoldCache[key]
        if (cached != null && now - cached.first < ETA_HOLD_MS) return cached.second
        etaHoldCache[key] = now to etaSec
        return etaSec
    }

    /**
     * Same as [updateNotification] but rate-limited to at most once every
     * [NOTIFY_THROTTLE_MS]. The three per-download progress callbacks
     * (downloadOne/downloadYoutube/downloadTorrentOne) fire up to ~5x/sec
     * *per active download* -- buildNotification() rescans the entire queue
     * every time, and NotificationManager.notify() is a cross-process Binder
     * call, so a handful of concurrent downloads meant tens of full
     * notification rebuilds a second for a progress bar that's visually
     * indistinguishable at that rate. That's pure CPU/Binder overhead
     * competing with the actual download threads. Status-change call sites
     * (pause/resume/done/failed/etc.) are untouched and stay immediate.
     */
    private fun updateNotificationThrottled() {
        val now = System.currentTimeMillis()
        val last = lastThrottledNotifyMs.get()
        if (now - last < NOTIFY_THROTTLE_MS) return
        if (lastThrottledNotifyMs.compareAndSet(last, now)) {
            // Piggybacks on the same throttle as the notification rebuild --
            // both are driven by the same per-download progress callbacks,
            // so this keeps the TrafficStats read + limit check down to the
            // same ~2x/sec cadence instead of running on every raw callback.
            checkDataLimit()
            updateNotification()
        }
    }

    private fun buildNotification(): Notification {
        val queue = QueueRepository.current()
        val active = queue.filter { it.status == ItemStatus.DOWNLOADING }
        // Paused/retrying items still need to be reflected in the notification --
        // otherwise pausing the only active download empties `active` and the
        // notification falls back to a permanent "Preparing…" + indeterminate bar.
        val relevant = queue.filter {
            it.status == ItemStatus.DOWNLOADING || it.status == ItemStatus.PAUSED ||
                it.status == ItemStatus.RETRYING
        }
        val resolving = queue.any { it.status == ItemStatus.RESOLVING }

        // Drop held ETA entries for items no longer in-flight so the cache
        // doesn't grow unboundedly across a long-lived service instance.
        if (etaHoldCache.isNotEmpty()) {
            val liveIds = active.mapTo(mutableSetOf("__aggregate__")) { it.id }
            etaHoldCache.keys.retainAll(liveIds)
        }

        // yt-dlp reports a plain 0-100% instead of bytes -- excluded from the
        // byte-based sums below (mixing the two would produce meaningless
        // totals) and handled as its own case in the single-item branch.
        val byteActive = active.filter { it.platform != MediaPlatform.YOUTUBE }
        val totalDone = byteActive.sumOf { it.bytesDone }
        val totalSize = byteActive.sumOf { it.bytesTotal }
        val totalSpeed = byteActive.sumOf { it.speedBps }
        val percent = if (totalSize > 0) ((totalDone * 100) / totalSize).toInt() else 0

        val title: String
        val text: String
        // Progress bar state: only truly indeterminate while resolving with nothing
        // else going on. A paused item keeps its last known (determinate) percent.
        var indeterminate = false
        var barPercent = percent

        when {
            relevant.isEmpty() && resolving -> {
                title = getString(R.string.app_name)
                text = "Preparing…"
                indeterminate = true
            }
            relevant.isEmpty() -> {
                title = getString(R.string.app_name)
                text = "Idle"
            }
            relevant.size == 1 -> {
                val item = relevant.first()
                title = item.fileName ?: item.sourceUrl
                text = when {
                    item.status == ItemStatus.PAUSED -> "Paused — " + buildDetailLine(item.bytesDone, item.bytesTotal, 0.0)
                    item.status == ItemStatus.RETRYING -> "${item.error ?: "Retrying…"}"
                    item.platform == MediaPlatform.YOUTUBE ->
                        (if (item.progressPercent >= 0) "${item.progressPercent}%" else "Resolving…") +
                            "  •  " + (item.mediaStatusText ?: item.mediaFormatLabel ?: "YouTube")
                    else -> buildDetailLine(item.bytesDone, item.bytesTotal, item.speedBps, etaHoldKey = item.id)
                }
                barPercent = when {
                    item.platform == MediaPlatform.YOUTUBE -> item.progressPercent.coerceAtLeast(0)
                    item.bytesTotal > 0 -> ((item.bytesDone * 100) / item.bytesTotal).toInt()
                    else -> 0
                }
            }
            else -> {
                val pausedCount = relevant.count { it.status == ItemStatus.PAUSED }
                val ytCount = relevant.count { it.platform == MediaPlatform.YOUTUBE }
                title = "${relevant.size} files" +
                    if (active.isNotEmpty()) " downloading" else " in queue"
                text = buildString {
                    append(buildDetailLine(totalDone, totalSize, totalSpeed, etaHoldKey = "__aggregate__"))
                    if (pausedCount > 0) append("  •  $pausedCount paused")
                    if (ytCount > 0) append("  •  $ytCount YouTube")
                }
                val relevantByte = relevant.filter { it.platform != MediaPlatform.YOUTUBE }
                val relevantTotal = relevantByte.sumOf { it.bytesTotal }
                val relevantDone = relevantByte.sumOf { it.bytesDone }
                barPercent = if (relevantTotal > 0) ((relevantDone * 100) / relevantTotal).toInt() else 0
            }
        }

        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val showBar = indeterminate || relevant.any { it.bytesTotal > 0 }
        val isPaused = relevant.isNotEmpty() && relevant.all { it.status == ItemStatus.PAUSED }
        val smallIconRes = if (isPaused) R.drawable.ic_notification_pause else android.R.drawable.stat_sys_download
        val builder = NotificationCompat.Builder(this, FfApp.DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(smallIconRes)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(if (!indeterminate && showBar) "$barPercent%" else null)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, barPercent, indeterminate)
            .setContentIntent(openIntent)

        // Per-item pause/resume + a single cancel-all action -- shown whenever
        // there's a live or paused item to act on (Via-style controls right in
        // the notification).
        if (relevant.size == 1) {
            val item = relevant.first()
            if (item.status == ItemStatus.PAUSED) {
                val resumeIntent = PendingIntent.getService(
                    this, 1,
                    Intent(this, DownloadService::class.java)
                        .setAction(ACTION_RESUME_ITEM)
                        .putExtra(EXTRA_ITEM_ID, item.id),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                builder.addAction(0, getString(R.string.action_resume), resumeIntent)
            } else if (item.status == ItemStatus.DOWNLOADING && item.platform != MediaPlatform.YOUTUBE) {
                // yt-dlp has no native pause -- see the QueueItemRow (DownloadsScreen.kt)/DownloadService
                // pause-routing comments elsewhere for the same reasoning.
                val pauseIntent = PendingIntent.getService(
                    this, 1,
                    Intent(this, DownloadService::class.java)
                        .setAction(ACTION_PAUSE_ITEM)
                        .putExtra(EXTRA_ITEM_ID, item.id),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                builder.addAction(R.drawable.ic_notification_pause, getString(R.string.action_pause), pauseIntent)
            }
        }
        if (relevant.isNotEmpty()) {
            val cancelIntent = PendingIntent.getService(
                this, 2,
                Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL_ALL),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(0, getString(R.string.action_cancel), cancelIntent)
        }

        return builder.build()
    }

    /**
     * "12.4 MB / 45.0 MB  •  1.2 MB/s  •  5 mins left"
     *
     * [etaHoldKey] identifies which row this line belongs to (an item id, or
     * "__aggregate__" for the multi-item summary) so its ETA can be held
     * steady via [holdSteadyEtaSec] instead of recomputing from instantaneous
     * speed on every throttled notification rebuild -- pass null to skip
     * holding (e.g. a paused item, whose speed/ETA aren't live anyway).
     */
    private fun buildDetailLine(done: Long, total: Long, speedBps: Double, etaHoldKey: String? = null): String {
        val sizePart = if (total > 0) "${com.invictus.xmd.utils.formatBytes(done)} / ${com.invictus.xmd.utils.formatBytes(total)}" else com.invictus.xmd.utils.formatBytes(done)
        if (speedBps <= 0.0) return sizePart

        val speedPart = com.invictus.xmd.utils.formatSpeed(speedBps)

        val remaining = (total - done).coerceAtLeast(0)
        var etaSec = if (total > 0) (remaining / speedBps).toLong() else -1L
        if (etaSec >= 0 && etaHoldKey != null) etaSec = holdSteadyEtaSec(etaHoldKey, etaSec)
        val etaPart = if (etaSec >= 0) "  •  " + com.invictus.xmd.utils.formatRemainingTimeChrome(etaSec) else ""

        return "$sizePart  •  $speedPart$etaPart"
    }
}