package com.invictus.xmd.core;

/**
 * DNS-over-HTTPS resolver for the in-app Browser tab only -- this never
 * touches the device's system DNS or any other traffic in the app (the
 * download engine's own OkHttpClient is untouched). Used as the [Dns]
 * for the OkHttpClient that BrowserFragment routes every WebView request
 * through (see shouldInterceptRequest), so DNS resolution for browsing
 * traffic follows whatever the user picked in the Browser's DNS setting.
 *
 * Uses the DoH JSON API (RFC 8427-ish `application/dns-json`), supported
 * by both AdGuard's public resolver and most other DoH providers, so the
 * same resolver class works for both the built-in AdGuard endpoint and
 * a user-supplied custom DoH URL.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000(\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0006\u0018\u0000 \u000f2\u00020\u0001:\u0001\u000fB\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u0016\u0010\u000b\u001a\b\u0012\u0004\u0012\u00020\b0\u00072\u0006\u0010\f\u001a\u00020\u0003H\u0016J\u001e\u0010\r\u001a\b\u0012\u0004\u0012\u00020\b0\u00072\u0006\u0010\f\u001a\u00020\u00032\u0006\u0010\u000e\u001a\u00020\u0003H\u0002R \u0010\u0005\u001a\u0014\u0012\u0004\u0012\u00020\u0003\u0012\n\u0012\b\u0012\u0004\u0012\u00020\b0\u00070\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\nX\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0010"}, d2 = {"Lcom/invictus/xmd/core/DnsOverHttpsResolver;", "Lokhttp3/Dns;", "dohUrl", "", "(Ljava/lang/String;)V", "cache", "Ljava/util/concurrent/ConcurrentHashMap;", "", "Ljava/net/InetAddress;", "lookupClient", "Lokhttp3/OkHttpClient;", "lookup", "hostname", "queryDoh", "type", "Companion", "app_liteDebug"})
public final class DnsOverHttpsResolver implements okhttp3.Dns {
    @org.jetbrains.annotations.NotNull()
    private final java.lang.String dohUrl = null;
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String ADGUARD_DOH_URL = "https://dns.adguard.com";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String GOOGLE_DOH_URL = "https://dns.google/resolve";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String CLOUDFLARE_DOH_URL = "https://cloudflare-dns.com/dns-query";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String CLOUDFLARE_ADBLOCK_DOH_URL = "https://security.cloudflare-dns.com/dns-query";
    @org.jetbrains.annotations.NotNull()
    private final okhttp3.OkHttpClient lookupClient = null;
    @org.jetbrains.annotations.NotNull()
    private final java.util.concurrent.ConcurrentHashMap<java.lang.String, java.util.List<java.net.InetAddress>> cache = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.invictus.xmd.core.DnsOverHttpsResolver.Companion Companion = null;
    
    public DnsOverHttpsResolver(@org.jetbrains.annotations.NotNull()
    java.lang.String dohUrl) {
        super();
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public java.util.List<java.net.InetAddress> lookup(@org.jetbrains.annotations.NotNull()
    java.lang.String hostname) {
        return null;
    }
    
    private final java.util.List<java.net.InetAddress> queryDoh(java.lang.String hostname, java.lang.String type) {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0004\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\b"}, d2 = {"Lcom/invictus/xmd/core/DnsOverHttpsResolver$Companion;", "", "()V", "ADGUARD_DOH_URL", "", "CLOUDFLARE_ADBLOCK_DOH_URL", "CLOUDFLARE_DOH_URL", "GOOGLE_DOH_URL", "app_liteDebug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}