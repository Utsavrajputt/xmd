package com.invictus.xmd.core;

/**
 * Status of a single queued link as it moves through resolve -> download.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0002\b\f\b\u0086\u0081\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00000\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002j\u0002\b\u0003j\u0002\b\u0004j\u0002\b\u0005j\u0002\b\u0006j\u0002\b\u0007j\u0002\b\bj\u0002\b\tj\u0002\b\nj\u0002\b\u000bj\u0002\b\f\u00a8\u0006\r"}, d2 = {"Lcom/invictus/xmd/core/ItemStatus;", "", "(Ljava/lang/String;I)V", "PENDING", "RESOLVING", "NEEDS_CHALLENGE", "READY", "DOWNLOADING", "PAUSED", "RETRYING", "SAVING", "DONE", "FAILED", "app_fullDebug"})
public enum ItemStatus {
    /*public static final*/ PENDING /* = new PENDING() */,
    /*public static final*/ RESOLVING /* = new RESOLVING() */,
    /*public static final*/ NEEDS_CHALLENGE /* = new NEEDS_CHALLENGE() */,
    /*public static final*/ READY /* = new READY() */,
    /*public static final*/ DOWNLOADING /* = new DOWNLOADING() */,
    /*public static final*/ PAUSED /* = new PAUSED() */,
    /*public static final*/ RETRYING /* = new RETRYING() */,
    /*public static final*/ SAVING /* = new SAVING() */,
    /*public static final*/ DONE /* = new DONE() */,
    /*public static final*/ FAILED /* = new FAILED() */;
    
    ItemStatus() {
    }
    
    @org.jetbrains.annotations.NotNull()
    public static kotlin.enums.EnumEntries<com.invictus.xmd.core.ItemStatus> getEntries() {
        return null;
    }
}