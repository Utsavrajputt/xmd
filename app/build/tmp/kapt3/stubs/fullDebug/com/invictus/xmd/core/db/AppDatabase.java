package com.invictus.xmd.core.db;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\b'\u0018\u0000 \u000b2\u00020\u0001:\u0001\u000bB\u0005\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u0003\u001a\u00020\u0004H&J\b\u0010\u0005\u001a\u00020\u0006H&J\b\u0010\u0007\u001a\u00020\bH&J\b\u0010\t\u001a\u00020\nH&\u00a8\u0006\f"}, d2 = {"Lcom/invictus/xmd/core/db/AppDatabase;", "Landroidx/room/RoomDatabase;", "()V", "bookmarkDao", "Lcom/invictus/xmd/core/db/BookmarkDao;", "historyDao", "Lcom/invictus/xmd/core/db/HistoryDao;", "queueItemDao", "Lcom/invictus/xmd/core/db/QueueItemDao;", "shortcutDao", "Lcom/invictus/xmd/core/db/ShortcutDao;", "Companion", "app_fullDebug"})
@androidx.room.Database(entities = {com.invictus.xmd.core.QueueItem.class, com.invictus.xmd.core.Shortcut.class, com.invictus.xmd.core.HistoryEntry.class, com.invictus.xmd.core.Bookmark.class}, version = 11, exportSchema = false)
@androidx.room.TypeConverters(value = {com.invictus.xmd.core.db.Converters.class})
public abstract class AppDatabase extends androidx.room.RoomDatabase {
    @kotlin.jvm.Volatile()
    @org.jetbrains.annotations.Nullable()
    private static volatile com.invictus.xmd.core.db.AppDatabase instance;
    @org.jetbrains.annotations.NotNull()
    private static final androidx.room.migration.Migration MIGRATION_1_2 = null;
    @org.jetbrains.annotations.NotNull()
    private static final androidx.room.migration.Migration MIGRATION_2_3 = null;
    @org.jetbrains.annotations.NotNull()
    private static final androidx.room.migration.Migration MIGRATION_3_4 = null;
    @org.jetbrains.annotations.NotNull()
    private static final androidx.room.migration.Migration MIGRATION_4_5 = null;
    @org.jetbrains.annotations.NotNull()
    private static final androidx.room.migration.Migration MIGRATION_5_6 = null;
    @org.jetbrains.annotations.NotNull()
    private static final androidx.room.migration.Migration MIGRATION_6_7 = null;
    @org.jetbrains.annotations.NotNull()
    private static final androidx.room.migration.Migration MIGRATION_7_8 = null;
    @org.jetbrains.annotations.NotNull()
    private static final androidx.room.migration.Migration MIGRATION_8_9 = null;
    @org.jetbrains.annotations.NotNull()
    private static final androidx.room.migration.Migration MIGRATION_9_10 = null;
    @org.jetbrains.annotations.NotNull()
    private static final androidx.room.migration.Migration MIGRATION_10_11 = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.invictus.xmd.core.db.AppDatabase.Companion Companion = null;
    
    public AppDatabase() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public abstract com.invictus.xmd.core.db.QueueItemDao queueItemDao();
    
    @org.jetbrains.annotations.NotNull()
    public abstract com.invictus.xmd.core.db.ShortcutDao shortcutDao();
    
    @org.jetbrains.annotations.NotNull()
    public abstract com.invictus.xmd.core.db.HistoryDao historyDao();
    
    @org.jetbrains.annotations.NotNull()
    public abstract com.invictus.xmd.core.db.BookmarkDao bookmarkDao();
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\n\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u0010\u001a\u00020\u000f2\u0006\u0010\u0011\u001a\u00020\u0012R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\f\u001a\u00020\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\r\u001a\u00020\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u000e\u001a\u0004\u0018\u00010\u000fX\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0013"}, d2 = {"Lcom/invictus/xmd/core/db/AppDatabase$Companion;", "", "()V", "MIGRATION_10_11", "Landroidx/room/migration/Migration;", "MIGRATION_1_2", "MIGRATION_2_3", "MIGRATION_3_4", "MIGRATION_4_5", "MIGRATION_5_6", "MIGRATION_6_7", "MIGRATION_7_8", "MIGRATION_8_9", "MIGRATION_9_10", "instance", "Lcom/invictus/xmd/core/db/AppDatabase;", "get", "context", "Landroid/content/Context;", "app_fullDebug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.invictus.xmd.core.db.AppDatabase get(@org.jetbrains.annotations.NotNull()
        android.content.Context context) {
            return null;
        }
    }
}