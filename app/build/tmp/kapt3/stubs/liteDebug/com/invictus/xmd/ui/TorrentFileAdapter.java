package com.invictus.xmd.ui;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000N\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\b\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\t\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0010!\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0010 \n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0002\b\b\u0018\u0000 #2\b\u0012\u0004\u0012\u00020\u00020\u0001:\u0002#$B=\u00126\u0010\u0003\u001a2\u0012\u0013\u0012\u00110\u0005\u00a2\u0006\f\b\u0006\u0012\b\b\u0007\u0012\u0004\b\b(\b\u0012\u0013\u0012\u00110\t\u00a2\u0006\f\b\u0006\u0012\b\b\u0007\u0012\u0004\b\b(\n\u0012\u0004\u0012\u00020\u000b0\u0004\u00a2\u0006\u0002\u0010\fJ\u0006\u0010\u0010\u001a\u00020\u0011J\b\u0010\u0012\u001a\u00020\u0005H\u0016J\u0006\u0010\u0013\u001a\u00020\u0005J\f\u0010\u0014\u001a\b\u0012\u0004\u0012\u00020\u00050\u0015J\u0006\u0010\u0016\u001a\u00020\u0005J\b\u0010\u0017\u001a\u00020\u000bH\u0002J\u0018\u0010\u0018\u001a\u00020\u000b2\u0006\u0010\u0019\u001a\u00020\u00022\u0006\u0010\u001a\u001a\u00020\u0005H\u0016J\u0018\u0010\u001b\u001a\u00020\u00022\u0006\u0010\u001c\u001a\u00020\u001d2\u0006\u0010\u001e\u001a\u00020\u0005H\u0016J\u000e\u0010\u001f\u001a\u00020\u000b2\u0006\u0010 \u001a\u00020\u0011J\u0014\u0010!\u001a\u00020\u000b2\f\u0010\"\u001a\b\u0012\u0004\u0012\u00020\u000f0\u0015R\u0014\u0010\r\u001a\b\u0012\u0004\u0012\u00020\u000f0\u000eX\u0082\u0004\u00a2\u0006\u0002\n\u0000R>\u0010\u0003\u001a2\u0012\u0013\u0012\u00110\u0005\u00a2\u0006\f\b\u0006\u0012\b\b\u0007\u0012\u0004\b\b(\b\u0012\u0013\u0012\u00110\t\u00a2\u0006\f\b\u0006\u0012\b\b\u0007\u0012\u0004\b\b(\n\u0012\u0004\u0012\u00020\u000b0\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006%"}, d2 = {"Lcom/invictus/xmd/ui/TorrentFileAdapter;", "Landroidx/recyclerview/widget/RecyclerView$Adapter;", "Lcom/invictus/xmd/ui/TorrentFileAdapter$FileViewHolder;", "onSelectionChanged", "Lkotlin/Function2;", "", "Lkotlin/ParameterName;", "name", "selectedCount", "", "selectedBytes", "", "(Lkotlin/jvm/functions/Function2;)V", "files", "", "Lcom/invictus/xmd/ui/TorrentFileEntry;", "areAllSelected", "", "getItemCount", "getSelectedCount", "getSelectedIndices", "", "getTotalCount", "notifySelectionChanged", "onBindViewHolder", "holder", "position", "onCreateViewHolder", "parent", "Landroid/view/ViewGroup;", "viewType", "selectAll", "select", "setFiles", "newFiles", "Companion", "FileViewHolder", "app_liteDebug"})
public final class TorrentFileAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<com.invictus.xmd.ui.TorrentFileAdapter.FileViewHolder> {
    @org.jetbrains.annotations.NotNull()
    private final kotlin.jvm.functions.Function2<java.lang.Integer, java.lang.Long, kotlin.Unit> onSelectionChanged = null;
    @org.jetbrains.annotations.NotNull()
    private final java.util.List<com.invictus.xmd.ui.TorrentFileEntry> files = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.invictus.xmd.ui.TorrentFileAdapter.Companion Companion = null;
    
    public TorrentFileAdapter(@org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function2<? super java.lang.Integer, ? super java.lang.Long, kotlin.Unit> onSelectionChanged) {
        super();
    }
    
    public final void setFiles(@org.jetbrains.annotations.NotNull()
    java.util.List<com.invictus.xmd.ui.TorrentFileEntry> newFiles) {
    }
    
    public final void selectAll(boolean select) {
    }
    
    public final boolean areAllSelected() {
        return false;
    }
    
    public final int getSelectedCount() {
        return 0;
    }
    
    public final int getTotalCount() {
        return 0;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<java.lang.Integer> getSelectedIndices() {
        return null;
    }
    
    private final void notifySelectionChanged() {
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public com.invictus.xmd.ui.TorrentFileAdapter.FileViewHolder onCreateViewHolder(@org.jetbrains.annotations.NotNull()
    android.view.ViewGroup parent, int viewType) {
        return null;
    }
    
    @java.lang.Override()
    public void onBindViewHolder(@org.jetbrains.annotations.NotNull()
    com.invictus.xmd.ui.TorrentFileAdapter.FileViewHolder holder, int position) {
    }
    
    @java.lang.Override()
    public int getItemCount() {
        return 0;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\t\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006\u00a8\u0006\u0007"}, d2 = {"Lcom/invictus/xmd/ui/TorrentFileAdapter$Companion;", "", "()V", "formatBytes", "", "bytes", "", "app_liteDebug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String formatBytes(long bytes) {
            return null;
        }
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0005\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004R\u0011\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0011\u0010\t\u001a\u00020\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\r\u001a\u00020\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\f\u00a8\u0006\u000f"}, d2 = {"Lcom/invictus/xmd/ui/TorrentFileAdapter$FileViewHolder;", "Landroidx/recyclerview/widget/RecyclerView$ViewHolder;", "itemView", "Landroid/view/View;", "(Landroid/view/View;)V", "checkbox", "Lcom/google/android/material/checkbox/MaterialCheckBox;", "getCheckbox", "()Lcom/google/android/material/checkbox/MaterialCheckBox;", "nameText", "Landroid/widget/TextView;", "getNameText", "()Landroid/widget/TextView;", "sizeText", "getSizeText", "app_liteDebug"})
    public static final class FileViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
        @org.jetbrains.annotations.NotNull()
        private final com.google.android.material.checkbox.MaterialCheckBox checkbox = null;
        @org.jetbrains.annotations.NotNull()
        private final android.widget.TextView nameText = null;
        @org.jetbrains.annotations.NotNull()
        private final android.widget.TextView sizeText = null;
        
        public FileViewHolder(@org.jetbrains.annotations.NotNull()
        android.view.View itemView) {
            super(null);
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.google.android.material.checkbox.MaterialCheckBox getCheckbox() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final android.widget.TextView getNameText() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final android.widget.TextView getSizeText() {
            return null;
        }
    }
}