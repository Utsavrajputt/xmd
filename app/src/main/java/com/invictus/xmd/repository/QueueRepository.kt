package com.invictus.xmd.repository

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import com.invictus.xmd.database.AppDatabase
import com.invictus.xmd.database.dao.QueueItemDao
import com.invictus.xmd.database.entities.Bookmark
import com.invictus.xmd.database.entities.QueueItem
import com.invictus.xmd.domain.download.CategoryDetector
import com.invictus.xmd.domain.download.DownloadCategory
import com.invictus.xmd.domain.download.DownloadScheduler
import com.invictus.xmd.domain.download.ItemStatus
import com.invictus.xmd.domain.download.ScheduleAlarmManager
import com.invictus.xmd.domain.download.ScheduleMode
import com.invictus.xmd.utils.storage.FileNameUtils
import com.invictus.xmd.utils.storage.OnDuplicateStrategy
import java.io.File

/**
 * Single in-memory source of truth for the queue, shared between MainActivity
 * (UI + resolve flow) and DownloadService (background download loop). Both
 * run in the same process, so a plain StateFlow-backed singleton is enough --
 * no cross-process IPC needed. (Was LiveData; switched to StateFlow so the
 * Compose Downloads screen can collect it directly via
 * collectAsStateWithLifecycle() -- see Phase 2's identical Bookmark/History
 * repository conversion in COMPOSE_MIGRATION.md.)
 *
 * IMPORTANT: reads/writes go through [master] under [lock], not through
 * [items].value directly. Even though MutableStateFlow's value setter is
 * itself atomic/thread-safe, a naive "read items.value, map, assign" pattern
 * still race-loses updates when called rapidly from a download thread (e.g.
 * a status change to DOWNLOADING gets silently clobbered by the very next
 * progress tick because that tick's map() was computed from a stale .value
 * read before the status change had been applied). Keeping our own
 * synchronized master list sidesteps that entirely.
 *
 * PERSISTENCE: [master] is mirrored to a Room DB (see core/db/AppDatabase.kt)
 * so the queue survives the app process being killed and restarted -- it
 * used to be purely in-memory, so a restart silently wiped the whole list
 * even though the already-downloaded files on disk were untouched. Call
 * [init] once (from FfApp.onCreate) before anything touches the queue.
 * Writes to Room happen off the main thread and don't block the in-memory
 * update; progress-only ticks (bytesDone/speedBps, which fire up to ~5x/sec
 * per active download) are throttled per-item so we're not hammering SQLite
 * on every tick -- status/error/fileName/directUrl/category changes are
 * always persisted immediately since those matter for correctness after a
 * restart.
 */
object QueueRepository {

    sealed interface EnqueueResult {
        data class Success(val item: QueueItem) : EnqueueResult
        data class ActiveConflict(val item: QueueItem) : EnqueueResult
        data class DeleteFailed(val file: File) : EnqueueResult
    }

    private val lock = Any()
    private var master: List<QueueItem> = emptyList()

    private val _items = MutableStateFlow<List<QueueItem>>(emptyList())
    val items: StateFlow<List<QueueItem>> = _items.asStateFlow()

    private lateinit var dao: QueueItemDao
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val lastPersistMs = ConcurrentHashMap<String, Long>()
    private const val PROGRESS_PERSIST_INTERVAL_MS = 1_000L

    /**
     * Loads whatever was persisted from a previous run, then starts
     * mirroring further changes back to disk. Safe to call once at app
     * startup (FfApp.onCreate); harmless if called again.
     *
     * Items that were mid-flight when the process died (RESOLVING /
     * DOWNLOADING / SAVING) can't just resume -- there's no worker thread
     * for them anymore -- so they're rolled back to a restartable state:
     * READY if we already have a directUrl (download can just restart),
     * otherwise PENDING (needs re-resolve). NEEDS_CHALLENGE/PAUSED/READY/
     * DONE/FAILED are left as-is.
     */
    fun init(context: Context) {
        if (::dao.isInitialized) return
        dao = AppDatabase.get(context).queueItemDao()
        scope.launch {
            val persisted = runCatching { dao.getAll() }.getOrDefault(emptyList())
            val recovered = persisted.map { item ->
                when (item.status) {
                    ItemStatus.RESOLVING -> item.copy(status = ItemStatus.PENDING)
                    ItemStatus.DOWNLOADING, ItemStatus.SAVING, ItemStatus.RETRYING ->
                        if (item.directUrl != null) item.copy(status = ItemStatus.READY)
                        else item.copy(status = ItemStatus.PENDING)
                    else -> item
                }
            }
            synchronized(lock) {
                // Don't clobber anything the UI already queued before this
                // background load finished.
                val current = master.associateBy { it.id }
                val recoveredIds = recovered.map { it.id }.toSet()
                master = recovered.map { current[it.id] ?: it } +
                    master.filter { it.id !in recoveredIds }
                _items.value = master
            }
            // Persist any status rollback we just did.
            val changed = recovered.filter { r ->
                persisted.find { it.id == r.id }?.status != r.status
            }
            if (changed.isNotEmpty()) runCatching { dao.upsertAll(changed) }
        }
    }

