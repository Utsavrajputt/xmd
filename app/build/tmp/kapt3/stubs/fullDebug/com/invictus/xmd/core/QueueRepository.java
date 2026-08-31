package com.invictus.xmd.core;

/**
 * Single in-memory source of truth for the queue, shared between MainActivity
 * (UI + resolve flow) and DownloadService (background download loop). Both
 * run in the same process, so a plain LiveData-backed singleton is enough --
 * no cross-process IPC needed.
 *
 * IMPORTANT: reads/writes go through [master] under [lock], not through
 * LiveData.value. LiveData.postValue() from a background thread is
 * fire-and-forget -- .value isn't updated until the main thread processes
 * it -- so a naive "read items.value, map, postValue" pattern race-loses
 * updates when called rapidly from a download thread (e.g. a status change
 * to DOWNLOADING gets silently clobbered by the very next progress tick
 * because that tick's map() was computed from a stale .value read before
 * the status change had been applied). Keeping our own synchronized master
 * list sidesteps that entirely.
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
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000R\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\t\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\n\n\u0002\u0018\u0002\n\u0000\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u0014\u001a\u0004\u0018\u00010\nJ\u0006\u0010\u0015\u001a\u00020\u0016J\f\u0010\u0017\u001a\b\u0012\u0004\u0012\u00020\n0\tJ\u000e\u0010\u0018\u001a\u00020\u00162\u0006\u0010\u0019\u001a\u00020\u001aJ\u001a\u0010\u001b\u001a\u00020\u00162\u0006\u0010\u001c\u001a\u00020\n2\b\u0010\u001d\u001a\u0004\u0018\u00010\nH\u0002J\u0016\u0010\u001e\u001a\u00020\u00162\f\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\n0\tH\u0002J\u000e\u0010\u001f\u001a\u00020\u00162\u0006\u0010 \u001a\u00020\u000fJ\u0014\u0010!\u001a\u00020\u00162\f\u0010\"\u001a\b\u0012\u0004\u0012\u00020\u000f0\tJ\"\u0010#\u001a\u00020\u00162\u0006\u0010 \u001a\u00020\u000f2\u0012\u0010$\u001a\u000e\u0012\u0004\u0012\u00020\n\u0012\u0004\u0012\u00020\n0%R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082.\u00a2\u0006\u0002\n\u0000R\u001d\u0010\u0007\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\n0\t0\b\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u001a\u0010\r\u001a\u000e\u0012\u0004\u0012\u00020\u000f\u0012\u0004\u0012\u00020\u00040\u000eX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0010\u001a\u00020\u0001X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u0011\u001a\b\u0012\u0004\u0012\u00020\n0\tX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0012\u001a\u00020\u0013X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006&"}, d2 = {"Lcom/invictus/xmd/core/QueueRepository;", "", "()V", "PROGRESS_PERSIST_INTERVAL_MS", "", "dao", "Lcom/invictus/xmd/core/db/QueueItemDao;", "items", "Landroidx/lifecycle/MutableLiveData;", "", "Lcom/invictus/xmd/core/QueueItem;", "getItems", "()Landroidx/lifecycle/MutableLiveData;", "lastPersistMs", "Ljava/util/concurrent/ConcurrentHashMap;", "", "lock", "master", "scope", "Lkotlinx/coroutines/CoroutineScope;", "claimNextReady", "clearFinishedAndFailed", "", "current", "init", "context", "Landroid/content/Context;", "persistDebounced", "item", "previous", "persistNow", "removeItem", "id", "setLinks", "rawLinks", "update", "mutate", "Lkotlin/Function1;", "app_fullDebug"})
public final class QueueRepository {
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.Object lock = null;
    @org.jetbrains.annotations.NotNull()
    private static java.util.List<com.invictus.xmd.core.QueueItem> master;
    @org.jetbrains.annotations.NotNull()
    private static final androidx.lifecycle.MutableLiveData<java.util.List<com.invictus.xmd.core.QueueItem>> items = null;
    private static com.invictus.xmd.core.db.QueueItemDao dao;
    @org.jetbrains.annotations.NotNull()
    private static final kotlinx.coroutines.CoroutineScope scope = null;
    @org.jetbrains.annotations.NotNull()
    private static final java.util.concurrent.ConcurrentHashMap<java.lang.String, java.lang.Long> lastPersistMs = null;
    private static final long PROGRESS_PERSIST_INTERVAL_MS = 1000L;
    @org.jetbrains.annotations.NotNull()
    public static final com.invictus.xmd.core.QueueRepository INSTANCE = null;
    
    private QueueRepository() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final androidx.lifecycle.MutableLiveData<java.util.List<com.invictus.xmd.core.QueueItem>> getItems() {
        return null;
    }
    
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
    public final void init(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
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
    public final void setLinks(@org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.String> rawLinks) {
    }
    
    public final void update(@org.jetbrains.annotations.NotNull()
    java.lang.String id, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function1<? super com.invictus.xmd.core.QueueItem, com.invictus.xmd.core.QueueItem> mutate) {
    }
    
    /**
     * Atomically finds the first READY item and marks it DOWNLOADING in one
     * step, so multiple concurrent download workers can't both grab the
     * same item.
     */
    @org.jetbrains.annotations.Nullable()
    public final com.invictus.xmd.core.QueueItem claimNextReady() {
        return null;
    }
    
    /**
     * Removes a single item from the queue (used by the per-item "Clear" button).
     */
    public final void removeItem(@org.jetbrains.annotations.NotNull()
    java.lang.String id) {
    }
    
    public final void clearFinishedAndFailed() {
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<com.invictus.xmd.core.QueueItem> current() {
        return null;
    }
    
    private final void persistNow(java.util.List<com.invictus.xmd.core.QueueItem> items) {
    }
    
    /**
     * Persists immediately on any state-relevant field change (status,
     * error, fileName, directUrl, category); otherwise throttles to at
     * most once per [PROGRESS_PERSIST_INTERVAL_MS] per item so rapid
     * progress ticks don't hit SQLite ~5x/sec per active download.
     */
    private final void persistDebounced(com.invictus.xmd.core.QueueItem item, com.invictus.xmd.core.QueueItem previous) {
    }
}