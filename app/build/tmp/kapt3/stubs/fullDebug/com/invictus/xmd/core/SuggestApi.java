package com.invictus.xmd.core;

/**
 * Address-bar autocomplete. Backed by Google's public suggest endpoint
 * (the same one Chrome's omnibox uses) rather than any list bundled in
 * this app -- we don't ship or maintain a list of sites of any kind
 * (movie, download, or otherwise). Whatever the user types is sent to
 * Google and results come back tagged by type; only "QUERY" (a search
 * phrase) results are kept, "NAVIGATION" (a website/URL guess) ones are
 * dropped, so the dropdown always reads as search suggestions rather than
 * site suggestions.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010 \n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u001c\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00040\u00062\u0006\u0010\u0007\u001a\u00020\u00042\u0006\u0010\b\u001a\u00020\tR\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\n"}, d2 = {"Lcom/invictus/xmd/core/SuggestApi;", "", "()V", "ENDPOINT", "", "suggest", "", "query", "client", "Lokhttp3/OkHttpClient;", "app_fullDebug"})
public final class SuggestApi {
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String ENDPOINT = "https://www.google.com/complete/search";
    @org.jetbrains.annotations.NotNull()
    public static final com.invictus.xmd.core.SuggestApi INSTANCE = null;
    
    private SuggestApi() {
        super();
    }
    
    /**
     * Empty list on any failure (network error, malformed response, blank query).
     */
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<java.lang.String> suggest(@org.jetbrains.annotations.NotNull()
    java.lang.String query, @org.jetbrains.annotations.NotNull()
    okhttp3.OkHttpClient client) {
        return null;
    }
}