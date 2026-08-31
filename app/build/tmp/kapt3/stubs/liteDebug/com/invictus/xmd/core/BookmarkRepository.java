package com.invictus.xmd.core;

/**
 * Real bookmarks -- pages the user starred in the Browser toolbar, listed
 * on their own Bookmarks screen (most-recent first). Same simple
 * Room-backed shape as HistoryRepository. Not to be confused with
 * [ShortcutRepository], which backs the speed-dial tiles on the new-tab
 * page; adding a bookmark can optionally also add a shortcut, but the two
 * are stored and managed independently.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000>\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0016\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0012\u001a\u00020\u0011J\u0006\u0010\u0013\u001a\u00020\u000fJ\u0010\u0010\u0014\u001a\u00020\u00112\u0006\u0010\u0012\u001a\u00020\u0011H\u0002J\u000e\u0010\u0015\u001a\u00020\u000f2\u0006\u0010\u0016\u001a\u00020\u0017J\u000e\u0010\u0018\u001a\u00020\u000f2\u0006\u0010\u0019\u001a\u00020\u0006R6\u0010\u0007\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00060\u00050\u00042\u0012\u0010\u0003\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00060\u00050\u0004@BX\u0086.\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u000e\u0010\n\u001a\u00020\u000bX\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\f\u001a\u00020\rX\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u001a"}, d2 = {"Lcom/invictus/xmd/core/BookmarkRepository;", "", "()V", "<set-?>", "Landroidx/lifecycle/LiveData;", "", "Lcom/invictus/xmd/core/Bookmark;", "bookmarks", "getBookmarks", "()Landroidx/lifecycle/LiveData;", "dao", "Lcom/invictus/xmd/core/db/BookmarkDao;", "scope", "Lkotlinx/coroutines/CoroutineScope;", "add", "", "title", "", "url", "clearAll", "hostOf", "init", "context", "Landroid/content/Context;", "remove", "bookmark", "app_liteDebug"})
public final class BookmarkRepository {
    private static com.invictus.xmd.core.db.BookmarkDao dao;
    @org.jetbrains.annotations.NotNull()
    private static final kotlinx.coroutines.CoroutineScope scope = null;
    private static androidx.lifecycle.LiveData<java.util.List<com.invictus.xmd.core.Bookmark>> bookmarks;
    @org.jetbrains.annotations.NotNull()
    public static final com.invictus.xmd.core.BookmarkRepository INSTANCE = null;
    
    private BookmarkRepository() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final androidx.lifecycle.LiveData<java.util.List<com.invictus.xmd.core.Bookmark>> getBookmarks() {
        return null;
    }
    
    public final void init(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
    }
    
    public final void add(@org.jetbrains.annotations.NotNull()
    java.lang.String title, @org.jetbrains.annotations.NotNull()
    java.lang.String url) {
    }
    
    public final void remove(@org.jetbrains.annotations.NotNull()
    com.invictus.xmd.core.Bookmark bookmark) {
    }
    
    public final void clearAll() {
    }
    
    private final java.lang.String hostOf(java.lang.String url) {
        return null;
    }
}