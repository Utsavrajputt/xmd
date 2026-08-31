package com.invictus.xmd.ui;

/**
 * Dedicated Settings screen -- replaces the old single-dialog Settings UI.
 * This Activity hosts a root category list ([SettingsRootFragment]); tapping
 * a category pushes its Fragment into [R.id.settingsFragmentContainer] via
 * addToBackStack, same manual FragmentManager pattern MainActivity already
 * uses for Home/Downloads/Browser/History (no Jetpack Navigation component
 * in this codebase, so we don't introduce one here either).
 *
 * The header (back button + title) is drawn once here rather than per
 * fragment; each pushed fragment updates [setHeaderTitle] instead of
 * carrying its own toolbar, matching the self-drawn-header convention
 * already used by HistoryFragment.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000R\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\b\u0006\u0018\u0000  2\u00020\u00012\u00020\u0002:\u0001 B\u0005\u00a2\u0006\u0002\u0010\u0003J\b\u0010\n\u001a\u00020\u0006H\u0002J\u0012\u0010\u000b\u001a\u00020\f2\b\u0010\r\u001a\u0004\u0018\u00010\u000eH\u0014J\u0016\u0010\u000f\u001a\u00020\f2\u0006\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0012\u001a\u00020\u0006J\u0010\u0010\u0013\u001a\u00020\f2\u0006\u0010\u0014\u001a\u00020\u0015H\u0002J\u0010\u0010\u0016\u001a\u00020\f2\u0006\u0010\u0017\u001a\u00020\u0018H\u0002J\u0016\u0010\u0019\u001a\u00020\f2\f\u0010\u001a\u001a\b\u0012\u0004\u0012\u00020\u00150\u001bH\u0002J\b\u0010\u001c\u001a\u00020\fH\u0016J\b\u0010\u001d\u001a\u00020\fH\u0016J\b\u0010\u001e\u001a\u00020\fH\u0002J\u0010\u0010\u001f\u001a\u00020\f2\u0006\u0010\u0017\u001a\u00020\u0018H\u0002R\u001c\u0010\u0004\u001a\u0010\u0012\f\u0012\n \u0007*\u0004\u0018\u00010\u00060\u00060\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\tX\u0082.\u00a2\u0006\u0002\n\u0000\u00a8\u0006!"}, d2 = {"Lcom/invictus/xmd/ui/SettingsActivity;", "Landroidx/appcompat/app/AppCompatActivity;", "Lcom/invictus/xmd/ui/SettingsBrowserFragment$Callbacks;", "()V", "exportLauncher", "Landroidx/activity/result/ActivityResultLauncher;", "", "kotlin.jvm.PlatformType", "headerTitle", "Landroid/widget/TextView;", "defaultExportFileName", "onCreate", "", "savedInstanceState", "Landroid/os/Bundle;", "openCategory", "fragment", "Landroidx/fragment/app/Fragment;", "tag", "runWebImport", "file", "Ljava/io/File;", "shareExportedFile", "uri", "Landroid/net/Uri;", "showImportCandidatesDialog", "files", "", "startWebExportFlow", "startWebImportFlow", "syncHeaderTitle", "writeAndShareExport", "Companion", "app_fullDebug"})
public final class SettingsActivity extends androidx.appcompat.app.AppCompatActivity implements com.invictus.xmd.ui.SettingsBrowserFragment.Callbacks {
    private android.widget.TextView headerTitle;
    @org.jetbrains.annotations.NotNull()
    private final androidx.activity.result.ActivityResultLauncher<java.lang.String> exportLauncher = null;
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String TAG_ROOT = "settings_root";
    
    /**
     * Intent extra: which category to land on directly, skipping the
     * root list. See [CATEGORY_YOUTUBE].
     */
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_OPEN_CATEGORY = "open_category";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String CATEGORY_YOUTUBE = "youtube";
    @org.jetbrains.annotations.NotNull()
    public static final com.invictus.xmd.ui.SettingsActivity.Companion Companion = null;
    
    public SettingsActivity() {
        super();
    }
    
    @java.lang.Override()
    protected void onCreate(@org.jetbrains.annotations.Nullable()
    android.os.Bundle savedInstanceState) {
    }
    
    private final void syncHeaderTitle() {
    }
    
    /**
     * Called by [SettingsRootFragment] when a category row is tapped.
     */
    public final void openCategory(@org.jetbrains.annotations.NotNull()
    androidx.fragment.app.Fragment fragment, @org.jetbrains.annotations.NotNull()
    java.lang.String tag) {
    }
    
    @java.lang.Override()
    public void startWebImportFlow() {
    }
    
    private final void showImportCandidatesDialog(java.util.List<? extends java.io.File> files) {
    }
    
    private final void runWebImport(java.io.File file) {
    }
    
    @java.lang.Override()
    public void startWebExportFlow() {
    }
    
    private final java.lang.String defaultExportFileName() {
        return null;
    }
    
    private final void writeAndShareExport(android.net.Uri uri) {
    }
    
    private final void shareExportedFile(android.net.Uri uri) {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0007"}, d2 = {"Lcom/invictus/xmd/ui/SettingsActivity$Companion;", "", "()V", "CATEGORY_YOUTUBE", "", "EXTRA_OPEN_CATEGORY", "TAG_ROOT", "app_fullDebug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}