    /**
     * Category is auto-detected per link from its extension (see
     * [CategoryDetector]) -- there's no manual picker anymore. Items
     * already in-flight keep whatever category they were queued under,
     * even if a re-resolve would now detect differently -- their
     * destination folder shouldn't move mid-download.
     *
     * IMPORTANT: this is additive, not a replace. It used to rebuild [master]
     * from just [rawLinks] (the current paste-box contents), which silently
     * dropped every previously-queued item -- including ones actively
     * downloading -- the moment a second batch was pasted, since they weren't
     * present in the new rawLinks. Now we keep every existing item and only
     * add/replace entries for the links just passed in, so an in-flight
     * download from a prior call is never removed from [master].
     */
    fun setLinks(rawLinks: List<String>, pageUrl: String? = null) {
        val toPersist: List<QueueItem>
        synchronized(lock) {
            val current = master.associateBy { it.sourceUrl }
            val updatedOrNew = rawLinks.map { link ->
                val existing = current[link]
                when {
                    existing == null ->
                        QueueItem(id = UUID.randomUUID().toString(), sourceUrl = link, category = CategoryDetector.detect(link), pageUrl = pageUrl)
                    // Finished or failed/cancelled items get a clean retry instead of being
                    // stuck reusing their old terminal status (which Prepare would then skip).
                    existing.status == ItemStatus.DONE || existing.status == ItemStatus.FAILED ->
                        QueueItem(id = UUID.randomUUID().toString(), sourceUrl = link, category = CategoryDetector.detect(link), pageUrl = pageUrl)
                    else -> existing // leave anything still in-flight alone
                }
            }
            val untouched = master.filter { it.sourceUrl !in rawLinks.toSet() }
            master = untouched + updatedOrNew
            _items.value = master
            toPersist = updatedOrNew
        }
        persistNow(toPersist)
    }

    /**
     * Resolves the destination conflict and reserves the resulting filename
     * in one critical section. This keeps simultaneous app/share entry points
     * from choosing the same numbered name or replacing an item that started
     * writing while a duplicate dialog was open.
     */
    fun enqueueResolvingDuplicate(
        item: QueueItem,
        duplicateStrategy: OnDuplicateStrategy?,
    ): EnqueueResult {
        val targetFile = FileNameUtils.destinationFileOf(item)
        var removedIds = emptyList<String>()
        var enqueuedItem: QueueItem? = null

        val result = synchronized(lock) {
            if (targetFile == null) {
                enqueuedItem = item
            } else {
                val targetPath = targetFile.absoluteFile
                val conflicts = master.filter { existing ->
                    existing.id != item.id &&
                        FileNameUtils.destinationFileOf(existing)?.absoluteFile == targetPath
                }

                if (duplicateStrategy == OnDuplicateStrategy.OverrideDownload) {
                    val activeConflict = conflicts.firstOrNull { it.status.isWritingDestination() }
                    if (activeConflict != null) {
                        return@synchronized EnqueueResult.ActiveConflict(activeConflict)
                    }

                    val deleted = !targetFile.exists() || runCatching {
                        targetFile.delete() || !targetFile.exists()
                    }.getOrDefault(false)
                    if (!deleted) {
                        return@synchronized EnqueueResult.DeleteFailed(targetFile)
                    }

                    removedIds = conflicts.map { it.id }
                    enqueuedItem = item
                } else {
                    val activeFiles = master
                        .asSequence()
                        .filter { it.id != item.id }
                        .mapNotNull(FileNameUtils::destinationFileOf)
                        .toSet()
                    enqueuedItem = item.copy(
                        fileName = FileNameUtils.numberedNameIfExists(targetFile, activeFiles),
                    )
                }
            }

            val finalItem = checkNotNull(enqueuedItem)
            master = master
                .filter { it.id !in removedIds && it.id != finalItem.id } + finalItem
            _items.value = master
            EnqueueResult.Success(finalItem)
        }

        if (result is EnqueueResult.Success) {
            persistReplacement(removedIds, result.item)
            if (result.item.scheduleMode != ScheduleMode.NONE) {
                runCatching { ScheduleAlarmManager.rearm(com.invictus.xmd.preferences.Settings.appContext()) }
            }
        }
        return result
    }

