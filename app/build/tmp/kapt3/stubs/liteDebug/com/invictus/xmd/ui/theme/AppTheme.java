package com.invictus.xmd.ui.theme;

/**
 * The app's selectable color themes. Each one maps to a dark `Theme.Xmd.*`
 * and a light `Theme.Xmd.*.Light` style in themes.xml (applied at runtime
 * via `Activity.setTheme()` before `super.onCreate()`, resolved through
 * [resolvedStyleRes] against the separately-stored dark/light mode flag)
 * plus a handful of swatch colors used to draw the little preview card in
 * the theme picker -- no need to inflate the real style just to show what
 * it looks like.
 *
 * Stored in [com.invictus.xmd.core.Settings] by [storageKey], so renaming an
 * enum entry is safe but changing [storageKey] is not. Dark/light mode is
 * orthogonal, stored separately via `Settings.isDarkMode()` and toggled by
 * double-tapping the app header; see MainActivity.toggleDarkMode().
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\b\n\u0002\b\u0013\n\u0002\u0010\u000b\n\u0002\b\n\b\u0086\u0081\u0002\u0018\u0000 \"2\b\u0012\u0004\u0012\u00020\u00000\u0001:\u0001\"BM\b\u0002\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\b\b\u0001\u0010\u0004\u001a\u00020\u0005\u0012\b\b\u0001\u0010\u0006\u001a\u00020\u0005\u0012\b\b\u0001\u0010\u0007\u001a\u00020\u0005\u0012\u0006\u0010\b\u001a\u00020\u0003\u0012\u0006\u0010\t\u001a\u00020\u0003\u0012\u0006\u0010\n\u001a\u00020\u0003\u0012\u0006\u0010\u000b\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\fJ\u0010\u0010\u0017\u001a\u00020\u00052\u0006\u0010\u0018\u001a\u00020\u0019H\u0007R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000eR\u0011\u0010\u0006\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u0011\u0010\u0007\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0010R\u0011\u0010\b\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0012\u0010\u000eR\u0011\u0010\t\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\u000eR\u0011\u0010\n\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0014\u0010\u000eR\u0011\u0010\u000b\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0015\u0010\u000eR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0016\u0010\u0010j\u0002\b\u001aj\u0002\b\u001bj\u0002\b\u001cj\u0002\b\u001dj\u0002\b\u001ej\u0002\b\u001fj\u0002\b j\u0002\b!\u00a8\u0006#"}, d2 = {"Lcom/invictus/xmd/ui/theme/AppTheme;", "", "storageKey", "", "titleRes", "", "styleResDark", "styleResLight", "swatchBackground", "swatchPrimary", "swatchSecondary", "swatchTertiary", "(Ljava/lang/String;ILjava/lang/String;IIILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", "getStorageKey", "()Ljava/lang/String;", "getStyleResDark", "()I", "getStyleResLight", "getSwatchBackground", "getSwatchPrimary", "getSwatchSecondary", "getSwatchTertiary", "getTitleRes", "resolvedStyleRes", "isDark", "", "DEFAULT", "AURORA", "NORD", "DRACULA", "CATPPUCCIN", "TOKYO_NIGHT", "GRUVBOX", "AMETHYST", "Companion", "app_liteDebug"})
public enum AppTheme {
    /*public static final*/ DEFAULT /* = new DEFAULT(null, 0, 0, 0, null, null, null, null) */,
    /*public static final*/ AURORA /* = new AURORA(null, 0, 0, 0, null, null, null, null) */,
    /*public static final*/ NORD /* = new NORD(null, 0, 0, 0, null, null, null, null) */,
    /*public static final*/ DRACULA /* = new DRACULA(null, 0, 0, 0, null, null, null, null) */,
    /*public static final*/ CATPPUCCIN /* = new CATPPUCCIN(null, 0, 0, 0, null, null, null, null) */,
    /*public static final*/ TOKYO_NIGHT /* = new TOKYO_NIGHT(null, 0, 0, 0, null, null, null, null) */,
    /*public static final*/ GRUVBOX /* = new GRUVBOX(null, 0, 0, 0, null, null, null, null) */,
    /*public static final*/ AMETHYST /* = new AMETHYST(null, 0, 0, 0, null, null, null, null) */;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.String storageKey = null;
    private final int titleRes = 0;
    private final int styleResDark = 0;
    private final int styleResLight = 0;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.String swatchBackground = null;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.String swatchPrimary = null;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.String swatchSecondary = null;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.String swatchTertiary = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.invictus.xmd.ui.theme.AppTheme.Companion Companion = null;
    
    AppTheme(java.lang.String storageKey, @androidx.annotation.StringRes()
    int titleRes, @androidx.annotation.StyleRes()
    int styleResDark, @androidx.annotation.StyleRes()
    int styleResLight, java.lang.String swatchBackground, java.lang.String swatchPrimary, java.lang.String swatchSecondary, java.lang.String swatchTertiary) {
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getStorageKey() {
        return null;
    }
    
    public final int getTitleRes() {
        return 0;
    }
    
    public final int getStyleResDark() {
        return 0;
    }
    
    public final int getStyleResLight() {
        return 0;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getSwatchBackground() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getSwatchPrimary() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getSwatchSecondary() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getSwatchTertiary() {
        return null;
    }
    
    /**
     * Resolves this color theme against the current dark/light mode.
     */
    @androidx.annotation.StyleRes()
    public final int resolvedStyleRes(boolean isDark) {
        return 0;
    }
    
    @org.jetbrains.annotations.NotNull()
    public static kotlin.enums.EnumEntries<com.invictus.xmd.ui.theme.AppTheme> getEntries() {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u0003\u001a\u00020\u00042\b\u0010\u0005\u001a\u0004\u0018\u00010\u0006\u00a8\u0006\u0007"}, d2 = {"Lcom/invictus/xmd/ui/theme/AppTheme$Companion;", "", "()V", "fromKey", "Lcom/invictus/xmd/ui/theme/AppTheme;", "key", "", "app_liteDebug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.invictus.xmd.ui.theme.AppTheme fromKey(@org.jetbrains.annotations.Nullable()
        java.lang.String key) {
            return null;
        }
    }
}