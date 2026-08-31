package com.invictus.xmd.core;

/**
 * Thin wrapper around ConnectivityManager used only for the Wi-Fi-only
 * downloads setting -- answers "is the active network Wi-Fi right now" and
 * lets [DownloadService] listen for Wi-Fi being lost/regained so it can
 * pause/resume live downloads without polling.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000*\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u0002\n\u0002\b\u0004\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006J*\u0010\u0007\u001a\u00020\b2\u0006\u0010\u0005\u001a\u00020\u00062\f\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u000b0\n2\f\u0010\f\u001a\b\u0012\u0004\u0012\u00020\u000b0\nJ\u0016\u0010\r\u001a\u00020\u000b2\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u000e\u001a\u00020\b\u00a8\u0006\u000f"}, d2 = {"Lcom/invictus/xmd/core/NetworkMonitor;", "", "()V", "isOnWifi", "", "context", "Landroid/content/Context;", "register", "Landroid/net/ConnectivityManager$NetworkCallback;", "onWifiAvailable", "Lkotlin/Function0;", "", "onWifiLost", "unregister", "callback", "app_fullDebug"})
public final class NetworkMonitor {
    @org.jetbrains.annotations.NotNull()
    public static final com.invictus.xmd.core.NetworkMonitor INSTANCE = null;
    
    private NetworkMonitor() {
        super();
    }
    
    /**
     * True if the currently active network is Wi-Fi (or Ethernet, treated
     * the same as "not metered cellular" -- e.g. an emulator/TV box).
     */
    public final boolean isOnWifi(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        return false;
    }
    
    /**
     * Registers [onWifiAvailable] / [onWifiLost] for as long as the returned
     * callback stays registered -- caller owns the lifecycle and must pass
     * the same callback to [unregister] (typically in Service.onDestroy()).
     */
    @org.jetbrains.annotations.NotNull()
    public final android.net.ConnectivityManager.NetworkCallback register(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function0<kotlin.Unit> onWifiAvailable, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function0<kotlin.Unit> onWifiLost) {
        return null;
    }
    
    public final void unregister(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.NotNull()
    android.net.ConnectivityManager.NetworkCallback callback) {
    }
}