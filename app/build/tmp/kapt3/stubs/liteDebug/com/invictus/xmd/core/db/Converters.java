package com.invictus.xmd.core.db;

/**
 * Room can't store enums natively -- it needs an explicit mapping to a
 * column type. We store both as their enum name (String) rather than
 * ordinal so that reordering/inserting entries in ItemStatus or
 * DownloadCategory later doesn't silently corrupt already-persisted rows.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0007J\u0010\u0010\u0007\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\bH\u0007J\u0010\u0010\t\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\nH\u0007J\u0010\u0010\u000b\u001a\u00020\u00062\u0006\u0010\u0005\u001a\u00020\u0004H\u0007J\u0010\u0010\f\u001a\u00020\b2\u0006\u0010\u0005\u001a\u00020\u0004H\u0007J\u0010\u0010\r\u001a\u00020\n2\u0006\u0010\u0005\u001a\u00020\u0004H\u0007\u00a8\u0006\u000e"}, d2 = {"Lcom/invictus/xmd/core/db/Converters;", "", "()V", "fromDownloadCategory", "", "value", "Lcom/invictus/xmd/core/DownloadCategory;", "fromItemStatus", "Lcom/invictus/xmd/core/ItemStatus;", "fromMediaPlatform", "Lcom/invictus/xmd/core/MediaPlatform;", "toDownloadCategory", "toItemStatus", "toMediaPlatform", "app_liteDebug"})
public final class Converters {
    
    public Converters() {
        super();
    }
    
    @androidx.room.TypeConverter()
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String fromItemStatus(@org.jetbrains.annotations.NotNull()
    com.invictus.xmd.core.ItemStatus value) {
        return null;
    }
    
    @androidx.room.TypeConverter()
    @org.jetbrains.annotations.NotNull()
    public final com.invictus.xmd.core.ItemStatus toItemStatus(@org.jetbrains.annotations.NotNull()
    java.lang.String value) {
        return null;
    }
    
    @androidx.room.TypeConverter()
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String fromDownloadCategory(@org.jetbrains.annotations.NotNull()
    com.invictus.xmd.core.DownloadCategory value) {
        return null;
    }
    
    @androidx.room.TypeConverter()
    @org.jetbrains.annotations.NotNull()
    public final com.invictus.xmd.core.DownloadCategory toDownloadCategory(@org.jetbrains.annotations.NotNull()
    java.lang.String value) {
        return null;
    }
    
    @androidx.room.TypeConverter()
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String fromMediaPlatform(@org.jetbrains.annotations.NotNull()
    com.invictus.xmd.core.MediaPlatform value) {
        return null;
    }
    
    @androidx.room.TypeConverter()
    @org.jetbrains.annotations.NotNull()
    public final com.invictus.xmd.core.MediaPlatform toMediaPlatform(@org.jetbrains.annotations.NotNull()
    java.lang.String value) {
        return null;
    }
}