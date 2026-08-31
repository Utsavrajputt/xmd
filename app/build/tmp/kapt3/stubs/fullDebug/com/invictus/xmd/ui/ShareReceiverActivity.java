package com.invictus.xmd.ui;

/**
 * Entry point for links handed to xmd from another app's "external
 * downloader" hook -- Morphe's Player > External downloads setting,
 * browsers without a download-manager chooser, etc. -- instead of
 * MainActivity.
 *
 * Why this exists instead of just handling ACTION_SEND in MainActivity
 * (which it used to): opening MainActivity brings xmd's whole UI to the
 * foreground, which briefly kicks the caller (YouTube inside Morphe, a
 * browser tab, ...) off-screen. This activity is themed fully transparent
 * (Theme.Xmd.Transparent, see themes.xml) so nothing behind it ever
 * disappears -- it just shows a small bottom sheet (quality picker for
 * YouTube, nothing at all for a plain direct-download link) and finishes
 * itself the moment a choice is made, exactly like YTDLnis/Seal do.
 *
 * Deliberately narrow in scope: only YouTube/HLS/DASH links (quality
 * picker) and
 * plain generic-download links (queue + start immediately, no UI) are
 * handled invisibly. Share-links that need the Cloudflare-challenge
 * WebView still hand off to MainActivity -- that flow can't happen without
 * a visible screen, so there's no point pretending otherwise.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00006\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0012\u0010\t\u001a\u0004\u0018\u00010\n2\u0006\u0010\u000b\u001a\u00020\fH\u0002J\u0010\u0010\r\u001a\u00020\u000e2\u0006\u0010\u000b\u001a\u00020\fH\u0002J\u0012\u0010\u000f\u001a\u00020\u000e2\b\u0010\u0010\u001a\u0004\u0018\u00010\u0011H\u0014J\u0010\u0010\u0012\u001a\u00020\u000e2\u0006\u0010\u0013\u001a\u00020\u0014H\u0002R\u001b\u0010\u0003\u001a\u00020\u00048BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b\u0007\u0010\b\u001a\u0004\b\u0005\u0010\u0006\u00a8\u0006\u0015"}, d2 = {"Lcom/invictus/xmd/ui/ShareReceiverActivity;", "Landroidx/appcompat/app/AppCompatActivity;", "()V", "themedContext", "Landroid/view/ContextThemeWrapper;", "getThemedContext", "()Landroid/view/ContextThemeWrapper;", "themedContext$delegate", "Lkotlin/Lazy;", "extractUrl", "", "intent", "Landroid/content/Intent;", "handleShareIntent", "", "onCreate", "savedInstanceState", "Landroid/os/Bundle;", "showYtDlpQualitySheet", "item", "Lcom/invictus/xmd/core/QueueItem;", "app_fullDebug"})
public final class ShareReceiverActivity extends androidx.appcompat.app.AppCompatActivity {
    @org.jetbrains.annotations.NotNull()
    private final kotlin.Lazy themedContext$delegate = null;
    
    public ShareReceiverActivity() {
        super();
    }
    
    private final android.view.ContextThemeWrapper getThemedContext() {
        return null;
    }
    
    @java.lang.Override()
    protected void onCreate(@org.jetbrains.annotations.Nullable()
    android.os.Bundle savedInstanceState) {
    }
    
    private final void handleShareIntent(android.content.Intent intent) {
    }
    
    private final java.lang.String extractUrl(android.content.Intent intent) {
        return null;
    }
    
    private final void showYtDlpQualitySheet(com.invictus.xmd.core.QueueItem item) {
    }
}