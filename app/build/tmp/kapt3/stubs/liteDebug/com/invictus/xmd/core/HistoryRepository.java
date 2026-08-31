package com.invictus.xmd.core;

/**
 * Browser tab visited-page history. Same simple Room-backed shape as BookmarkRepository.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000@\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0004\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0006\u0010\u000e\u001a\u00020\u000fJ\u000e\u0010\u0010\u001a\u00020\u000f2\u0006\u0010\u0011\u001a\u00020\u0012J\u0016\u0010\u0013\u001a\u00020\u000f2\u0006\u0010\u0014\u001a\u00020\u00152\u0006\u0010\u0016\u001a\u00020\u0015J\u000e\u0010\u0017\u001a\u00020\u000f2\u0006\u0010\u0018\u001a\u00020\bR\u000e\u0010\u0003\u001a\u00020\u0004X\u0082.\u00a2\u0006\u0002\n\u0000R6\u0010\t\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\b0\u00070\u00062\u0012\u0010\u0005\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\b0\u00070\u0006@BX\u0086.\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000bR\u000e\u0010\f\u001a\u00020\rX\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0019"}, d2 = {"Lcom/invictus/xmd/core/HistoryRepository;", "", "()V", "dao", "Lcom/invictus/xmd/core/db/HistoryDao;", "<set-?>", "Landroidx/lifecycle/LiveData;", "", "Lcom/invictus/xmd/core/HistoryEntry;", "entries", "getEntries", "()Landroidx/lifecycle/LiveData;", "scope", "Lkotlinx/coroutines/CoroutineScope;", "clearAll", "", "init", "context", "Landroid/content/Context;", "record", "url", "", "title", "remove", "entry", "app_liteDebug"})
public final class HistoryRepository {
    private static com.invictus.xmd.core.db.HistoryDao dao;
    @org.jetbrains.annotations.NotNull()
    private static final kotlinx.coroutines.CoroutineScope scope = null;
    private static androidx.lifecycle.LiveData<java.util.List<com.invictus.xmd.core.HistoryEntry>> entries;
    @org.jetbrains.annotations.NotNull()
    public static final com.invictus.xmd.core.HistoryRepository INSTANCE = null;
    
    private HistoryRepository() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final androidx.lifecycle.LiveData<java.util.List<com.invictus.xmd.core.HistoryEntry>> getEntries() {
        return null;
    }
    
    public final void init(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
    }
    
    public final void record(@org.jetbrains.annotations.NotNull()
    java.lang.String url, @org.jetbrains.annotations.NotNull()
    java.lang.String title) {
    }
    
    public final void remove(@org.jetbrains.annotations.NotNull()
    com.invictus.xmd.core.HistoryEntry entry) {
    }
    
    public final void clearAll() {
    }
}