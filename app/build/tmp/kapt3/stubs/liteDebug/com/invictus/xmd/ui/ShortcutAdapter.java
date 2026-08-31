package com.invictus.xmd.ui;

/**
 * Chrome-style speed-dial grid: one tile per shortcut plus a trailing
 * "+" tile to add a new one. Tap opens the URL; long-press on a real
 * shortcut tile offers edit/delete (handled by the fragment via
 * [onLongPress]).
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000r\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010%\n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\b\n\u0002\b\b\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0007\u0018\u0000 92\b\u0012\u0004\u0012\u00020\u00020\u0001:\u000389:BQ\u0012\u0012\u0010\u0003\u001a\u000e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020\u00060\u0004\u0012\u0012\u0010\u0007\u001a\u000e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020\u00060\u0004\u0012\f\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00060\t\u0012\u0014\b\u0002\u0010\n\u001a\u000e\u0012\u0004\u0012\u00020\u0002\u0012\u0004\u0012\u00020\u00060\u0004\u00a2\u0006\u0002\u0010\u000bJ\u0018\u0010\u001b\u001a\u00020\u00062\u0006\u0010\u001c\u001a\u00020\u001d2\u0006\u0010\u001e\u001a\u00020\u0005H\u0002J\f\u0010\u001f\u001a\b\u0012\u0004\u0012\u00020\u000e0\u001aJ\u0012\u0010 \u001a\u0004\u0018\u00010\u000f2\u0006\u0010!\u001a\u00020\u000eH\u0002J\b\u0010\"\u001a\u00020#H\u0016J\u0010\u0010$\u001a\u00020#2\u0006\u0010%\u001a\u00020#H\u0016J\u0016\u0010&\u001a\u00020\u00062\u0006\u0010'\u001a\u00020#2\u0006\u0010(\u001a\u00020#J\u0018\u0010)\u001a\u00020\u00062\u0006\u0010\u001c\u001a\u00020\u00022\u0006\u0010%\u001a\u00020#H\u0016J\u0018\u0010*\u001a\u00020\u00022\u0006\u0010+\u001a\u00020,2\u0006\u0010-\u001a\u00020#H\u0016J\u0010\u0010.\u001a\u00020\u00062\u0006\u0010/\u001a\u000200H\u0016J\u0010\u00101\u001a\u00020\u00062\u0006\u0010\u001c\u001a\u00020\u0002H\u0016J\u0018\u00102\u001a\u00020#2\u0006\u00103\u001a\u0002042\u0006\u00105\u001a\u00020#H\u0002J\u0014\u00106\u001a\u00020\u00062\f\u00107\u001a\b\u0012\u0004\u0012\u00020\u00050\u001aR\u001c\u0010\f\u001a\u0010\u0012\u0004\u0012\u00020\u000e\u0012\u0006\u0012\u0004\u0018\u00010\u000f0\rX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00060\tX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001a\u0010\u0007\u001a\u000e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020\u00060\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001a\u0010\n\u001a\u000e\u0012\u0004\u0012\u00020\u0002\u0012\u0004\u0012\u00020\u00060\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001a\u0010\u0003\u001a\u000e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020\u00060\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R$\u0010\u0012\u001a\u00020\u00112\u0006\u0010\u0010\u001a\u00020\u0011@FX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0013\u0010\u0014\"\u0004\b\u0015\u0010\u0016R\u000e\u0010\u0017\u001a\u00020\u0018X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u0019\u001a\b\u0012\u0004\u0012\u00020\u00050\u001aX\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006;"}, d2 = {"Lcom/invictus/xmd/ui/ShortcutAdapter;", "Landroidx/recyclerview/widget/RecyclerView$Adapter;", "Landroidx/recyclerview/widget/RecyclerView$ViewHolder;", "onTap", "Lkotlin/Function1;", "Lcom/invictus/xmd/core/Shortcut;", "", "onLongPress", "onAddTap", "Lkotlin/Function0;", "onStartDrag", "(Lkotlin/jvm/functions/Function1;Lkotlin/jvm/functions/Function1;Lkotlin/jvm/functions/Function0;Lkotlin/jvm/functions/Function1;)V", "customIconCache", "", "", "Landroid/graphics/Bitmap;", "value", "", "reorderMode", "getReorderMode", "()Z", "setReorderMode", "(Z)V", "scope", "Lkotlinx/coroutines/CoroutineScope;", "shortcuts", "", "bindFavicon", "holder", "Lcom/invictus/xmd/ui/ShortcutAdapter$ShortcutViewHolder;", "shortcut", "currentIds", "decodeCustomIcon", "path", "getItemCount", "", "getItemViewType", "position", "moveItem", "fromPosition", "toPosition", "onBindViewHolder", "onCreateViewHolder", "parent", "Landroid/view/ViewGroup;", "viewType", "onDetachedFromRecyclerView", "recyclerView", "Landroidx/recyclerview/widget/RecyclerView;", "onViewRecycled", "resolveThemeColor", "context", "Landroid/content/Context;", "attrResId", "submitList", "items", "AddTileViewHolder", "Companion", "ShortcutViewHolder", "app_liteDebug"})
public final class ShortcutAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder> {
    @org.jetbrains.annotations.NotNull()
    private final kotlin.jvm.functions.Function1<com.invictus.xmd.core.Shortcut, kotlin.Unit> onTap = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.jvm.functions.Function1<com.invictus.xmd.core.Shortcut, kotlin.Unit> onLongPress = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.jvm.functions.Function0<kotlin.Unit> onAddTap = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.jvm.functions.Function1<androidx.recyclerview.widget.RecyclerView.ViewHolder, kotlin.Unit> onStartDrag = null;
    @org.jetbrains.annotations.NotNull()
    private java.util.List<com.invictus.xmd.core.Shortcut> shortcuts;
    private boolean reorderMode = false;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.CoroutineScope scope = null;
    private static final int VIEW_TYPE_SHORTCUT = 0;
    private static final int VIEW_TYPE_ADD = 1;
    @org.jetbrains.annotations.NotNull()
    private final java.util.Map<java.lang.String, android.graphics.Bitmap> customIconCache = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.invictus.xmd.ui.ShortcutAdapter.Companion Companion = null;
    
    public ShortcutAdapter(@org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function1<? super com.invictus.xmd.core.Shortcut, kotlin.Unit> onTap, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function1<? super com.invictus.xmd.core.Shortcut, kotlin.Unit> onLongPress, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function0<kotlin.Unit> onAddTap, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function1<? super androidx.recyclerview.widget.RecyclerView.ViewHolder, kotlin.Unit> onStartDrag) {
        super();
    }
    
    public final boolean getReorderMode() {
        return false;
    }
    
    public final void setReorderMode(boolean value) {
    }
    
    /**
     * Current in-memory order, used by the fragment to persist once
     * reorder mode ends. Doesn't touch the DB itself.
     */
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<java.lang.String> currentIds() {
        return null;
    }
    
    /**
     * Called by the ItemTouchHelper callback as a tile is dragged over
     * another position; just reorders the in-memory list + notifies,
     * no DB write until the fragment calls ShortcutRepository.reorder().
     */
    public final void moveItem(int fromPosition, int toPosition) {
    }
    
    public final void submitList(@org.jetbrains.annotations.NotNull()
    java.util.List<com.invictus.xmd.core.Shortcut> items) {
    }
    
    @java.lang.Override()
    public int getItemCount() {
        return 0;
    }
    
    @java.lang.Override()
    public int getItemViewType(int position) {
        return 0;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public androidx.recyclerview.widget.RecyclerView.ViewHolder onCreateViewHolder(@org.jetbrains.annotations.NotNull()
    android.view.ViewGroup parent, int viewType) {
        return null;
    }
    
    @java.lang.Override()
    public void onBindViewHolder(@org.jetbrains.annotations.NotNull()
    androidx.recyclerview.widget.RecyclerView.ViewHolder holder, int position) {
    }
    
    @java.lang.Override()
    public void onDetachedFromRecyclerView(@org.jetbrains.annotations.NotNull()
    androidx.recyclerview.widget.RecyclerView recyclerView) {
    }
    
    @java.lang.Override()
    public void onViewRecycled(@org.jetbrains.annotations.NotNull()
    androidx.recyclerview.widget.RecyclerView.ViewHolder holder) {
    }
    
    /**
     * Loads the tile's real favicon in the background (FaviconLoader has its
     * own cache, so repeat binds of the same host are cheap). Falls back to
     * -- i.e. simply never replaces -- the generic ic_link icon already set
     * in the layout XML if the fetch fails or the view gets recycled before
     * it completes.
     */
    private final void bindFavicon(com.invictus.xmd.ui.ShortcutAdapter.ShortcutViewHolder holder, com.invictus.xmd.core.Shortcut shortcut) {
    }
    
    private final android.graphics.Bitmap decodeCustomIcon(java.lang.String path) {
        return null;
    }
    
    /**
     * Resolves a color from the current active theme (Theme.Xmd.*) instead
     * of a static @color resource, so the favicon fallback tint follows
     * the selected app theme.
     */
    private final int resolveThemeColor(android.content.Context context, int attrResId) {
        return 0;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004\u00a8\u0006\u0005"}, d2 = {"Lcom/invictus/xmd/ui/ShortcutAdapter$AddTileViewHolder;", "Landroidx/recyclerview/widget/RecyclerView$ViewHolder;", "view", "Landroid/view/View;", "(Landroid/view/View;)V", "app_liteDebug"})
    public static final class AddTileViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
        
        public AddTileViewHolder(@org.jetbrains.annotations.NotNull()
        android.view.View view) {
            super(null);
        }
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0006"}, d2 = {"Lcom/invictus/xmd/ui/ShortcutAdapter$Companion;", "", "()V", "VIEW_TYPE_ADD", "", "VIEW_TYPE_SHORTCUT", "app_liteDebug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0003\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004R\u0011\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0011\u0010\t\u001a\u00020\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u001c\u0010\r\u001a\u0004\u0018\u00010\u000eX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u000f\u0010\u0010\"\u0004\b\u0011\u0010\u0012R\u0011\u0010\u0013\u001a\u00020\u0014\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0015\u0010\u0016\u00a8\u0006\u0017"}, d2 = {"Lcom/invictus/xmd/ui/ShortcutAdapter$ShortcutViewHolder;", "Landroidx/recyclerview/widget/RecyclerView$ViewHolder;", "view", "Landroid/view/View;", "(Landroid/view/View;)V", "favicon", "Landroid/widget/ImageView;", "getFavicon", "()Landroid/widget/ImageView;", "faviconDefaultPadding", "", "getFaviconDefaultPadding", "()I", "faviconJob", "Lkotlinx/coroutines/Job;", "getFaviconJob", "()Lkotlinx/coroutines/Job;", "setFaviconJob", "(Lkotlinx/coroutines/Job;)V", "title", "Landroid/widget/TextView;", "getTitle", "()Landroid/widget/TextView;", "app_liteDebug"})
    public static final class ShortcutViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
        @org.jetbrains.annotations.NotNull()
        private final android.widget.ImageView favicon = null;
        @org.jetbrains.annotations.NotNull()
        private final android.widget.TextView title = null;
        private final int faviconDefaultPadding = 0;
        @org.jetbrains.annotations.Nullable()
        private kotlinx.coroutines.Job faviconJob;
        
        public ShortcutViewHolder(@org.jetbrains.annotations.NotNull()
        android.view.View view) {
            super(null);
        }
        
        @org.jetbrains.annotations.NotNull()
        public final android.widget.ImageView getFavicon() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final android.widget.TextView getTitle() {
            return null;
        }
        
        public final int getFaviconDefaultPadding() {
            return 0;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final kotlinx.coroutines.Job getFaviconJob() {
            return null;
        }
        
        public final void setFaviconJob(@org.jetbrains.annotations.Nullable()
        kotlinx.coroutines.Job p0) {
        }
    }
}