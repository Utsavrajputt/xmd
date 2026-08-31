package com.invictus.xmd.ui;

/**
 * Shows the share page in a visible WebView so the user can clear Cloudflare
 * / Turnstile exactly as they would in the desktop app's browser window.
 * Once cleared, injected JS calls the same HTMX "/f/{id}/go" endpoint the
 * desktop app calls and returns the resulting direct URL.
 *
 * Kotlin port of ff_downloader/core/browser_resolver.py's interactive flow.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000P\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\t\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0004\u0018\u0000  2\u00020\u0001:\u0002 !B\u0005\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u0012\u001a\u00020\u0013H\u0002J\u0010\u0010\u0014\u001a\u00020\u00132\u0006\u0010\u0015\u001a\u00020\u0006H\u0002J\u0010\u0010\u0016\u001a\u00020\u00132\u0006\u0010\u0017\u001a\u00020\u0006H\u0002J\b\u0010\u0018\u001a\u00020\u0013H\u0016J\u0012\u0010\u0019\u001a\u00020\u00132\b\u0010\u001a\u001a\u0004\u0018\u00010\u001bH\u0015J\b\u0010\u001c\u001a\u00020\u0013H\u0014J\u0010\u0010\u001d\u001a\u00020\u001e2\u0006\u0010\u001f\u001a\u00020\u001eH\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\bX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\nX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\fX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\r\u001a\u00020\u0006X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000e\u001a\u00020\u000fX\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0010\u001a\u00020\u0011X\u0082.\u00a2\u0006\u0002\n\u0000\u00a8\u0006\""}, d2 = {"Lcom/invictus/xmd/ui/ChallengeActivity;", "Landroidx/appcompat/app/AppCompatActivity;", "()V", "elapsedMs", "", "fileId", "", "finished", "", "handler", "Landroid/os/Handler;", "pollRunnable", "Ljava/lang/Runnable;", "shareUrl", "statusText", "Landroid/widget/TextView;", "webView", "Landroid/webkit/WebView;", "finishCancelled", "", "finishWithError", "message", "finishWithSuccess", "directUrl", "onBackPressed", "onCreate", "savedInstanceState", "Landroid/os/Bundle;", "onDestroy", "resolveThemeColor", "", "attrResId", "Companion", "JsBridge", "app_liteDebug"})
public final class ChallengeActivity extends androidx.appcompat.app.AppCompatActivity {
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_SHARE_URL = "extra_share_url";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_FILE_ID = "extra_file_id";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_DIRECT_URL = "extra_direct_url";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_ERROR = "extra_error";
    private static final long POLL_INTERVAL_MS = 1500L;
    private static final long TIMEOUT_MS = 120000L;
    private android.webkit.WebView webView;
    private android.widget.TextView statusText;
    @org.jetbrains.annotations.NotNull()
    private final android.os.Handler handler = null;
    private long elapsedMs = 0L;
    private boolean finished = false;
    private java.lang.String shareUrl;
    private java.lang.String fileId;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.Runnable pollRunnable = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.invictus.xmd.ui.ChallengeActivity.Companion Companion = null;
    
    public ChallengeActivity() {
        super();
    }
    
    @java.lang.Override()
    @android.annotation.SuppressLint(value = {"SetJavaScriptEnabled"})
    protected void onCreate(@org.jetbrains.annotations.Nullable()
    android.os.Bundle savedInstanceState) {
    }
    
    private final void finishWithSuccess(java.lang.String directUrl) {
    }
    
    private final void finishWithError(java.lang.String message) {
    }
    
    private final void finishCancelled() {
    }
    
    @java.lang.Override()
    protected void onDestroy() {
    }
    
    @java.lang.Override()
    public void onBackPressed() {
    }
    
    /**
     * Resolves a color from the current active theme (Theme.Xmd.*) instead
     * of a static @color resource, so the toolbar title follows the
     * selected app theme.
     */
    private final int resolveThemeColor(int attrResId) {
        return 0;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u001c\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0004\n\u0002\u0010\t\n\u0002\b\u0002\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\tX\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\tX\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u000b"}, d2 = {"Lcom/invictus/xmd/ui/ChallengeActivity$Companion;", "", "()V", "EXTRA_DIRECT_URL", "", "EXTRA_ERROR", "EXTRA_FILE_ID", "EXTRA_SHARE_URL", "POLL_INTERVAL_MS", "", "TIMEOUT_MS", "app_liteDebug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u001a\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u0082\u0004\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0007J\u0010\u0010\u0007\u001a\u00020\u00042\u0006\u0010\b\u001a\u00020\u0006H\u0007\u00a8\u0006\t"}, d2 = {"Lcom/invictus/xmd/ui/ChallengeActivity$JsBridge;", "", "(Lcom/invictus/xmd/ui/ChallengeActivity;)V", "onLog", "", "message", "", "onResolved", "redirectUrl", "app_liteDebug"})
    final class JsBridge {
        
        public JsBridge() {
            super();
        }
        
        @android.webkit.JavascriptInterface()
        public final void onResolved(@org.jetbrains.annotations.NotNull()
        java.lang.String redirectUrl) {
        }
        
        @android.webkit.JavascriptInterface()
        public final void onLog(@org.jetbrains.annotations.NotNull()
        java.lang.String message) {
        }
    }
}