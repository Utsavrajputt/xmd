package com.invictus.xmd.core;

/**
 * Speed-dial tiles ("Shortcuts") shown on the Browser tab's new-tab page.
 * Unlike QueueRepository, there's no in-flight/background-writer state to race
 * against here -- reads/writes are just simple CRUD against Room, so the
 * DAO's own LiveData query is exposed directly instead of hand-rolling a
 * synchronized master list.
 *
 * The app no longer ships with any preloaded shortcuts -- a fresh install
 * starts at zero, and every entry comes from the user either adding one by
 * hand (add()) or importing an xmdweb source pack (importWebsites()) via
 * Settings -> Import Websites, which scans Downloads for matching files
 * and lets the user pick one.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0080\u0001\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\t\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\n\n\u0002\u0010!\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0011\n\u0002\b\u0004\b\u00c6\u0002\u0018\u00002\u00020\u0001:\u0002BCB\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0016\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0012\u001a\u00020\u00132\u0006\u0010\u0014\u001a\u00020\u0013J0\u0010\u0015\u001a\u00020\u00112\u0006\u0010\u0016\u001a\u00020\u00172\u0006\u0010\u0012\u001a\u00020\u00132\u0006\u0010\u0014\u001a\u00020\u00132\b\u0010\u0018\u001a\u0004\u0018\u00010\u0019H\u0086@\u00a2\u0006\u0002\u0010\u001aJ(\u0010\u001b\u001a\u0004\u0018\u00010\u00132\u0006\u0010\u0016\u001a\u00020\u00172\u0006\u0010\u001c\u001a\u00020\u00192\u0006\u0010\u001d\u001a\u00020\u0013H\u0086@\u00a2\u0006\u0002\u0010\u001eJ\u000e\u0010\u001f\u001a\u00020\u0004H\u0086@\u00a2\u0006\u0002\u0010 J\u000e\u0010!\u001a\u00020\u0013H\u0086@\u00a2\u0006\u0002\u0010 J\f\u0010\"\u001a\b\u0012\u0004\u0012\u00020#0\u000bJ\u0010\u0010$\u001a\u00020\u00132\u0006\u0010\u0014\u001a\u00020\u0013H\u0002J\u000e\u0010%\u001a\b\u0012\u0004\u0012\u00020#0\u000bH\u0002J\u0016\u0010&\u001a\u00020'2\u0006\u0010(\u001a\u00020#H\u0086@\u00a2\u0006\u0002\u0010)J\u000e\u0010*\u001a\u00020\u00112\u0006\u0010\u0016\u001a\u00020\u0017J\u0010\u0010+\u001a\u00020,2\u0006\u0010-\u001a\u00020\u0013H\u0002J\u0016\u0010.\u001a\b\u0012\u0004\u0012\u00020/0\u000b2\u0006\u00100\u001a\u00020\u0013H\u0002J\u000e\u00101\u001a\u00020\u00112\u0006\u00102\u001a\u00020\fJ\u0016\u00103\u001a\u00020\u00112\u0006\u00102\u001a\u00020\f2\u0006\u00104\u001a\u00020\u0013J\u0014\u00105\u001a\u00020\u00112\f\u00106\u001a\b\u0012\u0004\u0012\u00020\u00130\u000bJ&\u00107\u001a\u00020\u00112\u0006\u00108\u001a\u00020#2\f\u00109\u001a\b\u0012\u0004\u0012\u00020#0:2\u0006\u0010;\u001a\u00020\u0004H\u0002J\u000e\u0010<\u001a\u00020\u00112\u0006\u00102\u001a\u00020\fJ'\u0010=\u001a\u0004\u0018\u00010\u0013*\u00020>2\u0012\u0010?\u001a\n\u0012\u0006\b\u0001\u0012\u00020\u00130@\"\u00020\u0013H\u0002\u00a2\u0006\u0002\u0010AR\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R6\u0010\r\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\f0\u000b0\n2\u0012\u0010\t\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\f0\u000b0\n@BX\u0086.\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u000f\u00a8\u0006D"}, d2 = {"Lcom/invictus/xmd/core/ShortcutRepository;", "", "()V", "MAX_SCAN_DEPTH", "", "dao", "Lcom/invictus/xmd/core/db/ShortcutDao;", "scope", "Lkotlinx/coroutines/CoroutineScope;", "<set-?>", "Landroidx/lifecycle/LiveData;", "", "Lcom/invictus/xmd/core/Shortcut;", "shortcuts", "getShortcuts", "()Landroidx/lifecycle/LiveData;", "add", "", "title", "", "url", "addWithIcon", "context", "Landroid/content/Context;", "iconUri", "Landroid/net/Uri;", "(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;Landroid/net/Uri;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "copyIconToInternalStorage", "sourceUri", "shortcutId", "(Landroid/content/Context;Landroid/net/Uri;Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "count", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "exportWebsitesJson", "findImportCandidates", "Ljava/io/File;", "hostOf", "importScanRoots", "importWebsites", "Lcom/invictus/xmd/core/ShortcutRepository$WebImportResult;", "file", "(Ljava/io/File;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "init", "matchesImportFileName", "", "name", "parseWebJson", "Lcom/invictus/xmd/core/ShortcutRepository$WebSiteEntry;", "json", "remove", "shortcut", "rename", "newTitle", "reorder", "orderedIds", "scanForCandidates", "dir", "out", "", "depth", "update", "optStringOrNull", "Lorg/json/JSONObject;", "keys", "", "(Lorg/json/JSONObject;[Ljava/lang/String;)Ljava/lang/String;", "WebImportResult", "WebSiteEntry", "app_fullDebug"})
public final class ShortcutRepository {
    private static com.invictus.xmd.core.db.ShortcutDao dao;
    @org.jetbrains.annotations.NotNull()
    private static final kotlinx.coroutines.CoroutineScope scope = null;
    private static androidx.lifecycle.LiveData<java.util.List<com.invictus.xmd.core.Shortcut>> shortcuts;
    private static final int MAX_SCAN_DEPTH = 15;
    @org.jetbrains.annotations.NotNull()
    public static final com.invictus.xmd.core.ShortcutRepository INSTANCE = null;
    
    private ShortcutRepository() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final androidx.lifecycle.LiveData<java.util.List<com.invictus.xmd.core.Shortcut>> getShortcuts() {
        return null;
    }
    
    public final void init(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
    }
    
    /**
     * Recursively scans a fixed set of likely folders (and their
     * subfolders) for any xmdweb source-pack file -- matched by filename
     * only (case/separator-insensitive: "xmd_web.json", "XmdWeb (1).json",
     * "xmdweb-movies.json" all match). Scanned folders: Downloads, this
     * app's own "Xmd" download folder, and the WhatsApp/WhatsApp Business
     * "Documents" subfolder specifically (not Images/Video/Audio/Statuses
     * -- a JSON pack only ever lands there), covering both the legacy and
     * scoped-storage paths since which one exists depends on Android
     * version. Newest first. The caller (Settings -> Import Websites) lists results and lets the user pick
     * one -- no auto-popup, no system file picker.
     */
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<java.io.File> findImportCandidates() {
        return null;
    }
    
    private final java.util.List<java.io.File> importScanRoots() {
        return null;
    }
    
    private final void scanForCandidates(java.io.File dir, java.util.List<java.io.File> out, int depth) {
    }
    
    private final boolean matchesImportFileName(java.lang.String name) {
        return false;
    }
    
    /**
     * Parses and merges websites from an xmd_web.json source pack.
     * Duplicates are skipped by exact URL match against what's already
     * saved -- an existing shortcut's title/icon/sortOrder is left
     * untouched even if the pack lists a different name for the same URL.
     *
     * Accepts either `{"websites": [...]}` or a bare `[...]` array at the
     * root. Each entry needs "name" and "url" at minimum; "icon" maps to
     * the favicon shown on the tile. "category" (and any other field) is
     * accepted for forward-compatibility but not persisted yet -- Shortcut
     * has no category column today.
     */
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object importWebsites(@org.jetbrains.annotations.NotNull()
    java.io.File file, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.invictus.xmd.core.ShortcutRepository.WebImportResult> $completion) {
        return null;
    }
    
    private final java.util.List<com.invictus.xmd.core.ShortcutRepository.WebSiteEntry> parseWebJson(java.lang.String json) {
        return null;
    }
    
    private final java.lang.String optStringOrNull(org.json.JSONObject $this$optStringOrNull, java.lang.String... keys) {
        return null;
    }
    
    /**
     * Serializes all saved shortcuts back into the same xmdweb source-pack
     * shape importWebsites() reads (`{"websites": [...]}`) -- so a file
     * exported here can be re-imported on this device or shared to
     * another one via Settings -> Import Websites, sortOrder preserved.
     */
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object exportWebsitesJson(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.String> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object count(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.Integer> $completion) {
        return null;
    }
    
    public final void add(@org.jetbrains.annotations.NotNull()
    java.lang.String title, @org.jetbrains.annotations.NotNull()
    java.lang.String url) {
    }
    
    /**
     * Like [add], but for the case where the user also picked a custom icon
     * in the add-shortcut dialog: generates the id up front so the picked
     * image can be copied into place and attached to the very same insert,
     * instead of racing the async [add] + a follow-up lookup by URL.
     */
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object addWithIcon(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.NotNull()
    java.lang.String title, @org.jetbrains.annotations.NotNull()
    java.lang.String url, @org.jetbrains.annotations.Nullable()
    android.net.Uri iconUri, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    public final void remove(@org.jetbrains.annotations.NotNull()
    com.invictus.xmd.core.Shortcut shortcut) {
    }
    
    public final void rename(@org.jetbrains.annotations.NotNull()
    com.invictus.xmd.core.Shortcut shortcut, @org.jetbrains.annotations.NotNull()
    java.lang.String newTitle) {
    }
    
    /**
     * Saves edits to an existing shortcut in place -- preserves [Shortcut.id]
     * and [Shortcut.sortOrder] (and anything else the caller didn't touch),
     * unlike the old edit flow which did remove()+add() and silently reset
     * the tile to the end of the grid every time you edited it.
     */
    public final void update(@org.jetbrains.annotations.NotNull()
    com.invictus.xmd.core.Shortcut shortcut) {
    }
    
    /**
     * Persists a new tile order after a drag-reorder session. [orderedIds]
     * is the full, final top-to-bottom/left-to-right id sequence; only
     * called once, when the user taps "Done" -- dragging itself just
     * reorders the adapter's in-memory list.
     */
    public final void reorder(@org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.String> orderedIds) {
    }
    
    /**
     * Copies a user-picked icon image (from a content:// Uri, e.g. the
     * system photo picker) into this app's private files dir so it survives
     * independent of the source app/gallery. Returns the new file's absolute
     * path, or null if the copy failed. Old custom icon files aren't
     * auto-deleted here -- callers that replace/clear a shortcut's icon
     * should remove the previous file themselves if they track it.
     */
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object copyIconToInternalStorage(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.NotNull()
    android.net.Uri sourceUri, @org.jetbrains.annotations.NotNull()
    java.lang.String shortcutId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.String> $completion) {
        return null;
    }
    
    private final java.lang.String hostOf(java.lang.String url) {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0002\b\f\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0000\b\u0086\b\u0018\u00002\u00020\u0001B\u001d\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u0012\u0006\u0010\u0005\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0006J\t\u0010\u000b\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\f\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\r\u001a\u00020\u0003H\u00c6\u0003J'\u0010\u000e\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00032\b\b\u0002\u0010\u0005\u001a\u00020\u0003H\u00c6\u0001J\u0013\u0010\u000f\u001a\u00020\u00102\b\u0010\u0011\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0012\u001a\u00020\u0003H\u00d6\u0001J\t\u0010\u0013\u001a\u00020\u0014H\u00d6\u0001R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0011\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\bR\u0011\u0010\u0005\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\b\u00a8\u0006\u0015"}, d2 = {"Lcom/invictus/xmd/core/ShortcutRepository$WebImportResult;", "", "imported", "", "skipped", "total", "(III)V", "getImported", "()I", "getSkipped", "getTotal", "component1", "component2", "component3", "copy", "equals", "", "other", "hashCode", "toString", "", "app_fullDebug"})
    public static final class WebImportResult {
        private final int imported = 0;
        private final int skipped = 0;
        private final int total = 0;
        
        public WebImportResult(int imported, int skipped, int total) {
            super();
        }
        
        public final int getImported() {
            return 0;
        }
        
        public final int getSkipped() {
            return 0;
        }
        
        public final int getTotal() {
            return 0;
        }
        
        public final int component1() {
            return 0;
        }
        
        public final int component2() {
            return 0;
        }
        
        public final int component3() {
            return 0;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.invictus.xmd.core.ShortcutRepository.WebImportResult copy(int imported, int skipped, int total) {
            return null;
        }
        
        @java.lang.Override()
        public boolean equals(@org.jetbrains.annotations.Nullable()
        java.lang.Object other) {
            return false;
        }
        
        @java.lang.Override()
        public int hashCode() {
            return 0;
        }
        
        @java.lang.Override()
        @org.jetbrains.annotations.NotNull()
        public java.lang.String toString() {
            return null;
        }
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u000f\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\b\u0082\b\u0018\u00002\u00020\u0001B)\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u0012\b\u0010\u0005\u001a\u0004\u0018\u00010\u0003\u0012\b\u0010\u0006\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\u0002\u0010\u0007J\t\u0010\r\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u000e\u001a\u00020\u0003H\u00c6\u0003J\u000b\u0010\u000f\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\u000b\u0010\u0010\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J5\u0010\u0011\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00032\n\b\u0002\u0010\u0005\u001a\u0004\u0018\u00010\u00032\n\b\u0002\u0010\u0006\u001a\u0004\u0018\u00010\u0003H\u00c6\u0001J\u0013\u0010\u0012\u001a\u00020\u00132\b\u0010\u0014\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0015\u001a\u00020\u0016H\u00d6\u0001J\t\u0010\u0017\u001a\u00020\u0003H\u00d6\u0001R\u0013\u0010\u0006\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u0013\u0010\u0005\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\tR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\tR\u0011\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\t\u00a8\u0006\u0018"}, d2 = {"Lcom/invictus/xmd/core/ShortcutRepository$WebSiteEntry;", "", "name", "", "url", "icon", "category", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", "getCategory", "()Ljava/lang/String;", "getIcon", "getName", "getUrl", "component1", "component2", "component3", "component4", "copy", "equals", "", "other", "hashCode", "", "toString", "app_fullDebug"})
    static final class WebSiteEntry {
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String name = null;
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String url = null;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.String icon = null;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.String category = null;
        
        public WebSiteEntry(@org.jetbrains.annotations.NotNull()
        java.lang.String name, @org.jetbrains.annotations.NotNull()
        java.lang.String url, @org.jetbrains.annotations.Nullable()
        java.lang.String icon, @org.jetbrains.annotations.Nullable()
        java.lang.String category) {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getName() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getUrl() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String getIcon() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String getCategory() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component1() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component2() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String component3() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String component4() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.invictus.xmd.core.ShortcutRepository.WebSiteEntry copy(@org.jetbrains.annotations.NotNull()
        java.lang.String name, @org.jetbrains.annotations.NotNull()
        java.lang.String url, @org.jetbrains.annotations.Nullable()
        java.lang.String icon, @org.jetbrains.annotations.Nullable()
        java.lang.String category) {
            return null;
        }
        
        @java.lang.Override()
        public boolean equals(@org.jetbrains.annotations.Nullable()
        java.lang.Object other) {
            return false;
        }
        
        @java.lang.Override()
        public int hashCode() {
            return 0;
        }
        
        @java.lang.Override()
        @org.jetbrains.annotations.NotNull()
        public java.lang.String toString() {
            return null;
        }
    }
}