    private fun mutate(id: String, transform: (QueueItem) -> QueueItem) {
        var previous: QueueItem? = null
        var updated: QueueItem? = null
        synchronized(lock) {
            master = master.map {
                if (it.id == id) {
                    previous = it
                    val mutated = transform(it)
                    updated = mutated
                    mutated
                } else it
            }
            _items.value = master
        }
        updated?.let { persistDebounced(it, previous) }
    }

    private fun mutateNonTerminal(id: String, transform: (QueueItem) -> QueueItem) = mutate(id) {
        if (it.status == ItemStatus.DONE || it.status == ItemStatus.FAILED) it else transform(it)
    }

    fun markResolving(id: String, resetProgress: Boolean = false) = mutate(id) {
        it.copy(
            status = ItemStatus.RESOLVING,
            error = null,
            bytesDone = if (resetProgress) 0L else it.bytesDone,
            bytesTotal = if (resetProgress) 0L else it.bytesTotal,
            speedBps = if (resetProgress) 0.0 else it.speedBps,
        )
    }

    fun markReady(id: String, directUrl: String? = null, resetMediaProgress: Boolean = false) = mutateNonTerminal(id) {
        it.copy(
            status = ItemStatus.READY,
            directUrl = directUrl ?: it.directUrl,
            error = null,
            progressPercent = if (resetMediaProgress) -1 else it.progressPercent,
            mediaStatusText = if (resetMediaProgress) null else it.mediaStatusText,
        )
    }

    fun markDownloading(id: String) = mutateNonTerminal(id) {
        it.copy(status = ItemStatus.DOWNLOADING, error = null)
    }

    fun markPaused(id: String, reason: String? = null, resetMediaProgress: Boolean = false) = mutateNonTerminal(id) {
        it.copy(
            status = ItemStatus.PAUSED,
            error = reason,
            progressPercent = if (resetMediaProgress) -1 else it.progressPercent,
            mediaStatusText = if (resetMediaProgress) null else it.mediaStatusText,
        )
    }

    fun markPending(id: String) = mutateNonTerminal(id) {
        it.copy(status = ItemStatus.PENDING, error = null)
    }

    fun markChallengeNeeded(id: String) = mutateNonTerminal(id) {
        it.copy(status = ItemStatus.NEEDS_CHALLENGE, error = null)
    }

    fun markRetrying(id: String, error: String) = mutateNonTerminal(id) {
        it.copy(status = ItemStatus.RETRYING, error = error)
    }

    fun markSaving(id: String) = mutateNonTerminal(id) {
        it.copy(status = ItemStatus.SAVING, error = null)
    }

    fun markFailed(id: String, error: String?, resetMediaProgress: Boolean = false) = mutate(id) {
        if (it.status == ItemStatus.DONE) {
            it
        } else {
            it.copy(
                status = ItemStatus.FAILED,
                error = error,
                progressPercent = if (resetMediaProgress) -1 else it.progressPercent,
                mediaStatusText = if (resetMediaProgress) null else it.mediaStatusText,
            )
        }
    }

    fun resetForRetry(id: String, needsResolve: Boolean) = mutate(id) {
        it.copy(
            status = if (needsResolve) ItemStatus.RESOLVING else ItemStatus.READY,
            error = null,
            bytesDone = 0L,
            bytesTotal = 0L,
            speedBps = 0.0,
            directUrl = if (needsResolve) null else (it.directUrl ?: it.sourceUrl),
        )
    }

    fun reportProgress(id: String, bytesDone: Long, bytesTotal: Long, speedBps: Double) = mutateNonTerminal(id) {
        it.copy(bytesDone = bytesDone, bytesTotal = bytesTotal, speedBps = speedBps)
    }

    fun reportYoutubeProgress(id: String, percent: Int, statusText: String?) = mutateNonTerminal(id) {
        if (it.status != ItemStatus.DOWNLOADING) {
            it
        } else {
            it.copy(
                progressPercent = percent,
                mediaStatusText = statusText,
                error = null,
            )
        }
    }

    fun updateDownloadMetadata(id: String, fileName: String, category: DownloadCategory) = mutate(id) {
        it.copy(fileName = fileName, category = category)
    }

    fun configureYoutubeDownload(
        id: String,
        formatSelector: String,
        formatLabel: String,
        category: DownloadCategory,
    ) = mutate(id) {
        it.copy(
            status = ItemStatus.READY,
            platform = com.invictus.xmd.domain.download.MediaPlatform.YOUTUBE,
            mediaFormatSelector = formatSelector,
            mediaFormatLabel = formatLabel,
            category = category,
            error = null,
        )
    }

