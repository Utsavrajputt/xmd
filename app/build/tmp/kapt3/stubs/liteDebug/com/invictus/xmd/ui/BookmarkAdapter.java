package com.invictus.xmd.ui;

/**
 * Chrome-style speed-dial grid: one tile per bookmark plus a trailing
 * "+" tile to add a new one. Tap opens the URL; long-press on a real
 * bookmark tile offers edit/delete (handled by the fragment via
 * [onLongPress]).
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\\\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0007\u0018\u0000 (2\b\u0012\u0004\u0012\u00020\u00020\u0001:\u0003&'(B;\u0012\u0012\u0010\u0003\u001a\u000e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020\u00060\u0004\u0012\u0012\u0010\u0007\u001a\u000e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020\u00060\u0004\u0012\f\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00060\t\u00a2\u0006\u0002\u0010\nJ\u0018\u0010\u000f\u001a\u00020\u00062\u0006\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0012\u001a\u00020\u0005H\u0002J\b\u0010\u0013\u001a\u00020\u0014H\u0016J\u0010\u0010\u0015\u001a\u00020\u00142\u0006\u0010\u0016\u001a\u00020\u0014H\u0016J\u0018\u0010\u0017\u001a\u00020\u00062\u0006\u0010\u0010\u001a\u00020\u00022\u0006\u0010\u0016\u001a\u00020\u0014H\u0016J\u0018\u0010\u0018\u001a\u00020\u00022\u0006\u0010\u0019\u001a\u00020\u001a2\u0006\u0010\u001b\u001a\u00020\u0014H\u0016J\u0010\u0010\u001c\u001a\u00020\u00062\u0006\u0010\u001d\u001a\u00020\u001eH\u0016J\u0010\u0010\u001f\u001a\u00020\u00062\u0006\u0010\u0010\u001a\u00020\u0002H\u0016J\u0018\u0010 \u001a\u00020\u00142\u0006\u0010!\u001a\u00020\"2\u0006\u0010#\u001a\u00020\u0014H\u0002J\u0014\u0010$\u001a\u00020\u00062\f\u0010%\u001a\b\u0012\u0004\u0012\u00020\u00050\fR\u0014\u0010\u000b\u001a\b\u0012\u0004\u0012\u00020\u00050\fX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0014\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00060\tX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001a\u0010\u0007\u001a\u000e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020\u00060\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001a\u0010\u0003\u001a\u000e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020\u00060\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\r\u001a\u00020\u000eX\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006)"}, d2 = {"Lcom/invictus/xmd/ui/BookmarkAdapter;", "Landroidx/recyclerview/widget/RecyclerView$Adapter;", "Landroidx/recyclerview/widget/RecyclerView$ViewHolder;", "onTap", "Lkotlin/Function1;", "Lcom/invictus/xmd/core/Bookmark;", "", "onLongPress", "onAddTap", "Lkotlin/Function0;", "(Lkotlin/jvm/functions/Function1;Lkotlin/jvm/functions/Function1;Lkotlin/jvm/functions/Function0;)V", "bookmarks", "", "scope", "Lkotlinx/coroutines/CoroutineScope;", "bindFavicon", "holder", "Lcom/invictus/xmd/ui/BookmarkAdapter$BookmarkViewHolder;", "bookmark", "getItemCount", "", "getItemViewType", "position", "onBindViewHolder", "onCreateViewHolder", "parent", "Landroid/view/ViewGroup;", "viewType", "onDetachedFromRecyclerView", "recyclerView", "Landroidx/recyclerview/widget/RecyclerView;", "onViewRecycled", "resolveThemeColor", "context", "Landroid/content/Context;", "attrResId", "submitList", "items", "AddTileViewHolder", "BookmarkViewHolder", "Companion", "app_liteDebug"})
public final class BookmarkAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder> {
    @org.jetbrains.annotations.NotNull()
    private final kotlin.jvm.functions.Function1<com.invictus.xmd.core.Bookmark, kotlin.Unit> onTap = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.jvm.functions.Function1<com.invictus.xmd.core.Bookmark, kotlin.Unit> onLongPress = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.jvm.functions.Function0<kotlin.Unit> onAddTap = null;
    @org.jetbrains.annotations.NotNull()
    private java.util.List<com.invictus.xmd.core.Bookmark> bookmarks;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.CoroutineScope scope = null;
    private static final int VIEW_TYPE_BOOKMARK = 0;
    private static final int VIEW_TYPE_ADD = 1;
    @org.jetbrains.annotations.NotNull()
    public static final com.invictus.xmd.ui.BookmarkAdapter.Companion Companion = null;
    
    public BookmarkAdapter(@org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function1<? super com.invictus.xmd.core.Bookmark, kotlin.Unit> onTap, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function1<? super com.invictus.xmd.core.Bookmark, kotlin.Unit> onLongPress, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function0<kotlin.Unit> onAddTap) {
        super();
    }
    
    public final void submitList(@org.jetbrains.annotations.NotNull()
    java.util.List<com.invictus.xmd.core.Bookmark> items) {
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
    private final void bindFavicon(com.invictus.xmd.ui.BookmarkAdapter.BookmarkViewHolder holder, com.invictus.xmd.core.Bookmark bookmark) {
    }
    
    /**
     * Resolves a color from the current active theme (Theme.Xmd.*) instead
     * of a static @color resource, so the favicon fallback tint follows
     * the selected app theme.
     */
    private final int resolveThemeColor(android.content.Context context, int attrResId) {
        return 0;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004\u00a8\u0006\u0005"}, d2 = {"Lcom/invictus/xmd/ui/BookmarkAdapter$AddTileViewHolder;", "Landroidx/recyclerview/widget/RecyclerView$ViewHolder;", "view", "Landroid/view/View;", "(Landroid/view/View;)V", "app_liteDebug"})
    public static final class AddTileViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
        
        public AddTileViewHolder(@org.jetbrains.annotations.NotNull()
        android.view.View view) {
            super(null);
        }
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0003\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004R\u0011\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0011\u0010\t\u001a\u00020\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u001c\u0010\r\u001a\u0004\u0018\u00010\u000eX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u000f\u0010\u0010\"\u0004\b\u0011\u0010\u0012R\u0011\u0010\u0013\u001a\u00020\u0014\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0015\u0010\u0016\u00a8\u0006\u0017"}, d2 = {"Lcom/invictus/xmd/ui/BookmarkAdapter$BookmarkViewHolder;", "Landroidx/recyclerview/widget/RecyclerView$ViewHolder;", "view", "Landroid/view/View;", "(Landroid/view/View;)V", "favicon", "Landroid/widget/ImageView;", "getFavicon", "()Landroid/widget/ImageView;", "faviconDefaultPadding", "", "getFaviconDefaultPadding", "()I", "faviconJob", "Lkotlinx/coroutines/Job;", "getFaviconJob", "()Lkotlinx/coroutines/Job;", "setFaviconJob", "(Lkotlinx/coroutines/Job;)V", "title", "Landroid/widget/TextView;", "getTitle", "()Landroid/widget/TextView;", "app_liteDebug"})
    public static final class BookmarkViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
        @org.jetbrains.annotations.NotNull()
        private final android.widget.ImageView favicon = null;
        @org.jetbrains.annotations.NotNull()
        private final android.widget.TextView title = null;
        private final int faviconDefaultPadding = 0;
        @org.jetbrains.annotations.Nullable()
        private kotlinx.coroutines.Job faviconJob;
        
        public BookmarkViewHolder(@org.jetbrains.annotations.NotNull()
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
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0006"}, d2 = {"Lcom/invictus/xmd/ui/BookmarkAdapter$Companion;", "", "()V", "VIEW_TYPE_ADD", "", "VIEW_TYPE_BOOKMARK", "app_liteDebug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}