    fun renameDownloadedFile(id: String, fileName: String, filePath: String) = mutate(id) {
        it.copy(fileName = fileName, filePath = filePath)
    }

    fun markDone(
        id: String,
        filePath: String?,
        fileName: String? = null,
        progressPercent: Int? = null,
    ) = mutate(id) {
        if (it.status != ItemStatus.DOWNLOADING && it.status != ItemStatus.SAVING) {
            it
        } else {
            it.copy(
                status = ItemStatus.DONE,
                fileName = fileName ?: it.fileName,
                filePath = filePath,
                progressPercent = progressPercent ?: it.progressPercent,
                mediaStatusText = null,
                error = null,
                downloadFinishedAtMs = System.currentTimeMillis(),
            )
        }
    }

    /**
     * Atomically finds the first READY item that the download scheduler
     * currently allows to start (see DownloadScheduler.isAllowedNow -- a
     * plain ScheduleMode.NONE item is always eligible, same as before this
     * existed) and marks it DOWNLOADING in one step, so multiple concurrent
     * download workers can't both grab the same item.
     */
    fun claimNextReady(): QueueItem? {
        var claimedItem: QueueItem? = null
        synchronized(lock) {
            val idx = master.indexOfFirst { it.status == ItemStatus.READY && DownloadScheduler.isAllowedNow(it) }
            if (idx == -1) return null
            val claimed = master[idx].copy(
                status = ItemStatus.DOWNLOADING,
                downloadStartedAtMs = System.currentTimeMillis()
            )
            master = master.toMutableList().also { it[idx] = claimed }
            _items.value = master
            claimedItem = claimed
        }
        claimedItem?.let { persistNow(listOf(it)) }
        return claimedItem
    }

    /** Removes a single item from the queue (used by the per-item "Clear" button). */
    fun removeItem(id: String) {
        synchronized(lock) {
            master = master.filter { it.id != id }
            _items.value = master
        }
        if (::dao.isInitialized) {
            scope.launch { runCatching { dao.deleteByIds(listOf(id)) } }
        }
    }

    fun clearFinishedAndFailed() {
        val removedIds: List<String>
        synchronized(lock) {
            val (removed, kept) = master.partition { it.status == ItemStatus.DONE || it.status == ItemStatus.FAILED }
            master = kept
            _items.value = master
            removedIds = removed.map { it.id }
        }
        if (removedIds.isNotEmpty() && ::dao.isInitialized) {
            scope.launch { runCatching { dao.deleteByIds(removedIds) } }
        }
    }

    fun current(): List<QueueItem> = synchronized(lock) { master }

    // ── Persistence helpers ─────────────────────────────────────────────

    private fun persistNow(items: List<QueueItem>) {
        if (items.isEmpty() || !::dao.isInitialized) return
        items.forEach { lastPersistMs[it.id] = System.currentTimeMillis() }
        scope.launch { runCatching { dao.upsertAll(items) } }
    }

    private fun persistReplacement(removedIds: List<String>, item: QueueItem) {
        if (!::dao.isInitialized) return
        removedIds.forEach(lastPersistMs::remove)
        lastPersistMs[item.id] = System.currentTimeMillis()
        scope.launch { runCatching { dao.replace(removedIds, item) } }
    }

    /**
     * Persists immediately on any state-relevant field change (status,
     * error, fileName, directUrl, category); otherwise throttles to at
     * most once per [PROGRESS_PERSIST_INTERVAL_MS] per item so rapid
     * progress ticks don't hit SQLite ~5x/sec per active download.
     */
    private fun persistDebounced(item: QueueItem, previous: QueueItem?) {
        if (!::dao.isInitialized) return
        val stateChanged = previous == null ||
            previous.status != item.status ||
            previous.error != item.error ||
            previous.fileName != item.fileName ||
            previous.directUrl != item.directUrl ||
            previous.category != item.category
        val now = System.currentTimeMillis()
        val last = lastPersistMs[item.id] ?: 0L
        if (!stateChanged && now - last < PROGRESS_PERSIST_INTERVAL_MS) return
        lastPersistMs[item.id] = now
        scope.launch { runCatching { dao.upsert(item) } }
    }

    private fun ItemStatus.isWritingDestination(): Boolean = when (this) {
        ItemStatus.DOWNLOADING,
        ItemStatus.PAUSED,
        ItemStatus.RETRYING,
        ItemStatus.SAVING -> true
        else -> false
    }
}
